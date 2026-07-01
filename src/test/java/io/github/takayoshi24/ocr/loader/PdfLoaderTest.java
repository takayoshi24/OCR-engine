package io.github.takayoshi24.ocr.loader;

import io.github.takayoshi24.ocr.PdfTestFixtures;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void textPageClassifiedAsText() throws IOException {
        Path pdf = tempDir.resolve("text.pdf");
        PdfTestFixtures.saveToFile("Hello World this is enough text to pass the threshold", pdf);

        try (PdfDocument doc = new PdfLoader().load(pdf)) {
            List<PageType> types = doc.getPageTypes();
            assertEquals(1, types.size());
            assertEquals(PageType.TEXT, types.get(0));
        }
    }

    @Test
    void blankPageClassifiedAsImage() throws IOException {
        Path pdf = tempDir.resolve("blank.pdf");
        PdfTestFixtures.saveToFile("", pdf);

        try (PdfDocument doc = new PdfLoader().load(pdf)) {
            List<PageType> types = doc.getPageTypes();
            assertEquals(1, types.size());
            assertEquals(PageType.IMAGE, types.get(0));
        }
    }

    @Test
    void multiPageMixedClassification() throws IOException {
        Path pdf = tempDir.resolve("mixed.pdf");
        try (PDDocument pdDoc = new PDDocument()) {
            PdfTestFixtures.addPage(pdDoc, "Sufficient embedded text on this page");
            PdfTestFixtures.addPage(pdDoc, "");  // blank / image-only
            PdfTestFixtures.addPage(pdDoc, "Another text page with content");
            pdDoc.save(pdf.toFile());
        }

        try (PdfDocument doc = new PdfLoader().load(pdf)) {
            List<PageType> types = doc.getPageTypes();
            assertEquals(3, types.size());
            assertEquals(PageType.TEXT,  types.get(0));
            assertEquals(PageType.IMAGE, types.get(1));
            assertEquals(PageType.TEXT,  types.get(2));
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
