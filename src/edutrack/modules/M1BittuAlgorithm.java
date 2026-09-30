package edutrack.modules;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling bigram-vector candidate filter followed by exact verification.
 *
 * The algorithm represents a string by frequencies of adjacent byte-sized
 * character pairs. A sliding window is updated in O(1) arithmetic per shift;
 * only windows whose cosine similarity with the pattern vector is effectively
 * one are then checked character-by-character.
 */
public final class M1BittuAlgorithm {
    private static final int TABLE_SIZE = 256 * 256;
    private static final double COSINE_THRESHOLD = 0.999999999;

    private M1BittuAlgorithm() {}

    public static final class SearchResult {
        public final List<Integer> matchPositions;
        public final int screenedInWindows;
        public final int totalWindowsExamined;
        public final double patternMagnitude;
        public final long executionNanos;

        SearchResult(List<Integer> matches, int screenedInWindows, int totalWindowsExamined,
                     double patternMagnitude, long executionNanos) {
            this.matchPositions = List.copyOf(matches);
            this.screenedInWindows = screenedInWindows;
            this.totalWindowsExamined = totalWindowsExamined;
            this.patternMagnitude = patternMagnitude;
            this.executionNanos = executionNanos;
        }
    }

    public static List<Integer> search(String text, String pattern) {
        return searchWithTelemetry(text, pattern).matchPositions;
    }

    public static SearchResult searchWithTelemetry(String text, String pattern) {
        long start = System.nanoTime();
        List<Integer> matches = new ArrayList<>();
        if (text == null || pattern == null) {
            return new SearchResult(matches, 0, 0, 0.0, System.nanoTime() - start);
        }

        int n = text.length();
        int m = pattern.length();
        if (m == 0) {
            for (int i = 0; i <= n; i++) matches.add(i);
            return new SearchResult(matches, n + 1, n + 1, 0.0, System.nanoTime() - start);
        }
        if (m > n) {
            return new SearchResult(matches, 0, 0, 0.0, System.nanoTime() - start);
        }
        if (m == 1) {
            char target = pattern.charAt(0);
            for (int i = 0; i < n; i++) if (text.charAt(i) == target) matches.add(i);
            return new SearchResult(matches, n, n, 1.0, System.nanoTime() - start);
        }

        int[] patternCounts = computeBigramCounts(pattern);
        long patternMagSq = squaredMagnitude(patternCounts);
        double patternMag = Math.sqrt(patternMagSq);

        int[] windowCounts = computeBigramCounts(text, 0, m);
        long dot = dot(windowCounts, patternCounts);
        long windowMagSq = squaredMagnitude(windowCounts);
        int totalWindows = n - m + 1;
        int screenedIn = 0;

        for (int startPos = 0; startPos < totalWindows; startPos++) {
            double windowMag = Math.sqrt(Math.max(0L, windowMagSq));
            double cosine = windowMag == 0.0 || patternMag == 0.0
                    ? 0.0 : ((double) dot) / (windowMag * patternMag);

            if (cosine >= COSINE_THRESHOLD) {
                screenedIn++;
                if (verifyExact(text, startPos, pattern)) matches.add(startPos);
            }

            if (startPos + 1 < totalWindows) {
                int leaving = key(text.charAt(startPos), text.charAt(startPos + 1));
                int entering = key(text.charAt(startPos + m - 1), text.charAt(startPos + m));

                int oldLeave = windowCounts[leaving];
                dot -= patternCounts[leaving];
                windowMagSq -= 2L * oldLeave - 1L;
                windowCounts[leaving] = oldLeave - 1;

                int oldEnter = windowCounts[entering];
                dot += patternCounts[entering];
                windowMagSq += 2L * oldEnter + 1L;
                windowCounts[entering] = oldEnter + 1;
            }
        }

        return new SearchResult(matches, screenedIn, totalWindows, patternMag,
                System.nanoTime() - start);
    }

    public static int key(char a, char b) {
        if (a > 255 || b > 255) {
            throw new IllegalArgumentException("Bittu's matcher supports characters in the 0..255 range");
        }
        return ((a & 0xFF) << 8) | (b & 0xFF);
    }

    private static int[] computeBigramCounts(String s) {
        int[] counts = new int[TABLE_SIZE];
        return computeBigramCounts(s, 0, s.length(), counts);
    }

    private static int[] computeBigramCounts(String s, int from, int length) {
        return computeBigramCounts(s, from, length, new int[TABLE_SIZE]);
    }

    private static int[] computeBigramCounts(String s, int from, int length, int[] counts) {
        for (int i = from; i + 1 < from + length; i++) {
            counts[key(s.charAt(i), s.charAt(i + 1))]++;
        }
        return counts;
    }

    private static long squaredMagnitude(int[] counts) {
        long sum = 0;
        for (int value : counts) sum += (long) value * value;
        return sum;
    }

    private static long dot(int[] a, int[] b) {
        long sum = 0;
        for (int i = 0; i < TABLE_SIZE; i++) sum += (long) a[i] * b[i];
        return sum;
    }

    private static boolean verifyExact(String text, int start, String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            if (text.charAt(start + i) != pattern.charAt(i)) return false;
        }
        return true;
    }
}
