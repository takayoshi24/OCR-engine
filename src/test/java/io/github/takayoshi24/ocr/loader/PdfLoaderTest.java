package io.github.takayoshi24.ocr.loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
        writePdfWithText(pdf, "Hello World this is enough text to pass the threshold");

        try (PdfDocument doc = new PdfLoader().load(pdf)) {
            List<PageType> types = doc.getPageTypes();
            assertEquals(1, types.size());
            assertEquals(PageType.TEXT, types.get(0));
        }
    }

    @Test
    void blankPageClassifiedAsImage() throws IOException {
        Path pdf = tempDir.resolve("blank.pdf");
        writePdfWithText(pdf, "");

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
            addPage(pdDoc, "Sufficient embedded text on this page");
            addPage(pdDoc, "");  // blank / image-only
            addPage(pdDoc, "Another text page with content");
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
        writePdfWithText(pdf, "test content");

        PdfDocument doc = new PdfLoader().load(pdf);
        assertDoesNotThrow(doc::close);
    }

    private void writePdfWithText(Path path, String text) throws IOException {
        try (PDDocument pdDoc = new PDDocument()) {
            addPage(pdDoc, text);
            pdDoc.save(path.toFile());
        }
    }

    private void addPage(PDDocument pdDoc, String text) throws IOException {
        PDPage page = new PDPage();
        pdDoc.addPage(page);
        if (!text.isEmpty()) {
            try (PDPageContentStream cs = new PDPageContentStream(pdDoc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
        }
    }
}
