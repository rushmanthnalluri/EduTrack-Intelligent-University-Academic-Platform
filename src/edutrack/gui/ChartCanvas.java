package edutrack.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

public class ChartCanvas extends JPanel {

    private String title = "";
    private String[] labels = new String[0];
    private double[] values = new double[0];
    private Double targetLine;
    private String targetLabel;
    private boolean horizontal;
    private int labelStep;
    private String placeholder = "Run to see results";

    public ChartCanvas() {
        setBackground(GuiTheme.CARD_BG);
        setPreferredSize(new Dimension(380, 230));
    }

    public void setData(String title, String[] labels, double[] values) {
        setData(title, labels, values, null, null);
    }

    public void setData(String title, String[] labels, double[] values, Double targetLine, String targetLabel) {
        this.title = title == null ? "" : title;
        this.labels = labels == null ? new String[0] : labels.clone();
        this.values = values == null ? new double[0] : values.clone();
        this.targetLine = targetLine;
        this.targetLabel = targetLabel;
        repaint();
    }

    public void setHorizontal(boolean horizontal) {
        this.horizontal = horizontal;
        repaint();
    }

    /** Sets vertical label density; values <= 0 use automatic density. */
    public void setLabelStep(int labelStep) {
        this.labelStep = Math.max(0, labelStep);
        repaint();
    }

    public void clear(String placeholder) {
        this.labels = new String[0];
        this.values = new double[0];
        this.placeholder = placeholder == null ? "" : placeholder;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            int w = getWidth();
            int h = getHeight();

            g2.setFont(GuiTheme.H2);
            g2.setColor(GuiTheme.TEXT);
            if (!title.isEmpty()) {
                g2.drawString(title, 14, 22);
            }

            if (values.length == 0) {
                g2.setFont(GuiTheme.BODY);
                g2.setColor(GuiTheme.MUTED);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, Math.max(14, (w - fm.stringWidth(placeholder)) / 2), h / 2);
                return;
            }

            double max = 0.0;
            for (double v : values) {
                max = Math.max(max, v);
            }
            if (max <= 0.0) {
                max = 1.0;
            }
            if (targetLine != null) {
                max = Math.max(max, targetLine);
            }

            int top = title.isEmpty() ? 12 : 36;
            int bottom = h - (horizontal ? 12 : 34);
            if (horizontal) {
                paintHorizontal(g2, w, top, bottom, max);
            } else {
                paintVertical(g2, w, top, bottom, max);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintVertical(Graphics2D g2, int w, int top, int bottom, double max) {
        int n = Math.min(values.length, labels.length);
        if (n == 0) {
            return;
        }
        int left = 42;
        int right = w - 14;
        int plotW = right - left;
        int plotH = bottom - top;
        if (plotW <= 0 || plotH <= 0) {
            return;
        }
        int slot = Math.max(1, plotW / n);
        int barW = Math.max(4, Math.min(56, slot - 8));
        int effectiveStep = labelStep > 0 ? labelStep : Math.max(1, (n + 9) / 10);

        g2.setFont(GuiTheme.BODY);
        FontMetrics fm = g2.getFontMetrics();
        g2.setStroke(new BasicStroke(1f));

        for (int tick = 0; tick <= 4; tick++) {
            double ratio = tick / 4.0;
            int y = bottom - (int) Math.round(ratio * (plotH - 16));
            g2.setColor(GuiTheme.CARD_BORDER);
            g2.drawLine(left, y, right, y);
            if (tick > 0) {
                g2.setColor(GuiTheme.MUTED);
                String scale = format(max * ratio);
                g2.drawString(scale, 4, y + fm.getAscent() / 2 - 1);
            }
        }

        for (int i = 0; i < n; i++) {
            int barH = (int) Math.round(values[i] / max * (plotH - 16));
            int x = left + i * slot + (slot - barW) / 2;
            int y = bottom - barH;

            g2.setColor(i == indexOfMax() ? GuiTheme.ACCENT_DARK : GuiTheme.ACCENT);
            g2.fill(new Rectangle2D.Double(x, y, barW, barH));

            g2.setColor(GuiTheme.TEXT);
            g2.setFont(slot < 48 ? new Font("Segoe UI", Font.PLAIN, 10) : GuiTheme.BODY);
            FontMetrics valueMetrics = g2.getFontMetrics();
            String value = fitValue(values[i], valueMetrics, Math.max(slot - 2, 20));
            int valueY = Math.max(top + valueMetrics.getAscent() + 2, y - 4);
            g2.drawString(value, x + (barW - valueMetrics.stringWidth(value)) / 2, valueY);

            if (i % effectiveStep == 0 || i == n - 1) {
                g2.setColor(GuiTheme.MUTED);
                g2.setFont(fm.getFont());
                FontMetrics labelMetrics = g2.getFontMetrics();
                String label = truncate(labelMetrics, labels[i], Math.max(slot - 2, 28));
                g2.drawString(label, x + (barW - labelMetrics.stringWidth(label)) / 2, bottom + 16);
            }
        }
        g2.setFont(GuiTheme.BODY);
        drawTarget(g2, left, right, bottom, plotH, max, false);
    }

    private static String fitValue(double v, FontMetrics fm, int maxWidth) {
        String plain = format(v);
        if (fm.stringWidth(plain) <= maxWidth) {
            return plain;
        }
        if (Math.abs(v) >= 1000) {
            String kOne = String.format("%.1fK", v / 1000.0);
            if (fm.stringWidth(kOne) <= maxWidth) {
                return kOne;
            }
            String kRound = String.format("%,dK", Math.round(v / 1000.0));
            if (fm.stringWidth(kRound) <= maxWidth) {
                return kRound;
            }
        }
        return plain;
    }

    private void paintHorizontal(Graphics2D g2, int w, int top, int bottom, double max) {
        int n = Math.min(values.length, labels.length);
        if (n == 0) {
            return;
        }
        g2.setFont(GuiTheme.BODY);
        FontMetrics fm = g2.getFontMetrics();

        int labelW = 60;
        for (int i = 0; i < n; i++) {
            labelW = Math.max(labelW, Math.min(180, fm.stringWidth(labels[i]) + 8));
        }
        int left = 14 + labelW;
        int right = w - 14;
        int plotW = right - left;
        int slot = Math.max(1, (bottom - top) / n);
        int barH = Math.max(4, Math.min(26, slot - 6));

        g2.setStroke(new BasicStroke(1f));
        for (int tick = 1; tick <= 4; tick++) {
            int x = left + (int) Math.round((tick / 4.0) * Math.max(1, plotW - 44));
            g2.setColor(GuiTheme.CARD_BORDER);
            g2.drawLine(x, top, x, bottom);
        }

        for (int i = 0; i < n; i++) {
            int barW = (int) Math.round(values[i] / max * Math.max(1, plotW - 44));
            int y = top + i * slot + (slot - barH) / 2;

            g2.setColor(GuiTheme.MUTED);
            String label = truncate(fm, labels[i], labelW);
            g2.drawString(label, 14 + labelW - fm.stringWidth(label), y + barH / 2 + 4);

            g2.setColor(i == indexOfMax() ? GuiTheme.ACCENT_DARK : GuiTheme.ACCENT);
            g2.fill(new Rectangle2D.Double(left, y, Math.max(1, barW), barH));

            g2.setColor(GuiTheme.TEXT);
            String value = fitValue(values[i], fm, Math.max(42, plotW / 5));
            g2.drawString(value, left + barW + 6, y + barH / 2 + 4);
        }
        drawTarget(g2, top, bottom, left, plotW, max, true);
    }

    private void drawTarget(Graphics2D g2, int a, int b, int base, int span, double max, boolean horizontalMode) {
        if (targetLine == null) {
            return;
        }
        g2.setColor(new Color(0xD9, 0x2D, 0x20));
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] { 5f, 4f }, 0f));
        if (!horizontalMode) {
            int y = base - (int) Math.round(targetLine / max * (span - 16));
            g2.draw(new Line2D.Double(a, y, b, y));
            if (targetLabel != null) {
                g2.setFont(GuiTheme.BODY);
                g2.drawString(targetLabel, a + 4, y - 4);
            }
        } else {
            int x = base + (int) Math.round(targetLine / max * (span - 44));
            g2.draw(new Line2D.Double(x, a, x, b));
            if (targetLabel != null) {
                g2.setFont(GuiTheme.BODY);
                g2.drawString(targetLabel, x + 4, a + 12);
            }
        }
    }

    private int indexOfMax() {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static String format(double v) {
        if (v == Math.floor(v) && v < 1_000_000) {
            return String.format("%,d", (long) v);
        }
        return String.format("%.2f", v);
    }

    private static String truncate(FontMetrics fm, String s, int maxWidth) {
        if (fm.stringWidth(s) <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && fm.stringWidth(sb.toString() + ellipsis) > maxWidth) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString() + ellipsis;
    }
}
