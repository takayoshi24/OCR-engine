package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

class CharacterRedactor {

    private static final float PAD_X = 2f;
    private static final float PAD_Y = 8f;

    record RedactResult(List<Object> tokens, float newX) {}

    private record ScanResult(COSArray out, float xAfter, boolean changed) {}

    /**
     * Walk a Tj string character-by-character. Characters whose user-space X falls
     * inside a redaction zone are replaced with a TJ numeric advance (so surrounding
     * text stays correctly positioned). Returns the updated X position in the result
     * rather than mutating tm[4] — the caller is responsible for assigning it.
     */
    RedactResult redactString(COSString str, PDFont font, TextRenderState state,
                              List<WordOccurrence> zones) throws IOException {
        byte[] raw = str.getBytes();

        if (font == null) {
            float avgAdv = 0.5f * state.fontSize() * state.tmA() * (state.th() / 100f);
            float newX = state.tmX() + raw.length * avgAdv;
            boolean overlap = inZone(state.tmX(), state.tmY(), zones);
            return new RedactResult(overlap ? List.of() : List.of(str, Operator.getOperator("Tj")), newX);
        }

        ScanResult result = scanString(raw, font, state, state.tmX(), state.tmY(), zones);

        List<Object> tokens;
        if (!result.changed()) tokens = List.of(str, Operator.getOperator("Tj"));
        else if (result.out().size() == 0) tokens = List.of();
        else tokens = List.of(result.out(), Operator.getOperator("TJ"));

        return new RedactResult(tokens, result.xAfter());
    }

    /**
     * Same as redactString but handles an existing TJ array whose numeric elements
     * are inter-glyph spacing adjustments (kept unchanged). Returns the updated X
     * position in the result — the caller is responsible for assigning it.
     */
    RedactResult redactArray(COSArray arr, PDFont font, TextRenderState state,
                             List<WordOccurrence> zones) throws IOException {
        if (font == null) {
            boolean anyOverlap = inZone(state.tmX(), state.tmY(), zones);
            float newX = state.tmX() + arr.size() * 0.5f * state.fontSize() * state.tmA() * (state.th() / 100f);
            return new RedactResult(anyOverlap ? List.of() : List.of(arr, Operator.getOperator("TJ")), newX);
        }

        COSArray out = new COSArray();
        float x = state.tmX();
        boolean changed = false;

        for (COSBase elem : arr) {
            if (elem instanceof COSString str) {
                ScanResult result = scanString(str.getBytes(), font, state, x, state.tmY(), zones);
                result.out().forEach(out::add);
                x = result.xAfter();
                if (result.changed()) changed = true;
            } else if (elem instanceof COSNumber num) {
                x += -num.floatValue() / 1000f * state.fontSize() * state.tmA() * (state.th() / 100f);
                out.add(elem);
            } else {
                out.add(elem);
            }
        }

        List<Object> tokens;
        if (!changed) tokens = List.of(arr, Operator.getOperator("TJ"));
        else if (out.size() == 0) tokens = List.of();
        else tokens = List.of(out, Operator.getOperator("TJ"));

        return new RedactResult(tokens, x);
    }

    private ScanResult scanString(byte[] raw, PDFont font, TextRenderState state,
                                  float x, float y, List<WordOccurrence> zones) throws IOException {
        float tmA = state.tmA();
        ByteArrayInputStream bis = new ByteArrayInputStream(raw);
        COSArray out = new COSArray();
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        boolean changed = false;

        while (bis.available() > 0) {
            int pos = raw.length - bis.available();
            int code;
            try { code = font.readCode(bis); } catch (Exception e) {
                throw new IOException("Failed to read glyph code at byte offset " + pos, e);
            }
            int posAfter = raw.length - bis.available();
            byte[] charRaw = Arrays.copyOfRange(raw, pos, posAfter);

            float glyphW  = glyphWidth(font, code);
            float advance = charAdvance(glyphW, code, state, tmA);

            if (inZone(x, y, zones)) {
                changed = true;
                if (kept.size() > 0) { out.add(new COSString(kept.toByteArray())); kept.reset(); }
                out.add(new COSFloat(-glyphW));
            } else {
                kept.write(charRaw);
            }
            x += advance;
        }
        if (kept.size() > 0) out.add(new COSString(kept.toByteArray()));

        return new ScanResult(out, x, changed);
    }

    private boolean inZone(float x, float y, List<WordOccurrence> zones) {
        for (WordOccurrence z : zones) {
            if (x >= z.x() - PAD_X && x < z.x() + z.width() + PAD_X
             && y >= z.y() - PAD_Y && y <= z.y() + z.height() + PAD_Y) {
                return true;
            }
        }
        return false;
    }

    private float glyphWidth(PDFont font, int code) {
        try { return font.getWidth(code); } catch (Exception e) { return 500f; }
    }

    private float charAdvance(float glyphW, int code, TextRenderState state, float tmA) {
        return (glyphW / 1000f * state.fontSize() + state.tc() + (code == 32 ? state.tw() : 0f))
                * (state.th() / 100f) * tmA;
    }
}
