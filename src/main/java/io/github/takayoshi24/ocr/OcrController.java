package io.github.takayoshi24.ocr;

import io.github.takayoshi24.ocr.extract.CompositeExtractor;
import io.github.takayoshi24.ocr.extract.EmbeddedTextExtractor;
import io.github.takayoshi24.ocr.extract.OcrTextExtractor;
import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import io.github.takayoshi24.ocr.find.WordFinder;
import io.github.takayoshi24.ocr.loader.PdfDocument;
import io.github.takayoshi24.ocr.loader.PdfLoader;
import io.github.takayoshi24.ocr.redact.Redactor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@RestController
public class OcrController {

    @PostMapping(value = "/api/process", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> process(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "words", defaultValue = "") String words,
            @RequestParam(value = "mode", defaultValue = "CASE_INSENSITIVE") String mode
    ) throws Exception {

        String tessData = System.getenv().getOrDefault("TESSDATA_PREFIX", "tessdata");

        Path tempInput = Files.createTempFile("ocr-", ".pdf");
        try {
            file.transferTo(tempInput);

            List<String> targets = Arrays.stream(words.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            WordFinder.MatchMode matchMode;
            try {
                matchMode = WordFinder.MatchMode.valueOf(mode);
            } catch (IllegalArgumentException e) {
                matchMode = WordFinder.MatchMode.CASE_INSENSITIVE;
            }

            PdfLoader loader = new PdfLoader();
            CompositeExtractor extractor = new CompositeExtractor(
                    new EmbeddedTextExtractor(), new OcrTextExtractor(tessData));
            WordFinder finder = new WordFinder(matchMode);
            Redactor redactor = new Redactor();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PdfDocument doc = loader.load(tempInput)) {
                List<WordOccurrence> extracted = extractor.extractAll(doc.getPdDocument(), doc.getPageTypes());
                if (!targets.isEmpty()) {
                    List<RedactionTarget> redactions = finder.find(extracted, targets);
                    redactor.redact(doc.getPdDocument(), redactions);
                }
                doc.getPdDocument().save(baos);
            }

            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "output.pdf";
            String filename = "redacted_" + original;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(baos.toByteArray());

        } finally {
            Files.deleteIfExists(tempInput);
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleError(Exception e) {
        return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }
}
