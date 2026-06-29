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
                    // PDFBox Y origin is bottom-left; our Y is top-left, so flip
                    float y = pageHeight - target.occurrence.y - target.occurrence.height;
                    float w = target.occurrence.width;
                    float h = target.occurrence.height;

                    cs.addRect(x, y, w, h);
                    cs.fill();
                }
            }
        }
    }
}
