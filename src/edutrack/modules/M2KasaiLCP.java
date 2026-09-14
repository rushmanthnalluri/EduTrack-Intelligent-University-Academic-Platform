package edutrack.modules;

public class M2KasaiLCP {

    public static int[] buildLCP(String text, int[] sa) {
        int n = text.length();
        if (n < 2) {
            return new int[0];
        }
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            rank[sa[i]] = i;
        }
        int[] lcp = new int[n - 1];
        int h = 0;
        for (int i = 0; i < n; i++) {
            int r = rank[i];
            if (r > 0) {
                int j = sa[r - 1];
                while (i + h < n && j + h < n && text.charAt(i + h) == text.charAt(j + h)) {
                    h++;
                }
                lcp[r - 1] = h;
                if (h > 0) {
                    h--;
                }
            }
        }
        return lcp;
    }
}
