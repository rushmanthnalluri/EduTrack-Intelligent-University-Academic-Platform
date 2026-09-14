package edutrack.gui.panels;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

import javax.swing.JPanel;

import edutrack.gui.GuiTheme;

/**
 * Small Java2D bar chart comparing Dinic and Edmonds-Karp running times on the
 * scaled network (bar height = elapsed ms; caption shows agreement and speedup).
 */
class M4BenchmarkCanvas extends JPanel {

    private static final Color EK_BAR = new Color(0xF59E0B);

    private boolean ran;
    private double dinicMs;
    private double ekMs;
    private long maxFlow;
    private int dinicPhases;
    private int ekPhases;
    private int nodes;
    private int edges;

    M4BenchmarkCanvas() {
        setBackground(GuiTheme.CARD_BG);
        setOpaque(true);
        setPreferredSize(new Dimension(200, 170));
    }

    void setResults(double dinicMs, int dinicPhases, double ekMs, int ekPhases,
            long maxFlow, int nodes, int edges) {
        this.ran = true;
        this.dinicMs = dinicMs;
        this.ekMs = ekMs;
        this.maxFlow = maxFlow;
        this.dinicPhases = dinicPhases;
        this.ekPhases = ekPhases;
        this.nodes = nodes;
        this.edges = edges;
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
            g2.setFont(GuiTheme.BODY);
            FontMetrics fm = g2.getFontMetrics();
            if (!ran) {
                g2.setColor(GuiTheme.MUTED);
                String hint = "Run the scaled benchmark to compare Dinic vs Edmonds-Karp";
                g2.drawString(hint, Math.max(8, (w - fm.stringWidth(hint)) / 2), h / 2);
                return;
            }

            double speedup = dinicMs > 0 ? ekMs / dinicMs : 0;
            String caption = String.format(Locale.US,
                    "Identical max flow %,d on %,d nodes / %,d edges — Dinic ≈ %.1fx faster",
                    maxFlow, nodes, edges, speedup);
            g2.setFont(GuiTheme.BODY_BOLD);
            g2.setColor(GuiTheme.TEXT);
            g2.drawString(caption, Math.max(8, (w - g2.getFontMetrics().stringWidth(caption)) / 2), 18);

            int baseline = h - 34;
            int top = 34;
            double maxMs = Math.max(dinicMs, ekMs);
            int barW = Math.min(90, w / 5);
            drawBar(g2, w / 4, barW, baseline, top, dinicMs, maxMs, GuiTheme.ACCENT,
                    "Dinic", dinicPhases + " level-graph phases", fm);
            drawBar(g2, (3 * w) / 4, barW, baseline, top, ekMs, maxMs, EK_BAR,
                    "Edmonds-Karp", ekPhases + " BFS phases", fm);
        } finally {
            g2.dispose();
        }
    }

    private static void drawBar(Graphics2D g2, int cx, int barW, int baseline, int top,
            double ms, double maxMs, Color color, String name, String sub, FontMetrics fm) {
        int bh = maxMs > 0 ? (int) Math.max(2, (baseline - top) * ms / maxMs) : 2;
        g2.setColor(color);
        g2.fillRoundRect(cx - barW / 2, baseline - bh, barW, bh, 6, 6);
        g2.setColor(GuiTheme.TEXT);
        String val = String.format(Locale.US, "%.1f ms", ms);
        g2.drawString(val, cx - fm.stringWidth(val) / 2, baseline - bh - 6);
        g2.drawString(name, cx - fm.stringWidth(name) / 2, baseline + 16);
        g2.setColor(GuiTheme.MUTED);
        g2.drawString(sub, cx - fm.stringWidth(sub) / 2, baseline + 30);
    }
}
