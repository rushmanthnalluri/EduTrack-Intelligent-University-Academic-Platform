package edutrack.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import edutrack.data.DataStore;
import edutrack.model.Course;
import edutrack.model.Faculty;
import edutrack.model.Student;

/**
 * Module M4 - Network Flow for Resource Allocation (CO4).
 * Hopcroft-Karp matching, Ford-Fulkerson, Edmonds-Karp, Dinic, and Konig's
 * minimum vertex cover, applied to university faculty/course/room data.
 */
public class M4NetworkFlow {

    public static final int ROOM_SEATS = 60;
    public static final int SCALE_CLONE_FACTOR = 100;
    public static final long SCALE_SEED = 4242L;

    public static void run(Scanner sc, DataStore ds) {
        int choice;

        do {
            System.out.println("\n--- M4: Network Flow for Resource Allocation ---");
            System.out.println("1. Bipartite Matching (Hopcroft-Karp) : faculty -> course assignment");
            System.out.println("2. Ford-Fulkerson (DFS) : classroom / resource allocation");
            System.out.println("3. Edmonds-Karp (BFS) : same network + cross-check");
            System.out.println("4. Dinic's Algorithm : scaled network vs Edmonds-Karp");
            System.out.println("5. Konig's Theorem : minimum vertex cover of eligibility graph");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {
                case 1:
                    hopcroftKarpOption(ds);
                    break;
                case 2:
                    fordFulkersonOption(ds);
                    break;
                case 3:
                    edmondsKarpOption(ds);
                    break;
                case 4:
                    dinicOption(ds);
                    break;
                case 5:
                    konigOption(ds);
                    break;
                case 0:
                    System.out.println("Returning to main menu...");
                    break;
                default:
                    System.out.println("\nInvalid Choice!");
            }
        } while (choice != 0);
    }

    // ------------------------------------------------------------------
    // Option 1: Hopcroft-Karp bipartite matching (faculty -> courses)
    // ------------------------------------------------------------------
    private static void hopcroftKarpOption(DataStore ds) {
        System.out.println("\n--- Hopcroft-Karp: Faculty-Course Bipartite Matching ---");
        List<Faculty> faculty = ds.faculty();
        List<Course> courses = ds.courses();
        List<List<Integer>> adj = buildFacultyCourseGraph(faculty, courseIndexByCode(courses));

        int edgeCount = 0;
        for (List<Integer> list : adj) {
            edgeCount += list.size();
        }
        System.out.println("Left side  : " + faculty.size() + " faculty");
        System.out.println("Right side : " + courses.size() + " courses");
        System.out.println("Edges      : " + edgeCount + " (edge when faculty.expertise contains the course code)");

        long start = System.nanoTime();
        M4HopcroftKarp.Result matching = M4HopcroftKarp.maxMatching(faculty.size(), courses.size(), adj);
        double ms = elapsedMs(start);

        System.out.println("\nMaximum matching size : " + matching.size
                + " (out of " + faculty.size() + " faculty, " + courses.size() + " courses)");
        System.out.println("Time taken            : " + fmtMs(ms));

        System.out.println("\nFaculty -> Course assignment pairs:");
        for (int u = 0; u < faculty.size(); u++) {
            int v = matching.matchLeft[u];
            if (v >= 0) {
                System.out.println("  " + faculty.get(u).name + "  ->  "
                        + courses.get(v).code + " (" + courses.get(v).name + ")");
            }
        }

        StringBuilder unmatchedFaculty = new StringBuilder();
        int unmatchedFacultyCount = 0;
        for (int u = 0; u < faculty.size(); u++) {
            if (matching.matchLeft[u] < 0) {
                if (unmatchedFacultyCount > 0) {
                    unmatchedFaculty.append(", ");
                }
                unmatchedFaculty.append(faculty.get(u).name);
                unmatchedFacultyCount++;
            }
        }
        StringBuilder unmatchedCourses = new StringBuilder();
        int unmatchedCourseCount = 0;
        for (int v = 0; v < courses.size(); v++) {
            if (matching.matchRight[v] < 0) {
                if (unmatchedCourseCount > 0) {
                    unmatchedCourses.append(", ");
                }
                unmatchedCourses.append(courses.get(v).code);
                unmatchedCourseCount++;
            }
        }
        System.out.println("\nUnmatched faculty (" + unmatchedFacultyCount + "): "
                + (unmatchedFacultyCount == 0 ? "none" : unmatchedFaculty));
        System.out.println("Unmatched courses (" + unmatchedCourseCount + "): "
                + (unmatchedCourseCount == 0 ? "none" : unmatchedCourses));
    }

    // ------------------------------------------------------------------
    // Option 2: Ford-Fulkerson (DFS) classroom allocation
    // ------------------------------------------------------------------
    private static void fordFulkersonOption(DataStore ds) {
        System.out.println("\n--- Ford-Fulkerson (DFS): Classroom / Resource Allocation ---");
        List<Course> courses = ds.courses();
        List<String> rooms = ds.rooms();
        List<String> slots = ds.timeSlots();

        System.out.println("\nNetwork layout (layered):");
        System.out.println("  source -> course   cap = ceil(enrollment / " + ROOM_SEATS + ") = sections needed");
        System.out.println("  course -> room     edge when the section fits the room; every room seats "
                + ROOM_SEATS + ", so every course may use any room");
        System.out.println("  room   -> slot     cap 1 (each room usable once per time slot)");
        System.out.println("  slot   -> sink     cap " + rooms.size() + " (all rooms may share a slot)");

        AllocationNetwork an = buildAllocationNetwork(ds);

        int totalSections = 0;
        System.out.println("\nEnrollment per course (students -> sections):");
        for (int i = 0; i < courses.size(); i++) {
            System.out.println("  " + courses.get(i).code + " : " + an.enrollment[i]
                    + " students -> " + an.sections[i] + " section(s)");
            totalSections += an.sections[i];
        }
        System.out.println("Total sections needed : " + totalSections);
        System.out.println("Room-slot supply      : " + rooms.size() + " rooms x " + slots.size()
                + " slots = " + (rooms.size() * slots.size()));

        long start = System.nanoTime();
        M4MaxFlow.Result result = M4MaxFlow.fordFulkerson(an.net, an.source, an.sink);
        double ms = elapsedMs(start);

        System.out.println("\nMax flow (sections scheduled) : " + result.maxFlow + " of " + totalSections);
        System.out.println("Augmenting DFS paths          : " + result.phases);
        System.out.println("Time taken                    : " + fmtMs(ms));

        System.out.println("\nAllocation table (course section -> room @ slot):");
        List<AllocationRow> rows = decomposeAllocation(an, courses, rooms, slots);
        if (rows.isEmpty()) {
            System.out.println("  (no sections to schedule)");
        } else {
            for (AllocationRow row : rows) {
                if (row.room == null) {
                    System.out.println("  " + row.courseCode + " section " + row.section
                            + "  ->  (could not be decomposed)");
                } else {
                    System.out.println("  " + row.courseCode + " section " + row.section
                            + "  ->  " + row.room + " @ " + row.slot);
                }
            }
        }

        long unscheduled = totalSections - result.maxFlow;
        if (unscheduled > 0) {
            System.out.println("\nWARNING: " + unscheduled
                    + " section(s) could not be scheduled (insufficient room-slot capacity).");
        } else {
            System.out.println("\nAll " + totalSections + " section(s) scheduled successfully.");
        }
    }

    /** One scheduled section: course code, section number, room and time slot. */
    public static class AllocationRow {
        public final String courseCode;
        public final int section;
        public final String room;   // null when the unit could not be decomposed
        public final String slot;

        AllocationRow(String courseCode, int section, String room, String slot) {
            this.courseCode = courseCode;
            this.section = section;
            this.room = room;
            this.slot = slot;
        }
    }

    /**
     * Decomposes the integral flow into per-section (course, room, slot) rows.
     * Consumes the flow stored in the network; build a fresh network (or call
     * resetFlow and re-run) before using it again.
     */
    public static List<AllocationRow> decomposeAllocation(AllocationNetwork an, List<Course> courses,
            List<String> rooms, List<String> slots) {
        List<AllocationRow> rows = new ArrayList<>();
        int[] sectionNo = new int[courses.size()];
        for (M4FlowNetwork.Edge srcEdge : an.net.edgesFrom(an.source)) {
            if (srcEdge.flow()()() <= 0) {
                continue;
            }
            int courseNode = srcEdge.to;
            int ci = courseNode - an.courseBase;
            if (ci < 0 || ci >= courses.size()) {
                continue;
            }
            for (int unit = 0; unit < srcEdge.flow()()(); unit++) {
                int roomNode = -1;
                for (M4FlowNetwork.Edge e : an.net.edgesFrom(courseNode)) {
                    if (e.to >= an.roomBase && e.to < an.slotBase && e.flow() > 0) {
                        an.net.augment(e, -1);
                        roomNode = e.to;
                        break;
                    }
                }
                int slotNode = -1;
                if (roomNode >= 0) {
                    for (M4FlowNetwork.Edge e : an.net.edgesFrom(roomNode)) {
                        if (e.to >= an.slotBase && e.to < an.sink && e.flow() > 0) {
                            an.net.augment(e, -1);
                            slotNode = e.to;
                            break;
                        }
                    }
                }
                if (roomNode < 0 || slotNode < 0) {
                    rows.add(new AllocationRow(courses.get(ci).code, sectionNo[ci] + 1, null, null));
                } else {
                    sectionNo[ci]++;
                    rows.add(new AllocationRow(courses.get(ci).code, sectionNo[ci],
                            rooms.get(roomNode - an.roomBase), slots.get(slotNode - an.slotBase)));
                }
                // The source->course unit is consumed only after its complete
                // course->room->slot path has been examined.
                an.net.augment(srcEdge, -1);
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Option 3: Edmonds-Karp (BFS) on the same network
    // ------------------------------------------------------------------
    private static void edmondsKarpOption(DataStore ds) {
        System.out.println("\n--- Edmonds-Karp (BFS): Classroom / Resource Allocation ---");
        System.out.println("Same layered network as option 2 (source -> courses -> rooms -> slots -> sink).");

        AllocationNetwork anEK = buildAllocationNetwork(ds);
        long start = System.nanoTime();
        M4MaxFlow.Result ek = M4MaxFlow.edmondsKarp(anEK.net, anEK.source, anEK.sink);
        double ekMs = elapsedMs(start);

        System.out.println("\nEdmonds-Karp max flow          : " + ek.maxFlow);
        System.out.println("Augmenting phases (BFS rounds) : " + ek.phases);
        System.out.println("Time taken                     : " + fmtMs(ekMs));

        AllocationNetwork anFF = buildAllocationNetwork(ds);
        start = System.nanoTime();
        M4MaxFlow.Result ff = M4MaxFlow.fordFulkerson(anFF.net, anFF.source, anFF.sink);
        double ffMs = elapsedMs(start);

        System.out.println("\nCross-check with Ford-Fulkerson : max flow = " + ff.maxFlow
                + " (" + ff.phases + " DFS paths, " + fmtMs(ffMs) + ")");
        if (ek.maxFlow == ff.maxFlow) {
            System.out.println("OK: both algorithms agree on max flow = " + ek.maxFlow);
        } else {
            System.out.println("MISMATCH: Edmonds-Karp=" + ek.maxFlow + " vs Ford-Fulkerson=" + ff.maxFlow);
        }
    }

    // ------------------------------------------------------------------
    // Option 4: Dinic on a synthetically scaled network
    // ------------------------------------------------------------------
    private static void dinicOption(DataStore ds) {
        System.out.println("\n--- Dinic's Algorithm: Scaled-Network Efficiency ---");
        System.out.println("Cloning the " + ds.courses().size() + " courses and " + ds.rooms().size()
                + " rooms x" + SCALE_CLONE_FACTOR + " with perturbed section demands"
                + " (base sections + 0..2); each course clone links to 4 room clones.");

        ScaledNetwork snDinic = buildScaledNetwork(ds, SCALE_CLONE_FACTOR, SCALE_SEED);
        System.out.println("\nScaled network:");
        System.out.println("  Nodes         : " + snDinic.net.nodeCount() + " (1 source + "
                + snDinic.courseClones + " course clones + " + snDinic.roomClones
                + " room clones + " + snDinic.slotCount + " slots + 1 sink)");
        System.out.println("  Forward edges : " + snDinic.forwardEdges);

        long start = System.nanoTime();
        M4MaxFlow.Result dinic = M4MaxFlow.dinic(snDinic.net, snDinic.source, snDinic.sink);
        double dinicMs = elapsedMs(start);

        System.out.println("\nDinic max flow          : " + dinic.maxFlow);
        System.out.println("Dinic level-graph phases: " + dinic.phases);
        System.out.println("Dinic time              : " + fmtMs(dinicMs));

        ScaledNetwork snEK = buildScaledNetwork(ds, SCALE_CLONE_FACTOR, SCALE_SEED);
        start = System.nanoTime();
        M4MaxFlow.Result ek = M4MaxFlow.edmondsKarp(snEK.net, snEK.source, snEK.sink);
        double ekMs = elapsedMs(start);

        System.out.println("\nEdmonds-Karp max flow (same network): " + ek.maxFlow);
        System.out.println("Edmonds-Karp phases               : " + ek.phases);
        System.out.println("Edmonds-Karp time                 : " + fmtMs(ekMs));

        if (dinic.maxFlow == ek.maxFlow) {
            System.out.println("\nOK: both algorithms agree on max flow = " + dinic.maxFlow);
        } else {
            System.out.println("\nMISMATCH: Dinic=" + dinic.maxFlow + " vs Edmonds-Karp=" + ek.maxFlow);
        }
        if (dinicMs > 0 && ekMs > 0) {
            System.out.println(String.format(Locale.US,
                    "Dinic was about %.1fx faster than Edmonds-Karp on this network.", ekMs / dinicMs));
        }
    }

    // ------------------------------------------------------------------
    // Option 5: Konig's theorem minimum vertex cover
    // ------------------------------------------------------------------
    private static void konigOption(DataStore ds) {
        System.out.println("\n--- Konig's Theorem: Minimum Vertex Cover ---");
        List<Faculty> faculty = ds.faculty();
        List<Course> courses = ds.courses();
        List<List<Integer>> adj = buildFacultyCourseGraph(faculty, courseIndexByCode(courses));

        int edgeCount = 0;
        for (List<Integer> list : adj) {
            edgeCount += list.size();
        }

        long start = System.nanoTime();
        M4HopcroftKarp.Result matching = M4HopcroftKarp.maxMatching(faculty.size(), courses.size(), adj);
        double matchMs = elapsedMs(start);

        start = System.nanoTime();
        M4Konig.Cover cover = M4Konig.minVertexCover(faculty.size(), courses.size(), adj,
                matching.matchLeft, matching.matchRight);
        double coverMs = elapsedMs(start);

        boolean covers = M4Konig.coversAllEdges(adj, cover);

        System.out.println("Cover reconstructed from the Hopcroft-Karp matching via alternating");
        System.out.println("paths grown from unmatched faculty vertices (Konig's construction).");
        System.out.println("\nMaximum matching size : " + matching.size + "  (" + fmtMs(matchMs) + ")");
        System.out.println("Minimum vertex cover  : " + cover.size + "  (" + fmtMs(coverMs) + ")");
        System.out.println("Konig's theorem holds : |cover| == |matching| -> " + (cover.size == matching.size));
        System.out.println("Every eligibility edge touches the cover : " + covers);

        System.out.println("\nFaculty in the cover (" + cover.leftSize + "):");
        for (int u = 0; u < faculty.size(); u++) {
            if (cover.leftInCover[u]) {
                System.out.println("  " + faculty.get(u).name + " (" + faculty.get(u).department + ")");
            }
        }
        if (cover.leftSize == 0) {
            System.out.println("  none");
        }
        System.out.println("Courses in the cover (" + cover.rightSize + "):");
        for (int v = 0; v < courses.size(); v++) {
            if (cover.rightInCover[v]) {
                System.out.println("  " + courses.get(v).code + " (" + courses.get(v).name + ")");
            }
        }
        if (cover.rightSize == 0) {
            System.out.println("  none");
        }

        System.out.println("\nInterpretation: this is the minimum set of faculty members and/or courses");
        System.out.println("that together touch every faculty-course eligibility conflict. Resolving");
        System.out.println("(e.g. pre-assigning) just these " + cover.size + " vertices settles all "
                + edgeCount + " eligibility edges.");
    }

    // ------------------------------------------------------------------
    // Shared builders (public for reuse by the GUI panel)
    // ------------------------------------------------------------------
    /** Eligibility graph of the dataset: faculty index -> course indices from Faculty.expertise. */
    public static List<List<Integer>> facultyCourseGraph(DataStore ds) {
        return buildFacultyCourseGraph(ds.faculty(), courseIndexByCode(ds.courses()));
    }

    private static Map<String, Integer> courseIndexByCode(List<Course> courses) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < courses.size(); i++) {
            map.put(courses.get(i).code, i);
        }
        return map;
    }

    private static List<List<Integer>> buildFacultyCourseGraph(List<Faculty> faculty,
            Map<String, Integer> courseIdx) {
        List<List<Integer>> adj = new ArrayList<>();
        for (Faculty f : faculty) {
            List<Integer> list = new ArrayList<>();
            for (String code : f.expertise) {
                Integer c = courseIdx.get(code);
                if (c != null && !list.contains(c)) {
                    list.add(c);
                }
            }
            list.sort(null);
            adj.add(list);
        }
        return adj;
    }

    public static int[] computeEnrollment(DataStore ds) {
        Map<String, Integer> idx = courseIndexByCode(ds.courses());
        int[] enrollment = new int[ds.courses().size()];
        for (Student s : ds.students()) {
            for (String code : s.enrolledCourses) {
                Integer i = idx.get(code);
                if (i != null) {
                    enrollment[i]++;
                }
            }
        }
        return enrollment;
    }

    public static int[] sectionsNeeded(int[] enrollment) {
        if (enrollment == null) throw new IllegalArgumentException("enrollment must not be null");
        int[] sections = new int[enrollment.length];
        for (int i = 0; i < enrollment.length; i++) {
            if (enrollment[i] < 0) {
                throw new IllegalArgumentException("enrollment cannot be negative");
            }
            sections[i] = (int) (((long) enrollment[i] + ROOM_SEATS - 1L) / ROOM_SEATS);
        }
        return sections;
    }

    public static class AllocationNetwork {
        public M4FlowNetwork net;
        public int source;
        public int sink;
        public int courseBase;
        public int roomBase;
        public int slotBase;
        public int[] enrollment;
        public int[] sections;
    }

    /** Layered network: source -> courses -> rooms -> time slots -> sink. */
    public static AllocationNetwork buildAllocationNetwork(DataStore ds) {
        List<Course> courses = ds.courses();
        List<String> rooms = ds.rooms();
        List<String> slots = ds.timeSlots();
        int c = courses.size();
        int r = rooms.size();
        int s = slots.size();

        AllocationNetwork an = new AllocationNetwork();
        an.enrollment = computeEnrollment(ds);
        an.sections = sectionsNeeded(an.enrollment);
        an.source = 0;
        an.courseBase = 1;
        an.roomBase = an.courseBase + c;
        an.slotBase = an.roomBase + r;
        an.sink = an.slotBase + s;
        an.net = new M4FlowNetwork(an.sink + 1);

        for (int i = 0; i < c; i++) {
            if (an.sections[i] == 0) {
                continue;
            }
            an.net.addEdge(an.source, an.courseBase + i, an.sections[i]);
            for (int j = 0; j < r; j++) {
                // every room seats ROOM_SEATS, so every section fits every room
                an.net.addEdge(an.courseBase + i, an.roomBase + j, an.sections[i]);
            }
        }
        for (int j = 0; j < r; j++) {
            for (int k = 0; k < s; k++) {
                an.net.addEdge(an.roomBase + j, an.slotBase + k, 1);
            }
        }
        for (int k = 0; k < s; k++) {
            an.net.addEdge(an.slotBase + k, an.sink, r);
        }
        return an;
    }

    public static class ScaledNetwork {
        public M4FlowNetwork net;
        public int source;
        public int sink;
        public int courseClones;
        public int roomClones;
        public int slotCount;
        public int forwardEdges;
    }

    /** Deterministically clones courses/rooms with perturbed demands for scaling tests. */
    public static ScaledNetwork buildScaledNetwork(DataStore ds, int factor, long seed) {
        if (ds == null) throw new IllegalArgumentException("data store must not be null");
        if (factor < 1) throw new IllegalArgumentException("scale factor must be >= 1");
        int[] baseSections = sectionsNeeded(computeEnrollment(ds));
        int baseCourses = ds.courses().size();
        Random rnd = new Random(seed);

        ScaledNetwork sn = new ScaledNetwork();
        sn.courseClones = baseCourses * factor;
        sn.roomClones = ds.rooms().size() * factor;
        sn.slotCount = ds.timeSlots().size();

        sn.source = 0;
        int courseBase = 1;
        int roomBase = courseBase + sn.courseClones;
        int slotBase = roomBase + sn.roomClones;
        sn.sink = slotBase + sn.slotCount;
        sn.net = new M4FlowNetwork(sn.sink + 1);

        int edges = 0;
        for (int j = 0; j < sn.courseClones; j++) {
            int demand = Math.max(1, baseSections[j % baseCourses] + rnd.nextInt(3));
            sn.net.addEdge(sn.source, courseBase + j, demand);
            edges++;
            for (int k = 0; k < 4; k++) {
                int room = (j * 7 + k * 199 + rnd.nextInt(5)) % sn.roomClones;
                sn.net.addEdge(courseBase + j, roomBase + room, demand);
                edges++;
            }
        }
        for (int j = 0; j < sn.roomClones; j++) {
            for (int k = 0; k < sn.slotCount; k++) {
                sn.net.addEdge(roomBase + j, slotBase + k, 1);
                edges++;
            }
        }
        for (int k = 0; k < sn.slotCount; k++) {
            sn.net.addEdge(slotBase + k, sn.sink, sn.roomClones);
            edges++;
        }
        sn.forwardEdges = edges;
        return sn;
    }

    // ------------------------------------------------------------------
    // Console helpers
    // ------------------------------------------------------------------
    private static int readInt(Scanner sc) {
        while (sc.hasNext() && !sc.hasNextInt()) {
            sc.next();
            System.out.print("Please enter a valid number : ");
        }
        if (!sc.hasNext()) {
            return 0; // end of input -> back to main menu instead of crashing
        }
        int value = sc.nextInt();
        if (sc.hasNextLine()) {
            sc.nextLine();
        }
        return value;
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private static String fmtMs(double ms) {
        return String.format(Locale.US, "%.3f ms", ms);
    }

    // ------------------------------------------------------------------
    // Non-interactive self-test (no Scanner)
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("--- M4NetworkFlow self-test ---");
        boolean allOk = true;

        // 1) CLRS textbook network (Fig. 26.1(b), 3rd ed.) - expected max flow 23.
        allOk &= report("Ford-Fulkerson (DFS) on CLRS network == 23", clrsCheck(0));
        allOk &= report("Edmonds-Karp (BFS) on CLRS network == 23", clrsCheck(1));
        allOk &= report("Dinic on CLRS network == 23", clrsCheck(2));

        // 2) Hopcroft-Karp vs brute-force matching on exhaustive tiny graphs.
        allOk &= report("Hopcroft-Karp == brute force on all 2x3 graphs (64)",
                hopcroftKarpMatchesBruteForce(2, 3));
        allOk &= report("Hopcroft-Karp == brute force on all 3x3 graphs (512)",
                hopcroftKarpMatchesBruteForce(3, 3));
        allOk &= report("Hopcroft-Karp == brute force on all 4x3 graphs (4096)",
                hopcroftKarpMatchesBruteForce(4, 3));
        allOk &= report("Hopcroft-Karp == brute force on 200 random 5x6 graphs",
                hopcroftKarpMatchesBruteForceRandom(5, 6, 200, 99L));

        // 3) Konig cover validity: every edge covered, |cover| == |matching|.
        allOk &= report("Konig cover valid on all 2x3 and 3x3 graphs",
                konigValid(2, 3) && konigValid(3, 3));
        allOk &= report("Konig cover valid on 200 random 5x6 graphs",
                konigValidRandom(5, 6, 200, 7L));

        // 4) Sanity checks on the real DataStore faculty-course graph.
        DataStore ds = new DataStore();
        List<Faculty> faculty = ds.faculty();
        List<Course> courses = ds.courses();
        List<List<Integer>> adj = buildFacultyCourseGraph(faculty, courseIndexByCode(courses));
        M4HopcroftKarp.Result hk = M4HopcroftKarp.maxMatching(faculty.size(), courses.size(), adj);
        M4HopcroftKarp.Result kuhn = M4HopcroftKarp.kuhnMatching(faculty.size(), courses.size(), adj);
        allOk &= report("DataStore matching: Hopcroft-Karp (" + hk.size + ") == Kuhn reference ("
                + kuhn.size + ")", hk.size == kuhn.size);
        M4Konig.Cover cover = M4Konig.minVertexCover(faculty.size(), courses.size(), adj,
                hk.matchLeft, hk.matchRight);
        allOk &= report("DataStore Konig cover valid (|cover| == |matching| == " + hk.size + ")",
                cover.size == hk.size && M4Konig.coversAllEdges(adj, cover));

        // 5) Edge-case guards.
        allOk &= report("source == sink yields zero flow (all three algorithms)", sourceEqualsSinkCheck());
        allOk &= report("Disconnected / empty network yields zero flow (all three algorithms)",
                disconnectedCheck());
        allOk &= report("resetFlow allows an identical re-run on the same network", resetFlowCheck());
        allOk &= report("Out-of-range endpoints rejected", endpointValidationCheck());

        System.out.println(allOk ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED");
        if (!allOk) {
            System.exit(1);
        }
    }

    private static boolean report(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
        return ok;
    }

    /** CLRS 3rd ed., Fig. 26.1(b): nodes 0=s, 1=v1, 2=v2, 3=v3, 4=v4, 5=t; max flow 23. */
    private static M4FlowNetwork clrsNetwork() {
        M4FlowNetwork net = new M4FlowNetwork(6);
        net.addEdge(0, 1, 16);
        net.addEdge(0, 2, 13);
        net.addEdge(1, 2, 10);
        net.addEdge(2, 1, 4);
        net.addEdge(1, 3, 12);
        net.addEdge(2, 4, 14);
        net.addEdge(3, 2, 9);
        net.addEdge(4, 3, 7);
        net.addEdge(3, 5, 20);
        net.addEdge(4, 5, 4);
        return net;
    }

    private static boolean clrsCheck(int algorithm) {
        M4FlowNetwork net = clrsNetwork();
        M4MaxFlow.Result r;
        switch (algorithm) {
            case 0:
                r = M4MaxFlow.fordFulkerson(net, 0, 5);
                break;
            case 1:
                r = M4MaxFlow.edmondsKarp(net, 0, 5);
                break;
            default:
                r = M4MaxFlow.dinic(net, 0, 5);
                break;
        }
        return r.maxFlow == 23 && M4MaxFlow.validateFlow(net, 0, 5) && outflowFrom(net, 0) == 23;
    }

    private static long outflowFrom(M4FlowNetwork net, int source) {
        long out = 0;
        for (M4FlowNetwork.Edge e : net.edgesFrom(source)) {
            out += e.flow();
        }
        return out;
    }

    private static boolean hopcroftKarpMatchesBruteForce(int n, int m) {
        int edgeBits = n * m;
        for (int mask = 0; mask < (1 << edgeBits); mask++) {
            List<List<Integer>> adj = adjacencyFromMask(n, m, mask);
            int hk = M4HopcroftKarp.maxMatching(n, m, adj).size;
            int bf = bruteForceMatching(n, m, adj);
            if (hk != bf) {
                return false;
            }
        }
        return true;
    }

    private static boolean hopcroftKarpMatchesBruteForceRandom(int n, int m, int trials, long seed) {
        Random rnd = new Random(seed);
        for (int t = 0; t < trials; t++) {
            List<List<Integer>> adj = randomGraph(n, m, rnd);
            int hk = M4HopcroftKarp.maxMatching(n, m, adj).size;
            int bf = bruteForceMatching(n, m, adj);
            if (hk != bf) {
                return false;
            }
        }
        return true;
    }

    private static boolean konigValid(int n, int m) {
        int edgeBits = n * m;
        for (int mask = 0; mask < (1 << edgeBits); mask++) {
            List<List<Integer>> adj = adjacencyFromMask(n, m, mask);
            if (!konigValidOnGraph(n, m, adj)) {
                return false;
            }
        }
        return true;
    }

    private static boolean konigValidRandom(int n, int m, int trials, long seed) {
        Random rnd = new Random(seed);
        for (int t = 0; t < trials; t++) {
            if (!konigValidOnGraph(n, m, randomGraph(n, m, rnd))) {
                return false;
            }
        }
        return true;
    }

    private static boolean konigValidOnGraph(int n, int m, List<List<Integer>> adj) {
        M4HopcroftKarp.Result matching = M4HopcroftKarp.maxMatching(n, m, adj);
        M4Konig.Cover cover = M4Konig.minVertexCover(n, m, adj, matching.matchLeft, matching.matchRight);
        return cover.size == matching.size && M4Konig.coversAllEdges(adj, cover);
    }

    private static List<List<Integer>> adjacencyFromMask(int n, int m, int mask) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            List<Integer> list = new ArrayList<>();
            for (int v = 0; v < m; v++) {
                if ((mask & (1 << (u * m + v))) != 0) {
                    list.add(v);
                }
            }
            adj.add(list);
        }
        return adj;
    }

    private static List<List<Integer>> randomGraph(int n, int m, Random rnd) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            List<Integer> list = new ArrayList<>();
            for (int v = 0; v < m; v++) {
                if (rnd.nextDouble() < 0.4) {
                    list.add(v);
                }
            }
            adj.add(list);
        }
        return adj;
    }

    /** Exhaustive matching size by trying every match/skip choice per left vertex. */
    private static int bruteForceMatching(int n, int m, List<List<Integer>> adj) {
        return bruteForceDfs(0, n, adj, new boolean[m]);
    }

    private static int bruteForceDfs(int u, int n, List<List<Integer>> adj, boolean[] used) {
        if (u == n) {
            return 0;
        }
        int best = bruteForceDfs(u + 1, n, adj, used); // leave u unmatched
        for (int v : adj.get(u)) {
            if (!used[v]) {
                used[v] = true;
                best = Math.max(best, 1 + bruteForceDfs(u + 1, n, adj, used));
                used[v] = false;
            }
        }
        return best;
    }

    private static boolean sourceEqualsSinkCheck() {
        M4FlowNetwork net = clrsNetwork();
        return M4MaxFlow.fordFulkerson(net, 2, 2).maxFlow == 0
                && M4MaxFlow.edmondsKarp(net, 2, 2).maxFlow == 0
                && M4MaxFlow.dinic(net, 2, 2).maxFlow == 0;
    }

    private static boolean disconnectedCheck() {
        M4FlowNetwork disconnected = new M4FlowNetwork(4);
        disconnected.addEdge(0, 1, 5);
        disconnected.addEdge(2, 3, 5);
        M4FlowNetwork empty = new M4FlowNetwork(3);
        return M4MaxFlow.fordFulkerson(disconnected, 0, 3).maxFlow == 0
                && M4MaxFlow.edmondsKarp(disconnected, 0, 3).maxFlow == 0
                && M4MaxFlow.dinic(disconnected, 0, 3).maxFlow == 0
                && M4MaxFlow.fordFulkerson(empty, 0, 2).maxFlow == 0
                && M4MaxFlow.edmondsKarp(empty, 0, 2).maxFlow == 0
                && M4MaxFlow.dinic(empty, 0, 2).maxFlow == 0;
    }

    private static boolean resetFlowCheck() {
        M4FlowNetwork net = clrsNetwork();
        if (M4MaxFlow.fordFulkerson(net, 0, 5).maxFlow != 23) {
            return false;
        }
        net.resetFlow();
        if (M4MaxFlow.edmondsKarp(net, 0, 5).maxFlow != 23) {
            return false;
        }
        net.resetFlow();
        return M4MaxFlow.dinic(net, 0, 5).maxFlow == 23 && M4MaxFlow.validateFlow(net, 0, 5);
    }

    private static boolean endpointValidationCheck() {
        M4FlowNetwork net = clrsNetwork();
        return throwsIllegalArgument(() -> M4MaxFlow.fordFulkerson(net, -1, 5))
                && throwsIllegalArgument(() -> M4MaxFlow.edmondsKarp(net, 0, 99))
                && throwsIllegalArgument(() -> M4MaxFlow.dinic(net, 6, 5));
    }

    private static boolean throwsIllegalArgument(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
