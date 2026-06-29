package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.find.RedactionTarget;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Redactor {

    public void redact(PDDocument document, List<RedactionTarget> targets) throws IOException {
        // Group targets by page so we open each page's content stream once
        Map<Integer, List<RedactionTarget>> byPage = targets.stream()
                .collect(Collectors.groupingBy(t -> t.occurrence.page));

        for (Map.Entry<Integer, List<RedactionTarget>> entry : byPage.entrySet()) {
            int pageIndex = entry.getKey();
            PDPage page = document.getPage(pageIndex);
            float pageHeight = page.getMediaBox().getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                cs.setNonStrokingColor(Color.BLACK);

                for (RedactionTarget target : entry.getValue()) {
                    float x = target.occurrence.x;
                    float w = target.occurrence.width;
                    // occurrence.y is the baseline measured from the top of the page.
                    // PDFBox content stream uses bottom-left origin, so baseline in PDF Y =
                    // pageHeight - occurrence.y. Characters sit above the baseline (~75%) with
                    // descenders below (~25%), so offset down by 25% and extend up by full height.
                    float h = target.occurrence.height;
                    float y = pageHeight - target.occurrence.y - h * 0.25f;

                    cs.addRect(x, y, w, h);
                    cs.fill();
                }
            }
        }
    }
}
