package ocr;

import ocr.export.PdfExporter;
import ocr.extract.CompositeExtractor;
import ocr.extract.EmbeddedTextExtractor;
import ocr.extract.OcrTextExtractor;
import ocr.extract.WordOccurrence;
import ocr.find.RedactionTarget;
import ocr.find.WordFinder;
import ocr.loader.PdfDocument;
import ocr.loader.PdfLoader;
import ocr.redact.Redactor;

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

        // Tesseract data path — override with TESSDATA_PREFIX env var if set
        String tessData = System.getenv().getOrDefault("TESSDATA_PREFIX", "/usr/share/tesseract-ocr/4.00/tessdata");

        PdfLoader loader = new PdfLoader();
        CompositeExtractor extractor = new CompositeExtractor(
                new EmbeddedTextExtractor(),
                new OcrTextExtractor(tessData)
        );
        WordFinder finder = new WordFinder(WordFinder.MatchMode.CASE_INSENSITIVE);
        Redactor redactor = new Redactor();
        PdfExporter exporter = new PdfExporter();

        try (PdfDocument doc = loader.load(input)) {
            List<WordOccurrence> words = extractor.extractAll(doc.getPdDocument(), doc.getPageTypes());
            List<RedactionTarget> redactions = finder.find(words, targets);

            System.out.printf("Found %d occurrence(s) to redact across %d page(s)%n",
                    redactions.size(), doc.getPdDocument().getNumberOfPages());

            redactor.redact(doc.getPdDocument(), redactions);
            exporter.export(doc.getPdDocument(), output);

            System.out.println("Saved: " + output);
        }
    }
}
