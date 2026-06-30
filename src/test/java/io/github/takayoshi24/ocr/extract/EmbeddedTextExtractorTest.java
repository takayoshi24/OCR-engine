package io.github.takayoshi24.ocr.extract;

import io.github.takayoshi24.ocr.PdfTestFixtures;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedTextExtractorTest {

    private final EmbeddedTextExtractor extractor = new EmbeddedTextExtractor();

    // -----------------------------------------------------------------------
    // Test 1 — single word: word is captured, page is 0, bounding box positive
    // -----------------------------------------------------------------------

    @Test
    void singleWordHasCorrectWordAndPositiveBox() throws IOException {
        try (PDDocument doc = PdfTestFixtures.buildDocument("Hello")) {
            List<WordOccurrence> words = extractor.extract(doc, 0);

            assertEquals(1, words.size(), "Expected exactly one WordOccurrence");

            WordOccurrence w = words.get(0);
            assertEquals("Hello", w.word());
            assertEquals(0, w.page());
            assertTrue(w.x() > 0,      "x should be positive");
            assertTrue(w.y() > 0,      "y should be positive");
            assertTrue(w.width() > 0,  "width should be positive");
            assertTrue(w.height() > 0, "height should be positive");

            // Text placed at newLineAtOffset(50, 700) on 792pt Letter page.
            // occurrence.y is PDF Y from bottom via getYDirAdj() — should be near 700,
            // not near 92 (which would be reading-order from top).
            assertTrue(w.y() > 500,
                    "y should be PDF coords (bottom=0), expected ~700 but got " + w.y());
        }
    }

    // -----------------------------------------------------------------------
    // Test 2 — space-separated words become separate WordOccurrences
    // -----------------------------------------------------------------------

    @Test
    void spaceSeparatedWordsProduceSeparateOccurrences() throws IOException {
        try (PDDocument doc = PdfTestFixtures.buildDocument("foo bar baz")) {
            List<WordOccurrence> words = extractor.extract(doc, 0);

            assertEquals(3, words.size(), "Three space-separated tokens should yield three occurrences");
            assertEquals("foo", words.get(0).word());
            assertEquals("bar", words.get(1).word());
            assertEquals("baz", words.get(2).word());
        }
    }

    // -----------------------------------------------------------------------
    // Test 3 — page index is respected in a two-page document
    // -----------------------------------------------------------------------

    @Test
    void extractFromSecondPageReturnsPageIndex1() throws IOException {
        // Page 0 has different text so any cross-page bleed would be obvious.
        try (PDDocument doc = PdfTestFixtures.buildDocument("PageZero", "PageOne")) {
            List<WordOccurrence> words = extractor.extract(doc, 1);

            assertFalse(words.isEmpty(), "Should find words on the second page");
            for (WordOccurrence w : words) {
                assertEquals(1, w.page(), "All occurrences must report page index 1");
            }
            // Sanity: the extracted words come from page 1's text
            assertTrue(words.stream().anyMatch(w -> w.word().contains("PageOne")),
                    "Expected to find the word 'PageOne' extracted from page index 1");
        }
    }

    // -----------------------------------------------------------------------
    // Test 4 — blank page returns empty list
    // -----------------------------------------------------------------------

    @Test
    void blankPageReturnsEmptyList() throws IOException {
        // Pass an empty string so buildDocument creates a page with no content.
        try (PDDocument doc = PdfTestFixtures.buildDocument("")) {
            List<WordOccurrence> words = extractor.extract(doc, 0);

            assertNotNull(words, "Result list must not be null");
            assertTrue(words.isEmpty(), "A blank page should yield no WordOccurrences");
        }
    }
}
