package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Redactor {

    // Resolution used when flattening a redacted page to an image.
    // Higher = better quality, larger file. 200 DPI is a good balance.
    private static final float DPI = 200f;

    public void redact(PDDocument document, List<RedactionTarget> targets) throws IOException {
        Map<Integer, List<RedactionTarget>> byPage = targets.stream()
                .collect(Collectors.groupingBy(t -> t.occurrence.page));

        // Renderer must be created before any page content is modified.
        PDFRenderer renderer = new PDFRenderer(document);

        for (Map.Entry<Integer, List<RedactionTarget>> entry : byPage.entrySet()) {
            int pageIndex = entry.getKey();
            PDPage page = document.getPage(pageIndex);

            // 1. Render the original page to a raster image.
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, DPI, ImageType.RGB);

            float pageW = page.getBBox().getWidth();
            float pageH = page.getBBox().getHeight();
            float scaleX = image.getWidth()  / pageW;
            float scaleY = image.getHeight() / pageH;

            // 2. Paint black boxes over the sensitive words in the image.
            Graphics2D g = image.createGraphics();
            g.setColor(Color.BLACK);
            for (RedactionTarget target : entry.getValue()) {
                WordOccurrence occ = target.occurrence;
                // occurrence coords are in PDF space (Y from bottom).
                // Image coords have Y from top, so flip Y.
                int px = Math.round(occ.x * scaleX);
                int py = Math.round((pageH - occ.y - occ.height) * scaleY);
                int pw = (int) Math.ceil(occ.width  * scaleX);
                int ph = (int) Math.ceil(occ.height * scaleY);
                g.fillRect(px, py, pw, ph);
            }
            g.dispose();

            // 3. Replace the page content with the flattened image.
            //    AppendMode.OVERWRITE discards all previous content streams,
            //    removing the underlying text so it can no longer be copied.
            PDImageXObject imgXObject = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.OVERWRITE, false)) {
                cs.drawImage(imgXObject, 0, 0, pageW, pageH);
            }
        }
    }
}
