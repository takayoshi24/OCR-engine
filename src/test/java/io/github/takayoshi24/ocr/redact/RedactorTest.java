package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedactorTest {

    @TempDir
    Path tempDir;

    private final Redactor redactor = new Redactor();

    /** Helper: build a PDDocument with the given number of blank pages. */
    private PDDocument buildDocument(int pageCount) {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < pageCount; i++) {
            doc.addPage(new PDPage());
        }
        return doc;
    }

    /** Helper: create a RedactionTarget at plausible coordinates on the given page. */
    private RedactionTarget target(int page) {
        WordOccurrence occ = new WordOccurrence("secret", page, 50f, 700f, 60f, 12f);
        return new RedactionTarget(occ, "secret");
    }

    @Test
    void emptyTargetList_doesNotThrowAndDocumentUnchanged() throws IOException {
        try (PDDocument doc = buildDocument(1)) {
            assertDoesNotThrow(() -> redactor.redact(doc, List.of()));
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    void singleTargetOnPage0_canBeSavedAndReloaded() throws IOException {
        Path out = tempDir.resolve("single.pdf");
        try (PDDocument doc = buildDocument(1)) {
            redactor.redact(doc, List.of(target(0)));
            doc.save(out.toFile());
        }
        try (PDDocument reloaded = Loader.loadPDF(out.toFile())) {
            assertEquals(1, reloaded.getNumberOfPages());
        }
    }

    @Test
    void multipleTargetsOnSamePage_succeedsAndPageCountUnchanged() throws IOException {
        Path out = tempDir.resolve("multi-same.pdf");
        try (PDDocument doc = buildDocument(1)) {
            List<RedactionTarget> targets = List.of(target(0), target(0), target(0));
            redactor.redact(doc, targets);
            doc.save(out.toFile());
        }
        try (PDDocument reloaded = Loader.loadPDF(out.toFile())) {
            assertEquals(1, reloaded.getNumberOfPages());
        }
    }

    @Test
    void targetsOnDifferentPages_succeedsAndBothPagesRetained() throws IOException {
        Path out = tempDir.resolve("multi-page.pdf");
        try (PDDocument doc = buildDocument(2)) {
            List<RedactionTarget> targets = List.of(target(0), target(1));
            redactor.redact(doc, targets);
            doc.save(out.toFile());
        }
        try (PDDocument reloaded = Loader.loadPDF(out.toFile())) {
            assertEquals(2, reloaded.getNumberOfPages());
        }
    }

    /**
     * Core surgical-redaction test: "Hello" and "World" are placed on the same line.
     * Only "World" is in the redaction zone. After redaction, PDFTextStripper must
     * still find "Hello" (text remains selectable) but must NOT find "World"
     * (its Tj has been removed from the content stream).
     */
    @Test
    void surgicalRedact_nonRedactedWordRemainsExtractable() throws IOException {
        Path out = tempDir.resolve("surgical.pdf");

        // Build a PDF with real text: "Hello" at (50, 700) and "World" offset 100pts right.
        // PDPageContentStream emits:  BT  /Helvetica 12 Tf  50 700 Td  (Hello) Tj
        //                             100 0 Td  (World) Tj  ET
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Hello");
                cs.newLineAtOffset(100, 0); // World starts at x=150, y=700
                cs.showText("World");
                cs.endText();
            }

            // Zone for "World": Tm will be (150, 700) when that Tj executes.
            // EmbeddedTextExtractor stores y = baseline - height*0.3, height = fontSize*1.5.
            float fontSize = 12f;
            float baselineY = 700f;
            float boxH = fontSize * 1.5f;
            float boxY = baselineY - fontSize * 0.3f; // ≈ 696.4
            float boxW = 40f;                          // wider than "World" at 12pt
            WordOccurrence zone = new WordOccurrence("World", 0, 150f, boxY, boxW, boxH);

            redactor.redact(doc, List.of(new RedactionTarget(zone, "World")));
            doc.save(out.toFile());
        }

        try (PDDocument reloaded = Loader.loadPDF(out.toFile())) {
            String text = new PDFTextStripper().getText(reloaded);
            assertTrue(text.contains("Hello"),
                    "Non-redacted word must still be extractable, got: " + text);
            assertFalse(text.contains("World"),
                    "Redacted word must be absent from content stream, got: " + text);
        }
    }

    /**
     * Verifies the case that previously failed: "secret" is embedded in the MIDDLE of a
     * single Tj string ("Hello secret World").  Character-level position tracking must
     * remove only "secret" while keeping "Hello" and "World" selectable.
     */
    @Test
    void surgicalRedact_wordInMiddleOfSingleTj() throws IOException {
        Path out = tempDir.resolve("mid-word.pdf");
        final float fontSize = 12f;

        // Compute zone coords using PDFBox's own font metrics so our estimates match
        // what the content stream will actually use.
        PDType1Font helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float helloSpaceW = helvetica.getStringWidth("Hello ") / 1000f * fontSize;
        float secretW     = helvetica.getStringWidth("secret") / 1000f * fontSize;
        float secretX     = 50f + helloSpaceW;
        float boxY        = 700f - fontSize * 0.3f;
        float boxH        = fontSize * 1.5f;

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            // Emit the entire phrase as ONE Tj — the failing case in the previous approach.
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(helvetica, fontSize);
                cs.newLineAtOffset(50, 700);
                cs.showText("Hello secret World");
                cs.endText();
            }

            WordOccurrence zone = new WordOccurrence("secret", 0, secretX, boxY, secretW, boxH);
            redactor.redact(doc, List.of(new RedactionTarget(zone, "secret")));
            doc.save(out.toFile());
        }

        try (PDDocument reloaded = Loader.loadPDF(out.toFile())) {
            String text = new PDFTextStripper().getText(reloaded);
            assertTrue(text.contains("Hello"),   "Hello must remain selectable");
            assertTrue(text.contains("World"),   "World must remain selectable");
            assertFalse(text.contains("secret"), "secret must be removed from content stream");
        }
    }
}
