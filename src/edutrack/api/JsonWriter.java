package edutrack.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal hand-rolled JSON builder: objects and arrays with string, number,
 * boolean and null primitives. Strings are escaped per RFC 8259 (quote,
 * backslash and control characters); numbers are emitted raw (non-finite
 * doubles collapse to 0, since NaN/Infinity are not valid JSON).
 *
 * Usage is fluent: {@code JsonWriter.object().put("id", 7).put("name", "x")}
 * builds an object, {@code JsonWriter.array().value(1).value("x")} builds an
 * array, and writers nest by passing one as a value. Calling toString() (or
 * nesting the writer) finishes it; appending afterwards throws.
 *
 * The non-interactive main() self-test checks exact serialization, escaping,
 * nesting and the mode guards; it exits 1 on any failure.
 * Run: java -cp bin edutrack.api.JsonWriter
 */
public final class JsonWriter {

    private final StringBuilder out = new StringBuilder();
    private final boolean objectMode;
    private boolean first = true;
    private boolean finished;

    private JsonWriter(boolean objectMode) {
        this.objectMode = objectMode;
        out.append(objectMode ? '{' : '[');
    }

    /** Starts a new JSON object builder. */
    public static JsonWriter object() {
        return new JsonWriter(true);
    }

    /** Starts a new JSON array builder. */
    public static JsonWriter array() {
        return new JsonWriter(false);
    }

    // ------------------------------------------------------------------
    // Object members
    // ------------------------------------------------------------------

    public JsonWriter put(String key, String value) {
        return entry(key, value == null ? "null" : quoted(value));
    }

    public JsonWriter put(String key, long value) {
        return entry(key, Long.toString(value));
    }

    public JsonWriter put(String key, double value) {
        return entry(key, number(value));
    }

    public JsonWriter put(String key, boolean value) {
        return entry(key, value ? "true" : "false");
    }

    public JsonWriter put(String key, JsonWriter value) {
        return entry(key, raw(value));
    }

    // ------------------------------------------------------------------
    // Array elements
    // ------------------------------------------------------------------

    public JsonWriter value(String value) {
        return element(value == null ? "null" : quoted(value));
    }

    public JsonWriter value(long value) {
        return element(Long.toString(value));
    }

    public JsonWriter value(double value) {
        return element(number(value));
    }

    public JsonWriter value(boolean value) {
        return element(value ? "true" : "false");
    }

    public JsonWriter value(JsonWriter value) {
        return element(raw(value));
    }

    // ------------------------------------------------------------------
    // Escaping
    // ------------------------------------------------------------------

    /** Escapes a string for use inside JSON double quotes (quotes, backslash, control chars). */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        escapeInto(s, sb);
        return sb.toString();
    }

    private static void escapeInto(String s, StringBuilder sb) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    @Override
    public String toString() {
        finish();
        return out.toString();
    }

    private JsonWriter entry(String key, String rawValue) {
        require(objectMode, "put() is only valid on an object writer");
        require(!finished, "writer is already finished");
        comma();
        out.append(quoted(key)).append(':').append(rawValue);
        return this;
    }

    private JsonWriter element(String rawValue) {
        require(!objectMode, "value() is only valid on an array writer");
        require(!finished, "writer is already finished");
        comma();
        out.append(rawValue);
        return this;
    }

    private void comma() {
        if (first) {
            first = false;
        } else {
            out.append(',');
        }
    }

    private void finish() {
        if (!finished) {
            out.append(objectMode ? '}' : ']');
            finished = true;
        }
    }

    private static String raw(JsonWriter nested) {
        if (nested == null) {
            return "null";
        }
        nested.finish();
        return nested.out.toString();
    }

    private static String quoted(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        escapeInto(s, sb);
        sb.append('"');
        return sb.toString();
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        return Double.toString(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    // ------------------------------------------------------------------
    // Self-test (non-interactive): java -cp bin edutrack.api.JsonWriter
    // ------------------------------------------------------------------

    private static int checks;

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        check(failures, "object with primitives serializes exactly",
                JsonWriter.object()
                        .put("id", 7)
                        .put("name", "x")
                        .put("ok", true)
                        .put("gpa", 2.5)
                        .toString()
                        .equals("{\"id\":7,\"name\":\"x\",\"ok\":true,\"gpa\":2.5}"));

        check(failures, "array with mixed primitives serializes exactly",
                JsonWriter.array().value(1).value("x").value(false).toString()
                        .equals("[1,\"x\",false]"));

        check(failures, "empty object and array", JsonWriter.object().toString().equals("{}")
                && JsonWriter.array().toString().equals("[]"));

        check(failures, "null string becomes JSON null",
                JsonWriter.object().put("a", (String) null).toString().equals("{\"a\":null}"));

        check(failures, "escape quotes and backslash",
                escape("a\"b\\c").equals("a\\\"b\\\\c"));

        check(failures, "escape newline/tab shortcuts",
                escape("x\ny\t").equals("x\\ny\\t"));

        check(failures, "escape control char as \\u00xx",
                escape(String.valueOf((char) 0x01)).equals("\\u0001") && escape(String.valueOf((char) 0x1F)).equals("\\u001f"));

        check(failures, "non-ASCII passes through unescaped",
                escape("café").equals("café"));

        check(failures, "non-finite doubles collapse to 0",
                JsonWriter.array().value(Double.NaN).value(Double.POSITIVE_INFINITY)
                        .value(Double.NEGATIVE_INFINITY).toString().equals("[0,0,0]"));

        String nested = JsonWriter.object()
                .put("student", JsonWriter.object()
                        .put("id", 1000)
                        .put("courses", JsonWriter.array().value("CS101").value("CS201")))
                .put("tags", JsonWriter.array())
                .toString();
        check(failures, "nested writers serialize exactly", nested.equals(
                "{\"student\":{\"id\":1000,\"courses\":[\"CS101\",\"CS201\"]},\"tags\":[]}"));

        check(failures, "toString() is idempotent", JsonWriter.object().put("a", 1).toString()
                .equals(JsonWriter.object().put("a", 1).toString()));

        JsonWriter same = JsonWriter.object().put("a", 1);
        String firstRender = same.toString();
        check(failures, "repeated toString() does not duplicate closing brace",
                same.toString().equals(firstRender) && firstRender.equals("{\"a\":1}"));

        check(failures, "put() on an array writer throws",
                throwsIllegalState(() -> JsonWriter.array().put("a", 1)));

        check(failures, "value() on an object writer throws",
                throwsIllegalState(() -> JsonWriter.object().value(1)));

        JsonWriter done = JsonWriter.object().put("a", 1);
        done.toString();
        check(failures, "appending after finish throws",
                throwsIllegalState(() -> done.put("b", 2)));

        check(failures, "null nested writer becomes JSON null",
                JsonWriter.object().put("x", (JsonWriter) null).toString().equals("{\"x\":null}"));

        System.out.println("----");
        if (!failures.isEmpty()) {
            System.out.println("SELF-TEST FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("ALL " + checks + " CHECKS PASSED");
    }

    private static boolean throwsIllegalState(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static void check(List<String> failures, String name, boolean ok) {
        checks++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) {
            failures.add(name);
        }
    }
}
