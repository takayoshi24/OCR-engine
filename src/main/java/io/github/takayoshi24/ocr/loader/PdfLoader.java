package io.github.takayoshi24.ocr.loader;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class PdfLoader {

    public PdfDocument load(Path path) throws IOException {
        PDDocument pdDocument = Loader.loadPDF(path.toFile());
        try {
            return new PdfDocument(pdDocument);
        } catch (Exception e) {
            pdDocument.close();
            throw e;
        }
    }
}
