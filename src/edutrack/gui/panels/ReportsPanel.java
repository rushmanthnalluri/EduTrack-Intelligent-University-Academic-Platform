package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import edutrack.data.DataStore;
import edutrack.features.ReportGenerator;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Course;
import edutrack.model.Student;

/**
 * Reports &amp; Export panel: one card per document the academic office needs
 * (student transcript, course grade sheet, department summary, at-risk list).
 * Every card offers Preview into the shared read-only preview pane and Save…
 * through a JFileChooser; all generation and file I/O runs via runAsync.
 */
public class ReportsPanel extends ModulePanel {

    private final JTextArea previewArea;
    private final JLabel statusLabel;

    private final JComboBox<String> studentCombo;
    private final JComboBox<String> courseCombo;
    private List<Student> students;
    private List<Course> courses;

    private final JButton deptPreviewButton = GuiTheme.primaryButton("Preview");
    private final JButton deptSaveButton = GuiTheme.secondaryButton("Save…");
    private final JButton riskPreviewButton = GuiTheme.primaryButton("Preview");
    private final JButton riskSaveButton = GuiTheme.secondaryButton("Save…");

    // Cache of the last previewed document so Save… can reuse it without rescanning.
    private String cachedKey;
    private String cachedText;

    public ReportsPanel(DataStore dataStore) {
        super(dataStore);

        students = dataStore.students();
        courses = dataStore.courses();

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Reports & Export");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Transcripts · course grade sheets · department summaries · at-risk lists — preview, then save as text or CSV");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        studentCombo = new JComboBox<>();
        for (Student s : students) {
            studentCombo.addItem(s.id + " — " + s.name);
        }
        studentCombo.setFont(GuiTheme.BODY);
        courseCombo = new JComboBox<>();
        for (Course c : courses) {
            courseCombo.addItem(c.code + " — " + c.name);
        }
        courseCombo.setFont(GuiTheme.BODY);

        JPanel cardsRow = new JPanel(new GridLayout(1, 4, 12, 0));
        cardsRow.setOpaque(false);
        cardsRow.add(card("Student Transcript", transcriptControls()));
        cardsRow.add(card("Course Grade Sheet", gradeSheetControls()));
        cardsRow.add(card("Department Summary", fixedControls(
                "One row per department: courses, enrollment, exam stats, GPA",
                deptPreviewButton, deptSaveButton,
                this::previewDepartmentSummary, this::saveDepartmentSummary)));
        cardsRow.add(card("At-Risk Report", fixedControls(
                "Students with 2+ F grades or GPA < 5.0 (via Exam Analytics)",
                riskPreviewButton, riskSaveButton,
                this::previewAtRisk, this::saveAtRisk)));

        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(GuiTheme.MONO);
        previewArea.setForeground(GuiTheme.TEXT);
        previewArea.setBackground(GuiTheme.CARD_BG);
        previewArea.setText("Pick a report above and press Preview.");
        previewArea.setCaretPosition(0);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(GuiTheme.BODY_BOLD);
        statusLabel.setForeground(GuiTheme.ACCENT_DARK);

        JPanel previewBody = new JPanel(new BorderLayout(8, 8));
        previewBody.setOpaque(false);
        previewBody.add(new JScrollPane(previewArea), BorderLayout.CENTER);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusRow.setOpaque(false);
        statusRow.add(statusLabel);
        previewBody.add(statusRow, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.add(cardsRow, BorderLayout.NORTH);
        center.add(card("Preview", previewBody), BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    /** Refreshes report selectors after Manage Records changes the live dataset. */
    public void refresh() {
        int oldStudent = studentCombo.getSelectedIndex();
        int oldCourse = courseCombo.getSelectedIndex();
        students = dataStore.students();
        courses = dataStore.courses();
        studentCombo.removeAllItems();
        for (Student s : students) {
            studentCombo.addItem(s.id + " — " + s.name);
        }
        courseCombo.removeAllItems();
        for (Course c : courses) {
            courseCombo.addItem(c.code + " — " + c.name);
        }
        if (!students.isEmpty()) {
            studentCombo.setSelectedIndex(Math.min(Math.max(oldStudent, 0), students.size() - 1));
        }
        if (!courses.isEmpty()) {
            courseCombo.setSelectedIndex(Math.min(Math.max(oldCourse, 0), courses.size() - 1));
        }
        cachedKey = null;
        cachedText = null;
        previewArea.setText("Pick a report above and press Preview.");
        statusLabel.setText("Ready — live dataset refreshed.");
        studentCombo.setEnabled(!students.isEmpty());
        courseCombo.setEnabled(!courses.isEmpty());
    }

    // ------------------------------------------------------------------
    // Card controls
    // ------------------------------------------------------------------

    private JPanel transcriptControls() {
        JButton previewButton = GuiTheme.primaryButton("Preview");
        JButton saveButton = GuiTheme.secondaryButton("Save…");

        JPanel panel = controlCard();
        panel.add(fieldLabel("Student:"));
        studentCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, studentCombo.getPreferredSize().height));
        studentCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(studentCombo);
        panel.add(Box.createVerticalStrut(6));
        panel.add(buttonRow(previewButton, saveButton));

        previewButton.addActionListener(e -> {
            if (students.isEmpty() || studentCombo.getSelectedIndex() < 0) {
                statusLabel.setText("No students are available.");
                return;
            }
            int id = selectedStudentId();
            runPreview(previewButton, saveButton, "transcript:" + id,
                    () -> ReportGenerator.transcript(dataStore, id));
        });
        saveButton.addActionListener(e -> {
            if (students.isEmpty() || studentCombo.getSelectedIndex() < 0) {
                statusLabel.setText("No students are available.");
                return;
            }
            int id = selectedStudentId();
            runSave(saveButton, previewButton, "transcript:" + id,
                    () -> ReportGenerator.transcript(dataStore, id),
                    ReportGenerator.transcriptFileName(id));
        });
        return panel;
    }

    private JPanel gradeSheetControls() {
        JButton previewButton = GuiTheme.primaryButton("Preview");
        JButton saveButton = GuiTheme.secondaryButton("Save…");

        JPanel panel = controlCard();
        panel.add(fieldLabel("Course:"));
        courseCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, courseCombo.getPreferredSize().height));
        courseCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(courseCombo);
        panel.add(Box.createVerticalStrut(6));
        panel.add(buttonRow(previewButton, saveButton));

        previewButton.addActionListener(e -> {
            if (courses.isEmpty() || courseCombo.getSelectedIndex() < 0) {
                statusLabel.setText("No courses are available.");
                return;
            }
            String code = selectedCourseCode();
            runPreview(previewButton, saveButton, "gradesheet:" + code,
                    () -> ReportGenerator.csvToString(ReportGenerator.courseGradeSheet(dataStore, code)));
        });
        saveButton.addActionListener(e -> {
            if (courses.isEmpty() || courseCombo.getSelectedIndex() < 0) {
                statusLabel.setText("No courses are available.");
                return;
            }
            String code = selectedCourseCode();
            runSave(saveButton, previewButton, "gradesheet:" + code,
                    () -> ReportGenerator.csvToString(ReportGenerator.courseGradeSheet(dataStore, code)),
                    ReportGenerator.gradeSheetFileName(code));
        });
        return panel;
    }

    private JPanel fixedControls(String description, JButton previewButton, JButton saveButton,
            Runnable onPreview, Runnable onSave) {
        JPanel panel = controlCard();
        JLabel desc = new JLabel("<html>" + description + "</html>");
        desc.setFont(GuiTheme.BODY);
        desc.setForeground(GuiTheme.MUTED);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(desc);
        panel.add(Box.createVerticalStrut(6));
        panel.add(buttonRow(previewButton, saveButton));

        previewButton.addActionListener(e -> onPreview.run());
        saveButton.addActionListener(e -> onSave.run());
        return panel;
    }

    private void previewDepartmentSummary() {
        runPreview(deptPreviewButton, deptSaveButton, "department",
                () -> ReportGenerator.csvToString(ReportGenerator.departmentSummary(dataStore)));
    }

    private void saveDepartmentSummary() {
        runSave(deptSaveButton, deptPreviewButton, "department",
                () -> ReportGenerator.csvToString(ReportGenerator.departmentSummary(dataStore)),
                ReportGenerator.departmentSummaryFileName());
    }

    private void previewAtRisk() {
        runPreview(riskPreviewButton, riskSaveButton, "atrisk",
                () -> ReportGenerator.csvToString(ReportGenerator.atRiskReport(dataStore)));
    }

    private void saveAtRisk() {
        runSave(riskSaveButton, riskPreviewButton, "atrisk",
                () -> ReportGenerator.csvToString(ReportGenerator.atRiskReport(dataStore)),
                ReportGenerator.atRiskFileName());
    }

    // ------------------------------------------------------------------
    // Preview / save plumbing (all heavy work via runAsync)
    // ------------------------------------------------------------------

    private interface TextWork {
        String build() throws Exception;
    }

    private void runPreview(JButton previewButton, JButton saveButton, String key, TextWork work) {
        setBusy(previewButton, saveButton, true);
        statusLabel.setText("Generating preview…");
        runAsync(work::build, text -> {
            setBusy(previewButton, saveButton, false);
            cachedKey = key;
            cachedText = text;
            previewArea.setText(text);
            previewArea.setCaretPosition(0);
            statusLabel.setText("Preview ready — " + countLines(text) + " lines.");
        }, error -> {
            setBusy(previewButton, saveButton, false);
            statusLabel.setText("Preview failed.");
            showError(error);
        }, previewButton, saveButton);
    }

    private void runSave(JButton saveButton, JButton previewButton, String key, TextWork work,
            String defaultFileName) {
        setBusy(previewButton, saveButton, true);
        statusLabel.setText("Preparing document…");
        runAsync(work::build, text -> {
            cachedKey = key;
            cachedText = text;
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File(defaultFileName));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                setBusy(previewButton, saveButton, false);
                statusLabel.setText("Save cancelled.");
                return;
            }
            Path path = chooser.getSelectedFile().toPath();
            statusLabel.setText("Writing " + path.getFileName() + "…");
            runAsync(() -> {
                ReportGenerator.saveText(path, text);
                return path;
            }, written -> {
                setBusy(previewButton, saveButton, false);
                statusLabel.setText("Saved to " + written.toAbsolutePath());
            }, error -> {
                setBusy(previewButton, saveButton, false);
                statusLabel.setText("Save failed.");
                showError(error);
            });
        }, error -> {
            setBusy(previewButton, saveButton, false);
            statusLabel.setText("Save failed.");
            showError(error);
        });
    }

    private static void setBusy(JButton previewButton, JButton saveButton, boolean busy) {
        previewButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
    }

    private static int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // Small UI helpers
    // ------------------------------------------------------------------

    private int selectedStudentId() {
        return students.get(studentCombo.getSelectedIndex()).id;
    }

    private String selectedCourseCode() {
        return courses.get(courseCombo.getSelectedIndex()).code;
    }

    private static JPanel controlCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel buttonRow(JButton previewButton, JButton saveButton) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(previewButton);
        row.add(saveButton);
        return row;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.BODY);
        label.setForeground(GuiTheme.TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
