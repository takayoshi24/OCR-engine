package io.github.takayoshi24.ocr.find;

import io.github.takayoshi24.ocr.extract.WordOccurrence;

public class RedactionTarget {

    public final WordOccurrence occurrence;
    public final String matchedPattern;

    public RedactionTarget(WordOccurrence occurrence, String matchedPattern) {
        this.occurrence = occurrence;
        this.matchedPattern = matchedPattern;
    }
}
