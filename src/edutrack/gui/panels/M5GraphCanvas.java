package edutrack.gui.panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;

import edutrack.gui.GuiTheme;
import edutrack.modules.M5Graph;

public class M5GraphCanvas extends JComponent {

    private static final int NODE_R = 19;

    private static final Color[] SLOT_COLORS = {
        new Color(0x2563EB), new Color(0x0E9F6E), new Color(0xD97706), new Color(0x7C3AED),
        new Color(0xDB2777), new Color(0x0D9488), new Color(0x65A30D), new Color(0xDC2626),
        new Color(0x0891B2), new Color(0x9333EA), new Color(0xCA8A04), new Color(0x4B5563)
    };

    private static final Map<String, Color> DEPT_COLORS = new HashMap<>();
    static {
        DEPT_COLORS.put("CS", new Color(0x2563EB));
        DEPT_COLORS.put("MA", new Color(0x0E9F6E));
        DEPT_COLORS.put("PH", new Color(0xD97706));
        DEPT_COLORS.put("CH", new Color(0x7C3AED));
        DEPT_COLORS.put("EE", new Color(0x0D9488));
        DEPT_COLORS.put("HS", new Color(0xDB2777));
        DEPT_COLORS.put("MG", new Color(0x6B7280));
    }

    private M5Graph graph;
    private int[] layoutOrder = new int[0];
    private final double[] xs;
    private final double[] ys;
    private boolean[] cover;
    private int[] slotOf;
    private int slotCount;

    public M5GraphCanvas() {
        xs = new double[64];
        ys = new double[64];
        setPreferredSize(new Dimension(560, 560));
        setOpaque(true);
        setBackground(GuiTheme.CARD_BG);
        setToolTipText("conflict graph");
    }

    public void setGraph(M5Graph g) {
        this.graph = g;
        this.cover = null;
        this.slotOf = null;
        this.slotCount = 0;
        if (g != null) {
            int[] byDegree = g.degreeOrder();
            Integer[] withDept = new Integer[g.n];
            for (int i = 0; i < g.n; i++) {
                withDept[i] = byDegree[i];
            }
            java.util.Arrays.sort(withDept, (a, b) -> {
                int byDept = deptOf(g.labels[a]).compareTo(deptOf(g.labels[b]));
                return byDept != 0 ? byDept : Integer.compare(g.degree(b), g.degree(a));
            });
            layoutOrder = new int[g.n];
            for (int i = 0; i < g.n; i++) {
                layoutOrder[i] = withDept[i];
            }
        } else {
            layoutOrder = new int[0];
        }
        repaint();
    }

    public void setCover(boolean[] cover) {
        this.cover = cover;
        repaint();
    }

    public void setSchedule(int[] slotOf, int slotCount) {
        this.slotOf = slotOf;
        this.slotCount = slotCount;
        repaint();
    }

    public void clearDecorations() {
        this.cover = null;
        this.slotOf = null;
        this.slotCount = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (graph == null || graph.n == 0) {
            g2.setColor(GuiTheme.MUTED);
            g2.setFont(GuiTheme.BODY);
            g2.drawString("No conflict graph loaded.", 20, 30);
            g2.dispose();
            return;
        }
        layoutNodes();

        g2.setStroke(new BasicStroke(1.1f));
        g2.setColor(new Color(0xC3CAD8));
        for (int u = 0; u < graph.n; u++) {
            for (int v = u + 1; v < graph.n; v++) {
                if (graph.adj[u][v]) {
                    g2.draw(new Line2D.Double(xs[u], ys[u], xs[v], ys[v]));
                }
            }
        }

        for (int pos = 0; pos < graph.n; pos++) {
            int v = layoutOrder[pos];
            double x = xs[v];
            double y = ys[v];
            if (cover != null && cover[v]) {
                g2.setColor(GuiTheme.HIGHLIGHT);
                g2.fill(new Ellipse2D.Double(x - NODE_R - 6, y - NODE_R - 6,
                        2.0 * (NODE_R + 6), 2.0 * (NODE_R + 6)));
                g2.setColor(new Color(0xB45309));
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(new Ellipse2D.Double(x - NODE_R - 6, y - NODE_R - 6,
                        2.0 * (NODE_R + 6), 2.0 * (NODE_R + 6)));
            }
            Color fill = nodeColor(v);
            g2.setColor(fill);
            g2.fill(new Ellipse2D.Double(x - NODE_R, y - NODE_R, 2.0 * NODE_R, 2.0 * NODE_R));
            g2.setColor(fill.darker());
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(new Ellipse2D.Double(x - NODE_R, y - NODE_R, 2.0 * NODE_R, 2.0 * NODE_R));

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();
            String code = graph.labels[v];
            g2.drawString(code, (float) (x - fm.stringWidth(code) / 2.0), (float) (y + 3.5));

            if (slotOf != null && slotOf[v] >= 0) {
                String tag = "S" + (slotOf[v] + 1);
                g2.setColor(GuiTheme.MUTED);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(tag, (float) (x - fm2.stringWidth(tag) / 2.0),
                        (float) (y + NODE_R + 13));
            }
        }
        g2.dispose();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (graph == null) {
            return null;
        }
        layoutNodes();
        for (int v = 0; v < graph.n; v++) {
            double dx = e.getX() - xs[v];
            double dy = e.getY() - ys[v];
            if (dx * dx + dy * dy <= NODE_R * NODE_R) {
                StringBuilder tip = new StringBuilder(graph.labels[v])
                        .append(" — degree ").append(graph.degree(v));
                if (slotOf != null && slotOf[v] >= 0) {
                    tip.append(" · exam slot ").append(slotOf[v] + 1);
                }
                if (cover != null && cover[v]) {
                    tip.append(" · in vertex cover");
                }
                return tip.toString();
            }
        }
        return null;
    }

    private void layoutNodes() {
        int w = getWidth();
        int h = getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double radius = Math.max(60, Math.min(w, h) / 2.0 - NODE_R - 26);
        for (int pos = 0; pos < graph.n; pos++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * pos / graph.n;
            xs[layoutOrder[pos]] = cx + radius * Math.cos(angle);
            ys[layoutOrder[pos]] = cy + radius * Math.sin(angle);
        }
    }

    private Color nodeColor(int v) {
        if (slotOf != null && slotOf[v] >= 0 && slotCount > 0) {
            return SLOT_COLORS[slotOf[v] % SLOT_COLORS.length];
        }
        return colorForCode(graph.labels[v]);
    }

    static Color colorForCode(String code) {
        Color c = DEPT_COLORS.get(deptOf(code));
        return c == null ? GuiTheme.ACCENT : c;
    }

    private static String deptOf(String code) {
        int i = 0;
        while (i < code.length() && !Character.isDigit(code.charAt(i))) {
            i++;
        }
        return code.substring(0, i);
    }
}
