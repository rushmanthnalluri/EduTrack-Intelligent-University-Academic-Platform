package edutrack.gui;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class ConsoleArea extends JPanel {

    private final JTextArea area;

    public ConsoleArea(int visibleRows) {
        super(new BorderLayout());
        area = new JTextArea(visibleRows, 40);
        area.setEditable(false);
        area.setFont(GuiTheme.MONO);
        area.setBackground(GuiTheme.CONSOLE_BG);
        area.setForeground(GuiTheme.CONSOLE_FG);
        area.setCaretColor(GuiTheme.CONSOLE_FG);
        area.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    public void append(String s) {
        runOnEdt(() -> {
            area.append(s);
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    public void appendLine(String s) {
        append(s + "\n");
    }

    public void setText(String s) {
        runOnEdt(() -> {
            area.setText(s);
            area.setCaretPosition(0);
        });
    }

    public void clear() {
        setText("");
    }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
