package io.github.takayoshi24.ocr.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfExporterTest {

    @TempDir
    Path tempDir;

    // 1. File is created on disk after export()
    @Test
    void exportCreatesFile() throws IOException {
        Path outputPath = tempDir.resolve("output.pdf");
        PdfExporter exporter = new PdfExporter();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            exporter.export(doc, outputPath);
        }

        assertTrue(Files.exists(outputPath), "Exported file should exist on disk");
    }

    // 2. File is a valid PDF: reloading with PDDocument.load() succeeds without exception
    @Test
    void exportedFileIsValidPdf() throws IOException {
        Path outputPath = tempDir.resolve("valid.pdf");
        PdfExporter exporter = new PdfExporter();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            exporter.export(doc, outputPath);
        }

        assertDoesNotThrow(() -> {
            try (PDDocument reloaded = PDDocument.load(outputPath.toFile())) {
                assertNotNull(reloaded);
            }
        }, "Reloading the exported file should not throw any exception");
    }

    // 3. Page count is preserved: a 2-page document saved and reloaded still has 2 pages
    @Test
    void pageCountIsPreserved() throws IOException {
        Path outputPath = tempDir.resolve("two-pages.pdf");
        PdfExporter exporter = new PdfExporter();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            exporter.export(doc, outputPath);
        }

        try (PDDocument reloaded = PDDocument.load(outputPath.toFile())) {
            assertEquals(2, reloaded.getNumberOfPages(), "Reloaded document should have 2 pages");
        }
    }

    // 4. Overwrite: exporting again to the same path succeeds and the file is still valid
    @Test
    void overwriteSucceedsAndFileRemainsValid() throws IOException {
        Path outputPath = tempDir.resolve("overwrite.pdf");
        PdfExporter exporter = new PdfExporter();

        // First export
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            exporter.export(doc, outputPath);
        }

        // Second export to the same path
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            assertDoesNotThrow(() -> exporter.export(doc, outputPath),
                    "Re-exporting to the same path should not throw");
        }

        // File should still be a valid PDF after overwrite
        assertDoesNotThrow(() -> {
            try (PDDocument reloaded = PDDocument.load(outputPath.toFile())) {
                assertNotNull(reloaded);
            }
        }, "File should still be valid after overwrite");
    }
}
