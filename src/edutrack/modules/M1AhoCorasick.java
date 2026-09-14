package edutrack.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Aho-Corasick multi-pattern string matching automaton (hand-written).
 * Builds a trie over the keywords, adds failure links and dictionary-suffix
 * links via BFS, then scans a text in O(text length + matches) reporting
 * every occurrence of every keyword, including overlapping ones.
 */
public class M1AhoCorasick {

    private static final int SAMPLE_LIMIT = 40;

    /** One reported occurrence: pattern[patternIndex] starting at index start. */
    public static final class Match {
        public final int start;
        public final int patternIndex;

        Match(int start, int patternIndex) {
            this.start = start;
            this.patternIndex = patternIndex;
        }
    }

    /** Aggregated scan result: per-pattern counts, grand total, capped samples. */
    public static final class Result {
        public final long[] counts;
        public final long total;
        public final List<Match> samples;

        Result(long[] counts, long total, List<Match> samples) {
            this.counts = counts;
            this.total = total;
            this.samples = samples;
        }
    }

    private static final class Node {
        final Map<Character, Integer> next = new HashMap<>();
        int fail;
        int dictSuffix = -1;
        final List<Integer> outputs = new ArrayList<>();
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<String> patterns = new ArrayList<>();
    private final Map<String, Integer> indexByPattern = new HashMap<>();
    private volatile boolean built;

    public M1AhoCorasick() {
        nodes.add(new Node()); // root at state 0
    }

    /** Adds a keyword; returns its pattern index. Empty/duplicate keywords are ignored. */
    public int addPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return -1;
        }
        Integer existing = indexByPattern.get(pattern);
        if (existing != null) {
            return existing;
        }
        int state = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            Integer nxt = nodes.get(state).next.get(c);
            if (nxt == null) {
                nxt = nodes.size();
                nodes.add(new Node());
                nodes.get(state).next.put(c, nxt);
            }
            state = nxt;
        }
        int index = patterns.size();
        nodes.get(state).outputs.add(index);
        patterns.add(pattern);
        indexByPattern.put(pattern, index);
        built = false;
        return index;
    }

    public int patternCount() {
        return patterns.size();
    }

    public String pattern(int index) {
        return patterns.get(index);
    }

    public int stateCount() {
        return nodes.size();
    }

    /** Computes failure links and dictionary-suffix links with a BFS over the trie. */
    public void build() {
        Queue<Integer> queue = new ArrayDeque<>();
        for (int child : nodes.get(0).next.values()) {
            nodes.get(child).fail = 0;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            int state = queue.remove();
            Node node = nodes.get(state);
            Node failNode = nodes.get(node.fail);
            node.dictSuffix = failNode.outputs.isEmpty() ? failNode.dictSuffix : node.fail;
            for (Map.Entry<Character, Integer> edge : node.next.entrySet()) {
                char c = edge.getKey();
                int child = edge.getValue();
                int f = node.fail;
                while (f != 0 && !nodes.get(f).next.containsKey(c)) {
                    f = nodes.get(f).fail;
                }
                Integer candidate = nodes.get(f).next.get(c);
                nodes.get(child).fail = (candidate != null && candidate != child) ? candidate : 0;
                queue.add(child);
            }
        }
        built = true;
    }

    /** Scans the text, reporting every occurrence (overlaps included) of every keyword. */
    public Result search(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (!built) {
            // Lazy build with double-checked locking: after build() completes, the
            // volatile write publishes the fully-linked automaton to other threads,
            // and concurrent searches only read the trie.
            synchronized (this) {
                if (!built) {
                    build();
                }
            }
        }
        long[] counts = new long[patterns.size()];
        long total = 0;
        List<Match> samples = new ArrayList<>();
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (state != 0 && !nodes.get(state).next.containsKey(c)) {
                state = nodes.get(state).fail;
            }
            Integer nxt = nodes.get(state).next.get(c);
            state = (nxt != null) ? nxt : 0;
            int out = state;
            while (out > 0) {
                for (int p : nodes.get(out).outputs) {
                    counts[p]++;
                    total++;
                    if (samples.size() < SAMPLE_LIMIT) {
                        samples.add(new Match(i - patterns.get(p).length() + 1, p));
                    }
                }
                out = nodes.get(out).dictSuffix;
            }
        }
        return new Result(counts, total, Collections.unmodifiableList(samples));
    }
}
