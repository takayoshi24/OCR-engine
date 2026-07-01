package io.github.takayoshi24.ocr.loader;

import io.github.takayoshi24.ocr.PdfTestFixtures;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void load_singlePagePdf_returnsDocumentWithOnePage() throws IOException {
        Path pdf = tempDir.resolve("single.pdf");
        PdfTestFixtures.saveToFile("Hello World", pdf);

        try (PdfDocument doc = new PdfLoader().load(pdf)) {
            assertEquals(1, doc.getPdDocument().getNumberOfPages());
        }
    }

    @Test
    void load_multiPagePdf_returnsDocumentWithAllPages() throws IOException {
        Path pdf = tempDir.resolve("multi.pdf");
        try (PDDocument pdDoc = new PDDocument()) {
            PdfTestFixtures.addPage(pdDoc, "Page one");
            PdfTestFixtures.addPage(pdDoc, "");
            PdfTestFixtures.addPage(pdDoc, "Page three");
            pdDoc.save(pdf.toFile());
        }

        try (PdfDocument doc = new PdfLoader().load(pdf)) {
            assertEquals(3, doc.getPdDocument().getNumberOfPages());
        }
    }

    @Test
    void load_blankPage_returnsDocument() throws IOException {
        Path pdf = tempDir.resolve("blank.pdf");
        PdfTestFixtures.saveToFile("", pdf);

        try (PdfDocument doc = new PdfLoader().load(pdf)) {
            assertEquals(1, doc.getPdDocument().getNumberOfPages());
        }
    }

    @Test
    void pdfDocumentIsCloseable() throws IOException {
        Path pdf = tempDir.resolve("close.pdf");
        PdfTestFixtures.saveToFile("test content", pdf);

        PdfDocument doc = new PdfLoader().load(pdf);
        assertDoesNotThrow(doc::close);
    }
}
