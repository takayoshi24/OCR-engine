package ocr.extract;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class EmbeddedTextExtractor implements TextExtractor {

    @Override
    public List<WordOccurrence> extract(PDDocument document, int pageIndex) throws IOException {
        List<WordOccurrence> results = new ArrayList<>();

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
                            wordY = pos.getYDirAdj();
                            wordHeight = pos.getHeightDir();
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
