package io.github.takayoshi24.ocr.find;

import io.github.takayoshi24.ocr.extract.WordOccurrence;

public record RedactionTarget(WordOccurrence occurrence, String matchedPattern) {}
