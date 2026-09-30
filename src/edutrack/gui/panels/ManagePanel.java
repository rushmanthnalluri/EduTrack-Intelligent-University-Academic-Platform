package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import edutrack.data.CsvStore;
import edutrack.data.DataStore;
import edutrack.features.ManageSupport;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.Assignment;
import edutrack.model.Course;
import edutrack.model.ExamRecord;
import edutrack.model.Faculty;
import edutrack.model.LearningResource;
import edutrack.model.Student;

/**
 * Manage Records (CRUD): add/delete students, faculty and courses, manage
 * enrollments and exam marks, and persist or reset the CSV store. Header
 * follows the platform's standard panel style. All CRUD goes through DataStore's synchronized
 * copy-on-write API; IllegalArgumentException is surfaced via showError. File
 * I/O (save / reset) runs via runAsync with the triggering button disabled.
 * Key components carry setName() identifiers so a UI harness can drive them.
 */
public class ManagePanel extends ModulePanel {

    private static final Color AMBER = new Color(0xD97706);

    private ManageTableModel studentsModel;
    private JTable studentsTable;
    private JLabel studentsCount;

    private ManageTableModel facultyModel;
    private JTable facultyTable;
    private JLabel facultyCount;

    private ManageTableModel coursesModel;
    private JTable coursesTable;
    private JLabel coursesCount;

    private JComboBox<Student> studentPicker;
    private DefaultListModel<String> enrolledModel;
    private JList<String> enrolledList;
    private JComboBox<String> enrollCombo;
    private JComboBox<String> marksCourseCombo;
    private JSpinner midsemSpinner;
    private JSpinner endsemSpinner;
    private JLabel gradePreview;
    private boolean enrollmentRefreshing;

    private JLabel dirtyLabel;

    public ManagePanel(DataStore dataStore) {
        super(dataStore);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Manage Records");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Add / remove students, faculty and courses · enrollments and marks entry · CSV persistence");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(GuiTheme.BODY_BOLD);
        tabs.setName("manage.tabs");
        tabs.addTab("Students", buildStudentsTab());
        tabs.addTab("Faculty", buildFacultyTab());
        tabs.addTab("Courses", buildCoursesTab());
        tabs.addTab("Enrollment & Marks", buildEnrollmentTab());

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        refreshAll();
    }

    // ------------------------------------------------------------------
    // Tab (a): Students
    // ------------------------------------------------------------------

    private JComponent buildStudentsTab() {
        JButton addButton = GuiTheme.primaryButton("Add Student…");
        JButton deleteButton = GuiTheme.secondaryButton("Delete Selected");

        studentsModel = new ManageTableModel(
                new String[] { "ID", "Name", "Program", "Semester", "CGPA", "Courses" },
                new Class<?>[] { Integer.class, String.class, String.class,
                        Integer.class, Double.class, Integer.class });
        studentsTable = styledTable(studentsModel);
        setWidths(studentsTable, 70, 230, 180, 90, 90, 90);
        studentsCount = countLabel();

        JPanel controls = controlRow();
        controls.add(addButton);
        controls.add(deleteButton);

        JPanel tab = tabPanel();
        tab.add(card("Record actions", controls), BorderLayout.NORTH);
        tab.add(card("Students", new JScrollPane(studentsTable)), BorderLayout.CENTER);
        tab.add(studentsCount, BorderLayout.SOUTH);

        addButton.addActionListener(e -> openAddStudentDialog());
        deleteButton.addActionListener(e -> deleteSelectedStudent());
        return tab;
    }

    private void openAddStudentDialog() {
        JTextField idField = new JTextField(String.valueOf(ManageSupport.nextFreeStudentId(dataStore)), 10);
        JTextField nameField = new JTextField(18);
        JComboBox<String> programCombo = new JComboBox<>(existingPrograms());
        JSpinner semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
        JSpinner cgpaSpinner = new JSpinner(new SpinnerNumberModel(8.0, 0.0, 10.0, 0.1));
        CourseCheckList courseChecks = new CourseCheckList(dataStore.courses());
        JScrollPane checksScroll = new JScrollPane(courseChecks);
        checksScroll.setPreferredSize(new Dimension(360, 130));

        JPanel form = formPanel();
        addRow(form, 0, "ID:", idField);
        addRow(form, 1, "Name:", nameField);
        addRow(form, 2, "Program:", programCombo);
        addRow(form, 3, "Semester:", semesterSpinner);
        addRow(form, 4, "CGPA:", cgpaSpinner);
        addWideRow(form, 5, "Enrolled courses:", checksScroll);

        ManageFormDialog dialog = new ManageFormDialog(SwingUtilities.getWindowAncestor(this),
                "Add Student", form, () -> {
                    int id;
                    try {
                        id = Integer.parseInt(idField.getText().trim());
                    } catch (NumberFormatException ex) {
                        showInputError("ID must be a whole number.");
                        return false;
                    }
                    commitSpinner(semesterSpinner);
                    commitSpinner(cgpaSpinner);
                    String name = nameField.getText().trim();
                    int semester = ((Number) semesterSpinner.getValue()).intValue();
                    double cgpa = ((Number) cgpaSpinner.getValue()).doubleValue();
                    String error = ManageSupport.validateStudent(id, name, semester, cgpa);
                    if (error != null) {
                        showInputError(error);
                        return false;
                    }
                    String program = (String) programCombo.getSelectedItem();
                    try {
                        dataStore.addStudent(
                                new Student(id, name, program, semester, cgpa, courseChecks.selectedCodes()));
                    } catch (IllegalArgumentException ex) {
                        showError(ex);
                        return false;
                    }
                    refreshAll();
                    return true;
                });
        dialog.setVisible(true);
    }

    private void deleteSelectedStudent() {
        int view = studentsTable.getSelectedRow();
        if (view < 0) {
            showInputError("Select a student row first.");
            return;
        }
        int row = studentsTable.convertRowIndexToModel(view);
        int id = (Integer) studentsModel.getValueAt(row, 0);
        String name = (String) studentsModel.getValueAt(row, 1);
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete student " + id + " (" + name + ")?\nTheir exam records will be removed as well.",
                "Delete student", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            dataStore.removeStudent(id);
        } catch (IllegalArgumentException ex) {
            showError(ex);
            return;
        }
        refreshAll();
    }

    // ------------------------------------------------------------------
    // Tab (b): Faculty
    // ------------------------------------------------------------------

    private JComponent buildFacultyTab() {
        JButton addButton = GuiTheme.primaryButton("Add Faculty…");
        JButton deleteButton = GuiTheme.secondaryButton("Delete Selected");

        facultyModel = new ManageTableModel(
                new String[] { "ID", "Name", "Department", "Expertise" },
                new Class<?>[] { Integer.class, String.class, String.class, Integer.class });
        facultyTable = styledTable(facultyModel);
        setWidths(facultyTable, 70, 250, 220, 100);
        facultyCount = countLabel();

        JPanel controls = controlRow();
        controls.add(addButton);
        controls.add(deleteButton);

        JPanel tab = tabPanel();
        tab.add(card("Record actions", controls), BorderLayout.NORTH);
        tab.add(card("Faculty", new JScrollPane(facultyTable)), BorderLayout.CENTER);
        tab.add(facultyCount, BorderLayout.SOUTH);

        addButton.addActionListener(e -> openAddFacultyDialog());
        deleteButton.addActionListener(e -> deleteSelectedFaculty());
        return tab;
    }

    private void openAddFacultyDialog() {
        JTextField idField = new JTextField(String.valueOf(ManageSupport.nextFreeFacultyId(dataStore)), 10);
        JTextField nameField = new JTextField(18);
        JComboBox<String> deptCombo = new JComboBox<>(existingDepartments());
        deptCombo.setEditable(true);
        CourseCheckList expertiseChecks = new CourseCheckList(dataStore.courses());
        JScrollPane checksScroll = new JScrollPane(expertiseChecks);
        checksScroll.setPreferredSize(new Dimension(360, 130));

        JPanel form = formPanel();
        addRow(form, 0, "ID:", idField);
        addRow(form, 1, "Name:", nameField);
        addRow(form, 2, "Department:", deptCombo);
        addWideRow(form, 3, "Expertise (courses):", checksScroll);

        ManageFormDialog dialog = new ManageFormDialog(SwingUtilities.getWindowAncestor(this),
                "Add Faculty", form, () -> {
                    int id;
                    try {
                        id = Integer.parseInt(idField.getText().trim());
                    } catch (NumberFormatException ex) {
                        showInputError("ID must be a whole number.");
                        return false;
                    }
                    String name = nameField.getText().trim();
                    String error = ManageSupport.validateFaculty(id, name);
                    if (error != null) {
                        showInputError(error);
                        return false;
                    }
                    String department = String.valueOf(deptCombo.getEditor().getItem()).trim();
                    try {
                        dataStore.addFaculty(
                                new Faculty(id, name, department, expertiseChecks.selectedCodes()));
                    } catch (IllegalArgumentException ex) {
                        showError(ex);
                        return false;
                    }
                    refreshAll();
                    return true;
                });
        dialog.setVisible(true);
    }

    private void deleteSelectedFaculty() {
        int view = facultyTable.getSelectedRow();
        if (view < 0) {
            showInputError("Select a faculty row first.");
            return;
        }
        int row = facultyTable.convertRowIndexToModel(view);
        int id = (Integer) facultyModel.getValueAt(row, 0);
        String name = (String) facultyModel.getValueAt(row, 1);
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete faculty member " + id + " (" + name + ")?",
                "Delete faculty", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            dataStore.removeFaculty(id);
        } catch (IllegalArgumentException ex) {
            showError(ex);
            return;
        }
        refreshAll();
    }

    // ------------------------------------------------------------------
    // Tab (c): Courses
    // ------------------------------------------------------------------

    private JComponent buildCoursesTab() {
        JButton addButton = GuiTheme.primaryButton("Add Course…");
        JButton deleteButton = GuiTheme.secondaryButton("Delete Selected");

        coursesModel = new ManageTableModel(
                new String[] { "Code", "Name", "Department", "Credits", "Semester", "Enrolled" },
                new Class<?>[] { String.class, String.class, String.class,
                        Integer.class, Integer.class, Integer.class });
        coursesTable = styledTable(coursesModel);
        setWidths(coursesTable, 80, 260, 150, 80, 90, 90);
        coursesCount = countLabel();

        JPanel controls = controlRow();
        controls.add(addButton);
        controls.add(deleteButton);

        JPanel tab = tabPanel();
        tab.add(card("Record actions", controls), BorderLayout.NORTH);
        tab.add(card("Courses", new JScrollPane(coursesTable)), BorderLayout.CENTER);
        tab.add(coursesCount, BorderLayout.SOUTH);

        addButton.addActionListener(e -> openAddCourseDialog());
        deleteButton.addActionListener(e -> deleteSelectedCourse());
        return tab;
    }

    private void openAddCourseDialog() {
        JTextField nameField = new JTextField(18);
        JTextField codeField = new JTextField(ManageSupport.suggestCourseCode(dataStore, ""), 10);
        JComboBox<String> deptCombo = new JComboBox<>(existingDepartments());
        deptCombo.setEditable(true);
        JSpinner creditsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 6, 1));
        JSpinner semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));

        final boolean[] codeTouched = { false };
        final boolean[] autoUpdating = { false };
        codeField.getDocument().addDocumentListener(onChange(() -> {
            if (!autoUpdating[0]) {
                codeTouched[0] = true;
            }
        }));
        nameField.getDocument().addDocumentListener(onChange(() -> {
            if (!codeTouched[0]) {
                autoUpdating[0] = true;
                codeField.setText(ManageSupport.suggestCourseCode(dataStore, nameField.getText()));
                autoUpdating[0] = false;
            }
        }));

        JPanel form = formPanel();
        addRow(form, 0, "Name:", nameField);
        addRow(form, 1, "Code (suggested):", codeField);
        addRow(form, 2, "Department:", deptCombo);
        addRow(form, 3, "Credits:", creditsSpinner);
        addRow(form, 4, "Semester:", semesterSpinner);

        ManageFormDialog dialog = new ManageFormDialog(SwingUtilities.getWindowAncestor(this),
                "Add Course", form, () -> {
                    commitSpinner(creditsSpinner);
                    commitSpinner(semesterSpinner);
                    String code = codeField.getText().trim().toUpperCase(Locale.ROOT);
                    String name = nameField.getText().trim();
                    int credits = ((Number) creditsSpinner.getValue()).intValue();
                    int semester = ((Number) semesterSpinner.getValue()).intValue();
                    String error = ManageSupport.validateCourse(code, name, credits, semester);
                    if (error != null) {
                        showInputError(error);
                        return false;
                    }
                    String department = String.valueOf(deptCombo.getEditor().getItem()).trim();
                    if (department.isEmpty()) {
                        showInputError("Department must not be empty.");
                        return false;
                    }
                    try {
                        dataStore.addCourse(new Course(code, name, department, credits, semester));
                    } catch (IllegalArgumentException ex) {
                        showError(ex);
                        return false;
                    }
                    refreshAll();
                    return true;
                });
        dialog.setVisible(true);
    }

    private void deleteSelectedCourse() {
        int view = coursesTable.getSelectedRow();
        if (view < 0) {
            showInputError("Select a course row first.");
            return;
        }
        int row = coursesTable.convertRowIndexToModel(view);
        String code = (String) coursesModel.getValueAt(row, 0);
        String name = (String) coursesModel.getValueAt(row, 1);

        int enrollments = 0;
        for (Student s : dataStore.students()) {
            if (s.enrolledCourses.contains(code)) {
                enrollments++;
            }
        }
        int exams = 0;
        for (ExamRecord r : dataStore.examRecords()) {
            if (r.courseCode.equals(code)) {
                exams++;
            }
        }
        int assignments = 0;
        for (Assignment a : dataStore.assignments()) {
            if (a.courseCode.equals(code)) {
                assignments++;
            }
        }
        int resources = 0;
        for (LearningResource r : dataStore.resources()) {
            if (r.courseCode.equals(code)) {
                resources++;
            }
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Delete course " + code + " (" + name + ")?\nThis cascades to:\n"
                        + "  • " + enrollments + " enrollments\n"
                        + "  • " + exams + " exam records\n"
                        + "  • " + assignments + " assignments\n"
                        + "  • " + resources + " learning resources",
                "Delete course", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            dataStore.removeCourse(code);
        } catch (IllegalArgumentException ex) {
            showError(ex);
            return;
        }
        refreshAll();
    }

    // ------------------------------------------------------------------
    // Tab (d): Enrollment & Marks
    // ------------------------------------------------------------------

    private JComponent buildEnrollmentTab() {
        studentPicker = new JComboBox<>();
        studentPicker.setName("manage.studentPicker");
        studentPicker.setFont(GuiTheme.BODY);
        studentPicker.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Student s) {
                    setText(s.id + " · " + s.name + "  (" + s.program + ")");
                }
                return this;
            }
        });

        enrolledModel = new DefaultListModel<>();
        enrolledList = new JList<>(enrolledModel);
        enrolledList.setName("manage.enrolledList");
        enrolledList.setFont(GuiTheme.BODY);
        enrolledList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        enrolledList.setCellRenderer(new CourseCodeRenderer());
        JButton dropButton = GuiTheme.secondaryButton("Drop Selected");
        enrollCombo = new JComboBox<>();
        enrollCombo.setName("manage.enrollCombo");
        enrollCombo.setFont(GuiTheme.BODY);
        enrollCombo.setRenderer(new CourseCodeRenderer());
        JButton enrollButton = GuiTheme.primaryButton("Enroll");

        JPanel leftControls = controlRow();
        leftControls.add(fieldLabel("Enroll in:"));
        leftControls.add(enrollCombo);
        leftControls.add(enrollButton);
        leftControls.add(Box.createHorizontalStrut(10));
        leftControls.add(dropButton);
        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setOpaque(false);
        left.add(new JScrollPane(enrolledList), BorderLayout.CENTER);
        left.add(leftControls, BorderLayout.SOUTH);

        marksCourseCombo = new JComboBox<>();
        marksCourseCombo.setName("manage.marksCourse");
        marksCourseCombo.setFont(GuiTheme.BODY);
        marksCourseCombo.setRenderer(new CourseCodeRenderer());
        midsemSpinner = new JSpinner(new SpinnerNumberModel(0, 0, ExamRecord.MIDSEM_MAX, 1));
        midsemSpinner.setName("manage.midsem");
        endsemSpinner = new JSpinner(new SpinnerNumberModel(0, 0, ExamRecord.ENDSEM_MAX, 1));
        endsemSpinner.setName("manage.endsem");
        gradePreview = new JLabel(" ");
        gradePreview.setName("manage.gradePreview");
        gradePreview.setFont(GuiTheme.BODY_BOLD);
        JButton saveMarksButton = GuiTheme.primaryButton("Save Marks");
        saveMarksButton.setName("manage.saveMarks");

        JPanel marksForm = formPanel();
        addRow(marksForm, 0, "Course:", marksCourseCombo);
        addRow(marksForm, 1, "Midsem (0–" + ExamRecord.MIDSEM_MAX + "):", midsemSpinner);
        addRow(marksForm, 2, "Endsem (0–" + ExamRecord.ENDSEM_MAX + "):", endsemSpinner);
        addRow(marksForm, 3, "Preview:", gradePreview);
        JPanel saveRow = controlRow();
        saveRow.add(saveMarksButton);
        JPanel rightTop = new JPanel(new BorderLayout(8, 8));
        rightTop.setOpaque(false);
        rightTop.add(marksForm, BorderLayout.CENTER);
        rightTop.add(saveRow, BorderLayout.SOUTH);
        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setOpaque(false);
        right.add(rightTop, BorderLayout.NORTH);

        JPanel split = new JPanel(new GridLayout(1, 2, 12, 0));
        split.setOpaque(false);
        split.add(card("Enrolled courses", left));
        split.add(card("Marks entry", right));

        JPanel pickerRow = controlRow();
        pickerRow.add(fieldLabel("Student:"));
        pickerRow.add(studentPicker);

        JPanel tab = tabPanel();
        tab.add(card("Select a student", pickerRow), BorderLayout.NORTH);
        tab.add(split, BorderLayout.CENTER);

        studentPicker.addActionListener(e -> refreshEnrollmentDetails());
        marksCourseCombo.addActionListener(e -> prefillMarks());
        midsemSpinner.addChangeListener(e -> updateGradePreview());
        endsemSpinner.addChangeListener(e -> updateGradePreview());
        enrollButton.addActionListener(e -> enrollSelected());
        dropButton.addActionListener(e -> dropSelected());
        saveMarksButton.addActionListener(e -> saveMarks());
        return tab;
    }

    private Student selectedStudent() {
        Object item = studentPicker.getSelectedItem();
        return item instanceof Student s ? s : null;
    }

    private void refreshStudentPicker() {
        Student previous = selectedStudent();
        Integer previousId = previous == null ? null : previous.id;
        enrollmentRefreshing = true;
        try {
            DefaultComboBoxModel<Student> model = new DefaultComboBoxModel<>();
            Student reselect = null;
            for (Student s : dataStore.students()) {
                model.addElement(s);
                if (previousId != null && s.id == previousId) {
                    reselect = s;
                }
            }
            studentPicker.setModel(model);
            if (reselect != null) {
                studentPicker.setSelectedItem(reselect);
            } else if (model.getSize() > 0) {
                studentPicker.setSelectedIndex(0);
            }
        } finally {
            enrollmentRefreshing = false;
        }
        refreshEnrollmentDetails();
    }

    private void refreshEnrollmentDetails() {
        if (enrollmentRefreshing) {
            return;
        }
        enrollmentRefreshing = true;
        try {
            String previousCourse = (String) marksCourseCombo.getSelectedItem();
            Student student = selectedStudent();
            enrolledModel.clear();
            DefaultComboBoxModel<String> enrollModel = new DefaultComboBoxModel<>();
            DefaultComboBoxModel<String> marksModel = new DefaultComboBoxModel<>();
            if (student != null) {
                for (String code : new ArrayList<>(student.enrolledCourses)) {
                    enrolledModel.addElement(code);
                    marksModel.addElement(code);
                }
                for (Course c : dataStore.courses()) {
                    if (!student.enrolledCourses.contains(c.code)) {
                        enrollModel.addElement(c.code);
                    }
                }
            }
            enrollCombo.setModel(enrollModel);
            marksCourseCombo.setModel(marksModel);
            if (previousCourse != null && student != null
                    && student.enrolledCourses.contains(previousCourse)) {
                marksCourseCombo.setSelectedItem(previousCourse);
            }
            prefillMarksInternal();
        } finally {
            enrollmentRefreshing = false;
        }
    }

    private void prefillMarks() {
        if (!enrollmentRefreshing) {
            prefillMarksInternal();
        }
    }

    private void prefillMarksInternal() {
        Student student = selectedStudent();
        String code = (String) marksCourseCombo.getSelectedItem();
        int mid = 0;
        int end = 0;
        if (student != null && code != null) {
            ExamRecord record = findExamRecord(student.id, code);
            if (record != null) {
                mid = record.midsem;
                end = record.endsem;
            }
        }
        midsemSpinner.setValue(mid);
        endsemSpinner.setValue(end);
        updateGradePreview();
    }

    private ExamRecord findExamRecord(int studentId, String courseCode) {
        for (ExamRecord r : dataStore.examRecords()) {
            if (r.studentId == studentId && r.courseCode.equals(courseCode)) {
                return r;
            }
        }
        return null;
    }

    private void updateGradePreview() {
        int mid = ((Number) midsemSpinner.getValue()).intValue();
        int end = ((Number) endsemSpinner.getValue()).intValue();
        int total = mid + end;
        String grade;
        if (total >= 90) grade = "AA";
        else if (total >= 80) grade = "AB";
        else if (total >= 70) grade = "BB";
        else if (total >= 60) grade = "BC";
        else if (total >= 50) grade = "CC";
        else if (total >= 40) grade = "DD";
        else grade = "F";
        boolean passed = total >= ExamRecord.PASS_TOTAL;
        gradePreview.setText("Total " + total + "/100 · Grade " + grade
                + " · " + (passed ? "Pass" : "Fail"));
        gradePreview.setForeground(passed ? GuiTheme.SUCCESS : GuiTheme.ERROR);
    }

    private void enrollSelected() {
        Student student = selectedStudent();
        String code = (String) enrollCombo.getSelectedItem();
        if (student == null || code == null) {
            showInputError("Select a student and a course to enroll in.");
            return;
        }
        try {
            dataStore.enroll(student.id, code);
        } catch (IllegalArgumentException ex) {
            showError(ex);
            return;
        }
        refreshAll();
        marksCourseCombo.setSelectedItem(code);
    }

    private void dropSelected() {
        Student student = selectedStudent();
        String code = enrolledList.getSelectedValue();
        if (student == null) {
            showInputError("Select a student first.");
            return;
        }
        if (code == null) {
            showInputError("Select an enrolled course to drop.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Drop " + code + " for student " + student.id + " (" + student.name + ")?\n"
                        + "Any saved marks for this course will be removed.",
                "Drop course", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            dataStore.drop(student.id, code);
        } catch (IllegalArgumentException ex) {
            showError(ex);
            return;
        }
        refreshAll();
    }

    private void saveMarks() {
        Student student = selectedStudent();
        String code = (String) marksCourseCombo.getSelectedItem();
        if (student == null || code == null) {
            showInputError("Select a student and an enrolled course first.");
            return;
        }
        commitSpinner(midsemSpinner);
        commitSpinner(endsemSpinner);
        int mid = ((Number) midsemSpinner.getValue()).intValue();
        int end = ((Number) endsemSpinner.getValue()).intValue();
        String error = ManageSupport.validateMarks(mid, end);
        if (error != null) {
            showInputError(error);
            return;
        }
        try {
            dataStore.upsertExamRecord(new ExamRecord(student.id, code, mid, end));
        } catch (IllegalArgumentException ex) {
            showError(ex);
            return;
        }
        refreshAll();
    }

    // ------------------------------------------------------------------
    // Bottom bar: dirty indicator + save/reset
    // ------------------------------------------------------------------

    private JComponent buildBottomBar() {
        dirtyLabel = new JLabel(" ");
        dirtyLabel.setFont(GuiTheme.BODY_BOLD);
        JButton saveButton = GuiTheme.primaryButton("Save to CSV files");
        JButton resetButton = GuiTheme.secondaryButton("Reset to generated data");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(resetButton);
        buttons.add(saveButton);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(GuiTheme.CARD_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, GuiTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        bar.add(dirtyLabel, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);

        saveButton.addActionListener(e -> {
            saveButton.setEnabled(false);
            runAsync(() -> {
                dataStore.saveToDisk();
                return null;
            }, ok -> {
                saveButton.setEnabled(true);
                refreshDirtyIndicator();
                JOptionPane.showMessageDialog(this,
                        "Records saved to CSV files in:\n" + CsvStore.resolveDir().toAbsolutePath(),
                        "Save complete", JOptionPane.INFORMATION_MESSAGE);
            }, error -> {
                saveButton.setEnabled(true);
                showError(error);
            }, saveButton);
        });

        resetButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Delete the saved CSV files?\nGenerated data returns on the next application start.\n"
                            + "Unsaved in-memory changes stay active until then.",
                    "Reset to generated data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            resetButton.setEnabled(false);
            runAsync(dataStore::resetToGenerated, deleted -> {
                resetButton.setEnabled(true);
                refreshDirtyIndicator();
                if (deleted) {
                    JOptionPane.showMessageDialog(this,
                            "Saved data deleted — restart the application to regenerate",
                            "Reset complete", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "No saved CSV files found — nothing to delete.",
                            "Reset to generated data", JOptionPane.INFORMATION_MESSAGE);
                }
            }, error -> {
                resetButton.setEnabled(true);
                showError(error);
            }, resetButton);
        });
        return bar;
    }

    private void refreshDirtyIndicator() {
        if (dataStore.isDirty()) {
            dirtyLabel.setText("● Unsaved changes");
            dirtyLabel.setForeground(AMBER);
        } else {
            dirtyLabel.setText("No unsaved changes");
            dirtyLabel.setForeground(GuiTheme.MUTED);
        }
    }

    // ------------------------------------------------------------------
    // Refresh
    // ------------------------------------------------------------------

    private void refreshAll() {
        refreshStudentsTable();
        refreshFacultyTable();
        refreshCoursesTable();
        refreshStudentPicker();
        refreshDirtyIndicator();
    }

    private void refreshStudentsTable() {
        studentsModel.setRowCount(0);
        for (Student s : dataStore.students()) {
            studentsModel.addRow(new Object[] {
                    s.id, s.name, s.program, s.semester, s.cgpa, s.enrolledCourses.size()
            });
        }
        studentsCount.setText(dataStore.students().size() + " students");
    }

    private void refreshFacultyTable() {
        facultyModel.setRowCount(0);
        for (Faculty f : dataStore.faculty()) {
            facultyModel.addRow(new Object[] { f.id, f.name, f.department, f.expertise.size() });
        }
        facultyCount.setText(dataStore.faculty().size() + " faculty");
    }

    private void refreshCoursesTable() {
        Map<String, Integer> enrollments = new HashMap<>();
        for (Student s : dataStore.students()) {
            for (String code : s.enrolledCourses) {
                enrollments.merge(code, 1, Integer::sum);
            }
        }
        coursesModel.setRowCount(0);
        for (Course c : dataStore.courses()) {
            coursesModel.addRow(new Object[] {
                    c.code, c.name, c.department, c.credits, c.semester,
                    enrollments.getOrDefault(c.code, 0)
            });
        }
        coursesCount.setText(dataStore.courses().size() + " courses");
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private String[] existingPrograms() {
        Set<String> programs = new TreeSet<>();
        for (Student s : dataStore.students()) {
            programs.add(s.program);
        }
        return programs.toArray(new String[0]);
    }

    private String[] existingDepartments() {
        Set<String> departments = new TreeSet<>();
        for (Course c : dataStore.courses()) {
            departments.add(c.department);
        }
        for (Faculty f : dataStore.faculty()) {
            departments.add(f.department);
        }
        return departments.toArray(new String[0]);
    }

    private void showInputError(String message) {
        JOptionPane.showMessageDialog(this, message, "Invalid input", JOptionPane.WARNING_MESSAGE);
    }

    private static void commitSpinner(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (java.text.ParseException ignored) {
            // keep the last valid value
        }
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }

    private final class CourseCodeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String code) {
                Course c = dataStore.coursesByCode().get(code);
                setText(c == null ? code : code + " · " + c.name);
            }
            return this;
        }
    }

    private static final class CourseCheckList extends JPanel {
        private final List<JCheckBox> boxes = new ArrayList<>();
        private final List<String> codes = new ArrayList<>();

        CourseCheckList(List<Course> courses) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            for (Course c : courses) {
                JCheckBox box = new JCheckBox(c.code + " · " + c.name);
                box.setOpaque(false);
                box.setFont(GuiTheme.BODY);
                boxes.add(box);
                codes.add(c.code);
                add(box);
            }
        }

        List<String> selectedCodes() {
            List<String> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) {
                    selected.add(codes.get(i));
                }
            }
            selected.sort(null);
            return selected;
        }
    }

    private static final class ManageTableModel extends DefaultTableModel {
        private final Class<?>[] columnClasses;

        ManageTableModel(String[] columns, Class<?>[] columnClasses) {
            super(columns, 0);
            this.columnClasses = columnClasses;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return columnClasses[column];
        }
    }

    private static JPanel tabPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    private static JPanel controlRow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    private static void addRow(JPanel form, int row, String label, JComponent field) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = row;
        gc.anchor = GridBagConstraints.LINE_END;
        gc.insets = new Insets(5, 4, 5, 10);
        form.add(fieldLabel(label), gc);
        gc.gridx = 1;
        gc.anchor = GridBagConstraints.LINE_START;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.insets = new Insets(5, 0, 5, 4);
        form.add(field, gc);
    }

    private static void addWideRow(JPanel form, int row, String label, JComponent field) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 2;
        gc.anchor = GridBagConstraints.LINE_START;
        gc.insets = new Insets(8, 4, 4, 4);
        form.add(fieldLabel(label), gc);
        gc.gridy = row + 1;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1.0;
        gc.weighty = 1.0;
        gc.insets = new Insets(0, 4, 4, 4);
        form.add(field, gc);
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(GuiTheme.BODY);
        label.setForeground(GuiTheme.TEXT);
        return label;
    }

    private static JLabel countLabel() {
        JLabel label = new JLabel(" ");
        label.setFont(GuiTheme.BODY);
        label.setForeground(GuiTheme.MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        return label;
    }

    private static JTable styledTable(ManageTableModel model) {
        JTable table = new JTable(model);
        table.setFont(GuiTheme.BODY);
        table.setForeground(GuiTheme.TEXT);
        table.setGridColor(GuiTheme.CARD_BORDER);
        table.setSelectionBackground(GuiTheme.ACCENT_SOFT);
        table.setSelectionForeground(GuiTheme.TEXT);
        table.setRowSorter(new TableRowSorter<>(model));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        return table;
    }

    private static void setWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }
}
