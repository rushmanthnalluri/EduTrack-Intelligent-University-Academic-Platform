package edutrack.gui;

import java.awt.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;

public final class CsvExporter {

    private CsvExporter() {
    }

    public static boolean exportTable(Component parent, JTable table, String suggestedFileName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(suggestedFileName));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        Path path = chooser.getSelectedFile().toPath();
        if (!path.toString().toLowerCase().endsWith(".csv")) {
            path = path.resolveSibling(path.getFileName() + ".csv");
        }
        try {
            write(path, rowsOf(table));
            JOptionPane.showMessageDialog(parent, "Exported " + path.getFileName(),
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(),
                    "Export failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public static void write(Path path, List<String[]> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(row[i]));
            }
            sb.append(System.lineSeparator());
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static List<String[]> rowsOf(JTable table) {
        List<String[]> rows = new ArrayList<>();
        int cols = table.getColumnCount();
        String[] header = new String[cols];
        for (int c = 0; c < cols; c++) {
            header[c] = table.getColumnName(c);
        }
        rows.add(header);
        for (int r = 0; r < table.getRowCount(); r++) {
            String[] row = new String[cols];
            for (int c = 0; c < cols; c++) {
                Object value = table.getValueAt(r, c);
                row[c] = value == null ? "" : value.toString();
            }
            rows.add(row);
        }
        return rows;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
