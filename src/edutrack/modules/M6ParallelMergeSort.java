package edutrack.modules;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class M6ParallelMergeSort {

    public static final int SEQUENTIAL_THRESHOLD = 8192;

    private M6ParallelMergeSort() {
    }

    public static void sequentialSort(double[] a) {
        if (a == null) throw new IllegalArgumentException("array must not be null");
        double[] aux = new double[a.length];
        sequentialSort(a, aux, 0, a.length);
    }

    static void sequentialSort(double[] a, double[] aux, int lo, int hi) {
        if (hi - lo < 2) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        sequentialSort(a, aux, lo, mid);
        sequentialSort(a, aux, mid, hi);
        merge(a, aux, lo, mid, hi);
    }

    public static void parallelSort(double[] a, int processors) {
        if (a == null) throw new IllegalArgumentException("array must not be null");
        if (processors < 1) throw new IllegalArgumentException("processors must be >= 1");
        double[] aux = new double[a.length];
        ForkJoinPool pool = new ForkJoinPool(Math.max(1, processors));
        try {
            pool.invoke(new SortTask(a, aux, 0, a.length));
        } finally {
            pool.shutdown();
        }
    }

    private static void merge(double[] a, double[] aux, int lo, int mid, int hi) {
        System.arraycopy(a, lo, aux, lo, hi - lo);
        int i = lo;
        int j = mid;
        for (int k = lo; k < hi; k++) {
            if (i >= mid) {
                a[k] = aux[j++];
            } else if (j >= hi) {
                a[k] = aux[i++];
            } else if (Double.compare(aux[j], aux[i]) < 0) {
                a[k] = aux[j++];
            } else {
                a[k] = aux[i++];
            }
        }
    }

    private static class SortTask extends RecursiveAction {

        private final double[] a;
        private final double[] aux;
        private final int lo;
        private final int hi;

        SortTask(double[] a, double[] aux, int lo, int hi) {
            this.a = a;
            this.aux = aux;
            this.lo = lo;
            this.hi = hi;
        }

        @Override
        protected void compute() {
            if (hi - lo <= SEQUENTIAL_THRESHOLD) {
                sequentialSort(a, aux, lo, hi);
                return;
            }
            int mid = (lo + hi) >>> 1;
            invokeAll(new SortTask(a, aux, lo, mid), new SortTask(a, aux, mid, hi));
            merge(a, aux, lo, mid, hi);
        }
    }
}
