package modules;

import java.util.ArrayList;
import java.util.List;

public final class ZFunctionSearch {

    private ZFunctionSearch() {
    }

    public static List<Integer> search(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("text and pattern must not be null");
        }
        int m = pattern.length();
        int n = text.length();
        if (m == 0 || m > n) {
            return matches;
        }

        // Integer sentinel -1 cannot collide with any Java char value (0..65535).
        int[] combined = new int[m + 1 + n];
        for (int i = 0; i < m; i++) {
            combined[i] = pattern.charAt(i);
        }
        combined[m] = -1;
        for (int i = 0; i < n; i++) {
            combined[m + 1 + i] = text.charAt(i);
        }

        int[] z = buildZArray(combined);
        int offset = m + 1;
        for (int i = offset; i < combined.length; i++) {
            if (z[i] >= m) {
                matches.add(i - offset);
            }
        }
        return matches;
    }

    private static int[] buildZArray(int[] s) {
        int n = s.length;
        int[] z = new int[n];
        int left = 0;
        int right = 0;

        for (int i = 1; i < n; i++) {
            if (i <= right) {
                z[i] = Math.min(right - i + 1, z[i - left]);
            }
            while (i + z[i] < n && s[z[i]] == s[i + z[i]]) {
                z[i]++;
            }
            if (i + z[i] - 1 > right) {
                left = i;
                right = i + z[i] - 1;
            }
        }
        return z;
    }
}
