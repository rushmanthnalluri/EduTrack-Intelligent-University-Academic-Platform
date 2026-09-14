package edutrack.gui.panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.JPanel;

import edutrack.gui.GuiTheme;

/**
 * Java2D canvas for the faculty-course bipartite eligibility graph: faculty in a
 * left column (~22-25% of width), courses in a right column (~75-78%), thin gray
 * edges for eligibility, thick accent edges for the Hopcroft-Karp matching, amber
 * nodes for the Konig cover. Faculty names are right-aligned ending just left of
 * their nodes; course codes are left-aligned starting just right of their nodes.
 */
class M4BipartiteCanvas extends JPanel {

    private static final Color EDGE = new Color(0xCBD3E0);
    private static final Color NODE_OUTLINE = new Color(0x9AA5B5);
    private static final Color COVER_OUTLINE = new Color(0xB45309);
    private static final int NODE_R = 7;
    private static final int TOP_PAD = 22;
    private static final int BOTTOM_PAD = 24;

    private String[] leftLabels = new String[0];
    private String[] rightLabels = new String[0];
    private List<List<Integer>> adj = List.of();
    private int[] matchLeft;        // null until the matching has been computed
    private int[] matchRight;
    private boolean[] coverLeft;    // null when the cover is hidden
    private boolean[] coverRight;

    M4BipartiteCanvas() {
        setBackground(GuiTheme.CARD_BG);
        setOpaque(true);
    }

    void setGraph(String[] leftLabels, String[] rightLabels, List<List<Integer>> adj) {
        this.leftLabels = leftLabels;
        this.rightLabels = rightLabels;
        this.adj = adj;
        this.matchLeft = null;
        this.matchRight = null;
        this.coverLeft = null;
        this.coverRight = null;
        repaint();
    }

    void setMatching(int[] matchLeft, int[] matchRight) {
        this.matchLeft = matchLeft;
        this.matchRight = matchRight;
        repaint();
    }

    void setCover(boolean[] coverLeft, boolean[] coverRight) {
        this.coverLeft = coverLeft;
        this.coverRight = coverRight;
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
            int nL = leftLabels.length;
            int nR = rightLabels.length;
            if (nL == 0 || nR == 0 || w < 60 || h < 60) {
                return;
            }

            Font labelFont = GuiTheme.BODY.deriveFont(12f);
            FontMetrics fm = g2.getFontMetrics(labelFont);

            // Columns sit at ~24% / ~76% of the width, but the margins are widened
            // whenever the labels need more room, so names are never clipped.
            int maxLeftLabel = 0;
            for (String s : leftLabels) {
                maxLeftLabel = Math.max(maxLeftLabel, fm.stringWidth(s));
            }
            int maxRightLabel = 0;
            for (String s : rightLabels) {
                maxRightLabel = Math.max(maxRightLabel, fm.stringWidth(s));
            }
            int leftX = Math.max((int) (w * 0.24), 10 + maxLeftLabel + NODE_R + 6);
            int rightX = Math.min((int) (w * 0.76), w - 10 - maxRightLabel - NODE_R - 6);
            if (rightX - leftX < 30) {
                leftX = (int) (w * 0.25);
                rightX = (int) (w * 0.75);
            }

            int usable = h - TOP_PAD - BOTTOM_PAD;
            int[] yL = new int[nL];
            int[] yR = new int[nR];
            for (int i = 0; i < nL; i++) {
                yL[i] = TOP_PAD + (int) ((i + 0.5) * usable / nL);
            }
            for (int i = 0; i < nR; i++) {
                yR[i] = TOP_PAD + (int) ((i + 0.5) * usable / nR);
            }

            g2.setFont(GuiTheme.BODY_BOLD);
            g2.setColor(GuiTheme.MUTED);
            FontMetrics hfm = g2.getFontMetrics();
            String lh = "FACULTY (" + nL + ")";
            String rh = "COURSES (" + nR + ")";
            g2.drawString(lh, leftX - hfm.stringWidth(lh) / 2, 13);
            g2.drawString(rh, rightX - hfm.stringWidth(rh) / 2, 13);

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(EDGE);
            for (int u = 0; u < nL && u < adj.size(); u++) {
                for (int v : adj.get(u)) {
                    if (v >= 0 && v < nR) {
                        g2.drawLine(leftX + NODE_R, yL[u], rightX - NODE_R, yR[v]);
                    }
                }
            }

            if (matchLeft != null) {
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(GuiTheme.ACCENT);
                for (int u = 0; u < nL && u < matchLeft.length; u++) {
                    int v = matchLeft[u];
                    if (v >= 0 && v < nR) {
                        g2.drawLine(leftX + NODE_R, yL[u], rightX - NODE_R, yR[v]);
                    }
                }
            }

            g2.setFont(labelFont);
            for (int i = 0; i < nL; i++) {
                boolean matched = matchLeft != null && i < matchLeft.length && matchLeft[i] >= 0;
                boolean inCover = coverLeft != null && i < coverLeft.length && coverLeft[i];
                drawNode(g2, leftX, yL[i], matched, inCover);
                g2.setColor(matched || matchLeft == null ? GuiTheme.TEXT : GuiTheme.MUTED);
                String s = leftLabels[i];
                g2.drawString(s, leftX - NODE_R - 6 - fm.stringWidth(s), yL[i] + fm.getAscent() / 2 - 1);
            }
            for (int i = 0; i < nR; i++) {
                boolean matched = matchRight != null && i < matchRight.length && matchRight[i] >= 0;
                boolean inCover = coverRight != null && i < coverRight.length && coverRight[i];
                drawNode(g2, rightX, yR[i], matched, inCover);
                g2.setColor(matched || matchLeft == null ? GuiTheme.TEXT : GuiTheme.MUTED);
                g2.drawString(rightLabels[i], rightX + NODE_R + 6, yR[i] + fm.getAscent() / 2 - 1);
            }

            int ly = h - 8;
            int lx = 10;
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(EDGE);
            g2.drawLine(lx, ly - 4, lx + 22, ly - 4);
            g2.setColor(GuiTheme.MUTED);
            g2.drawString("eligibility", lx + 28, ly);
            lx += 96;
            g2.setStroke(new BasicStroke(2.2f));
            g2.setColor(GuiTheme.ACCENT);
            g2.drawLine(lx, ly - 4, lx + 22, ly - 4);
            g2.setColor(GuiTheme.MUTED);
            g2.drawString("matching", lx + 28, ly);
            lx += 88;
            if (coverLeft != null || coverRight != null) {
                g2.setColor(GuiTheme.HIGHLIGHT);
                g2.fillOval(lx, ly - 11, 12, 12);
                g2.setColor(COVER_OUTLINE);
                g2.drawOval(lx, ly - 11, 12, 12);
                g2.setColor(GuiTheme.MUTED);
                g2.drawString("Konig cover", lx + 17, ly);
            }

            if (matchLeft == null) {
                g2.setColor(GuiTheme.MUTED);
                String hint = "Run Hopcroft-Karp to compute the maximum matching";
                g2.drawString(hint, Math.max(10, (w - fm.stringWidth(hint)) / 2), h / 2);
            }
        } finally {
            g2.dispose();
        }
    }

    private static void drawNode(Graphics2D g2, int x, int y, boolean matched, boolean inCover) {
        Color fill = inCover ? GuiTheme.HIGHLIGHT : (matched ? GuiTheme.ACCENT_SOFT : Color.WHITE);
        Color outline = inCover ? COVER_OUTLINE : (matched ? GuiTheme.ACCENT : NODE_OUTLINE);
        g2.setColor(fill);
        g2.fillOval(x - NODE_R, y - NODE_R, 2 * NODE_R, 2 * NODE_R);
        g2.setStroke(new BasicStroke(inCover || matched ? 1.8f : 1.2f));
        g2.setColor(outline);
        g2.drawOval(x - NODE_R, y - NODE_R, 2 * NODE_R, 2 * NODE_R);
    }
}
