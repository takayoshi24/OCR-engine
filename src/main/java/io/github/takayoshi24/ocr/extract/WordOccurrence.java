package io.github.takayoshi24.ocr.extract;

public record WordOccurrence(String word, int page, float x, float y, float width, float height) {

    @Override
    public String toString() {
        return String.format("'%s' p%d @(%.1f,%.1f %.1fx%.1f)", word, page, x, y, width, height);
    }
}
