package io.github.takayoshi24.ocr.extract;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.List;

public interface TextExtractor {
    List<WordOccurrence> extract(PDDocument document, int pageIndex) throws IOException;
}
