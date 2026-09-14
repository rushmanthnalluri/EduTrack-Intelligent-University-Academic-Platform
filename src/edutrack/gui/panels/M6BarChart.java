package edutrack.gui.panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import edutrack.gui.GuiTheme;

public class M6BarChart extends JComponent {

    public static class Bar {
        public final String label;
        public final double value;
        public final Color color;

        public Bar(String label, double value, Color color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    private List<Bar> bars = List.of();
    private String valueFormat = "%.1f";
    private Double targetValue;
    private String targetLabel;

    public M6BarChart() {
        setPreferredSize(new Dimension(240, 240));
        setBackground(GuiTheme.CARD_BG);
        setOpaque(true);
    }

    public void setData(List<Bar> bars, String valueFormat) {
        this.bars = new ArrayList<>(bars);
        this.valueFormat = valueFormat;
        repaint();
    }

    public void setTarget(Double value, String label) {
        this.targetValue = value;
        this.targetLabel = label;
        repaint();
    }

    public void clear() {
        bars = List.of();
        targetValue = null;
        targetLabel = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (bars.isEmpty()) {
                g2.setFont(GuiTheme.BODY);
                g2.setColor(GuiTheme.MUTED);
                g2.drawString("Run to see results", 14, 24);
                return;
            }
            boolean labeled = bars.size() <= 12;
            double max = 0;
            for (Bar bar : bars) {
                max = Math.max(max, bar.value);
            }
            if (targetValue != null) {
                max = Math.max(max, targetValue);
            }
            if (max <= 0) {
                max = 1;
            }
            max *= 1.15;
            int left = 14;
            int right = w - 14;
            int top = labeled ? 22 : 14;
            int bottom = h - (labeled ? 30 : 14);
            if (right <= left || bottom <= top) {
                return;
            }
            int n = bars.size();
            double slot = (right - left) / (double) n;
            int barWidth = Math.max(1, (int) (slot * (labeled ? 0.55 : 0.8)));

            for (int i = 0; i < n; i++) {
                Bar bar = bars.get(i);
                int bh = (int) Math.round(bar.value / max * (bottom - top));
                int x = left + (int) Math.round(i * slot + (slot - barWidth) / 2);
                int y = bottom - bh;
                g2.setColor(bar.color);
                g2.fillRect(x, y, barWidth, Math.max(1, bh));
                if (labeled) {
                    g2.setFont(GuiTheme.BODY.deriveFont(11f));
                    FontMetrics fm = g2.getFontMetrics();
                    String value = String.format(valueFormat, bar.value);
                    g2.setColor(GuiTheme.TEXT);
                    g2.drawString(value, x + barWidth / 2 - fm.stringWidth(value) / 2, Math.max(y - 4, 12));
                    g2.setColor(GuiTheme.MUTED);
                    g2.drawString(bar.label, x + barWidth / 2 - fm.stringWidth(bar.label) / 2, bottom + 16);
                }
            }
            g2.setColor(GuiTheme.CARD_BORDER);
            g2.drawLine(left, bottom, right, bottom);

            if (targetValue != null) {
                int ty = bottom - (int) Math.round(targetValue / max * (bottom - top));
                g2.setColor(GuiTheme.ERROR);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[] { 5f, 4f }, 0f));
                g2.drawLine(left, ty, right, ty);
                if (targetLabel != null) {
                    g2.setFont(GuiTheme.BODY.deriveFont(11f));
                    g2.drawString(targetLabel, left + 4, ty - 5);
                }
            }
        } finally {
            g2.dispose();
        }
    }
}
