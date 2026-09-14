package edutrack.gui.panels;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import edutrack.gui.GuiTheme;

/** Simple horizontal bar chart (Java2D) for per-keyword hit counts. */
public class M1KeywordBarChart extends JComponent {

    private static final int PADDING = 10;
    private static final int VALUE_WIDTH = 56;
    private static final int MAX_LABEL_WIDTH = 120;
    private static final int MAX_ROW_HEIGHT = 26;

    private List<String> labels = new ArrayList<>();
    private long[] values = new long[0];

    public M1KeywordBarChart() {
        setPreferredSize(new Dimension(340, 300));
        setOpaque(true);
        setBackground(GuiTheme.CARD_BG);
    }

    public void setData(List<String> newLabels, long[] newValues) {
        labels = new ArrayList<>(newLabels);
        values = newValues.clone();
        repaint();
    }

    public void clear() {
        labels = new ArrayList<>();
        values = new long[0];
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // JComponent subclasses have no UI delegate, so the background is never
        // filled automatically — fill it explicitly or stale pixels bleed through.
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        int width = getWidth();
        int height = getHeight();
        g2.setColor(GuiTheme.CARD_BG);
        g2.fillRect(0, 0, width, height);

        if (labels.isEmpty()) {
            g2.setColor(GuiTheme.MUTED);
            g2.setFont(GuiTheme.BODY);
            String message = "Run a scan to see keyword hit counts";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(message, (width - fm.stringWidth(message)) / 2,
                    height / 2 + fm.getAscent() / 2);
            g2.dispose();
            return;
        }

        g2.setFont(GuiTheme.BODY);
        FontMetrics fm = g2.getFontMetrics();
        int labelWidth = 0;
        for (String label : labels) {
            labelWidth = Math.max(labelWidth, fm.stringWidth(label));
        }
        labelWidth = Math.min(labelWidth, MAX_LABEL_WIDTH);

        long max = 0;
        for (long value : values) {
            max = Math.max(max, value);
        }

        int barX = PADDING + labelWidth + 8;
        int barAreaWidth = Math.max(10, width - barX - VALUE_WIDTH - PADDING);
        int rowHeight = Math.max(12, Math.min(MAX_ROW_HEIGHT, (height - 2 * PADDING) / labels.size()));
        int barHeight = Math.max(6, rowHeight - 8);

        for (int i = 0; i < labels.size(); i++) {
            int rowTop = PADDING + i * rowHeight;
            int barY = rowTop + (rowHeight - barHeight) / 2;
            int baseline = rowTop + (rowHeight + fm.getAscent() - fm.getDescent()) / 2;

            g2.setColor(GuiTheme.TEXT);
            String label = labels.get(i);
            int textWidth = fm.stringWidth(label);
            while (textWidth > labelWidth && label.length() > 1) {
                label = label.substring(0, label.length() - 1);
                textWidth = fm.stringWidth(label + "…");
            }
            if (!label.equals(labels.get(i))) {
                label = label + "…";
                textWidth = fm.stringWidth(label);
            }
            g2.drawString(label, PADDING + labelWidth - textWidth, baseline);

            int barWidth = max == 0 ? 0 : (int) Math.round(values[i] * (double) barAreaWidth / max);
            if (values[i] > 0 && barWidth < 3) {
                barWidth = 3;
            }
            g2.setColor(values[i] == max && max > 0 ? GuiTheme.ACCENT_DARK : GuiTheme.ACCENT);
            if (barWidth > 0) {
                g2.fillRoundRect(barX, barY, barWidth, barHeight, 6, 6);
            }

            g2.setColor(GuiTheme.MUTED);
            g2.drawString(String.valueOf(values[i]), barX + barWidth + 6, baseline);
        }
        g2.dispose();
    }
}
