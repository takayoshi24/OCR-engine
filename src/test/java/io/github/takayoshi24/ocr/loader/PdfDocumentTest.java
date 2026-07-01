package io.github.takayoshi24.ocr.loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfDocumentTest {

    @Test
    void getPageTypes_returnsUnmodifiableList() throws IOException {
        // PdfLoader builds an ArrayList — mirror that so the test reflects the real production path.
        List<PageType> mutableList = new ArrayList<>(List.of(PageType.TEXT, PageType.IMAGE));
        try (PdfDocument doc = new PdfDocument(new PDDocument(), mutableList)) {
            assertThrows(UnsupportedOperationException.class, () -> doc.getPageTypes().clear());
        }
    }
}
