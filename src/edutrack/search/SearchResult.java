package edutrack.search;

public class SearchResult {

    public final String kind;
    public final String id;
    public final String title;
    public final String subtitle;
    public final int score;

    public SearchResult(String kind, String id, String title, String subtitle, int score) {
        this.kind = kind;
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.score = score;
    }

    @Override
    public String toString() {
        return String.format("%-10s %-14s %s - %s (score %d)", kind, id, title, subtitle, score);
    }
}
