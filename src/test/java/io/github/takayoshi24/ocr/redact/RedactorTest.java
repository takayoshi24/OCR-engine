package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
        try (PDDocument reloaded = PDDocument.load(out.toFile())) {
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
        try (PDDocument reloaded = PDDocument.load(out.toFile())) {
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
        try (PDDocument reloaded = PDDocument.load(out.toFile())) {
            assertEquals(2, reloaded.getNumberOfPages());
        }
    }
}
