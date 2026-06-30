# OCR Engine

A PDF redaction tool built on Apache PDFBox and Tesseract. It extracts text from both
text-based and scanned (image-only) PDFs, locates target words, and permanently removes
them — both visually (black box) and from the text layer.

## Features

- Extracts embedded text from native PDF pages (PDFBox)
- OCR fallback for scanned / image-only pages (Tess4J / Tesseract)
- Per-page auto-detection: text pages use the fast embedded path; image pages fall back to OCR
- Three match modes: `EXACT`, `CASE_INSENSITIVE`, `REGEX`
- Redaction removes text from the content stream *and* paints a black rectangle — the text cannot be recovered by copy-paste or PDF search
- Usable as a CLI tool or a Spring Boot REST API

## Requirements

- Java 21
- Maven 3.x
- Tesseract 4+ installed with language data (`eng.traineddata` at minimum)

## Build

```bash
mvn package -DskipTests
```

The fat JAR is produced at `target/OCR-engine-1.0-SNAPSHOT.jar`.

## CLI usage

```bash
java -jar target/OCR-engine-1.0-SNAPSHOT.jar <input.pdf> <output.pdf> <word1> [word2 ...]
```

Example:

```bash
java -jar target/OCR-engine-1.0-SNAPSHOT.jar report.pdf redacted.pdf "John Doe" "123-45-6789"
```

Tesseract data path defaults to `/usr/share/tesseract-ocr/4.00/tessdata`. Override with:

```bash
TESSDATA_PREFIX=/path/to/tessdata java -jar ...
```

## REST API usage

Start the server:

```bash
java -jar target/OCR-engine-1.0-SNAPSHOT.jar
```

The server listens on port 8080 by default.

### `POST /api/process`

| Parameter | Type            | Required | Description                                      |
|-----------|-----------------|----------|--------------------------------------------------|
| `file`    | multipart/form  | yes      | Input PDF                                        |
| `words`   | string          | no       | Comma-separated list of words to redact          |
| `mode`    | string          | no       | `EXACT`, `CASE_INSENSITIVE` (default), or `REGEX`|

Returns the redacted PDF as `application/pdf`.

Example with curl:

```bash
curl -X POST http://localhost:8080/api/process \
  -F "file=@report.pdf" \
  -F "words=John Doe,secret" \
  -F "mode=CASE_INSENSITIVE" \
  --output redacted.pdf
```

## Running tests

```bash
mvn test
```

## Project structure

```
src/main/java/io/github/takayoshi24/ocr/
├── OcrApplication.java          # Spring Boot entry point
├── OcrController.java           # REST API
├── Main.java                    # CLI entry point
├── loader/
│   ├── PdfLoader.java           # Opens PDF and classifies pages
│   ├── PdfDocument.java
│   └── PageType.java            # TEXT or IMAGE
├── extract/
│   ├── TextExtractor.java       # Interface
│   ├── EmbeddedTextExtractor.java  # PDFBox text extraction
│   ├── OcrTextExtractor.java    # Tess4J OCR extraction
│   ├── CompositeExtractor.java  # Routes per page type
│   └── WordOccurrence.java      # Word + bounding box
├── find/
│   ├── WordFinder.java          # Matches targets against occurrences
│   └── RedactionTarget.java
├── redact/
│   ├── Redactor.java            # Page orchestration
│   ├── ContentStreamFilter.java # PDF content stream rewriting
│   └── CharacterRedactor.java   # Glyph-level suppression
└── export/
    └── PdfExporter.java         # Saves output PDF
```

## Dependencies

| Library             | Version | Purpose                        |
|---------------------|---------|--------------------------------|
| Spring Boot Web     | 3.3.0   | REST API                       |
| Apache PDFBox       | 3.0.4   | PDF parsing and content stream |
| Tess4J              | 5.11.0  | Tesseract OCR bindings         |
| JUnit Jupiter       | 5.10.2  | Unit tests                     |
| Mockito             | 5.11.0  | Test mocking                   |
