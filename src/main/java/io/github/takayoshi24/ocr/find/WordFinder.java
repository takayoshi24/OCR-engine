package io.github.takayoshi24.ocr.find;

import com.google.re2j.Pattern;
import io.github.takayoshi24.ocr.extract.WordOccurrence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WordFinder {

    public enum MatchMode {
        EXACT,
        CASE_INSENSITIVE,
        REGEX
    }

    private final MatchMode mode;

    public WordFinder(MatchMode mode) {
        this.mode = mode;
    }

    public List<RedactionTarget> find(List<WordOccurrence> occurrences, List<String> targets) {
        List<Pattern> patterns = targets.stream().map(this::toPattern).toList();
        // Pre-compute how many tokens each target phrase contains
        List<Integer> wordCounts = targets.stream()
                .map(t -> t.isBlank() ? 0 : t.strip().split("\\s+").length)
                .toList();

        List<RedactionTarget> results = new ArrayList<>();

        for (int i = 0; i < occurrences.size(); i++) {
            for (int p = 0; p < patterns.size(); p++) {
                int wc = wordCounts.get(p);
                if (wc == 0 || i + wc > occurrences.size()) continue;

                // Build the candidate phrase from wc consecutive same-page tokens
                int page = occurrences.get(i).page();
                StringBuilder phrase = new StringBuilder();
                boolean samePage = true;
                for (int j = 0; j < wc; j++) {
                    WordOccurrence curr = occurrences.get(i + j);
                    if (curr.page() != page) { samePage = false; break; }
                    if (j > 0) phrase.append(' ');
                    phrase.append(curr.word());
                }
                if (!samePage) continue;

                if (patterns.get(p).matcher(phrase).find()) {
                    List<WordOccurrence> window = occurrences.subList(i, i + wc);
                    WordOccurrence occ = wc == 1 ? window.get(0) : merge(window);
                    results.add(new RedactionTarget(occ, targets.get(p)));
                    i += wc - 1; // outer loop will do i++ so net advance = wc
                    break;
                }
            }
        }

        return results;
    }

    private WordOccurrence merge(List<WordOccurrence> words) {
        float x = Float.MAX_VALUE, top = -Float.MAX_VALUE, right = -Float.MAX_VALUE;
        float bottom = Float.MAX_VALUE;
        for (WordOccurrence w : words) {
            x      = Math.min(x,      w.x());
            bottom = Math.min(bottom, w.y());
            right  = Math.max(right,  w.x() + w.width());
            top    = Math.max(top,    w.y() + w.height());
        }
        String text = words.stream().map(WordOccurrence::word).collect(Collectors.joining(" "));
        return new WordOccurrence(text, words.get(0).page(), x, bottom, right - x, top - bottom);
    }

    private Pattern toPattern(String target) {
        return switch (mode) {
            // \b anchors match at a word/non-word boundary, so "John" matches "John,"
            // but not "Johnson"
            case EXACT -> Pattern.compile("\\b" + java.util.regex.Pattern.quote(target) + "\\b");
            case CASE_INSENSITIVE -> Pattern.compile(
                    "\\b" + java.util.regex.Pattern.quote(target) + "\\b", Pattern.CASE_INSENSITIVE);
            case REGEX -> {
                // Length check is a cheap first-pass guard before pattern compilation.
                if (target.length() > 200) throw new IllegalArgumentException("Regex pattern too long: " + target);
                try {
                    yield Pattern.compile(target);
                } catch (com.google.re2j.PatternSyntaxException e) {
                    throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage(), e);
                }
            }
        };
    }
}
