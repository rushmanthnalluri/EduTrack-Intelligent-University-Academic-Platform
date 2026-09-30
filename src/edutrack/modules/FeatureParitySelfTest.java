package edutrack.modules;

import java.util.Arrays;

/** Regression checks for features ported from the reference EduTrack implementation. */
public final class FeatureParitySelfTest {
    private FeatureParitySelfTest() {}

    public static void main(String[] args) {
        int failures = 0;

        M1BittuAlgorithm.SearchResult bittu =
                M1BittuAlgorithm.searchWithTelemetry("xxacademic algorithmsyyacademic algorithms", "academic algorithms");
        if (!bittu.matchPositions.equals(Arrays.asList(2, 24))) {
            System.out.println("FAIL Bittu positions: " + bittu.matchPositions);
            failures++;
        } else {
            System.out.println("PASS Bittu exact positions");
        }
        if (bittu.screenedInWindows < bittu.matchPositions.size()) {
            System.out.println("FAIL Bittu screening telemetry");
            failures++;
        } else {
            System.out.println("PASS Bittu telemetry");
        }

        int[][] distance = {
            {0,10,15,20,25,30},
            {10,0,35,25,18,22},
            {15,35,0,30,28,14},
            {20,25,30,0,12,16},
            {25,18,28,12,0,24},
            {30,22,14,16,24,0}
        };
        M3CampusTsp.TourResult tour = M3CampusTsp.solve(distance);
        if (tour.minCost != 85 || tour.path.length != 7
                || tour.path[0] != 0 || tour.path[tour.path.length - 1] != 0) {
            System.out.println("FAIL Campus TSP: cost=" + tour.minCost
                    + " path=" + Arrays.toString(tour.path));
            failures++;
        } else {
            System.out.println("PASS Campus TSP optimal tour = 85");
        }

        if (failures > 0) {
            System.out.println("FEATURE PARITY SELF-TEST FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("All feature parity self-tests passed.");
    }
}
