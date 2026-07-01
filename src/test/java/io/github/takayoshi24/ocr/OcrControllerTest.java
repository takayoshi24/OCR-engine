package io.github.takayoshi24.ocr;

import io.github.takayoshi24.ocr.extract.CompositeExtractor;
import io.github.takayoshi24.ocr.loader.PdfDocument;
import io.github.takayoshi24.ocr.loader.PdfLoader;
import io.github.takayoshi24.ocr.redact.Redactor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OcrControllerTest {

    private MockMvc mockMvc;
    private PdfLoader loader;
    private CompositeExtractor extractor;

    @BeforeEach
    void setUp() {
        loader    = mock(PdfLoader.class);
        extractor = mock(CompositeExtractor.class);
        OcrController controller = new OcrController(
                extractor, loader, mock(Redactor.class), Executors.newCachedThreadPool(), 300);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void invalidMode_returns400WithHelpfulMessage() throws Exception {
        mockMvc.perform(multipart("/api/process")
                        .file(minimalPdfFile("test.pdf"))
                        .param("mode", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("BOGUS")))
                .andExpect(content().string(containsString("EXACT")));
    }

    @Test
    void validMode_exact_returns200() throws Exception {
        stubLoader();
        mockMvc.perform(multipart("/api/process")
                        .file(minimalPdfFile("test.pdf"))
                        .param("mode", "EXACT"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void filenameWithCrLf_sanitisedInContentDispositionHeader() throws Exception {
        stubLoader();
        mockMvc.perform(multipart("/api/process")
                        .file(minimalPdfFile("evil\r\ninjected.pdf")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("\r"))))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("\n"))));
    }

    @Test
    void loaderThrows_exceptionHandlerReturnsMessageWithoutStackTrace() throws Exception {
        when(loader.load(any(Path.class))).thenThrow(new IOException("disk full"));
        mockMvc.perform(multipart("/api/process")
                        .file(minimalPdfFile("test.pdf")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("disk full"))
                .andExpect(content().string(not(containsString("at io.github"))));
    }

    @Test
    void nonPdfContent_returns415BeforeWritingToDisk() throws Exception {
        MockMultipartFile notPdf = new MockMultipartFile(
                "file", "evil.pdf", "application/pdf", "not a pdf at all".getBytes());
        mockMvc.perform(multipart("/api/process").file(notPdf))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().string("File does not appear to be a PDF"));
        verifyNoInteractions(loader);
    }

    @Test
    void emptyFile_returns415() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);
        mockMvc.perform(multipart("/api/process").file(empty))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().string("File does not appear to be a PDF"));
        verifyNoInteractions(loader);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pipelineTimeout_returns503WithHelpfulMessage() throws Exception {
        Future<byte[]> timeoutFuture = mock(Future.class);
        when(timeoutFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
        ExecutorService mockPipeline = mock(ExecutorService.class);
        when(mockPipeline.submit(any(Callable.class))).thenReturn((Future) timeoutFuture);

        OcrController timeoutController = new OcrController(
                extractor, loader, mock(Redactor.class), mockPipeline, 300);
        MockMvc timeoutMvc = MockMvcBuilders.standaloneSetup(timeoutController).build();

        timeoutMvc.perform(multipart("/api/process").file(minimalPdfFile("big.pdf")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("timed out")));
    }

    // ---- helpers ----

    private void stubLoader() throws Exception {
        PDDocument pdDoc = new PDDocument();
        pdDoc.addPage(new PDPage());
        when(loader.load(any(Path.class))).thenReturn(new PdfDocument(pdDoc));
        when(extractor.extractAll(any(PDDocument.class))).thenReturn(List.of());
    }

    private MockMultipartFile minimalPdfFile(String filename) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new MockMultipartFile("file", filename, "application/pdf", baos.toByteArray());
        }
    }
}
