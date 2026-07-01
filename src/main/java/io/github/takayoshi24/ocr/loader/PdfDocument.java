package io.github.takayoshi24.ocr.loader;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.Closeable;
import java.io.IOException;

public class PdfDocument implements Closeable {

    private final PDDocument pdDocument;

    public PdfDocument(PDDocument pdDocument) {
        this.pdDocument = pdDocument;
    }

    public PDDocument getPdDocument() {
        return pdDocument;
    }

    @Override
    public void close() throws IOException {
        pdDocument.close();
    }
}
