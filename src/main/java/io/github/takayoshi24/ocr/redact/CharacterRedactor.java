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
            boolean overlap = false;
            for (WordOccurrence z : zones) {
                if (tm[4] + totalAdv >= z.x && tm[4] <= z.x + z.width
                 && tm[5] >= z.y - 8f && tm[5] <= z.y + z.height + 8f) {
                    overlap = true; break;
                }
            }
            tm[4] += totalAdv;
            return overlap ? List.of() : List.of(str, Operator.getOperator("Tj"));
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(raw);
        COSArray out = new COSArray();
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        float x = tm[4];
        boolean changed = false;

        while (bis.available() > 0) {
            int pos = raw.length - bis.available();
            int code;
            try { code = font.readCode(bis); } catch (Exception e) { break; }
            int posAfter = raw.length - bis.available();
            byte[] charRaw = Arrays.copyOfRange(raw, pos, posAfter);

            float glyphW  = glyphWidth(font, code);
            float advance = charAdvance(glyphW, code, fontSize, tc, tw, th, tm[0]);

            if (inZone(x, tm[5], zones)) {
                changed = true;
                if (kept.size() > 0) { out.add(new COSString(kept.toByteArray())); kept.reset(); }
                out.add(new COSFloat(-glyphW));
            } else {
                try { kept.write(charRaw); } catch (Exception ignored) {}
            }
            x += advance;
        }
        if (kept.size() > 0) out.add(new COSString(kept.toByteArray()));

        tm[4] = x;

        if (!changed) return List.of(str, Operator.getOperator("Tj"));
        if (out.size() == 0) return List.of();
        return List.of(out, Operator.getOperator("TJ"));
    }

    /**
     * Same as redactString but handles an existing TJ array whose numeric elements
     * are inter-glyph spacing adjustments (kept unchanged).
     */
    List<Object> redactArray(COSArray arr, PDFont font, float fontSize,
                              float tc, float tw, float th,
                              float[] tm, List<WordOccurrence> zones) {
        if (font == null) {
            boolean anyOverlap = false;
            for (WordOccurrence z : zones) {
                if (tm[5] >= z.y - 8f && tm[5] <= z.y + z.height + 8f
                 && tm[4] <= z.x + z.width && inZone(tm[4], tm[5], zones)) {
                    anyOverlap = true; break;
                }
            }
            int charCount = arr.size();
            tm[4] += charCount * 0.5f * fontSize * tm[0] * (th / 100f);
            return anyOverlap ? List.of() : List.of(arr, Operator.getOperator("TJ"));
        }

        COSArray out = new COSArray();
        float x = tm[4];
        boolean changed = false;

        for (COSBase elem : arr) {
            if (elem instanceof COSString str) {
                byte[] raw = str.getBytes();
                ByteArrayInputStream bis = new ByteArrayInputStream(raw);
                ByteArrayOutputStream kept = new ByteArrayOutputStream();

                while (bis.available() > 0) {
                    int pos = raw.length - bis.available();
                    int code;
                    try { code = font.readCode(bis); } catch (Exception e) { break; }
                    int posAfter = raw.length - bis.available();
                    byte[] charRaw = Arrays.copyOfRange(raw, pos, posAfter);

                    float glyphW  = glyphWidth(font, code);
                    float advance = charAdvance(glyphW, code, fontSize, tc, tw, th, tm[0]);

                    if (inZone(x, tm[5], zones)) {
                        changed = true;
                        if (kept.size() > 0) { out.add(new COSString(kept.toByteArray())); kept.reset(); }
                        out.add(new COSFloat(-glyphW));
                    } else {
                        try { kept.write(charRaw); } catch (Exception ignored) {}
                    }
                    x += advance;
                }
                if (kept.size() > 0) out.add(new COSString(kept.toByteArray()));

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

    private boolean inZone(float x, float y, List<WordOccurrence> zones) {
        final float padX = 2f, padY = 8f;
        for (WordOccurrence z : zones) {
            if (x >= z.x - padX && x < z.x + z.width + padX
             && y >= z.y - padY && y <= z.y + z.height + padY) {
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
