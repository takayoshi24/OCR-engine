package io.github.takayoshi24.ocr;

import io.github.takayoshi24.ocr.extract.CompositeExtractor;
import io.github.takayoshi24.ocr.extract.EmbeddedTextExtractor;
import io.github.takayoshi24.ocr.extract.OcrTextExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OcrConfiguration {

    // Re-using a single CompositeExtractor avoids reloading Tesseract language
    // data (≈50 MB) on every request.
    @Bean
    public CompositeExtractor compositeExtractor(EmbeddedTextExtractor embedded, OcrTextExtractor ocr) {
        return new CompositeExtractor(embedded, ocr);
    }
}
