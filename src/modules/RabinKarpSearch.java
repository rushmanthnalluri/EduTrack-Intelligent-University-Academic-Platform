package modules;

import java.util.ArrayList;
import java.util.List;

public class RabinKarpSearch {

    private static final int BASE = 256;
    private static final int MOD = 1_000_000_007;

    public static List<Integer> search(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();

        if (m == 0 || m > n) {
            return matches;
        }

        long patternHash = 0;
        long textHash = 0;
        long highestPower = 1;

        for (int i = 0; i < m - 1; i++) {
            highestPower = (highestPower * BASE) % MOD;
        }

        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * BASE + pattern.charAt(i)) % MOD;
            textHash = (textHash * BASE + text.charAt(i)) % MOD;
        }

        for (int i = 0; i <= n - m; i++) {
            if (patternHash == textHash && text.regionMatches(i, pattern, 0, m)) {
                matches.add(i);
            }

            if (i < n - m) {
                textHash = (textHash - text.charAt(i) * highestPower % MOD + MOD) % MOD;
                textHash = (textHash * BASE + text.charAt(i + m)) % MOD;
            }
        }

        return matches;
    }
}
