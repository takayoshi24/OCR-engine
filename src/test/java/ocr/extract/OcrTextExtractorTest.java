package ocr.extract;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class OcrTextExtractorTest {

    // -----------------------------------------------------------------------
    // Test 1: public constructor must not throw for any (even bogus) path
    // -----------------------------------------------------------------------

    @Test
    void constructorDoesNotThrowForBogusPath() {
        assertDoesNotThrow(() -> new OcrTextExtractor("/non/existent/tessdata"));
    }

    // -----------------------------------------------------------------------
    // Tests 2 & 3: when getWords() throws, extract() must wrap in IOException
    //
    // Tess4J 5.x on Windows silently returns an empty list when tessdata is
    // missing, so we inject a mock Tesseract (via the package-private ctor)
    // that throws a RuntimeException to exercise the catch block reliably.
    // -----------------------------------------------------------------------

    @Test
    void extractThrowsIOExceptionWhenTesseractFails() throws IOException {
        Tesseract mockTesseract = mock(Tesseract.class);
        when(mockTesseract.getWords(any(java.awt.image.BufferedImage.class), anyInt()))
                .thenThrow(new RuntimeException("simulated tessdata failure"));

        OcrTextExtractor extractor = new OcrTextExtractor(mockTesseract);

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());

            IOException ex = assertThrows(IOException.class,
                    () -> extractor.extract(doc, 0));

            assertTrue(ex.getMessage().contains("Tesseract OCR failed"),
                    "Expected message to contain 'Tesseract OCR failed', but was: " + ex.getMessage());
        }
    }

    @Test
    void extractWrapsOriginalCause() throws IOException {
        Tesseract mockTesseract = mock(Tesseract.class);
        when(mockTesseract.getWords(any(java.awt.image.BufferedImage.class), anyInt()))
                .thenThrow(new RuntimeException("simulated tessdata failure"));

        OcrTextExtractor extractor = new OcrTextExtractor(mockTesseract);

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());

            IOException ex = assertThrows(IOException.class,
                    () -> extractor.extract(doc, 0));

            assertNotNull(ex.getCause(),
                    "Expected IOException to wrap a cause, but getCause() was null");
        }
    }
}
