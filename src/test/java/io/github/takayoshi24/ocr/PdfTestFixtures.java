package io.github.takayoshi24.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Path;

public final class PdfTestFixtures {

    private PdfTestFixtures() {}

    /**
     * Adds one page to {@code doc} with Helvetica-12 text at (50, 700).
     * Passing an empty string adds a blank page with no content stream.
     */
    public static void addPage(PDDocument doc, String text) throws IOException {
        PDPage page = new PDPage();
        doc.addPage(page);
        if (!text.isEmpty()) {
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
        }
    }

    /** Returns an in-memory PDDocument with one page per supplied text. */
    public static PDDocument buildDocument(String... pageTexts) throws IOException {
        PDDocument doc = new PDDocument();
        for (String text : pageTexts) {
            addPage(doc, text != null ? text : "");
        }
        return doc;
    }

    /** Saves a single-page document with the given text to {@code dest}. */
    public static void saveToFile(String text, Path dest) throws IOException {
        try (PDDocument doc = buildDocument(text)) {
            doc.save(dest.toFile());
        }
    }
}
