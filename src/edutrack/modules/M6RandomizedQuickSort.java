package edutrack.modules;

import java.util.Arrays;
import java.util.Random;

import edutrack.model.Student;

public class M6RandomizedQuickSort {

    private M6RandomizedQuickSort() {
    }

    public static long sort(double[] a, Random rnd) {
        long[] comparisons = new long[1];
        sort(a, 0, a.length - 1, rnd, comparisons);
        return comparisons[0];
    }

    private static void sort(double[] a, int lo, int hi, Random rnd, long[] comparisons) {
        while (lo < hi) {
            int pivotIndex = lo + rnd.nextInt(hi - lo + 1);
            swap(a, pivotIndex, lo);
            double pivot = a[lo];
            int lt = lo;
            int i = lo;
            int gt = hi;
            while (i <= gt) {
                comparisons[0]++;
                int c = Double.compare(a[i], pivot);
                if (c < 0) {
                    swap(a, lt++, i++);
                } else if (c > 0) {
                    swap(a, i, gt--);
                } else {
                    i++;
                }
            }
            if (lt - lo < hi - gt) {
                sort(a, lo, lt - 1, rnd, comparisons);
                lo = gt + 1;
            } else {
                sort(a, gt + 1, hi, rnd, comparisons);
                hi = lt - 1;
            }
        }
    }

    public static long sortDeterministic(double[] a) {
        long comparisons = 0;
        int[] stack = new int[64];
        int top = 0;
        stack[top++] = 0;
        stack[top++] = a.length - 1;
        while (top > 0) {
            int hi = stack[--top];
            int lo = stack[--top];
            if (lo >= hi) {
                continue;
            }
            double pivot = a[lo];
            int i = lo + 1;
            for (int j = lo + 1; j <= hi; j++) {
                comparisons++;
                if (Double.compare(a[j], pivot) < 0) {
                    swap(a, i, j);
                    i++;
                }
            }
            swap(a, lo, i - 1);
            int p = i - 1;
            if (top + 4 > stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            stack[top++] = lo;
            stack[top++] = p - 1;
            stack[top++] = p + 1;
            stack[top++] = hi;
        }
        return comparisons;
    }

    public static long sortStudents(Student[] a, Random rnd) {
        long[] comparisons = new long[1];
        sortStudents(a, 0, a.length - 1, rnd, comparisons);
        return comparisons[0];
    }

    private static void sortStudents(Student[] a, int lo, int hi, Random rnd, long[] comparisons) {
        while (lo < hi) {
            int p = partitionStudents(a, lo, hi, rnd, comparisons);
            if (p - lo < hi - p) {
                sortStudents(a, lo, p - 1, rnd, comparisons);
                lo = p + 1;
            } else {
                sortStudents(a, p + 1, hi, rnd, comparisons);
                hi = p - 1;
            }
        }
    }

    private static int partitionStudents(Student[] a, int lo, int hi, Random rnd, long[] comparisons) {
        int pivotIndex = lo + rnd.nextInt(hi - lo + 1);
        swap(a, pivotIndex, hi);
        Student pivot = a[hi];
        int i = lo;
        for (int j = lo; j < hi; j++) {
            comparisons[0]++;
            if (compareStudents(a[j], pivot) < 0) {
                swap(a, i, j);
                i++;
            }
        }
        swap(a, i, hi);
        return i;
    }

    public static int compareStudents(Student x, Student y) {
        int c = Double.compare(y.cgpa, x.cgpa);
        if (c != 0) {
            return c;
        }
        c = x.name.compareTo(y.name);
        if (c != 0) {
            return c;
        }
        return Integer.compare(x.id, y.id);
    }

    private static void swap(double[] a, int i, int j) {
        double tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    private static void swap(Student[] a, int i, int j) {
        Student tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }
}
