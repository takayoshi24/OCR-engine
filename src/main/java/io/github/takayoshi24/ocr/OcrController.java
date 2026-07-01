package io.github.takayoshi24.ocr;

import io.github.takayoshi24.ocr.extract.CompositeExtractor;
import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import io.github.takayoshi24.ocr.find.WordFinder;
import io.github.takayoshi24.ocr.loader.PdfDocument;
import io.github.takayoshi24.ocr.loader.PdfLoader;
import io.github.takayoshi24.ocr.redact.Redactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@RestController
public class OcrController {

    private static final Logger log = LoggerFactory.getLogger(OcrController.class);

    private final CompositeExtractor extractor;
    private final PdfLoader loader;
    private final Redactor redactor;

    OcrController(CompositeExtractor extractor, PdfLoader loader, Redactor redactor) {
        this.extractor = extractor;
        this.loader    = loader;
        this.redactor  = redactor;
    }

    @PostMapping(value = "/api/process", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> process(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "words", defaultValue = "") String words,
            @RequestParam(value = "mode", defaultValue = "CASE_INSENSITIVE") String mode
    ) throws IOException {

        byte[] magic = file.getInputStream().readNBytes(5);
        if (magic.length < 5 || !new String(magic, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("File does not appear to be a PDF".getBytes(StandardCharsets.UTF_8));
        }

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
                return ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(("Invalid mode '" + mode + "'. Valid values: "
                                + Arrays.toString(WordFinder.MatchMode.values())).getBytes(StandardCharsets.UTF_8));
            }

            WordFinder finder = new WordFinder(matchMode);

            long start = System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PdfDocument doc = loader.load(tempInput)) {
                log.info("Processing '{}': {} page(s), {} target(s), mode={}",
                        file.getOriginalFilename(), doc.getPdDocument().getNumberOfPages(),
                        targets.size(), matchMode);
                List<WordOccurrence> extracted = extractor.extractAll(doc.getPdDocument());
                if (!targets.isEmpty()) {
                    List<RedactionTarget> redactions = finder.find(extracted, targets);
                    redactor.redact(doc.getPdDocument(), redactions);
                }
                doc.getPdDocument().save(baos);
            }
            log.info("Completed '{}' in {}ms", file.getOriginalFilename(),
                    System.currentTimeMillis() - start);

            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "output.pdf";
            // Strip control characters and quotes to prevent header injection.
            String safe = original.replaceAll("[\\r\\n\"]", "_");
            String filename = "redacted_" + safe;

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
        String msg = e.getMessage();
        return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body(msg != null ? msg : e.getClass().getSimpleName());
    }
}
