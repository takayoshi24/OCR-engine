package io.github.takayoshi24.ocr.redact;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

class ContentStreamFilter {

    private final CharacterRedactor cr = new CharacterRedactor();

    void filter(PDDocument document, PDPage page, List<WordOccurrence> zones) throws IOException {
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
                        var r = cr.redactString(str, font, currentFontSize, tc, tw, th, tm, zones);
                        filtered.addAll(r.tokens());
                        tm[4] = r.newX();
                    } else {
                        flush(filtered, buf); filtered.add(op);
                    }
                }
                case "TJ" -> {
                    if (inText && !buf.isEmpty() && buf.get(buf.size()-1) instanceof COSArray arr) {
                        buf.remove(buf.size()-1);
                        flush(filtered, buf);
                        PDFont font = lookupFont(res, currentFontName);
                        var r = cr.redactArray(arr, font, currentFontSize, tc, tw, th, tm, zones);
                        filtered.addAll(r.tokens());
                        tm[4] = r.newX();
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
                        var r = cr.redactString(str, font, currentFontSize, tc, tw, th, tm, zones);
                        filtered.addAll(r.tokens());
                        tm[4] = r.newX();
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
                        var r = cr.redactString(str, font, currentFontSize, tc, tw, th, tm, zones);
                        filtered.addAll(r.tokens());
                        tm[4] = r.newX();
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
