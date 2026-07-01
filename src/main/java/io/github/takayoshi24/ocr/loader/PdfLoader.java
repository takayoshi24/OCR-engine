package io.github.takayoshi24.ocr.loader;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfLoader {

    // Pages with fewer characters than this threshold are treated as image-only
    private static final int TEXT_THRESHOLD = 10;

    public PdfDocument load(Path path) throws IOException {
        PDDocument pdDocument = Loader.loadPDF(path.toFile());
        try {
            List<PageType> pageTypes = classifyPages(pdDocument);
            return new PdfDocument(pdDocument, pageTypes);
        } catch (IOException e) {
            pdDocument.close();
            throw e;
        }
    }

    private List<PageType> classifyPages(PDDocument document) throws IOException {
        List<PageType> types = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper();

        for (int i = 1; i <= document.getNumberOfPages(); i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String text = stripper.getText(document).trim();
            types.add(text.length() >= TEXT_THRESHOLD ? PageType.TEXT : PageType.IMAGE);
        }

        return types;
    }
}
