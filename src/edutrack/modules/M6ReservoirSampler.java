package edutrack.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public class M6ReservoirSampler {

    private M6ReservoirSampler() {
    }

    public static class SampleResult<T> {
        public final List<T> reservoir;
        public final long seen;

        SampleResult(List<T> reservoir, long seen) {
            this.reservoir = reservoir;
            this.seen = seen;
        }
    }

    public static <T> SampleResult<T> sample(Iterable<T> stream, int k, Random rnd) {
        return sample(stream, null, k, rnd);
    }

    public static <T> SampleResult<T> sample(Iterable<T> stream, Predicate<T> filter, int k, Random rnd) {
        if (stream == null || rnd == null) {
            throw new IllegalArgumentException("stream and random generator must not be null");
        }
        if (k < 0) {
            throw new IllegalArgumentException("k must be non-negative");
        }
        List<T> reservoir = new ArrayList<>(k);
        long seen = 0;
        for (T item : stream) {
            if (filter != null && !filter.test(item)) {
                continue;
            }
            seen++;
            if (reservoir.size() < k) {
                reservoir.add(item);
            } else {
                long j = rnd.nextLong(seen);
                if (j < k) {
                    reservoir.set((int) j, item);
                }
            }
        }
        return new SampleResult<>(reservoir, seen);
    }
}
