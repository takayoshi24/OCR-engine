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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
public class OcrController {

    private static final Logger log = LoggerFactory.getLogger(OcrController.class);

    private final CompositeExtractor extractor;
    private final PdfLoader loader;
    private final Redactor redactor;
    private final ExecutorService pipeline;
    private final long timeoutSeconds;

    // Spring injection constructor — pool size and timeout are configurable via properties.
    @Autowired
    public OcrController(CompositeExtractor extractor, PdfLoader loader, Redactor redactor,
                         @Value("${ocr.pipeline.thread-pool-size:4}") int threadPoolSize,
                         @Value("${ocr.pipeline.timeout-seconds:300}") long timeoutSeconds) {
        this(extractor, loader, redactor, Executors.newFixedThreadPool(threadPoolSize), timeoutSeconds);
    }

    // Package-private for testing — allows injecting a mock ExecutorService.
    OcrController(CompositeExtractor extractor, PdfLoader loader, Redactor redactor,
                  ExecutorService pipeline, long timeoutSeconds) {
        this.extractor      = extractor;
        this.loader         = loader;
        this.redactor       = redactor;
        this.pipeline       = pipeline;
        this.timeoutSeconds = timeoutSeconds;
    }

    @PostMapping(value = "/api/process", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> process(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "words", defaultValue = "") String words,
            @RequestParam(value = "mode", defaultValue = "CASE_INSENSITIVE") String mode
    ) throws IOException {

        byte[] magic;
        try (InputStream is = file.getInputStream()) {
            magic = is.readNBytes(5);
        }
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
            String originalFilename = file.getOriginalFilename();

            Future<byte[]> job = pipeline.submit(
                    () -> runPipeline(tempInput, targets, finder, originalFilename));
            byte[] pdf;
            try {
                pdf = job.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                job.cancel(true);
                log.warn("Request timed out after {}s for '{}'", timeoutSeconds, originalFilename);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(("Processing timed out after " + timeoutSeconds + "s — "
                                + "try a smaller file or fewer pages").getBytes(StandardCharsets.UTF_8));
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException ioEx) throw ioEx;
                if (cause instanceof IllegalArgumentException) {
                    return ResponseEntity.badRequest()
                            .contentType(MediaType.TEXT_PLAIN)
                            .body(cause.getMessage().getBytes(StandardCharsets.UTF_8));
                }
                throw new IOException("Pipeline failed", cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Processing interrupted", e);
            }

            String name = (originalFilename != null ? originalFilename : "output.pdf");
            String disposition = ContentDisposition.attachment()
                    .filename("redacted_" + name, StandardCharsets.UTF_8)
                    .build()
                    .toString();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } finally {
            try {
                Files.deleteIfExists(tempInput);
            } catch (java.nio.file.AccessDeniedException e) {
                // On Windows a cancelled pipeline thread may still hold the file open via PDFBox.
                // runPipeline deletes the file once PDFBox closes; deleteOnExit is the safety net.
                tempInput.toFile().deleteOnExit();
                log.debug("Temp file still locked, scheduled for deletion on exit: {}", tempInput);
            }
        }
    }

    private byte[] runPipeline(Path tempInput, List<String> targets, WordFinder finder,
                               String originalFilename) throws IOException {
        long start = System.currentTimeMillis();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument doc = loader.load(tempInput)) {
            log.info("Processing '{}': {} page(s), {} target(s)",
                    originalFilename, doc.getPdDocument().getNumberOfPages(), targets.size());
            List<WordOccurrence> extracted = extractor.extractAll(doc.getPdDocument());
            if (!targets.isEmpty()) {
                List<RedactionTarget> redactions = finder.find(extracted, targets);
                redactor.redact(doc.getPdDocument(), redactions);
            }
            doc.getPdDocument().save(baos);
        } finally {
            // PDFBox has released its handles by here; safe to delete on any OS.
            Files.deleteIfExists(tempInput);
        }
        log.info("Completed '{}' in {}ms", originalFilename, System.currentTimeMillis() - start);
        return baos.toByteArray();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleError(Exception e) {
        String msg = e.getMessage();
        return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body(msg != null ? msg : e.getClass().getSimpleName());
    }
}
