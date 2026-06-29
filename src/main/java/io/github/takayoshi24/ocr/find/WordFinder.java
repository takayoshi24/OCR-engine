package io.github.takayoshi24.ocr.find;

import io.github.takayoshi24.ocr.extract.WordOccurrence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
        List<Pattern> patterns = targets.stream()
                .map(this::toPattern)
                .toList();

        List<RedactionTarget> results = new ArrayList<>();

        for (WordOccurrence occ : occurrences) {
            for (int i = 0; i < patterns.size(); i++) {
                if (patterns.get(i).matcher(occ.word).find()) {
                    results.add(new RedactionTarget(occ, targets.get(i)));
                    break;
                }
            }
        }

        return results;
    }

    private Pattern toPattern(String target) {
        return switch (mode) {
            // \b anchors match at a word/non-word boundary, so "John" matches "John,"
            // but not "Johnson"
            case EXACT -> Pattern.compile("\\b" + Pattern.quote(target) + "\\b");
            case CASE_INSENSITIVE -> Pattern.compile("\\b" + Pattern.quote(target) + "\\b", Pattern.CASE_INSENSITIVE);
            case REGEX -> Pattern.compile(target);
        };
    }
}
