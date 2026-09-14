package edutrack.gui.panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import edutrack.gui.GuiTheme;

public class M5TimetableCanvas extends JComponent {

    private static final String[] DAYS = { "MON", "TUE", "WED", "THU", "FRI" };
    private static final String[] TIMES = { "09:00", "11:00", "14:00" };
    private static final int SLOTS_PER_DAY = 3;
    private static final int SLOT_CELL_COUNT = DAYS.length * SLOTS_PER_DAY;

    private final List<String>[][] cells;
    private String placeholder = "Solve to populate the timetable.";
    private boolean hasSchedule;

    @SuppressWarnings("unchecked")
    public M5TimetableCanvas() {
        cells = new ArrayList[DAYS.length][SLOTS_PER_DAY];
        for (int d = 0; d < DAYS.length; d++) {
            for (int t = 0; t < SLOTS_PER_DAY; t++) {
                cells[d][t] = new ArrayList<>();
            }
        }
        setPreferredSize(new Dimension(280, 168));
        setOpaque(true);
        setBackground(GuiTheme.CARD_BG);
    }

    public void setSchedule(Map<Integer, List<String>> slotToCourses) {
        for (int d = 0; d < DAYS.length; d++) {
            for (int t = 0; t < SLOTS_PER_DAY; t++) {
                cells[d][t].clear();
            }
        }
        for (Map.Entry<Integer, List<String>> entry : slotToCourses.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < SLOT_CELL_COUNT) {
                cells[slot / SLOTS_PER_DAY][slot % SLOTS_PER_DAY].addAll(entry.getValue());
            }
        }
        hasSchedule = true;
        repaint();
    }

    public void showPlaceholder(String message) {
        placeholder = message;
        hasSchedule = false;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int pad = 4;
        int labelW = 42;
        int headerH = 18;
        double cellW = (getWidth() - 2.0 * pad - labelW) / DAYS.length;
        double cellH = (getHeight() - 2.0 * pad - headerH) / SLOTS_PER_DAY;

        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics headerFm = g2.getFontMetrics();
        g2.setColor(GuiTheme.MUTED);
        for (int d = 0; d < DAYS.length; d++) {
            double x = pad + labelW + d * cellW;
            g2.drawString(DAYS[d], (float) (x + (cellW - headerFm.stringWidth(DAYS[d])) / 2.0),
                    pad + 12.0f);
        }
        for (int t = 0; t < SLOTS_PER_DAY; t++) {
            double y = pad + headerH + t * cellH;
            g2.drawString(TIMES[t], pad + 2.0f, (float) (y + cellH / 2.0 + 3));
        }

        for (int d = 0; d < DAYS.length; d++) {
            for (int t = 0; t < SLOTS_PER_DAY; t++) {
                double x = pad + labelW + d * cellW;
                double y = pad + headerH + t * cellH;
                List<String> courses = cells[d][t];
                boolean empty = courses.isEmpty();
                g2.setColor(empty ? new Color(0xF7F8FA) : Color.WHITE);
                g2.fill(new RoundRectangle2D.Double(x + 1, y + 1, cellW - 2, cellH - 2, 8, 8));
                g2.setColor(GuiTheme.CARD_BORDER);
                g2.setStroke(new BasicStroke(1.0f));
                g2.draw(new RoundRectangle2D.Double(x + 1, y + 1, cellW - 2, cellH - 2, 8, 8));
                if (hasSchedule) {
                    if (empty) {
                        g2.setColor(new Color(0xC3CAD8));
                        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString("—", (float) (x + (cellW - fm.stringWidth("—")) / 2.0),
                                (float) (y + cellH / 2.0 + 3));
                    } else {
                        paintChips(g2, courses, x, y, cellW, cellH);
                    }
                }
            }
        }

        if (!hasSchedule) {
            g2.setColor(GuiTheme.MUTED);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder,
                    (float) (pad + labelW + (getWidth() - 2.0 * pad - labelW
                            - fm.stringWidth(placeholder)) / 2.0),
                    (float) (pad + headerH + (getHeight() - 2.0 * pad - headerH) / 2.0 + 4));
        }
        g2.dispose();
    }

    private void paintChips(Graphics2D g2, List<String> courses, double x, double y,
            double cellW, double cellH) {
        int chipH = 16;
        int gap = 3;
        int maxChips = Math.max(1, (int) ((cellH - 6 + gap) / (chipH + gap)));
        int shown = Math.min(courses.size(), maxChips);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < shown; i++) {
            String code = courses.get(i);
            double chipY = y + 3 + i * (chipH + gap);
            Color fill = M5GraphCanvas.colorForCode(code);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Double(x + 4, chipY, cellW - 8, chipH, 7, 7));
            g2.setColor(Color.WHITE);
            g2.drawString(code, (float) (x + (cellW - fm.stringWidth(code)) / 2.0),
                    (float) (chipY + 12));
        }
        if (courses.size() > shown) {
            String more = "+" + (courses.size() - shown) + " more";
            g2.setColor(GuiTheme.MUTED);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            FontMetrics fm2 = g2.getFontMetrics();
            g2.drawString(more, (float) (x + (cellW - fm2.stringWidth(more)) / 2.0),
                    (float) (y + cellH - 4));
        }
    }
}
