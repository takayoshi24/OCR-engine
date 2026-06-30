package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
                              List<WordOccurrence> zones) {
        byte[] raw = str.getBytes();
        float[] tm = state.tm();

        if (font == null) {
            float avgAdv = 0.5f * state.fontSize() * tm[0] * (state.th() / 100f);
            float newX = tm[4] + raw.length * avgAdv;
            boolean overlap = inZone(tm[4], tm[5], zones);
            return new RedactResult(overlap ? List.of() : List.of(str, Operator.getOperator("Tj")), newX);
        }

        ScanResult result = scanString(raw, font, state, tm[4], tm[5], zones);

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
                             List<WordOccurrence> zones) {
        float[] tm = state.tm();

        if (font == null) {
            boolean anyOverlap = inZone(tm[4], tm[5], zones);
            float newX = tm[4] + arr.size() * 0.5f * state.fontSize() * tm[0] * (state.th() / 100f);
            return new RedactResult(anyOverlap ? List.of() : List.of(arr, Operator.getOperator("TJ")), newX);
        }

        COSArray out = new COSArray();
        float x = tm[4];
        boolean changed = false;

        for (COSBase elem : arr) {
            if (elem instanceof COSString str) {
                ScanResult result = scanString(str.getBytes(), font, state, x, tm[5], zones);
                result.out().forEach(out::add);
                x = result.xAfter();
                if (result.changed()) changed = true;
            } else if (elem instanceof COSNumber num) {
                x += -num.floatValue() / 1000f * state.fontSize() * tm[0] * (state.th() / 100f);
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
                                  float x, float y, List<WordOccurrence> zones) {
        float tmA = state.tm()[0];
        ByteArrayInputStream bis = new ByteArrayInputStream(raw);
        COSArray out = new COSArray();
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        boolean changed = false;

        while (bis.available() > 0) {
            int pos = raw.length - bis.available();
            int code;
            try { code = font.readCode(bis); } catch (Exception e) { break; }
            int posAfter = raw.length - bis.available();
            byte[] charRaw = Arrays.copyOfRange(raw, pos, posAfter);

            float glyphW  = glyphWidth(font, code);
            float advance = charAdvance(glyphW, code, state, tmA);

            if (inZone(x, y, zones)) {
                changed = true;
                if (kept.size() > 0) { out.add(new COSString(kept.toByteArray())); kept.reset(); }
                out.add(new COSFloat(-glyphW));
            } else {
                try { kept.write(charRaw); } catch (Exception ignored) {}
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
