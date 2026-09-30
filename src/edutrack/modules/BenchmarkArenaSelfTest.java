package edutrack.modules;

import java.util.Arrays;
import java.util.List;

import modules.KMPSearch;
import modules.RabinKarpSearch;
import modules.ZFunctionSearch;

/**
 * Non-interactive verification for the benchmark arena workloads.
 * It validates that the compared algorithms agree on deterministic correctness
 * cases before timing them in the GUI.
 */
public final class BenchmarkArenaSelfTest {

    private static int checks;
    private static int failures;

    private BenchmarkArenaSelfTest() {}

    public static void main(String[] args) {
        check("KMP/Z/Rabin-Karp agree",
                same(KMPSearch.search(TEXT, PATTERN),
                        ZFunctionSearch.search(TEXT, PATTERN),
                        RabinKarpSearch.search(TEXT, PATTERN)));

        M1AhoCorasick ac = new M1AhoCorasick();
        int idx = ac.addPattern(PATTERN);
        M1AhoCorasick.Result aho = ac.search(TEXT);
        check("Aho-Corasick agrees with single-pattern count",
                aho.counts[idx] == KMPSearch.search(TEXT, PATTERN).size());

        int[] suffix = M2SuffixArray.buildSuffixArray(TEXT);
        int[] sais = M2SAIS.buildSuffixArray(TEXT);
        check("SA-IS equals suffix-array construction", Arrays.equals(suffix, sais));

        int[] lcp = M2KasaiLCP.buildLCP(TEXT, suffix);
        check("Kasai LCP has n-1 entries", lcp.length == TEXT.length() - 1);
        check("Kasai finds repeated substring",
                max(lcp) >= 3);

        M2SuffixAutomaton sam = M2SuffixAutomaton.build(TEXT);
        check("Suffix automaton contains pattern", sam.contains(PATTERN));
        check("Suffix automaton occurrence count",
                sam.countOccurrences(PATTERN) == KMPSearch.search(TEXT, PATTERN).size());

        check("Levenshtein known value",
                M3DynamicProgramming.levenshtein("kitten", "sitting") == 3);
        check("Damerau known value",
                M3DynamicProgramming.damerauOSA("ca", "ac") == 1);

        int[] dims = { 10, 20, 30, 40, 30 };
        int[][] split = new int[4][4];
        check("Matrix-chain known optimum",
                M3DynamicProgramming.matrixChainOrder(dims, split) == 30000);

        if (failures > 0) {
            System.out.println("Benchmark arena self-test: " + (checks - failures)
                    + "/" + checks + " passed.");
            System.exit(1);
        }
        System.out.println("Benchmark arena self-test: " + checks + "/" + checks + " passed.");
    }

    private static boolean same(List<Integer> a, List<Integer> b, List<Integer> c) {
        return a.equals(b) && a.equals(c);
    }

    private static int max(int[] values) {
        int max = 0;
        for (int value : values) max = Math.max(max, value);
        return max;
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + name);
        } else {
            System.out.println("PASS: " + name);
        }
    }

    private static final String TEXT =
            "academic algorithms course academic assignment algorithms course student academic";
    private static final String PATTERN = "academic";
}
