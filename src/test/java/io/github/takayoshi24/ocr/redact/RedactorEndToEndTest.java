package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.EmbeddedTextExtractor;
import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import io.github.takayoshi24.ocr.find.WordFinder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests that run EmbeddedTextExtractor → WordFinder → Redactor
 * the same way the HTTP controller does, so coordinate translation is identical.
 */
class RedactorEndToEndTest {

    @TempDir Path tempDir;

    private final EmbeddedTextExtractor extractor = new EmbeddedTextExtractor();
    private final WordFinder finder = new WordFinder(WordFinder.MatchMode.CASE_INSENSITIVE);
    private final Redactor  redactor = new Redactor();

    // ---- helpers ----

    private PDDocument buildSingleTjPage(String text) throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.LETTER);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(50, 700);
            cs.showText(text);       // ONE Tj for the whole string
            cs.endText();
        }
        return doc;
    }

    private PDDocument buildMultiTjPage(String... words) throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.LETTER);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(50, 700);
            for (String w : words) {
                cs.showText(w); // consecutive Tj calls without Td between them
            }
            cs.endText();
        }
        return doc;
    }

    // ---- tests ----

    @Test
    void singleTj_secretInMiddle_removedButSurroundingTextKept() throws IOException {
        Path out = tempDir.resolve("e2e-single.pdf");
        try (PDDocument doc = buildSingleTjPage("Hello secret World")) {
            List<WordOccurrence> words = extractor.extract(doc, 0);
            System.out.println("[e2e-single] extracted words:");
            words.forEach(w -> System.out.printf("  '%s'  x=%.1f y=%.1f w=%.1f h=%.1f%n",
                    w.word, w.x, w.y, w.width, w.height));

            List<RedactionTarget> targets = finder.find(words, List.of("secret"));
            System.out.println("[e2e-single] redaction targets: " + targets.size());
            assertFalse(targets.isEmpty(), "should find 'secret'");

            redactor.redact(doc, targets);
            doc.save(out.toFile());
        }
        try (PDDocument reloaded = PDDocument.load(out.toFile())) {
            String text = new PDFTextStripper().getText(reloaded);
            System.out.println("[e2e-single] after redact: '" + text.trim() + "'");
            assertTrue(text.contains("Hello"),   "Hello must stay selectable");
            assertTrue(text.contains("World"),   "World must stay selectable");
            assertFalse(text.contains("secret"), "secret must be gone");
        }
    }

    @Test
    void multiConsecutiveTj_secretInMiddle_removedButSurroundingTextKept() throws IOException {
        // Each word is a separate Tj; no Td between them.
        // This tests that tm[4] advances correctly after each Tj.
        Path out = tempDir.resolve("e2e-multi.pdf");
        try (PDDocument doc = buildMultiTjPage("Hello ", "secret", " World")) {
            List<WordOccurrence> words = extractor.extract(doc, 0);
            System.out.println("[e2e-multi] extracted words:");
            words.forEach(w -> System.out.printf("  '%s'  x=%.1f y=%.1f w=%.1f h=%.1f%n",
                    w.word, w.x, w.y, w.width, w.height));

            List<RedactionTarget> targets = finder.find(words, List.of("secret"));
            assertFalse(targets.isEmpty(), "should find 'secret'");

            redactor.redact(doc, targets);
            doc.save(out.toFile());
        }
        try (PDDocument reloaded = PDDocument.load(out.toFile())) {
            String text = new PDFTextStripper().getText(reloaded);
            System.out.println("[e2e-multi] after redact: '" + text.trim() + "'");
            assertTrue(text.contains("Hello"),   "Hello must stay selectable");
            assertTrue(text.contains("World"),   "World must stay selectable");
            assertFalse(text.contains("secret"), "secret must be gone");
        }
    }
}
