package io.github.takayoshi24.ocr.extract;

import io.github.takayoshi24.ocr.PdfTestFixtures;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class CompositeExtractorTest {

    // -----------------------------------------------------------------------
    // Text pages — embedded extraction path
    // -----------------------------------------------------------------------

    @Test
    void singleTextPage_returnsNonEmptyListContainingWrittenWord() throws IOException {
        CompositeExtractor extractor = new CompositeExtractor(
                new EmbeddedTextExtractor(), new OcrTextExtractor("bogus-path"));

        try (PDDocument doc = PdfTestFixtures.buildDocument("Hello World")) {
            List<WordOccurrence> results = extractor.extractAll(doc);

            assertFalse(results.isEmpty(), "Expected at least one word from a text page");
            assertTrue(results.stream().anyMatch(w -> w.word().equals("Hello")),
                    "Expected 'Hello' in results");
        }
    }

    @Test
    void twoTextPages_returnsCombinedWordsWithCorrectPageIndices() throws IOException {
        CompositeExtractor extractor = new CompositeExtractor(
                new EmbeddedTextExtractor(), new OcrTextExtractor("bogus-path"));

        try (PDDocument doc = new PDDocument()) {
            PdfTestFixtures.addPage(doc, "Alpha text here long enough");
            PdfTestFixtures.addPage(doc, "Beta text here long enough");

            List<WordOccurrence> results = extractor.extractAll(doc);

            assertTrue(results.stream().anyMatch(w -> w.page() == 0), "Expected words on page 0");
            assertTrue(results.stream().anyMatch(w -> w.page() == 1), "Expected words on page 1");
            assertTrue(results.stream().anyMatch(w -> w.word().equals("Alpha")), "Expected 'Alpha'");
            assertTrue(results.stream().anyMatch(w -> w.word().equals("Beta")), "Expected 'Beta'");
        }
    }

    @Test
    void emptyDocument_returnsEmptyList() throws IOException {
        CompositeExtractor extractor = new CompositeExtractor(
                new EmbeddedTextExtractor(), new OcrTextExtractor("bogus-path"));

        try (PDDocument doc = new PDDocument()) {
            assertTrue(extractor.extractAll(doc).isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Image / blank pages — OCR fallback path
    // -----------------------------------------------------------------------

    @Test
    void blankPage_fallsBackToOcrExtractor() throws IOException {
        WordOccurrence fakeOcrResult = new WordOccurrence("OCR-word", 0, 0f, 0f, 10f, 10f);
        OcrTextExtractor mockOcr = mock(OcrTextExtractor.class);
        when(mockOcr.extract(any(PDDocument.class), anyInt())).thenReturn(List.of(fakeOcrResult));

        CompositeExtractor extractor = new CompositeExtractor(new EmbeddedTextExtractor(), mockOcr);

        // Blank page → embedded produces 0 chars (< threshold) → OCR fallback
        try (PDDocument doc = PdfTestFixtures.buildDocument("")) {
            List<WordOccurrence> results = extractor.extractAll(doc);

            assertEquals(1, results.size());
            assertEquals("OCR-word", results.get(0).word());
            verify(mockOcr).extract(any(PDDocument.class), eq(0));
        }
    }

    @Test
    void textPage_doesNotCallOcrExtractor() throws IOException {
        OcrTextExtractor mockOcr = mock(OcrTextExtractor.class);
        CompositeExtractor extractor = new CompositeExtractor(new EmbeddedTextExtractor(), mockOcr);

        // "Hello World" = 10 chars → exactly at threshold → embedded path, no OCR
        try (PDDocument doc = PdfTestFixtures.buildDocument("Hello World")) {
            extractor.extractAll(doc);
            verifyNoInteractions(mockOcr);
        }
    }
}
