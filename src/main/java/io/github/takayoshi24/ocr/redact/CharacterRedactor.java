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

    private record ScanResult(COSArray out, float xAfter, boolean changed) {}

    /**
     * Walk a Tj string character-by-character.  Characters whose user-space X falls
     * inside a redaction zone are replaced with a TJ numeric advance (so surrounding
     * text stays correctly positioned).  tm[4] is updated to the post-Tj X position
     * so that the NEXT text operator starts tracking from the right place.
     */
    List<Object> redactString(COSString str, PDFont font, float fontSize,
                              float tc, float tw, float th,
                              float[] tm, List<WordOccurrence> zones) {
        byte[] raw = str.getBytes();

        if (font == null) {
            float avgAdv = 0.5f * fontSize * tm[0] * (th / 100f);
            float totalAdv = raw.length * avgAdv;
            boolean overlap = inZone(tm[4], tm[5], zones);
            tm[4] += totalAdv;
            return overlap ? List.of() : List.of(str, Operator.getOperator("Tj"));
        }

        ScanResult result = scanString(raw, font, fontSize, tc, tw, th, tm[0], tm[4], tm[5], zones);
        tm[4] = result.xAfter();

        if (!result.changed()) return List.of(str, Operator.getOperator("Tj"));
        if (result.out().size() == 0) return List.of();
        return List.of(result.out(), Operator.getOperator("TJ"));
    }

    /**
     * Same as redactString but handles an existing TJ array whose numeric elements
     * are inter-glyph spacing adjustments (kept unchanged).
     */
    List<Object> redactArray(COSArray arr, PDFont font, float fontSize,
                              float tc, float tw, float th,
                              float[] tm, List<WordOccurrence> zones) {
        if (font == null) {
            boolean anyOverlap = inZone(tm[4], tm[5], zones);
            int charCount = arr.size();
            tm[4] += charCount * 0.5f * fontSize * tm[0] * (th / 100f);
            return anyOverlap ? List.of() : List.of(arr, Operator.getOperator("TJ"));
        }

        COSArray out = new COSArray();
        float x = tm[4];
        boolean changed = false;

        for (COSBase elem : arr) {
            if (elem instanceof COSString str) {
                ScanResult result = scanString(str.getBytes(), font, fontSize, tc, tw, th, tm[0], x, tm[5], zones);
                result.out().forEach(out::add);
                x = result.xAfter();
                if (result.changed()) changed = true;
            } else if (elem instanceof COSNumber num) {
                x += -num.floatValue() / 1000f * fontSize * tm[0] * (th / 100f);
                out.add(elem);
            } else {
                out.add(elem);
            }
        }

        tm[4] = x;

        if (!changed) return List.of(arr, Operator.getOperator("TJ"));
        if (out.size() == 0) return List.of();
        return List.of(out, Operator.getOperator("TJ"));
    }

    private ScanResult scanString(byte[] raw, PDFont font, float fontSize,
                                  float tc, float tw, float th, float tmA,
                                  float x, float y, List<WordOccurrence> zones) {
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
            float advance = charAdvance(glyphW, code, fontSize, tc, tw, th, tmA);

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

    private float charAdvance(float glyphW, int code, float fontSize,
                               float tc, float tw, float th, float tmA) {
        return (glyphW / 1000f * fontSize + tc + (code == 32 ? tw : 0f)) * (th / 100f) * tmA;
    }
}
