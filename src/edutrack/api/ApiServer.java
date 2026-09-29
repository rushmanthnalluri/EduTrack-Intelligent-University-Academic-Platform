package edutrack.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import edutrack.data.DataStore;
import edutrack.features.ActivityAnalytics;
import edutrack.features.ExamAnalytics;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Faculty;
import edutrack.model.Student;
import edutrack.search.SearchResult;
import edutrack.search.SearchService;

/**
 * Read-only REST API over the EduTrack data store (JDK-only:
 * com.sun.net.httpserver, hand-rolled JSON via {@link JsonWriter}).
 *
 * Endpoints (GET only, UTF-8 JSON, Content-Type application/json):
 *   /api/summary                  counts of every collection + loadedFromDisk
 *   /api/students                 id, name, program, semester, cgpa, courses
 *   /api/students?id=N            adds exam records + credit-weighted GPA (404 unknown id)
 *   /api/courses                  code, name, department, credits, semester, enrollment
 *   /api/courses?code=X           adds exam stats avg/min/max/pass% (404 unknown code)
 *   /api/faculty                  id, name, department, expertise
 *   /api/exams?studentId=N|course=X   exam records (both filters optional, combined if given)
 *   /api/search?q=text            global search: kind/id/title/subtitle/score array
 *   /api/analytics                activity stream: counts per action, total, days, peak hour
 * Unknown paths get a 404 JSON error, non-GET methods a 405 JSON error.
 *
 * Thread-safety: handlers only read the DataStore. Its copy-on-write lists
 * give every request a consistent snapshot, and the activity analytics
 * payload is cached because the stream never changes after construction.
 *
 * Non-interactive self-test: java -cp bin edutrack.api.ApiServer
 */
public final class ApiServer {

    private static final String JSON_TYPE = "application/json; charset=utf-8";
    private static final int DEFAULT_PORT = 8080;
    private static final int HANDLER_THREADS = 4;

    private final DataStore ds;
    private final HttpServer server;
    private final ExecutorService executor;
    private volatile boolean running;

    private ApiServer(DataStore ds, HttpServer server, ExecutorService executor) {
        this.ds = ds;
        this.server = server;
        this.executor = executor;
    }

    /**
     * Starts the API server on the given port (0 = ephemeral free port) and
     * returns once it is accepting connections.
     */
    public static ApiServer start(DataStore ds, int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService pool = Executors.newFixedThreadPool(HANDLER_THREADS);
        http.setExecutor(pool);
        ApiServer api = new ApiServer(ds, http, pool);
        http.createContext("/", api::dispatch);
        http.start();
        api.running = true;
        return api;
    }

    /** Actual bound port (meaningful when started with port 0). */
    public int getPort() {
        return server.getAddress().getPort();
    }

    public boolean isRunning() {
        return running;
    }

    /** Stops the server and its handler threads. Safe to call more than once. */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        server.stop(0);
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Request handling
    // ------------------------------------------------------------------

    private void dispatch(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, errorJson("method " + method + " not allowed; use GET"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            try {
                send(exchange, 200, route(path, query));
            } catch (ApiError e) {
                send(exchange, e.status, errorJson(e.getMessage()));
            } catch (RuntimeException e) {
                send(exchange, 500, errorJson("internal error: " + e.getMessage()));
            }
        } finally {
            exchange.close();
        }
    }

    private String route(String path, Map<String, String> query) throws ApiError {
        switch (path) {
            case "/api/summary": return summary();
            case "/api/students": return students(query);
            case "/api/courses": return courses(query);
            case "/api/faculty": return faculty();
            case "/api/exams": return exams(query);
            case "/api/search": return search(query);
            case "/api/analytics": return analytics();
            default: throw new ApiError(404, "unknown endpoint: " + path);
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", JSON_TYPE);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return map;
    }

    private static String errorJson(String message) {
        return JsonWriter.object().put("error", message).toString();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // Endpoints
    // ------------------------------------------------------------------

    private String summary() {
        return JsonWriter.object()
                .put("students", ds.students().size())
                .put("faculty", ds.faculty().size())
                .put("courses", ds.courses().size())
                .put("assignments", ds.assignments().size())
                .put("resources", ds.resources().size())
                .put("examRecords", ds.examRecords().size())
                .put("activityEvents", ds.activityStream().size())
                .put("rooms", ds.rooms().size())
                .put("timeSlots", ds.timeSlots().size())
                .put("loadedFromDisk", ds.isLoadedFromDisk())
                .toString();
    }

    private String students(Map<String, String> query) throws ApiError {
        String idParam = query.get("id");
        if (idParam == null || idParam.isBlank()) {
            JsonWriter array = JsonWriter.array();
            for (Student s : ds.students()) {
                array.value(studentSummaryJson(s));
            }
            return array.toString();
        }
        int id = parseInt(idParam, "id");
        Student student = ds.studentsById().get(id);
        if (student == null) {
            throw new ApiError(404, "unknown student id: " + id);
        }
        ExamAnalytics.ReportCard card = ExamAnalytics.reportCard(ds, id);
        JsonWriter records = JsonWriter.array();
        for (ExamRecord r : card.records) {
            records.value(examJson(r));
        }
        return studentSummaryJson(student)
                .put("examRecords", records)
                .put("gpa", round2(card.gpa))
                .put("credits", card.credits)
                .put("failCount", card.failCount)
                .toString();
    }

    private static JsonWriter studentSummaryJson(Student s) {
        JsonWriter courses = JsonWriter.array();
        for (String code : s.enrolledCourses) {
            courses.value(code);
        }
        return JsonWriter.object()
                .put("id", s.id)
                .put("name", s.name)
                .put("program", s.program)
                .put("semester", s.semester)
                .put("cgpa", s.cgpa)
                .put("courses", courses);
    }

    private String courses(Map<String, String> query) throws ApiError {
        String codeParam = query.get("code");
        if (codeParam == null || codeParam.isBlank()) {
            Map<String, Integer> enrollment = enrollmentByCourse();
            JsonWriter array = JsonWriter.array();
            for (Course c : ds.courses()) {
                array.value(JsonWriter.object()
                        .put("code", c.code)
                        .put("name", c.name)
                        .put("department", c.department)
                        .put("credits", c.credits)
                        .put("semester", c.semester)
                        .put("enrollment", enrollment.getOrDefault(c.code, 0)));
            }
            return array.toString();
        }
        Course course = ds.coursesByCode().get(codeParam);
        if (course == null) {
            throw new ApiError(404, "unknown course code: " + codeParam);
        }
        ExamAnalytics.CourseStats stats = null;
        for (ExamAnalytics.CourseStats cs : ExamAnalytics.courseStats(ds)) {
            if (cs.code.equals(course.code)) {
                stats = cs;
                break;
            }
        }
        JsonWriter grades = JsonWriter.object();
        int studentsWithExams = 0;
        double avg = 0.0;
        int min = 0;
        int max = 0;
        double passPercent = 0.0;
        if (stats != null) {
            studentsWithExams = stats.students;
            avg = round2(stats.avgTotal);
            min = stats.minTotal;
            max = stats.maxTotal;
            passPercent = round2(stats.passPercent);
            for (int i = 0; i < ExamAnalytics.GRADES.length; i++) {
                grades.put(ExamAnalytics.GRADES[i], stats.gradeCounts[i]);
            }
        }
        return JsonWriter.object()
                .put("code", course.code)
                .put("name", course.name)
                .put("department", course.department)
                .put("credits", course.credits)
                .put("semester", course.semester)
                .put("enrollment", enrollmentByCourse().getOrDefault(course.code, 0))
                .put("examStats", JsonWriter.object()
                        .put("students", studentsWithExams)
                        .put("avg", avg)
                        .put("min", min)
                        .put("max", max)
                        .put("passPercent", passPercent)
                        .put("grades", grades))
                .toString();
    }

    private Map<String, Integer> enrollmentByCourse() {
        Map<String, Integer> enrollment = new HashMap<>();
        for (Student s : ds.students()) {
            for (String code : s.enrolledCourses) {
                enrollment.merge(code, 1, Integer::sum);
            }
        }
        return enrollment;
    }

    private String faculty() {
        JsonWriter array = JsonWriter.array();
        for (Faculty f : ds.faculty()) {
            JsonWriter expertise = JsonWriter.array();
            for (String code : f.expertise) {
                expertise.value(code);
            }
            array.value(JsonWriter.object()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("department", f.department)
                    .put("expertise", expertise));
        }
        return array.toString();
    }

    private String exams(Map<String, String> query) throws ApiError {
        Integer studentId = null;
        String idParam = query.get("studentId");
        if (idParam != null && !idParam.isBlank()) {
            studentId = parseInt(idParam, "studentId");
        }
        String courseParam = query.get("course");
        JsonWriter array = JsonWriter.array();
        for (ExamRecord r : ds.examRecords()) {
            if (studentId != null && r.studentId != studentId) {
                continue;
            }
            if (courseParam != null && !courseParam.isBlank()
                    && !r.courseCode.equalsIgnoreCase(courseParam)) {
                continue;
            }
            array.value(examJson(r));
        }
        return array.toString();
    }

    private static JsonWriter examJson(ExamRecord r) {
        return JsonWriter.object()
                .put("studentId", r.studentId)
                .put("courseCode", r.courseCode)
                .put("midsem", r.midsem)
                .put("endsem", r.endsem)
                .put("total", r.total())
                .put("grade", r.grade())
                .put("gradePoints", r.gradePoints())
                .put("passed", r.passed());
    }

    private String search(Map<String, String> query) {
        String q = query.getOrDefault("q", "");
        JsonWriter array = JsonWriter.array();
        for (SearchResult r : SearchService.search(ds, q)) {
            array.value(JsonWriter.object()
                    .put("kind", r.kind)
                    .put("id", r.id)
                    .put("title", r.title)
                    .put("subtitle", r.subtitle)
                    .put("score", r.score));
        }
        return array.toString();
    }

    private String analytics() {
        List<edutrack.model.ActivityEvent> events = ds.activityStream();
        LinkedHashMap<String, Integer> actions = ActivityAnalytics.countByAction(events);
        ActivityAnalytics.Summary s = ActivityAnalytics.summarize(events);
        JsonWriter actionsJson = JsonWriter.object();
        for (Map.Entry<String, Integer> e : actions.entrySet()) {
            actionsJson.put(e.getKey(), e.getValue());
        }
        return JsonWriter.object()
                .put("total", s.totalEvents)
                .put("days", s.distinctDays)
                .put("avgPerDay", round2(s.avgEventsPerDay))
                .put("peakHour", s.peakHour)
                .put("peakHourCount", s.peakHourCount)
                .put("courseRelatedEvents", s.courseRelatedEvents)
                .put("courseRelatedPercent", round2(s.courseRelatedPercent))
                .put("actions", actionsJson)
                .toString();
    }

    private static int parseInt(String raw, String param) throws ApiError {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiError(400, "parameter '" + param + "' must be an integer: " + raw);
        }
    }

    private static final class ApiError extends Exception {
        final int status;

        ApiError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    // ------------------------------------------------------------------
    // CLI entry (main menu): start, list endpoints, run until the user enters 0
    // ------------------------------------------------------------------

    public static void run(Scanner sc, DataStore ds) {
        System.out.println("\n--- EduTrack REST API ---");
        System.out.print("Port for the API server [8080] (0 = pick a free port, Enter keeps default) : ");
        String input = sc.hasNextLine() ? sc.nextLine().trim() : "";
        int port = DEFAULT_PORT;
        if (!input.isEmpty()) {
            try {
                int parsed = Integer.parseInt(input);
                if (parsed < 0 || parsed > 65535) {
                    System.out.println("Port must be 0..65535 — using " + DEFAULT_PORT + ".");
                } else {
                    port = parsed;
                }
            } catch (NumberFormatException e) {
                System.out.println("Not a number — using " + DEFAULT_PORT + ".");
            }
        }

        ApiServer api;
        try {
            api = start(ds, port);
        } catch (IOException e) {
            System.out.println("Could not start the API server on port " + port + ": " + e.getMessage());
            return;
        }

        System.out.println("EduTrack API listening on http://localhost:" + api.getPort() + "/api/summary");
        System.out.println("Endpoints (GET only, JSON):");
        System.out.println("  /api/summary                     counts of every collection + loadedFromDisk");
        System.out.println("  /api/students                    all students; ?id=N adds exam records + GPA");
        System.out.println("  /api/courses                     all courses;  ?code=X adds exam stats");
        System.out.println("  /api/faculty                     all faculty");
        System.out.println("  /api/exams                       exam records; ?studentId=N and/or ?course=X");
        System.out.println("  /api/search?q=text               global search (kind/id/title/subtitle/score)");
        System.out.println("  /api/analytics                   activity stats (per-action counts, days, peak hour)");
        System.out.println();
        System.out.println("Enter 0 to stop the server.");

        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.equals("0")) {
                break;
            }
            System.out.println("Server running on port " + api.getPort() + " — enter 0 to stop.");
        }
        api.stop();
        System.out.println("API server stopped.");
    }

    // ------------------------------------------------------------------
    // Self-test (non-interactive): java -cp bin edutrack.api.ApiServer
    // ------------------------------------------------------------------

    private static int checks;

    private static final class Fetch {
        final int status;
        final String body;
        final String contentType;

        Fetch(int status, String body, String contentType) {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
        }

        boolean isJson() {
            return contentType != null && contentType.contains("application/json");
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<>();
        DataStore ds = new DataStore(false);
        ApiServer api = start(ds, 0);
        int port = api.getPort();

        check(failures, "ephemeral port assigned", port > 0);
        check(failures, "isRunning() true after start", api.isRunning());

        Fetch summary = fetch(port, "/api/summary");
        check(failures, "GET /api/summary -> 200 JSON", summary.status == 200 && summary.isJson());
        check(failures, "/api/summary has all collection counts",
                summary.body.contains("\"students\":200")
                        && summary.body.contains("\"faculty\":12")
                        && summary.body.contains("\"courses\":20")
                        && summary.body.contains("\"assignments\":40")
                        && summary.body.contains("\"resources\":35")
                        && summary.body.contains("\"examRecords\":981")
                        && summary.body.contains("\"activityEvents\":100000")
                        && summary.body.contains("\"loadedFromDisk\":false"));
        check(failures, "/api/summary JSON balanced", balanced(summary.body));

        Fetch students = fetch(port, "/api/students");
        check(failures, "GET /api/students -> 200 JSON", students.status == 200 && students.isJson());
        check(failures, "/api/students lists first generated student",
                students.body.contains("\"id\":1000") && students.body.contains("Aarav Sharma"));
        check(failures, "/api/students JSON balanced", balanced(students.body));

        Fetch one = fetch(port, "/api/students?id=1000");
        check(failures, "GET /api/students?id=1000 -> 200", one.status == 200);
        check(failures, "student detail adds exam records and GPA",
                one.body.contains("Aarav Sharma")
                        && one.body.contains("\"examRecords\"")
                        && one.body.contains("\"gpa\"")
                        && one.body.contains("\"courseCode\""));
        check(failures, "student detail JSON balanced", balanced(one.body));

        Fetch missingStudent = fetch(port, "/api/students?id=999999");
        check(failures, "GET /api/students?id=999999 -> 404 JSON error",
                missingStudent.status == 404 && missingStudent.body.contains("\"error\""));

        Fetch badStudent = fetch(port, "/api/students?id=abc");
        check(failures, "GET /api/students?id=abc -> 400 JSON error",
                badStudent.status == 400 && badStudent.body.contains("\"error\""));

        Fetch courses = fetch(port, "/api/courses");
        check(failures, "GET /api/courses -> 200", courses.status == 200);
        check(failures, "/api/courses lists CS201 with enrollment",
                courses.body.contains("\"code\":\"CS201\"")
                        && courses.body.contains("Data Structures")
                        && courses.body.contains("\"enrollment\""));
        check(failures, "/api/courses JSON balanced", balanced(courses.body));

        Fetch course = fetch(port, "/api/courses?code=CS201");
        check(failures, "GET /api/courses?code=CS201 -> 200", course.status == 200);
        check(failures, "course detail adds exam stats",
                course.body.contains("Data Structures")
                        && course.body.contains("\"avg\"")
                        && course.body.contains("\"min\"")
                        && course.body.contains("\"max\"")
                        && course.body.contains("\"passPercent\""));
        check(failures, "course detail JSON balanced", balanced(course.body));

        Fetch missingCourse = fetch(port, "/api/courses?code=XX999");
        check(failures, "GET /api/courses?code=XX999 -> 404 JSON error",
                missingCourse.status == 404 && missingCourse.body.contains("\"error\""));

        Fetch faculty = fetch(port, "/api/faculty");
        check(failures, "GET /api/faculty -> 200", faculty.status == 200);
        check(failures, "/api/faculty lists generated faculty",
                faculty.body.contains("Dr. Anil Kapoor") && faculty.body.contains("\"expertise\""));
        check(failures, "/api/faculty JSON balanced", balanced(faculty.body));

        Fetch examsByStudent = fetch(port, "/api/exams?studentId=1000");
        check(failures, "GET /api/exams?studentId=1000 -> 200", examsByStudent.status == 200);
        check(failures, "exams filtered by student contain only that student",
                examsByStudent.body.contains("\"studentId\":1000")
                        && !examsByStudent.body.contains("\"studentId\":1001"));
        check(failures, "exams by student JSON balanced", balanced(examsByStudent.body));

        Fetch examsByCourse = fetch(port, "/api/exams?course=CS201");
        check(failures, "GET /api/exams?course=CS201 -> 200", examsByCourse.status == 200);
        check(failures, "exams filtered by course contain only CS201",
                examsByCourse.body.contains("\"courseCode\":\"CS201\"")
                        && !examsByCourse.body.contains("\"courseCode\":\"CS202\""));

        Fetch badExams = fetch(port, "/api/exams?studentId=xyz");
        check(failures, "GET /api/exams?studentId=xyz -> 400 JSON error",
                badExams.status == 400 && badExams.body.contains("\"error\""));

        Fetch search = fetch(port, "/api/search?q=data");
        check(failures, "GET /api/search?q=data -> 200", search.status == 200);
        check(failures, "search finds Data Structures with score fields",
                search.body.contains("Data Structures")
                        && search.body.contains("\"kind\":\"Course\"")
                        && search.body.contains("\"score\""));
        check(failures, "search JSON balanced", balanced(search.body));

        Fetch analytics = fetch(port, "/api/analytics");
        check(failures, "GET /api/analytics -> 200", analytics.status == 200);
        check(failures, "analytics has totals, days, peak hour and per-action counts",
                analytics.body.contains("\"total\":100000")
                        && analytics.body.contains("\"days\"")
                        && analytics.body.contains("\"peakHour\"")
                        && analytics.body.contains("\"LOGIN\""));
        check(failures, "analytics JSON balanced", balanced(analytics.body));

        Fetch unknown = fetch(port, "/api/nope");
        check(failures, "unknown path -> 404 JSON error",
                unknown.status == 404 && unknown.isJson() && unknown.body.contains("\"error\""));

        Fetch root = fetch(port, "/");
        check(failures, "root path -> 404 JSON error", root.status == 404 && root.body.contains("\"error\""));

        Fetch post = fetch(port, "/api/summary", "POST");
        check(failures, "POST /api/summary -> 405 JSON error",
                post.status == 405 && post.body.contains("\"error\""));

        check(failures, "concurrent requests all succeed", concurrentRequestsOk(port));

        api.stop();
        check(failures, "isRunning() false after stop", !api.isRunning());
        api.stop();
        check(failures, "stop() is idempotent", !api.isRunning());

        System.out.println("----");
        if (!failures.isEmpty()) {
            System.out.println("SELF-TEST FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("ALL " + checks + " CHECKS PASSED");
    }

    private static boolean concurrentRequestsOk(int port) {
        String[] paths = {
            "/api/summary", "/api/students?id=1000", "/api/courses?code=CS201",
            "/api/analytics", "/api/search?q=data", "/api/exams?course=CS201"
        };
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger bad = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 6; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    try {
                        Fetch f = fetch(port, paths[i % paths.length]);
                        if (f.status == 200 && balanced(f.body)) {
                            ok.incrementAndGet();
                        } else {
                            bad.incrementAndGet();
                        }
                    } catch (IOException e) {
                        bad.incrementAndGet();
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return ok.get() == 60 && bad.get() == 0;
    }

    private static Fetch fetch(int port, String pathAndQuery) throws IOException {
        return fetch(port, pathAndQuery, "GET");
    }

    private static Fetch fetch(int port, String pathAndQuery, String method) throws IOException {
        URL url = URI.create("http://localhost:" + port + pathAndQuery).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setUseCaches(false);
        int status = conn.getResponseCode();
        InputStream in = status < 400 ? conn.getInputStream() : conn.getErrorStream();
        String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        String contentType = conn.getContentType();
        conn.disconnect();
        return new Fetch(status, body, contentType);
    }

    /** Structural sanity: balanced {} and [] outside of string literals. */
    private static boolean balanced(String json) {
        int braces = 0;
        int brackets = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                braces++;
            } else if (ch == '}') {
                braces--;
            } else if (ch == '[') {
                brackets++;
            } else if (ch == ']') {
                brackets--;
            }
            if (braces < 0 || brackets < 0) {
                return false;
            }
        }
        return braces == 0 && brackets == 0 && !inString;
    }

    private static void check(List<String> failures, String name, boolean ok) {
        checks++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) {
            failures.add(name);
        }
    }
}
