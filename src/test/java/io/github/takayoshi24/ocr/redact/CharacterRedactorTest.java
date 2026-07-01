package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterRedactorTest {

    private final CharacterRedactor redactor = new CharacterRedactor();

    @Test
    void redactString_fontReadCodeThrows_propagatesAsIOException() throws Exception {
        PDFont font = mock(PDFont.class);
        when(font.readCode(any(InputStream.class))).thenThrow(new RuntimeException("bad encoding"));

        COSString str = new COSString(new byte[]{65}); // single byte — ensures readCode is reached
        TextRenderState state = new TextRenderState(12f, 0f, 0f, 100f, 1f, 50f, 700f);

        assertThrows(IOException.class, () ->
                redactor.redactString(str, font, state, List.of()));
    }
}
