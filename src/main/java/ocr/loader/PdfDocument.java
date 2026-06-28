package ocr.loader;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public class PdfDocument implements Closeable {

    private final PDDocument pdDocument;
    private final List<PageType> pageTypes;

    public PdfDocument(PDDocument pdDocument, List<PageType> pageTypes) {
        this.pdDocument = pdDocument;
        this.pageTypes = pageTypes;
    }

    public PDDocument getPdDocument() {
        return pdDocument;
    }

    public List<PageType> getPageTypes() {
        return pageTypes;
    }

    @Override
    public void close() throws IOException {
        pdDocument.close();
    }
}
