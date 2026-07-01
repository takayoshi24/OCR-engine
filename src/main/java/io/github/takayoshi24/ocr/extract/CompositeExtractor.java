package io.github.takayoshi24.ocr.extract;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompositeExtractor {

    // Pages whose embedded text totals fewer characters than this are treated as image-only.
    private static final int TEXT_THRESHOLD = 10;

    private final EmbeddedTextExtractor embeddedExtractor;
    private final OcrTextExtractor ocrExtractor;

    public CompositeExtractor(EmbeddedTextExtractor embeddedExtractor, OcrTextExtractor ocrExtractor) {
        this.embeddedExtractor = embeddedExtractor;
        this.ocrExtractor = ocrExtractor;
    }

    public List<WordOccurrence> extractAll(PDDocument document) throws IOException {
        List<WordOccurrence> all = new ArrayList<>();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            List<WordOccurrence> embedded = embeddedExtractor.extract(document, i);
            int charCount = embedded.stream().mapToInt(w -> w.word().length()).sum();
            List<WordOccurrence> pageWords = charCount >= TEXT_THRESHOLD ? embedded
                                                                         : ocrExtractor.extract(document, i);
            all.addAll(pageWords);
        }

        return all;
    }
}
