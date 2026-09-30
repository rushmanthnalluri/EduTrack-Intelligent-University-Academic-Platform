package edutrack.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class M2SuffixArray {

    private static final int CHAR_ALPHABET = 1 << 16;

    public static int[] buildSuffixArray(String text) {
        if (text == null) throw new IllegalArgumentException("text must not be null");
        int n = text.length();
        if (n == 0) {
            return new int[0];
        }

        int[] sa = new int[n];
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            sa[i] = i;
            rank[i] = text.charAt(i);
        }

        int numRanks = CHAR_ALPHABET;
        int[] newRank = new int[n];
        int[] buffer = new int[n];
        int[] count = new int[Math.max(CHAR_ALPHABET + 1, n + 2)];

        for (int k = 1; ; k <<= 1) {
            sortBySecondKey(sa, buffer, rank, n, k, numRanks, count);
            sortByFirstKey(buffer, sa, rank, n, numRanks, count);

            int r = 0;
            newRank[sa[0]] = 0;
            for (int i = 1; i < n; i++) {
                int a = sa[i - 1];
                int b = sa[i];
                if (rank[a] != rank[b] || secondKey(rank, n, k, a) != secondKey(rank, n, k, b)) {
                    r++;
                }
                newRank[b] = r;
            }
            int[] swap = rank;
            rank = newRank;
            newRank = swap;
            numRanks = r + 1;

            if (numRanks == n || k >= n) {
                break;
            }
        }
        return sa;
    }

    public static List<Integer> findOccurrences(String text, int[] sa, String pattern) {
        if (text == null || sa == null || pattern == null) {
            throw new IllegalArgumentException("text, suffix array and pattern must not be null");
        }
        validateSuffixArray(text.length(), sa);
        List<Integer> occurrences = new ArrayList<>();
        int n = sa.length;
        if (n == 0 || pattern.isEmpty()) {
            return occurrences;
        }
        int lo = lowerBound(text, sa, pattern);
        int hi = upperBound(text, sa, pattern);
        for (int i = lo; i < hi; i++) {
            occurrences.add(sa[i]);
        }
        Collections.sort(occurrences);
        return occurrences;
    }

    private static int secondKey(int[] rank, int n, int k, int i) {
        return i + k < n ? rank[i + k] + 1 : 0;
    }

    private static void sortBySecondKey(int[] in, int[] out, int[] rank, int n, int k,
            int numRanks, int[] count) {
        Arrays.fill(count, 0, numRanks + 1, 0);
        for (int i = 0; i < n; i++) {
            count[secondKey(rank, n, k, in[i])]++;
        }
        for (int i = 1; i <= numRanks; i++) {
            count[i] += count[i - 1];
        }
        for (int i = n - 1; i >= 0; i--) {
            int key = secondKey(rank, n, k, in[i]);
            out[--count[key]] = in[i];
        }
    }

    private static void sortByFirstKey(int[] in, int[] out, int[] rank, int n,
            int numRanks, int[] count) {
        Arrays.fill(count, 0, numRanks, 0);
        for (int i = 0; i < n; i++) {
            count[rank[in[i]]]++;
        }
        for (int i = 1; i < numRanks; i++) {
            count[i] += count[i - 1];
        }
        for (int i = n - 1; i >= 0; i--) {
            int key = rank[in[i]];
            out[--count[key]] = in[i];
        }
    }

    private static void validateSuffixArray(int textLength, int[] sa) {
        if (sa.length != textLength) {
            throw new IllegalArgumentException("suffix array length must equal text length");
        }
        boolean[] seen = new boolean[textLength];
        for (int pos : sa) {
            if (pos < 0 || pos >= textLength || seen[pos]) {
                throw new IllegalArgumentException("suffix array is not a permutation of text positions");
            }
            seen[pos] = true;
        }
    }

    private static int lowerBound(String text, int[] sa, String pattern) {
        int lo = 0;
        int hi = sa.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffixToPattern(text, sa[mid], pattern) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static int upperBound(String text, int[] sa, String pattern) {
        int lo = 0;
        int hi = sa.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffixToPattern(text, sa[mid], pattern) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static int compareSuffixToPattern(String text, int pos, String pattern) {
        int n = text.length();
        int m = pattern.length();
        for (int i = 0; i < m; i++) {
            if (pos + i >= n) {
                return -1;
            }
            char a = text.charAt(pos + i);
            char b = pattern.charAt(i);
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }
}
