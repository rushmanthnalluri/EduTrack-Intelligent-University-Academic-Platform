package edutrack.modules;

import java.util.HashMap;

public class M2SuffixAutomaton {

    private static class State {
        int len;
        int link = -1;
        int occ;
        boolean isClone;
        HashMap<Character, Integer> next = new HashMap<>();
    }

    private State[] states;
    private int size;
    private int last;
    private boolean occComputed;

    public M2SuffixAutomaton(int capacityHint) {
        states = new State[Math.max(2, 2 * capacityHint + 1)];
        states[0] = new State();
        size = 1;
        last = 0;
        occComputed = false;
    }

    public static M2SuffixAutomaton build(String text) {
        M2SuffixAutomaton automaton = new M2SuffixAutomaton(text.length());
        for (int i = 0; i < text.length(); i++) {
            automaton.extend(text.charAt(i));
        }
        automaton.computeOccurrences();
        return automaton;
    }

    public void extend(char c) {
        int cur = newState(states[last].len + 1);
        int p = last;
        while (p != -1 && !states[p].next.containsKey(c)) {
            states[p].next.put(c, cur);
            p = states[p].link;
        }
        if (p == -1) {
            states[cur].link = 0;
        } else {
            int q = states[p].next.get(c);
            if (states[p].len + 1 == states[q].len) {
                states[cur].link = q;
            } else {
                int clone = newState(states[p].len + 1);
                states[clone].next.putAll(states[q].next);
                states[clone].link = states[q].link;
                states[clone].isClone = true;
                while (p != -1) {
                    Integer t = states[p].next.get(c);
                    if (t == null || t != q) {
                        break;
                    }
                    states[p].next.put(c, clone);
                    p = states[p].link;
                }
                states[q].link = clone;
                states[cur].link = clone;
            }
        }
        last = cur;
        occComputed = false;
    }

    public boolean contains(String pattern) {
        return walk(pattern) != -1;
    }

    public long countOccurrences(String pattern) {
        if (pattern.isEmpty()) {
            return states[last].len + 1L;
        }
        int v = walk(pattern);
        if (v == -1) {
            return 0;
        }
        if (!occComputed) {
            computeOccurrences();
        }
        return states[v].occ;
    }

    public long countDistinctSubstrings() {
        long total = 0;
        for (int i = 1; i < size; i++) {
            total += states[i].len - states[states[i].link].len;
        }
        return total;
    }

    public int[] longestCommonSubstring(String other) {
        int v = 0;
        int l = 0;
        int bestLen = 0;
        int bestEnd = 0;
        for (int i = 0; i < other.length(); i++) {
            char c = other.charAt(i);
            while (v != 0 && !states[v].next.containsKey(c)) {
                v = states[v].link;
                l = states[v].len;
            }
            Integer t = states[v].next.get(c);
            if (t != null) {
                v = t;
                l++;
            } else {
                v = 0;
                l = 0;
            }
            if (l > bestLen) {
                bestLen = l;
                bestEnd = i + 1;
            }
        }
        return new int[] { bestLen, bestEnd };
    }

    public int stateCount() {
        return size;
    }

    public int textLength() {
        return states[last].len;
    }

    private int newState(int len) {
        if (size == states.length) {
            State[] bigger = new State[states.length * 2];
            System.arraycopy(states, 0, bigger, 0, size);
            states = bigger;
        }
        State st = new State();
        st.len = len;
        states[size] = st;
        return size++;
    }

    private int walk(String pattern) {
        int v = 0;
        for (int i = 0; i < pattern.length(); i++) {
            Integer t = states[v].next.get(pattern.charAt(i));
            if (t == null) {
                return -1;
            }
            v = t;
        }
        return v;
    }

    private void computeOccurrences() {
        int maxLen = states[last].len;
        int[] count = new int[maxLen + 1];
        for (int i = 0; i < size; i++) {
            states[i].occ = states[i].isClone ? 0 : 1;
            count[states[i].len]++;
        }
        for (int i = 1; i <= maxLen; i++) {
            count[i] += count[i - 1];
        }
        int[] order = new int[size];
        for (int i = size - 1; i >= 0; i--) {
            order[--count[states[i].len]] = i;
        }
        for (int i = size - 1; i > 0; i--) {
            int v = order[i];
            states[states[v].link].occ += states[v].occ;
        }
        occComputed = true;
    }
}
