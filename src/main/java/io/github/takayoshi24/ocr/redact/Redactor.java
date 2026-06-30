package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Redactor {

    private final ContentStreamFilter filter = new ContentStreamFilter();

    public void redact(PDDocument document, List<RedactionTarget> targets) throws IOException {
        Map<Integer, List<RedactionTarget>> byPage = targets.stream()
                .collect(Collectors.groupingBy(t -> t.occurrence().page()));

        for (Map.Entry<Integer, List<RedactionTarget>> entry : byPage.entrySet()) {
            int pageIndex = entry.getKey();
            PDPage page = document.getPage(pageIndex);
            List<WordOccurrence> zones = entry.getValue().stream()
                    .map(t -> t.occurrence())
                    .toList();

            // 1. Remove text operators for the redacted words so they can't be copied.
            filter.filter(document, page, zones);

            // 2. Paint a black box over each redacted area.
            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                cs.setNonStrokingColor(Color.BLACK);
                for (WordOccurrence zone : zones) {
                    cs.addRect(zone.x(), zone.y(), zone.width(), zone.height());
                    cs.fill();
                }
            }
        }
    }
}
