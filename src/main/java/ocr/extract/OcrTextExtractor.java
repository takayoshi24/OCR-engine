package ocr.extract;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OcrTextExtractor implements TextExtractor {

    private static final int DPI = 300;

    private final Tesseract tesseract;

    public OcrTextExtractor(String tessDataPath) {
        tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);
    }

    /** Package-private constructor for testing — allows injecting a pre-configured Tesseract. */
    OcrTextExtractor(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    @Override
    public List<WordOccurrence> extract(PDDocument document, int pageIndex) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, DPI, ImageType.GRAY);

        PDPage page = document.getPage(pageIndex);
        float pageHeightPt = page.getMediaBox().getHeight();
        float pageWidthPt = page.getMediaBox().getWidth();
        float scaleX = pageWidthPt / image.getWidth();
        float scaleY = pageHeightPt / image.getHeight();

        List<Word> words;
        try {
            words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        } catch (Exception e) {
            throw new IOException("Tesseract OCR failed on page " + pageIndex, e);
        }

        List<WordOccurrence> results = new ArrayList<>();
        for (Word word : words) {
            java.awt.Rectangle r = word.getBoundingBox();
            float x = r.x * scaleX;
            float y = pageHeightPt - (r.y + r.height) * scaleY; // flip Y axis to PDF coords
            float w = r.width * scaleX;
            float h = r.height * scaleY;
            results.add(new WordOccurrence(word.getText().trim(), pageIndex, x, y, w, h));
        }

        return results;
    }
}
