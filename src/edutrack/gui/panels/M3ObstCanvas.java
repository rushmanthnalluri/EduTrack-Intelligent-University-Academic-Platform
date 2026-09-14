package edutrack.gui.panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JPanel;

import edutrack.gui.GuiTheme;

public class M3ObstCanvas extends JPanel {

    private static final double NODE_HEIGHT = 46;
    private static final double NODE_MIN_WIDTH = 58;
    private static final double NODE_MAX_WIDTH = 118;
    private static final double MAX_LEVEL_GAP = 92;

    private String[] keys = new String[0];
    private int[] freq = new int[0];
    private int[][] rootTable;
    private Node rootNode;
    private int nodeCount;
    private int maxDepth;

    public M3ObstCanvas() {
        setBackground(GuiTheme.CARD_BG);
        setPreferredSize(new Dimension(980, 430));
    }

    public void setData(String[] keys, int[] freq, int[][] rootTable) {
        if (keys == null || freq == null || rootTable == null || keys.length == 0) {
            this.keys = new String[0];
            this.freq = new int[0];
            this.rootTable = null;
            this.rootNode = null;
            this.nodeCount = 0;
            this.maxDepth = 0;
        } else {
            this.keys = keys;
            this.freq = freq;
            this.rootTable = rootTable;
            int[] inorderCounter = new int[1];
            this.rootNode = build(0, keys.length - 1, 0, inorderCounter);
            this.nodeCount = inorderCounter[0];
            this.maxDepth = depthOf(rootNode);
        }
        repaint();
    }

    public void clearData() {
        setData(null, null, null);
    }

    private Node build(int i, int j, int depth, int[] inorderCounter) {
        if (i > j) {
            return null;
        }
        int r = rootTable[i][j];
        Node node = new Node(r, depth);
        node.left = build(i, r - 1, depth + 1, inorderCounter);
        node.x = inorderCounter[0]++;
        node.right = build(r + 1, j, depth + 1, inorderCounter);
        return node;
    }

    private static int depthOf(Node node) {
        if (node == null) {
            return -1;
        }
        return Math.max(node.depth, Math.max(depthOf(node.left), depthOf(node.right)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        try {
            if (rootNode == null) {
                drawHint(g2);
                return;
            }
            int marginX = 14;
            int marginY = 16;
            double xGap = (getWidth() - 2.0 * marginX) / Math.max(1, nodeCount);
            double yGap = Math.min(MAX_LEVEL_GAP,
                    (getHeight() - 2.0 * marginY) / Math.max(1, maxDepth + 1));
            double nodeW = Math.max(NODE_MIN_WIDTH, Math.min(xGap - 12, NODE_MAX_WIDTH));
            g2.setStroke(new BasicStroke(1.4f));
            drawEdges(g2, rootNode, marginX, marginY, xGap, yGap);
            drawNode(g2, rootNode, marginX, marginY, xGap, yGap, nodeW);
        } finally {
            g2.dispose();
        }
    }

    private void drawHint(Graphics2D g2) {
        g2.setFont(GuiTheme.BODY);
        g2.setColor(GuiTheme.MUTED);
        String hint = "Press \"Build Optimal BST\" to construct and draw the tree.";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(hint, (getWidth() - fm.stringWidth(hint)) / 2, getHeight() / 2);
    }

    private double centerX(Node node, int marginX, double xGap) {
        return marginX + xGap * (node.x + 0.5);
    }

    private double centerY(Node node, int marginY, double yGap) {
        return marginY + yGap * (node.depth + 0.5);
    }

    private void drawEdges(Graphics2D g2, Node node, int marginX, int marginY, double xGap, double yGap) {
        if (node == null) {
            return;
        }
        drawEdgeTo(g2, node, node.left, marginX, marginY, xGap, yGap);
        drawEdgeTo(g2, node, node.right, marginX, marginY, xGap, yGap);
        drawEdges(g2, node.left, marginX, marginY, xGap, yGap);
        drawEdges(g2, node.right, marginX, marginY, xGap, yGap);
    }

    private void drawEdgeTo(Graphics2D g2, Node parent, Node child,
            int marginX, int marginY, double xGap, double yGap) {
        if (child == null) {
            return;
        }
        g2.setColor(GuiTheme.MUTED);
        g2.drawLine(
                (int) centerX(parent, marginX, xGap),
                (int) (centerY(parent, marginY, yGap) + NODE_HEIGHT / 2),
                (int) centerX(child, marginX, xGap),
                (int) (centerY(child, marginY, yGap) - NODE_HEIGHT / 2));
    }

    private void drawNode(Graphics2D g2, Node node, int marginX, int marginY,
            double xGap, double yGap, double nodeW) {
        if (node == null) {
            return;
        }
        double cx = centerX(node, marginX, xGap);
        double cy = centerY(node, marginY, yGap);
        boolean isRoot = node.depth == 0;
        boolean isLeaf = node.left == null && node.right == null;

        RoundRectangle2D box = new RoundRectangle2D.Double(
                cx - nodeW / 2, cy - NODE_HEIGHT / 2, nodeW, NODE_HEIGHT, 14, 14);
        if (isRoot) {
            g2.setColor(GuiTheme.ACCENT);
            g2.fill(box);
        } else {
            g2.setColor(isLeaf ? GuiTheme.ACCENT_SOFT : Color.WHITE);
            g2.fill(box);
            g2.setColor(GuiTheme.ACCENT_DARK);
            g2.draw(box);
        }

        String code = keys[node.keyIndex];
        String freqText = "f=" + freq[node.keyIndex];
        g2.setFont(GuiTheme.BODY_BOLD);
        FontMetrics fmBold = g2.getFontMetrics();
        g2.setColor(isRoot ? Color.WHITE : GuiTheme.TEXT);
        g2.drawString(code, (float) (cx - fmBold.stringWidth(code) / 2.0), (float) (cy - 1));
        g2.setFont(GuiTheme.BODY.deriveFont(11f));
        FontMetrics fmSmall = g2.getFontMetrics();
        g2.setColor(isRoot ? new Color(0xD8E4FF) : GuiTheme.MUTED);
        g2.drawString(freqText, (float) (cx - fmSmall.stringWidth(freqText) / 2.0), (float) (cy + 15));

        drawNode(g2, node.left, marginX, marginY, xGap, yGap, nodeW);
        drawNode(g2, node.right, marginX, marginY, xGap, yGap, nodeW);
    }

    private static final class Node {
        final int keyIndex;
        final int depth;
        Node left;
        Node right;
        int x;

        Node(int keyIndex, int depth) {
            this.keyIndex = keyIndex;
            this.depth = depth;
        }
    }
}
