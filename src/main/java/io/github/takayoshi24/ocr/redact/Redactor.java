package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.RedactionTarget;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Redactor {

    public void redact(PDDocument document, List<RedactionTarget> targets) throws IOException {
        Map<Integer, List<RedactionTarget>> byPage = targets.stream()
                .collect(Collectors.groupingBy(t -> t.occurrence.page));

        for (Map.Entry<Integer, List<RedactionTarget>> entry : byPage.entrySet()) {
            int pageIndex = entry.getKey();
            PDPage page = document.getPage(pageIndex);
            List<WordOccurrence> zones = entry.getValue().stream()
                    .map(t -> t.occurrence)
                    .toList();

            // 1. Remove text operators for the redacted words so they can't be copied.
            filterPageContent(document, page, zones);

            // 2. Paint a black box over each redacted area.
            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                cs.setNonStrokingColor(Color.BLACK);
                for (WordOccurrence zone : zones) {
                    cs.addRect(zone.x, zone.y, zone.width, zone.height);
                    cs.fill();
                }
            }
        }
    }

    private void filterPageContent(PDDocument document, PDPage page, List<WordOccurrence> zones)
            throws IOException {
        PDFStreamParser parser = new PDFStreamParser(page);
        List<Object> tokens = parser.parse();
        parser.close();

        List<Object> filtered = new ArrayList<>(tokens.size());
        List<Object> buf     = new ArrayList<>();

        // Text state
        float[] tm  = {1, 0, 0, 1, 0, 0}; // text matrix  [a b c d e f]
        float[] tlm = {1, 0, 0, 1, 0, 0}; // text line matrix
        float tl = 0, tc = 0, tw = 0, th = 100;
        boolean inText = false;

        PDResources res = page.getResources();
        String currentFontName = null;
        float  currentFontSize = 1f;

        for (Object token : tokens) {
            if (!(token instanceof Operator op)) {
                buf.add(token);
                continue;
            }

            switch (op.getName()) {
                case "BT" -> {
                    inText = true;
                    tm  = new float[]{1, 0, 0, 1, 0, 0};
                    tlm = tm.clone();
                    flush(filtered, buf); filtered.add(op);
                }
                case "ET" -> {
                    inText = false;
                    flush(filtered, buf); filtered.add(op);
                }
                case "Tm" -> {
                    if (buf.size() >= 6) {
                        int n = buf.size();
                        tm = new float[]{asFloat(buf.get(n-6)), asFloat(buf.get(n-5)),
                                         asFloat(buf.get(n-4)), asFloat(buf.get(n-3)),
                                         asFloat(buf.get(n-2)), asFloat(buf.get(n-1))};
                        tlm = tm.clone();
                    }
                    flush(filtered, buf); filtered.add(op);
                }
                case "Td", "TD" -> {
                    if (buf.size() >= 2) {
                        int n = buf.size();
                        float tx = asFloat(buf.get(n-2)), ty = asFloat(buf.get(n-1));
                        if ("TD".equals(op.getName())) tl = -ty;
                        tlm[4] = tx*tlm[0] + ty*tlm[2] + tlm[4];
                        tlm[5] = tx*tlm[1] + ty*tlm[3] + tlm[5];
                        tm = tlm.clone();
                    }
                    flush(filtered, buf); filtered.add(op);
                }
                case "T*" -> {
                    tlm[4] = (-tl)*tlm[2] + tlm[4];
                    tlm[5] = (-tl)*tlm[3] + tlm[5];
                    tm = tlm.clone();
                    flush(filtered, buf); filtered.add(op);
                }
                case "TL" -> { if (!buf.isEmpty()) tl = asFloat(buf.get(buf.size()-1)); flush(filtered, buf); filtered.add(op); }
                case "Tc" -> { if (!buf.isEmpty()) tc = asFloat(buf.get(buf.size()-1)); flush(filtered, buf); filtered.add(op); }
                case "Tw" -> { if (!buf.isEmpty()) tw = asFloat(buf.get(buf.size()-1)); flush(filtered, buf); filtered.add(op); }
                case "Tz" -> { if (!buf.isEmpty()) th = asFloat(buf.get(buf.size()-1)); flush(filtered, buf); filtered.add(op); }
                case "Tf" -> {
                    if (buf.size() >= 2) {
                        int n = buf.size();
                        Object name = buf.get(n-2);
                        currentFontName = (name instanceof COSName cn) ? cn.getName() : null;
                        currentFontSize = asFloat(buf.get(n-1));
                    }
                    flush(filtered, buf); filtered.add(op);
                }
                case "Tj" -> {
                    if (inText && !buf.isEmpty() && buf.get(buf.size()-1) instanceof COSString str) {
                        buf.remove(buf.size()-1);
                        flush(filtered, buf);
                        PDFont font = lookupFont(res, currentFontName);
                        // redactString updates tm[4] as a side effect so the next
                        // operator sees the correct text position.
                        filtered.addAll(redactString(str, font, currentFontSize, tc, tw, th, tm, zones));
                    } else {
                        flush(filtered, buf); filtered.add(op);
                    }
                }
                case "TJ" -> {
                    if (inText && !buf.isEmpty() && buf.get(buf.size()-1) instanceof COSArray arr) {
                        buf.remove(buf.size()-1);
                        flush(filtered, buf);
                        PDFont font = lookupFont(res, currentFontName);
                        filtered.addAll(redactArray(arr, font, currentFontSize, tc, tw, th, tm, zones));
                    } else {
                        flush(filtered, buf); filtered.add(op);
                    }
                }
                case "'" -> {
                    // Equivalent to T* then Tj — advance line first.
                    tlm[4] = (-tl)*tlm[2] + tlm[4];
                    tlm[5] = (-tl)*tlm[3] + tlm[5];
                    tm = tlm.clone();
                    if (inText && !buf.isEmpty() && buf.get(buf.size()-1) instanceof COSString str) {
                        buf.remove(buf.size()-1);
                        flush(filtered, buf);
                        PDFont font = lookupFont(res, currentFontName);
                        filtered.add(Operator.getOperator("T*"));
                        filtered.addAll(redactString(str, font, currentFontSize, tc, tw, th, tm, zones));
                    } else {
                        flush(filtered, buf); filtered.add(op);
                    }
                }
                case "\"" -> {
                    // aw ac string " — set spacing, advance line, show string.
                    tlm[4] = (-tl)*tlm[2] + tlm[4];
                    tlm[5] = (-tl)*tlm[3] + tlm[5];
                    tm = tlm.clone();
                    if (inText && buf.size() >= 3 && buf.get(buf.size()-1) instanceof COSString str) {
                        COSBase aw = (COSBase) buf.get(buf.size()-3);
                        COSBase ac = (COSBase) buf.get(buf.size()-2);
                        buf.clear();
                        filtered.add(aw); filtered.add(Operator.getOperator("Tw"));
                        filtered.add(ac); filtered.add(Operator.getOperator("Tc"));
                        filtered.add(Operator.getOperator("T*"));
                        PDFont font = lookupFont(res, currentFontName);
                        filtered.addAll(redactString(str, font, currentFontSize, tc, tw, th, tm, zones));
                    } else {
                        flush(filtered, buf); filtered.add(op);
                    }
                }
                default -> { flush(filtered, buf); filtered.add(op); }
            }
        }
        flush(filtered, buf);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ContentStreamWriter(baos).writeTokens(filtered);

        COSStream stream = document.getDocument().createCOSStream();
        try (OutputStream out = stream.createOutputStream()) {
            out.write(baos.toByteArray());
        }
        page.getCOSObject().setItem(COSName.CONTENTS, stream);
    }

    /**
     * Walk a Tj string character-by-character.  Characters whose user-space X falls
     * inside a redaction zone are replaced with a TJ numeric advance (so surrounding
     * text stays correctly positioned).  tm[4] is updated to the post-Tj X position
     * so that the NEXT text operator starts tracking from the right place.
     */
    private List<Object> redactString(COSString str, PDFont font, float fontSize,
                                       float tc, float tw, float th,
                                       float[] tm, List<WordOccurrence> zones) {
        byte[] raw = str.getBytes();

        if (font == null) {
            // No font metrics: use average char width to estimate advance and zone overlap.
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
                out.add(new COSFloat(-glyphW)); // advance-only, no glyph drawn
            } else {
                try { kept.write(charRaw); } catch (Exception ignored) {}
            }
            x += advance;
        }
        if (kept.size() > 0) out.add(new COSString(kept.toByteArray()));

        // Always advance tm[4] so the next text operator starts from the correct X.
        tm[4] = x;

        if (!changed) return List.of(str, Operator.getOperator("Tj"));
        if (out.size() == 0) return List.of();
        return List.of(out, Operator.getOperator("TJ"));
    }

    /**
     * Same as redactString but handles an existing TJ array whose numeric elements
     * are inter-glyph spacing adjustments (kept unchanged).
     */
    private List<Object> redactArray(COSArray arr, PDFont font, float fontSize,
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
            // Rough advance estimate
            int charCount = arr.size(); // approximate
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
                // TJ spacing number: negative = advance right.
                x += -num.floatValue() / 1000f * fontSize * tm[0] * (th / 100f);
                out.add(elem);
            } else {
                out.add(elem);
            }
        }

        tm[4] = x; // always update after processing the array

        if (!changed) return List.of(arr, Operator.getOperator("TJ"));
        if (out.size() == 0) return List.of();
        return List.of(out, Operator.getOperator("TJ"));
    }

    // ---- Helpers ----

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

    private PDFont lookupFont(PDResources res, String name) {
        if (res == null || name == null) return null;
        try { return res.getFont(COSName.getPDFName(name)); } catch (Exception e) { return null; }
    }

    private void flush(List<Object> out, List<Object> buf) {
        out.addAll(buf); buf.clear();
    }

    private float asFloat(Object o) {
        if (o instanceof COSFloat  f) return f.floatValue();
        if (o instanceof COSInteger i) return i.floatValue();
        return 0f;
    }
}
