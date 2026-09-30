package edutrack.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import edutrack.api.ApiServer;
import edutrack.gui.ModulePanel;
import edutrack.gui.panels.AcademicSearchPanel;
import edutrack.gui.panels.DocumentSimilarityPanel;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Faculty;
import edutrack.model.Student;
import edutrack.modules.M1AhoCorasick;
import edutrack.modules.M2SuffixAutomaton;
import edutrack.modules.M3DynamicProgramming;
import edutrack.modules.M4FlowNetwork;
import edutrack.modules.M5DPLLSolver;
import edutrack.modules.M6ReservoirSampler;
import edutrack.search.SearchResult;
import edutrack.search.SearchService;

/**
 * Regression suite for the repository-wide hardening pass.
 * It targets the previously uncovered edge cases around snapshots, persistence,
 * API decoding, async stale results, GUI selector refresh, and algorithm inputs.
 */
public final class RepositoryHardeningSelfTest {

    private RepositoryHardeningSelfTest() {
    }

    private static int checks;
    private static int failures;

    public static void main(String[] args) throws Exception {
        check("student course list is immutable", () -> {
            Student s = new Student(1, "A", "CSE", 1, 8.0, List.of("CS101"));
            expectThrows(UnsupportedOperationException.class, () -> s.enrolledCourses.add("CS201"));
        });
        check("faculty expertise list is immutable", () -> {
            Faculty f = new Faculty(1, "F", "CS", List.of("CS101"));
            expectThrows(UnsupportedOperationException.class, () -> f.expertise.remove("CS101"));
        });

        check("DataStore collections and indexes are immutable", () -> {
            DataStore ds = new DataStore(false);
            expectThrows(UnsupportedOperationException.class, () -> ds.students().clear());
            expectThrows(UnsupportedOperationException.class, () -> ds.coursesByCode().clear());
        });

        check("course removal creates a new immutable student snapshot", () -> {
            DataStore ds = new DataStore(false);
            Student before = ds.studentsById().get(1000);
            boolean had = before.enrolledCourses.contains("CS101");
            if (!had) {
                return;
            }
            long rev = ds.revision();
            ds.removeCourse("CS101");
            Student after = ds.studentsById().get(1000);
            assertTrue(before.enrolledCourses.contains("CS101"), "old snapshot was mutated");
            assertTrue(!after.enrolledCourses.contains("CS101"), "new snapshot still contains deleted course");
            assertTrue(ds.revision() == rev + 1, "revision did not increment");
        });

        check("saved-data validator rejects empty and broken datasets", () -> {
            expectThrows(IllegalArgumentException.class,
                    () -> DataStore.validateLoadedData(List.of(), List.of(), List.of(), List.of()));

            Course c = new Course("C1", "Course", "Dept", 3, 1);
            Student good = new Student(1, "A", "Program", 1, 8.0, List.of("C1"));
            Faculty f = new Faculty(1, "F", "Dept", List.of("C1"));
            ExamRecord e = new ExamRecord(1, "C1", 10, 50);
            DataStore.validateLoadedData(List.of(c), List.of(good), List.of(f), List.of(e));

            Student badRef = new Student(2, "B", "Program", 1, 8.0, List.of("NOPE"));
            expectThrows(IllegalArgumentException.class,
                    () -> DataStore.validateLoadedData(List.of(c), List.of(good, badRef), List.of(f), List.of(e)));

            Student duplicate = new Student(1, "B", "Program", 1, 8.0, List.of("C1"));
            expectThrows(IllegalArgumentException.class,
                    () -> DataStore.validateLoadedData(List.of(c), List.of(good, duplicate), List.of(f), List.of(e)));
        });

        check("CSV parser round-trips multiline quoted data", () -> {
            Path dir = Files.createTempDirectory("edutrack-csv-test");
            try {
                Files.writeString(dir.resolve(CsvStore.STUDENTS_FILE),
                        "id,name,program,semester,cgpa,enrolledCourses\n"
                        + "1,\"Alice\nSmith\",\"B.Tech CSE\",3,8.5,C1\n",
                        StandardCharsets.UTF_8);
                List<Student> loaded = CsvStore.loadStudents(dir);
                assertTrue(loaded.size() == 1, "wrong row count");
                assertTrue(loaded.get(0).name.equals("Alice\nSmith"), "multiline name was corrupted");
                assertTrue(loaded.get(0).program.equals("B.Tech CSE"), "program was corrupted");

                Files.writeString(dir.resolve(CsvStore.STUDENTS_FILE),
                        "id,name,program,semester,cgpa,enrolledCourses\n1,\"unterminated,CSE,1,8.0,C1\n",
                        StandardCharsets.UTF_8);
                expectThrows(IOException.class, () -> CsvStore.loadStudents(dir));
            } finally {
                deleteRecursively(dir);
            }
        });

        check("M4 flow network rejects self-loops and bad capacities", () -> {
            M4FlowNetwork net = new M4FlowNetwork(2);
            expectThrows(IllegalArgumentException.class, () -> net.addEdge(0, 0, 1));
            expectThrows(IllegalArgumentException.class, () -> net.addEdge(0, 1, -1));
            expectThrows(IllegalArgumentException.class, () -> net.addEdge(-1, 1, 1));
        });

        check("DPLL rejects invalid literals", () -> {
            expectThrows(IllegalArgumentException.class,
                    () -> M5DPLLSolver.solve(2, List.of(new int[] { 3 })));
            expectThrows(IllegalArgumentException.class,
                    () -> M5DPLLSolver.solve(2, List.of(new int[] { 0 })));
        });

        check("bitmask DP refuses impractical item counts", () -> {
            expectThrows(IllegalArgumentException.class,
                    () -> M3DynamicProgramming.bitmaskBestSubset(new int[23], new long[23], 10));
            expectThrows(IllegalArgumentException.class,
                    () -> M3DynamicProgramming.bitmaskBestSubset(new int[] { 1 }, new long[] { 1 }, -1));
        });

        check("reservoir sampling rejects negative k", () -> {
            expectThrows(IllegalArgumentException.class,
                    () -> M6ReservoirSampler.sample(List.of(1, 2, 3), -1, new java.util.Random(1)));
        });

        check("suffix automaton empty pattern count uses n+1 boundaries", () -> {
            M2SuffixAutomaton sam = M2SuffixAutomaton.build("banana");
            assertTrue(sam.contains(""), "empty pattern should be contained");
            assertTrue(sam.countOccurrences("") == 7, "expected 7 empty-pattern occurrences");
        });

        check("smart search finds faculty and resource IDs", () -> {
            DataStore ds = new DataStore(false);
            List<SearchResult> faculty = SearchService.search(ds, "500");
            assertContains(faculty, "Faculty", "500");
            List<SearchResult> resource = SearchService.search(ds, "RES-1");
            assertContains(resource, "Resource", "RES-1");
        });

        check("malformed API query is rejected by the API parser", () -> {
            java.lang.reflect.Method parse = edutrack.api.ApiServer.class
                    .getDeclaredMethod("parseQuery", String.class);
            parse.setAccessible(true);
            try {
                parse.invoke(null, "q=%");
                throw new AssertionError("malformed escape was accepted");
            } catch (java.lang.reflect.InvocationTargetException e) {
                assertTrue(e.getCause() instanceof IllegalArgumentException,
                        "expected IllegalArgumentException from query parser");
            }
        });


        check("async worker discards stale DataStore results", () -> {
            DataStore ds = new DataStore(false);
            TestPanel panel = new TestPanel(ds);
            panel.runOne();
            assertTrue(panel.started.await(5, TimeUnit.SECONDS), "worker did not start");
            ds.enroll(1000, "CS401");
            panel.release.countDown();
            Thread.sleep(250);
            assertTrue(panel.done.getCount() == 1, "stale result callback ran");
            assertTrue(panel.button.isEnabled(), "stale result left control disabled");
        });

        check("Academic Search refresh drops deleted assignments", () -> {
            DataStore ds = new DataStore(false);
            final AcademicSearchPanel[] holder = new AcademicSearchPanel[1];
            SwingUtilities.invokeAndWait(() -> holder[0] = new AcademicSearchPanel(ds));
            AcademicSearchPanel panel = holder[0];
            ds.removeCourse("CS101");
            SwingUtilities.invokeAndWait(panel::refresh);
            @SuppressWarnings("unchecked")
            javax.swing.JComboBox<String> combo = (javax.swing.JComboBox<String>)
                    getField(panel, "zAssignmentCombo");
            for (int i = 0; i < combo.getItemCount(); i++) {
                assertTrue(!combo.getItemAt(i).contains("ASG-CS101-"), "deleted assignment remains in selector");
            }
        });

        check("Document Similarity refresh drops deleted assignments", () -> {
            DataStore ds = new DataStore(false);
            final DocumentSimilarityPanel[] holder = new DocumentSimilarityPanel[1];
            SwingUtilities.invokeAndWait(() -> holder[0] = new DocumentSimilarityPanel(ds));
            DocumentSimilarityPanel panel = holder[0];
            ds.removeCourse("CS101");
            SwingUtilities.invokeAndWait(panel::refresh);
            @SuppressWarnings("unchecked")
            javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) getField(panel, "docCombo");
            for (int i = 0; i < combo.getItemCount(); i++) {
                Object item = combo.getItemAt(i);
                assertTrue(!String.valueOf(item).contains("ASG-CS101-"), "deleted assignment remains in document selector");
            }
        });

        System.out.println("----");
        System.out.println("Repository hardening checks: " + (checks - failures) + "/" + checks + " passed.");
        if (failures > 0) {
            System.out.println("SELF-TEST FAILED: " + failures + " check(s)");
            System.exit(1);
        }
        System.out.println("ALL CHECKS PASSED");
    }

    private static final class TestPanel extends ModulePanel {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        final JButton button = new JButton("work");

        TestPanel(DataStore ds) {
            super(ds);
            add(button);
        }

        void runOne() {
            button.setEnabled(false);
            runAsync(() -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return 1;
            }, value -> {
                button.setEnabled(true);
                done.countDown();
            }, error -> done.countDown());
        }
    }

    private static Object getField(Object target, String name) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not access " + name, e);
        }
    }

    private static void assertContains(List<SearchResult> results, String kind, String id) {
        for (SearchResult result : results) {
            if (kind.equals(result.kind) && id.equals(result.id)) {
                return;
            }
        }
        throw new AssertionError("missing " + kind + " " + id);
    }

    private static void expectThrows(Class<? extends Throwable> type, ThrowingAction action) throws Exception {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }
            throw new AssertionError("expected " + type.getName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getName());
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void check(String name, ThrowingAction action) {
        checks++;
        try {
            action.run();
            System.out.println("PASS " + name);
        } catch (Throwable t) {
            failures++;
            System.out.println("FAIL " + name + ": " + t.getMessage());
        }
    }

    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static void deleteRecursively(Path dir) {
        try {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }
}
