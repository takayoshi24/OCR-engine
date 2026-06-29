package io.github.takayoshi24.ocr.extract;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EmbeddedTextExtractor implements TextExtractor {

    @Override
    public List<WordOccurrence> extract(PDDocument document, int pageIndex) throws IOException {
        List<WordOccurrence> results = new ArrayList<>();

        // In PDFBox 2.x, TextPosition.getY() stores reading-order Y from the top
        // (not PDF Y from the bottom). To recover the PDF baseline Y from the bottom
        // we compute: pageH - pos.getY().
        PDPage page = document.getPage(pageIndex);
        final float pageH = page.getBBox().getHeight();

        PDFTextStripper stripper = new PDFTextStripper() {
            private final StringBuilder wordBuffer = new StringBuilder();
            private float wordX, wordY, wordWidth, wordHeight;

            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                for (TextPosition pos : positions) {
                    String c = pos.getUnicode();
                    if (c == null || c.isBlank()) {
                        flush(results, pageIndex);
                    } else {
                        if (wordBuffer.isEmpty()) {
                            wordX = pos.getXDirAdj();
                            // pageH - pos.getY() = PDF baseline Y from bottom.
                            // Shift down 30% of glyph height to cover descenders.
                            float baselineY = pageH - pos.getY();
                            wordY = baselineY - pos.getHeightDir() * 0.3f;
                            wordHeight = pos.getHeightDir() * 1.5f;
                            wordWidth = 0;
                        }
                        wordBuffer.append(c);
                        wordWidth += pos.getWidthDirAdj();
                    }
                }
                flush(results, pageIndex);
            }

            private void flush(List<WordOccurrence> out, int page) {
                if (!wordBuffer.isEmpty()) {
                    out.add(new WordOccurrence(wordBuffer.toString(), page, wordX, wordY, wordWidth, wordHeight));
                    wordBuffer.setLength(0);
                }
            }
        };

        stripper.setSortByPosition(true);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document); // triggers writeString callbacks

        return results;
    }
}
