package edutrack.data;

import java.util.List;
import java.util.Map;

import edutrack.model.ActivityEvent;
import edutrack.model.Assignment;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Faculty;
import edutrack.model.LearningResource;
import edutrack.model.Student;

/**
 * Immutable point-in-time view of all DataStore collections and indexes.
 * Every reference is already immutable in DataStore; the snapshot itself
 * prevents a multi-collection reader from mixing revisions.
 */
public final class DataSnapshot {
    private final long revision;
    private final boolean loadedFromDisk;
    private final List<Student> students;
    private final List<Faculty> faculty;
    private final List<Course> courses;
    private final List<Assignment> assignments;
    private final List<LearningResource> resources;
    private final List<ActivityEvent> activityStream;
    private final List<ExamRecord> examRecords;
    private final Map<Integer, Student> studentsById;
    private final Map<String, Course> coursesByCode;
    private final List<String> rooms;
    private final List<String> timeSlots;

    DataSnapshot(long revision, boolean loadedFromDisk,
            List<Student> students, List<Faculty> faculty, List<Course> courses,
            List<Assignment> assignments, List<LearningResource> resources,
            List<ActivityEvent> activityStream, List<ExamRecord> examRecords,
            Map<Integer, Student> studentsById, Map<String, Course> coursesByCode,
            List<String> rooms, List<String> timeSlots) {
        this.revision = revision;
        this.loadedFromDisk = loadedFromDisk;
        this.students = students;
        this.faculty = faculty;
        this.courses = courses;
        this.assignments = assignments;
        this.resources = resources;
        this.activityStream = activityStream;
        this.examRecords = examRecords;
        this.studentsById = studentsById;
        this.coursesByCode = coursesByCode;
        this.rooms = rooms;
        this.timeSlots = timeSlots;
    }

    public long revision() { return revision; }
    public boolean isLoadedFromDisk() { return loadedFromDisk; }
    public List<Student> students() { return students; }
    public List<Faculty> faculty() { return faculty; }
    public List<Course> courses() { return courses; }
    public List<Assignment> assignments() { return assignments; }
    public List<LearningResource> resources() { return resources; }
    public List<ActivityEvent> activityStream() { return activityStream; }
    public List<ExamRecord> examRecords() { return examRecords; }
    public Map<Integer, Student> studentsById() { return studentsById; }
    public Map<String, Course> coursesByCode() { return coursesByCode; }
    public List<String> rooms() { return rooms; }
    public List<String> timeSlots() { return timeSlots; }
}
