package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import edutrack.features.RecordQueries;
import edutrack.gui.GuiTheme;
import edutrack.model.ActivityEvent;
import edutrack.model.Assignment;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.LearningResource;

/**
 * Themed modal detail dialogs for the Records Browser. Each static method
 * renders one {@link RecordQueries} detail bundle; the dialogs are built on
 * the EDT after the bundle has been computed on a background thread.
 */
final class RecordsDetailDialog {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RecordsDetailDialog() {
    }

    // ------------------------------------------------------------------
    // Student detail: marks table + GPA/rank + recent activity
    // ------------------------------------------------------------------

    static void showStudent(Window owner, RecordQueries.StudentDetail d) {
        JDialog dialog = baseDialog(owner, "Student — " + d.student.name, 880, 640);

        Map<String, Course> byCode = new HashMap<>();
        for (Course c : d.courses) {
            byCode.put(c.code, c);
        }

        RecordsPanel.RecordsTableModel marksModel = new RecordsPanel.RecordsTableModel(
                new String[] { "Code", "Course", "Midsem /30", "Endsem /70", "Total /100", "Grade" },
                new Class<?>[] { String.class, String.class, Integer.class,
                        Integer.class, Integer.class, String.class });
        for (ExamRecord r : d.examRecords) {
            Course c = byCode.get(r.courseCode);
            marksModel.addRow(new Object[] {
                    r.courseCode, c == null ? "—" : c.name,
                    r.midsem, r.endsem, r.total(), r.grade()
            });
        }
        JTable marksTable = RecordsPanel.styledTable(marksModel);
        RecordsPanel.setWidths(marksTable, 70, 300, 90, 90, 90, 70);

        DefaultListModel<String> activityModel = new DefaultListModel<>();
        for (ActivityEvent ev : d.recentActivity) {
            activityModel.addElement(String.format("%s   %-18s %s",
                    TIME_FMT.format(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(ev.timestamp), ZoneId.systemDefault())),
                    ev.action, ev.details));
        }
        JList<String> activityList = new JList<>(activityModel);
        activityList.setFont(GuiTheme.MONO);
        activityList.setBackground(GuiTheme.CARD_BG);
        activityList.setForeground(GuiTheme.TEXT);
        activityList.setFixedCellHeight(24);

        JPanel header = dialogHeader(
                d.student.name,
                String.format("ID %d · %s · Semester %d · %d enrolled courses",
                        d.student.id, d.student.program, d.student.semester,
                        d.courses.size()),
                String.format("GPA (exam records): %.2f · Rank %d of %d · better than %.1f%% of students",
                        d.gpa, d.rank, d.totalStudents, d.percentile));

        JPanel center = new JPanel(new GridLayout(2, 1, 10, 10));
        center.setOpaque(false);
        center.add(section(String.format("Marks — %d courses", d.examRecords.size()),
                new JScrollPane(marksTable)));
        center.add(section(String.format("Recent activity — last %d of %d events",
                d.recentActivity.size(), d.activityTotal), new JScrollPane(activityList)));

        finish(dialog, header, center);
    }

    // ------------------------------------------------------------------
    // Course detail: exam stats + faculty/assignment/resource lists
    // ------------------------------------------------------------------

    static void showCourse(Window owner, RecordQueries.CourseDetail d) {
        JDialog dialog = baseDialog(owner, "Course — " + d.course.code, 900, 560);

        JPanel header = dialogHeader(
                d.course.code + " · " + d.course.name,
                String.format("%s · %d credits · Semester %d", d.course.department,
                        d.course.credits, d.course.semester),
                String.format("Enrolled: %d · Exam records: %d · Avg %.1f · Min %d · Max %d · Pass %.1f%%",
                        d.enrollmentCount, d.examCount, d.avgTotal, d.minTotal, d.maxTotal,
                        d.passPercent));

        DefaultListModel<String> facultyModel = new DefaultListModel<>();
        for (edutrack.model.Faculty f : d.assignedFaculty) {
            facultyModel.addElement(f.name + "  (" + f.department + ")");
        }
        JList<String> facultyList = textList(facultyModel);

        DefaultListModel<String> assignmentModel = new DefaultListModel<>();
        for (Assignment a : d.assignments) {
            assignmentModel.addElement(a.title);
        }
        JList<String> assignmentList = textList(assignmentModel);

        DefaultListModel<String> resourceModel = new DefaultListModel<>();
        for (LearningResource r : d.resources) {
            resourceModel.addElement("[" + r.type + "] " + r.title);
        }
        JList<String> resourceList = textList(resourceModel);

        JPanel lists = new JPanel(new GridLayout(1, 3, 10, 10));
        lists.setOpaque(false);
        lists.add(section("Assigned faculty (" + d.assignedFaculty.size() + ")",
                new JScrollPane(facultyList)));
        lists.add(section("Assignments (" + d.assignments.size() + ")",
                new JScrollPane(assignmentList)));
        lists.add(section("Resources (" + d.resources.size() + ")",
                new JScrollPane(resourceList)));

        JPanel stats = new JPanel(new GridLayout(2, 3, 10, 6));
        stats.setOpaque(false);
        addStat(stats, "Enrolled students", String.valueOf(d.enrollmentCount));
        addStat(stats, "Exam records", String.valueOf(d.examCount));
        addStat(stats, "Pass rate", String.format("%.1f%%", d.passPercent));
        addStat(stats, "Average total", String.format("%.1f / 100", d.avgTotal));
        addStat(stats, "Minimum total", d.minTotal + " / 100");
        addStat(stats, "Maximum total", d.maxTotal + " / 100");

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setOpaque(false);
        center.add(section("Exam statistics", stats), BorderLayout.NORTH);
        center.add(lists, BorderLayout.CENTER);

        finish(dialog, header, center);
    }

    // ------------------------------------------------------------------
    // Faculty detail: course list with enrollments
    // ------------------------------------------------------------------

    static void showFaculty(Window owner, RecordQueries.FacultyDetail d) {
        JDialog dialog = baseDialog(owner, "Faculty — " + d.faculty.name, 820, 520);

        int totalEnrollment = 0;
        RecordsPanel.RecordsTableModel courseModel = new RecordsPanel.RecordsTableModel(
                new String[] { "Code", "Course", "Credits", "Semester", "Enrolled" },
                new Class<?>[] { String.class, String.class, Integer.class,
                        Integer.class, Integer.class });
        for (RecordQueries.FacultyDetail.CourseLoad load : d.courses) {
            courseModel.addRow(new Object[] {
                    load.course.code, load.course.name, load.course.credits,
                    load.course.semester, load.enrollment
            });
            totalEnrollment += load.enrollment;
        }
        JTable courseTable = RecordsPanel.styledTable(courseModel);
        RecordsPanel.setWidths(courseTable, 80, 360, 80, 90, 90);

        JPanel header = dialogHeader(
                d.faculty.name,
                String.format("ID %d · %s", d.faculty.id, d.faculty.department),
                String.format("%d courses assigned · %d students across them",
                        d.courses.size(), totalEnrollment));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setOpaque(false);
        center.add(section("Courses taught (via expertise)", new JScrollPane(courseTable)),
                BorderLayout.CENTER);

        finish(dialog, header, center);
    }

    // ------------------------------------------------------------------
    // Shared dialog scaffolding
    // ------------------------------------------------------------------

    private static JDialog baseDialog(Window owner, String title, int width, int height) {
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(width, height);
        dialog.setMinimumSize(new Dimension(520, 380));
        return dialog;
    }

    private static JPanel dialogHeader(String titleText, String metaText, String highlightText) {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JLabel meta = new JLabel(metaText);
        meta.setFont(GuiTheme.BODY);
        meta.setForeground(GuiTheme.MUTED);
        meta.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JLabel highlight = new JLabel(highlightText);
        highlight.setFont(GuiTheme.BODY_BOLD);
        highlight.setForeground(GuiTheme.ACCENT_DARK);
        highlight.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(meta);
        header.add(Box.createVerticalStrut(4));
        header.add(highlight);
        return header;
    }

    private static JPanel section(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(GuiTheme.CARD_BG);
        panel.setBorder(GuiTheme.cardBorder());
        JLabel heading = new JLabel(title);
        heading.setFont(GuiTheme.H2);
        heading.setForeground(GuiTheme.TEXT);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private static JList<String> textList(DefaultListModel<String> model) {
        JList<String> list = new JList<>(model);
        list.setFont(GuiTheme.BODY);
        list.setBackground(GuiTheme.CARD_BG);
        list.setForeground(GuiTheme.TEXT);
        list.setFixedCellHeight(24);
        return list;
    }

    private static void addStat(JPanel panel, String label, String value) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setOpaque(false);
        JLabel name = new JLabel(label);
        name.setFont(GuiTheme.BODY);
        name.setForeground(GuiTheme.MUTED);
        JLabel number = new JLabel(value);
        number.setFont(GuiTheme.BODY_BOLD);
        number.setForeground(GuiTheme.TEXT);
        cell.add(name, BorderLayout.NORTH);
        cell.add(number, BorderLayout.CENTER);
        panel.add(cell);
    }

    private static void finish(JDialog dialog, JPanel header, JComponent center) {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBackground(GuiTheme.BG);
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.add(header, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);

        JButton close = GuiTheme.primaryButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(close);
        content.add(south, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
    }
}
