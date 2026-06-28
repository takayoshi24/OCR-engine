package ocr.extract;

public class WordOccurrence {

    public final String word;
    public final int page;
    public final float x;
    public final float y;
    public final float width;
    public final float height;

    public WordOccurrence(String word, int page, float x, float y, float width, float height) {
        this.word = word;
        this.page = page;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return String.format("'%s' p%d @(%.1f,%.1f %.1fx%.1f)", word, page, x, y, width, height);
    }
}
