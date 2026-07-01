package io.github.takayoshi24.ocr.loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PdfDocumentTest {

    @Test
    void getPdDocument_returnsProvidedDocument() throws IOException {
        try (PDDocument pdDoc = new PDDocument();
             PdfDocument doc = new PdfDocument(pdDoc)) {
            assertSame(pdDoc, doc.getPdDocument());
        }
    }
}
