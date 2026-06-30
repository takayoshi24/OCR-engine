package io.github.takayoshi24.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @TempDir
    Path tempDir;

    /** Build a single-page PDF with the given text and save it to {@code dest}. */
    private void buildPdf(Path dest, String text) throws IOException {
        try (PDDocument pdDoc = new PDDocument()) {
            PDPage page = new PDPage();
            pdDoc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(pdDoc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            pdDoc.save(dest.toFile());
        }
    }

    @Test
    void happyPath_wordFound_outputIsValidPdf() throws Exception {
        Path input  = tempDir.resolve("input.pdf");
        Path output = tempDir.resolve("output.pdf");

        // "Hello World" is 11 chars >= 10 threshold → TEXT page, no OCR needed
        buildPdf(input, "Hello World");

        Main.main(new String[]{
                input.toAbsolutePath().toString(),
                output.toAbsolutePath().toString(),
                "Hello"
        });

        assertTrue(output.toFile().exists(), "Output file should exist after redaction");
        // Verify the output is a valid PDF that can be reloaded
        try (PDDocument result = Loader.loadPDF(output.toFile())) {
            assertEquals(1, result.getNumberOfPages(), "Output PDF should have 1 page");
        }
    }

    @Test
    void noMatch_wordNotPresent_outputStillExists() throws Exception {
        Path input  = tempDir.resolve("input_nomatch.pdf");
        Path output = tempDir.resolve("output_nomatch.pdf");

        // "Hello World" is 11 chars >= 10 threshold → TEXT page, no OCR needed
        buildPdf(input, "Hello World");

        Main.main(new String[]{
                input.toAbsolutePath().toString(),
                output.toAbsolutePath().toString(),
                "Banana"
        });

        assertTrue(output.toFile().exists(), "Output file should exist even with zero redactions");
        try (PDDocument result = Loader.loadPDF(output.toFile())) {
            assertEquals(1, result.getNumberOfPages(), "Output PDF should have 1 page");
        }
    }

    @Test
    void multipleTargets_allTargetsRedacted_outputIsValidPdf() throws Exception {
        Path input  = tempDir.resolve("input_multi.pdf");
        Path output = tempDir.resolve("output_multi.pdf");

        // "Alice Bob Charlie" is 17 chars >= 10 threshold → TEXT page, no OCR needed
        buildPdf(input, "Alice Bob Charlie");

        Main.main(new String[]{
                input.toAbsolutePath().toString(),
                output.toAbsolutePath().toString(),
                "Alice",
                "Bob"
        });

        assertTrue(output.toFile().exists(), "Output file should exist after multi-target redaction");
        try (PDDocument result = Loader.loadPDF(output.toFile())) {
            assertEquals(1, result.getNumberOfPages(), "Output PDF should have 1 page");
        }
    }
}
