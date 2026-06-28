package ocr.extract;

import ocr.loader.PageType;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompositeExtractor {

    private final EmbeddedTextExtractor embeddedExtractor;
    private final OcrTextExtractor ocrExtractor;

    public CompositeExtractor(EmbeddedTextExtractor embeddedExtractor, OcrTextExtractor ocrExtractor) {
        this.embeddedExtractor = embeddedExtractor;
        this.ocrExtractor = ocrExtractor;
    }

    public List<WordOccurrence> extractAll(PDDocument document, List<PageType> pageTypes) throws IOException {
        List<WordOccurrence> all = new ArrayList<>();

        for (int i = 0; i < pageTypes.size(); i++) {
            TextExtractor extractor = pageTypes.get(i) == PageType.TEXT ? embeddedExtractor : ocrExtractor;
            all.addAll(extractor.extract(document, i));
        }

        return all;
    }
}
