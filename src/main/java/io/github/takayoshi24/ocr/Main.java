package io.github.takayoshi24.ocr;

import io.github.takayoshi24.ocr.extract.CompositeExtractor;
import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import io.github.takayoshi24.ocr.find.WordFinder;
import io.github.takayoshi24.ocr.loader.PdfDocument;
import io.github.takayoshi24.ocr.loader.PdfLoader;
import io.github.takayoshi24.ocr.redact.Redactor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: ocr-engine <input.pdf> <output.pdf> <word1> [word2 ...]");
            System.exit(1);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        List<String> targets = Arrays.asList(args).subList(2, args.length);

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(OcrApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {

            PdfLoader loader = ctx.getBean(PdfLoader.class);
            CompositeExtractor extractor = ctx.getBean(CompositeExtractor.class);
            WordFinder finder = new WordFinder(WordFinder.MatchMode.CASE_INSENSITIVE);
            Redactor redactor = ctx.getBean(Redactor.class);

            try (PdfDocument doc = loader.load(input)) {
                List<WordOccurrence> words = extractor.extractAll(doc.getPdDocument());
                List<RedactionTarget> redactions = finder.find(words, targets);

                System.out.printf("Found %d occurrence(s) to redact across %d page(s)%n",
                        redactions.size(), doc.getPdDocument().getNumberOfPages());

                redactor.redact(doc.getPdDocument(), redactions);
                doc.getPdDocument().save(output.toFile());

                System.out.println("Saved: " + output);
            }
        }
    }
}
