package edutrack.modules;

import java.util.Arrays;

public class M2SAIS {

    public static int[] buildSuffixArray(String text) {
        int n = text.length();
        if (n == 0) {
            return new int[0];
        }
        int[] s = new int[n + 1];
        int maxSymbol = 0;
        for (int i = 0; i < n; i++) {
            s[i] = text.charAt(i) + 1;
            if (s[i] > maxSymbol) {
                maxSymbol = s[i];
            }
        }
        s[n] = 0;

        int[] sa = sais(s, maxSymbol + 1);
        int[] result = new int[n];
        System.arraycopy(sa, 1, result, 0, n);
        return result;
    }

    private static int[] sais(int[] s, int alphabetSize) {
        int n = s.length;
        if (n == 1) {
            return new int[] { 0 };
        }

        boolean[] isS = classifyTypes(s);
        int[] bucketSizes = bucketSizes(s, alphabetSize);

        int lmsCount = 0;
        for (int i = 1; i < n; i++) {
            if (isLms(isS, i)) {
                lmsCount++;
            }
        }
        int[] lmsPositions = new int[lmsCount];
        int[] lmsIndexOf = new int[n];
        Arrays.fill(lmsIndexOf, -1);
        int idx = 0;
        for (int i = 1; i < n; i++) {
            if (isLms(isS, i)) {
                lmsIndexOf[i] = idx;
                lmsPositions[idx++] = i;
            }
        }

        int[] sa = new int[n];
        induceSort(s, isS, bucketSizes, lmsPositions, sa);

        int[] sortedLms = new int[lmsCount];
        idx = 0;
        for (int i = 0; i < n; i++) {
            if (isLms(isS, sa[i])) {
                sortedLms[idx++] = sa[i];
            }
        }

        int[] nameOfLms = new int[lmsCount];
        int name = 0;
        if (lmsCount > 0) {
            nameOfLms[lmsIndexOf[sortedLms[0]]] = 0;
            for (int i = 1; i < lmsCount; i++) {
                if (!sameLmsSubstring(s, isS, sortedLms[i - 1], sortedLms[i])) {
                    name++;
                }
                nameOfLms[lmsIndexOf[sortedLms[i]]] = name;
            }
        }
        int nameCount = name + 1;

        int[] finalLmsOrder;
        if (nameCount == lmsCount) {
            finalLmsOrder = sortedLms;
        } else {
            int[] reduced = new int[lmsCount];
            for (int i = 0; i < lmsCount; i++) {
                reduced[i] = nameOfLms[i] + 1;
            }
            reduced[lmsCount - 1] = 0;
            int[] reducedSa = sais(reduced, nameCount + 1);
            finalLmsOrder = new int[lmsCount];
            for (int i = 0; i < lmsCount; i++) {
                finalLmsOrder[i] = lmsPositions[reducedSa[i]];
            }
        }

        induceSort(s, isS, bucketSizes, finalLmsOrder, sa);
        return sa;
    }

    private static boolean[] classifyTypes(int[] s) {
        int n = s.length;
        boolean[] isS = new boolean[n];
        isS[n - 1] = true;
        for (int i = n - 2; i >= 0; i--) {
            isS[i] = s[i] < s[i + 1] || (s[i] == s[i + 1] && isS[i + 1]);
        }
        return isS;
    }

    private static boolean isLms(boolean[] isS, int i) {
        return i > 0 && isS[i] && !isS[i - 1];
    }

    private static int[] bucketSizes(int[] s, int alphabetSize) {
        int[] sizes = new int[alphabetSize];
        for (int c : s) {
            sizes[c]++;
        }
        return sizes;
    }

    private static int[] bucketHeads(int[] sizes) {
        int[] heads = new int[sizes.length];
        int sum = 0;
        for (int i = 0; i < sizes.length; i++) {
            heads[i] = sum;
            sum += sizes[i];
        }
        return heads;
    }

    private static int[] bucketTails(int[] sizes) {
        int[] tails = new int[sizes.length];
        int sum = 0;
        for (int i = 0; i < sizes.length; i++) {
            sum += sizes[i];
            tails[i] = sum - 1;
        }
        return tails;
    }

    private static void induceSort(int[] s, boolean[] isS, int[] bucketSizes,
            int[] lmsOrder, int[] sa) {
        int n = s.length;
        Arrays.fill(sa, -1);

        int[] tails = bucketTails(bucketSizes);
        for (int i = lmsOrder.length - 1; i >= 0; i--) {
            int p = lmsOrder[i];
            sa[tails[s[p]]--] = p;
        }

        int[] heads = bucketHeads(bucketSizes);
        for (int i = 0; i < n; i++) {
            int p = sa[i];
            if (p > 0 && !isS[p - 1]) {
                sa[heads[s[p - 1]]++] = p - 1;
            }
        }

        tails = bucketTails(bucketSizes);
        for (int i = n - 1; i >= 0; i--) {
            int p = sa[i];
            if (p > 0 && isS[p - 1]) {
                sa[tails[s[p - 1]]--] = p - 1;
            }
        }
    }

    private static boolean sameLmsSubstring(int[] s, boolean[] isS, int a, int b) {
        if (a == b) {
            return true;
        }
        for (int i = 0; ; i++) {
            boolean aEnd = i > 0 && isLms(isS, a + i);
            boolean bEnd = i > 0 && isLms(isS, b + i);
            if (s[a + i] != s[b + i] || isS[a + i] != isS[b + i]) {
                return false;
            }
            if (aEnd || bEnd) {
                return aEnd && bEnd;
            }
        }
    }
}
