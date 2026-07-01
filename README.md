# OCR Engine

A PDF redaction service that finds and permanently removes words from both text-based and scanned PDFs. Runs as a CLI tool, a REST API, or a browser-based web UI.

Redaction removes text from the PDF content stream **and** paints a black rectangle over it — the text cannot be recovered by copy-paste or PDF search.

## How it works

Each page is classified automatically:

- **Text page** — embedded text is extracted directly via PDFBox (fast, preserves exact coordinates)
- **Image/scanned page** — the page is rasterised at 300 DPI and passed through Tesseract OCR

Target words are matched against extracted tokens in one of three modes (`EXACT`, `CASE_INSENSITIVE`, `REGEX`), located by bounding box, and redacted in place.

## Prerequisites

- **Java 21+** — `java --version`
- **Maven 3.6+** — `mvn --version`
- **Tesseract 4+** with English language data — see [Tesseract installation](https://tesseract-ocr.github.io/tessdoc/Installation.html)

Verify Tesseract is working:

```bash
tesseract --version
```

The `eng.traineddata` file must exist in the Tesseract data directory. On most Linux systems this is `/usr/share/tesseract-ocr/4.00/tessdata`. On macOS via Homebrew it is `/usr/local/share/tessdata` or `/opt/homebrew/share/tessdata`.

## Build

```bash
mvn package -DskipTests
```

Produces `target/OCR-engine-1.0-SNAPSHOT.jar`.

To build and run all tests:

```bash
mvn package
```

## Configuration

The application reads `src/main/resources/application.properties` and a profile-specific overlay.

| Property | Default | Description |
|----------|---------|-------------|
| `tessdata.prefix` | `tessdata` (dev) / `/usr/share/tesseract-ocr/4.00/tessdata` (prod) | Path to the Tesseract data directory |
| `server.port` | `8080` | HTTP port (server mode only) |
| `ocr.pipeline.thread-pool-size` | `4` | Maximum concurrent PDF processing jobs |
| `ocr.pipeline.timeout-seconds` | `300` | Per-request hard timeout (seconds); returns 503 if exceeded |
| `spring.servlet.multipart.max-file-size` | `100MB` | Maximum upload size |

**Profiles:**

- `dev` (default) — looks for Tesseract data in a local `tessdata/` directory relative to the working directory; DEBUG logging enabled
- `prod` — uses the system Tesseract path; WARN logging

Override the active profile:

```bash
java -Dspring.profiles.active=prod -jar target/OCR-engine-1.0-SNAPSHOT.jar
```

Override the Tesseract data path without changing the profile:

```bash
java -Dtessdata.prefix=/path/to/tessdata -jar target/OCR-engine-1.0-SNAPSHOT.jar
```

## Usage

### CLI

```bash
java -jar target/OCR-engine-1.0-SNAPSHOT.jar <input.pdf> <output.pdf> <word1> [word2 ...]
```

Match mode is `CASE_INSENSITIVE` in CLI mode.

**Example — redact a name and a number:**

```bash
java -jar target/OCR-engine-1.0-SNAPSHOT.jar report.pdf redacted.pdf "John Doe" "123-45-6789"
```

```
Found 3 occurrence(s) to redact across 5 page(s)
Saved: redacted.pdf
```

### REST API

Start the server:

```bash
java -jar target/OCR-engine-1.0-SNAPSHOT.jar
```

The server starts on `http://localhost:8080`.

#### `POST /api/process`

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | multipart file | yes | Input PDF (max 100 MB) |
| `words` | string | no | Comma-separated words to redact |
| `mode` | string | no | `EXACT`, `CASE_INSENSITIVE` (default), or `REGEX` |

Returns the redacted PDF as `application/pdf` with a `Content-Disposition: attachment` header.

**Error responses:**

| Status | Cause |
|--------|-------|
| `400` | Invalid `mode` value, regex pattern too long (> 200 chars), or invalid regex syntax |
| `415` | Upload is not a PDF (magic-byte check) |
| `503` | Processing exceeded `ocr.pipeline.timeout-seconds` |
| `500` | Unexpected server error |

**Example — curl:**

```bash
curl -X POST http://localhost:8080/api/process \
  -F "file=@report.pdf" \
  -F "words=John Doe,confidential" \
  -F "mode=CASE_INSENSITIVE" \
  --output redacted.pdf
```

**Example — REGEX mode:**

```bash
curl -X POST http://localhost:8080/api/process \
  -F "file=@report.pdf" \
  -F "words=\d{3}-\d{2}-\d{4}" \
  -F "mode=REGEX" \
  --output redacted.pdf
```

REGEX mode uses [RE2/J](https://github.com/google/re2j) (linear-time engine — no ReDoS exposure). Backreferences and lookaheads are not supported.

#### Health check

```
GET /actuator/health → 200 {"status":"UP"}
```

### Web UI

With the server running, open `http://localhost:8080` in a browser. The drag-and-drop interface lets you upload a PDF, enter words to redact, select a match mode, and download the result directly.

## Testing

```bash
mvn test
```

Runs 55 tests across unit and integration test classes. No external services are required — Tesseract calls are mocked in tests that exercise the OCR path.

## Project structure

```
src/main/java/io/github/takayoshi24/ocr/
├── OcrApplication.java          # Spring Boot entry point (server mode)
├── Main.java                    # CLI entry point
├── OcrController.java           # REST endpoint — POST /api/process
├── OcrConfiguration.java        # Spring bean wiring
├── loader/
│   ├── PdfLoader.java           # Opens a PDF file into a PdfDocument
│   └── PdfDocument.java         # Closeable wrapper around PDDocument
├── extract/
│   ├── TextExtractor.java       # Interface
│   ├── EmbeddedTextExtractor.java  # PDFBox embedded text extraction
│   ├── OcrTextExtractor.java    # Tess4J OCR (300 DPI rasterisation)
│   ├── CompositeExtractor.java  # Per-page routing: embedded or OCR
│   └── WordOccurrence.java      # Word text + PDF-coordinate bounding box
├── find/
│   ├── WordFinder.java          # Matches target strings against WordOccurrences
│   └── RedactionTarget.java     # A matched occurrence with its pattern
└── redact/
    ├── Redactor.java            # Orchestrates redaction across pages
    ├── ContentStreamFilter.java # Rewrites the PDF content stream
    ├── CharacterRedactor.java   # Suppresses individual glyphs
    └── TextRenderState.java     # Tracks font and transform matrix during parsing
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot Web | 3.3.0 | REST API and embedded server |
| Spring Boot Actuator | 3.3.0 | Health and metrics endpoints |
| Apache PDFBox | 3.0.4 | PDF parsing and content stream rewriting |
| Tess4J | 5.11.0 | Tesseract OCR Java bindings |
| RE2/J | 1.7 | Linear-time regex matching |
| JUnit Jupiter | 5.10.2 | Tests |
| Mockito | 5.11.0 | Test mocking |

## Troubleshooting

**`Tesseract OCR failed on page N`**  
The Tesseract data directory is wrong or missing `eng.traineddata`. Confirm the path:

```bash
ls /usr/share/tesseract-ocr/4.00/tessdata/eng.traineddata
```

Then pass the correct path:

```bash
java -Dtessdata.prefix=/correct/path -jar target/OCR-engine-1.0-SNAPSHOT.jar ...
```

**HTTP 415 — "File does not appear to be a PDF"**  
The uploaded file does not start with the `%PDF-` magic bytes. Confirm the file is a valid PDF.

**HTTP 503 — Processing timed out**  
The PDF is large or OCR-heavy. Increase `ocr.pipeline.timeout-seconds` in `application.properties`, or split the document.

**HTTP 400 — "Regex pattern too long"**  
REGEX patterns are capped at 200 characters to prevent excessively complex compilations.
