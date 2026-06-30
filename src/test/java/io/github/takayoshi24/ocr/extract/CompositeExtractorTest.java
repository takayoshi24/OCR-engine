package io.github.takayoshi24.ocr.extract;

import io.github.takayoshi24.ocr.PdfTestFixtures;
import io.github.takayoshi24.ocr.loader.PageType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompositeExtractorTest {

    private CompositeExtractor extractor;

    @BeforeEach
    void setUp() {
        // OcrTextExtractor is constructed but never called because all test pages are TEXT.
        extractor = new CompositeExtractor(new EmbeddedTextExtractor(), new OcrTextExtractor("bogus-path"));
    }

    @Test
    void singleTextPage_returnsNonEmptyListContainingWrittenWord() throws IOException {
        try (PDDocument doc = PdfTestFixtures.buildDocument("Hello")) {
            List<WordOccurrence> results = extractor.extractAll(doc, List.of(PageType.TEXT));

            assertFalse(results.isEmpty(), "Expected at least one word from a TEXT page");
            assertTrue(
                results.stream().anyMatch(w -> w.word().equals("Hello")),
                "Expected the word 'Hello' to appear in results"
            );
        }
    }

    @Test
    void twoTextPages_returnsCombinedWordsWithCorrectPageIndices() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PdfTestFixtures.addPage(doc,"Alpha");
            PdfTestFixtures.addPage(doc,"Beta");

            List<WordOccurrence> results = extractor.extractAll(
                doc, List.of(PageType.TEXT, PageType.TEXT)
            );

            // Words from both pages must be present
            assertTrue(results.stream().anyMatch(w -> w.page() == 0),
                "Expected at least one word with page index 0");
            assertTrue(results.stream().anyMatch(w -> w.page() == 1),
                "Expected at least one word with page index 1");

            // Both words must appear somewhere in the results
            assertTrue(results.stream().anyMatch(w -> w.word().equals("Alpha")),
                "Expected 'Alpha' in results");
            assertTrue(results.stream().anyMatch(w -> w.word().equals("Beta")),
                "Expected 'Beta' in results");
        }
    }

    @Test
    void emptyPageTypeList_returnsEmptyList() throws IOException {
        try (PDDocument doc = PdfTestFixtures.buildDocument("Ignored")) {
            List<WordOccurrence> results = extractor.extractAll(doc, List.of());

            assertTrue(results.isEmpty(), "Expected empty list when no page types are provided");
        }
    }

    @Test
    void emptyDocumentWithNoPageTypes_returnsEmptyList() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            List<WordOccurrence> results = extractor.extractAll(doc, List.of());

            assertTrue(results.isEmpty(), "Expected empty list for document with no page-type entries");
        }
    }
}
