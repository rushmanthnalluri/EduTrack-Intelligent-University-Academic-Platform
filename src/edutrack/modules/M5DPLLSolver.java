package edutrack.modules;

import java.util.List;

public class M5DPLLSolver {

    public enum Status {
        SAT, UNSAT, UNKNOWN
    }

    public static class Result {
        public Status status;
        public boolean[] assignment;
        public long decisions;
    }

    private static final long DECISION_CAP = 5_000_000L;

    @SuppressWarnings("serial")
    private static class BudgetExceeded extends RuntimeException {
    }

    public static Result solve(int numVars, List<int[]> clauses) {
        validateFormula(numVars, clauses);
        Result res = new Result();
        int[] assign = new int[numVars + 1];
        long[] decisions = new long[1];
        try {
            boolean sat = dpll(clauses.toArray(new int[0][]), assign, decisions);
            res.status = sat ? Status.SAT : Status.UNSAT;
            if (sat) {
                res.assignment = new boolean[numVars + 1];
                for (int v = 1; v <= numVars; v++) {
                    res.assignment[v] = assign[v] == 1;
                }
            }
        } catch (BudgetExceeded e) {
            res.status = Status.UNKNOWN;
        }
        res.decisions = decisions[0];
        return res;
    }

    private static void validateFormula(int numVars, List<int[]> clauses) {
        if (numVars < 0 || clauses == null) {
            throw new IllegalArgumentException("numVars must be non-negative and clauses must not be null");
        }
        for (int[] clause : clauses) {
            if (clause == null) {
                throw new IllegalArgumentException("clause must not be null");
            }
            for (int lit : clause) {
                if (lit == 0 || Math.abs((long) lit) > numVars) {
                    throw new IllegalArgumentException("literal " + lit + " is outside 1.." + numVars);
                }
            }
        }
    }

    private static boolean dpll(int[][] clauses, int[] assign, long[] decisions) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int[] clause : clauses) {
                int open = 0;
                int lastLit = 0;
                boolean satisfied = false;
                for (int lit : clause) {
                    int val = assign[Math.abs(lit)];
                    if (val == 0) {
                        open++;
                        lastLit = lit;
                    } else if ((val > 0) == (lit > 0)) {
                        satisfied = true;
                        break;
                    }
                }
                if (satisfied) {
                    continue;
                }
                if (open == 0) {
                    return false;
                }
                if (open == 1) {
                    int v = Math.abs(lastLit);
                    if (assign[v] == 0) {
                        assign[v] = lastLit > 0 ? 1 : -1;
                        changed = true;
                    }
                }
            }
        }

        int[] freq = new int[assign.length];
        for (int[] clause : clauses) {
            boolean satisfied = false;
            for (int lit : clause) {
                int val = assign[Math.abs(lit)];
                if (val != 0 && (val > 0) == (lit > 0)) {
                    satisfied = true;
                    break;
                }
            }
            if (satisfied) {
                continue;
            }
            for (int lit : clause) {
                if (assign[Math.abs(lit)] == 0) {
                    freq[Math.abs(lit)]++;
                }
            }
        }
        int branchVar = 0;
        int best = 0;
        for (int v = 1; v < freq.length; v++) {
            if (freq[v] > best) {
                best = freq[v];
                branchVar = v;
            }
        }
        if (branchVar == 0) {
            return true;
        }

        for (int t = 0; t < 2; t++) {
            int[] next = assign.clone();
            next[branchVar] = t == 0 ? 1 : -1;
            if (++decisions[0] > DECISION_CAP) {
                throw new BudgetExceeded();
            }
            if (dpll(clauses, next, decisions)) {
                System.arraycopy(next, 0, assign, 0, next.length);
                return true;
            }
        }
        return false;
    }

    public static boolean satisfies(List<int[]> clauses, boolean[] assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("assignment must not be null");
        }
        for (int[] clause : clauses) {
            for (int lit : clause) {
                int v = Math.abs(lit);
                if (lit == 0 || v >= assignment.length) {
                    throw new IllegalArgumentException("literal " + lit + " does not fit assignment");
                }
            }
            boolean satisfied = false;
            for (int lit : clause) {
                if (assignment[Math.abs(lit)] == (lit > 0)) {
                    satisfied = true;
                    break;
                }
            }
            if (!satisfied) {
                return false;
            }
        }
        return true;
    }
}
