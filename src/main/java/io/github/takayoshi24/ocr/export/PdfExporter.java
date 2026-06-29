package io.github.takayoshi24.ocr.export;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Path;

public class PdfExporter {

    public void export(PDDocument document, Path outputPath) throws IOException {
        document.save(outputPath.toFile());
    }
}
