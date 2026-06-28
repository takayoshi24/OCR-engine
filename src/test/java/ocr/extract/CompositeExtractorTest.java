package ocr.extract;

import ocr.loader.PageType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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

    // Helper: build an in-memory PDDocument with one page containing the given text.
    private PDDocument singlePageDoc(String text) throws IOException {
        PDDocument doc = new PDDocument();
        addPage(doc, text);
        return doc;
    }

    private void addPage(PDDocument doc, String text) throws IOException {
        PDPage page = new PDPage();
        doc.addPage(page);
        if (!text.isEmpty()) {
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
        }
    }

    @Test
    void singleTextPage_returnsNonEmptyListContainingWrittenWord() throws IOException {
        try (PDDocument doc = singlePageDoc("Hello")) {
            List<WordOccurrence> results = extractor.extractAll(doc, List.of(PageType.TEXT));

            assertFalse(results.isEmpty(), "Expected at least one word from a TEXT page");
            assertTrue(
                results.stream().anyMatch(w -> w.word.equals("Hello")),
                "Expected the word 'Hello' to appear in results"
            );
        }
    }

    @Test
    void twoTextPages_returnsCombinedWordsWithCorrectPageIndices() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            addPage(doc, "Alpha");
            addPage(doc, "Beta");

            List<WordOccurrence> results = extractor.extractAll(
                doc, List.of(PageType.TEXT, PageType.TEXT)
            );

            // Words from both pages must be present
            assertTrue(results.stream().anyMatch(w -> w.page == 0),
                "Expected at least one word with page index 0");
            assertTrue(results.stream().anyMatch(w -> w.page == 1),
                "Expected at least one word with page index 1");

            // Both words must appear somewhere in the results
            assertTrue(results.stream().anyMatch(w -> w.word.equals("Alpha")),
                "Expected 'Alpha' in results");
            assertTrue(results.stream().anyMatch(w -> w.word.equals("Beta")),
                "Expected 'Beta' in results");
        }
    }

    @Test
    void emptyPageTypeList_returnsEmptyList() throws IOException {
        try (PDDocument doc = singlePageDoc("Ignored")) {
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
