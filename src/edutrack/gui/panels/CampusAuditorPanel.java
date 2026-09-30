package edutrack.gui.panels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;

import edutrack.data.DataStore;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.modules.M3CampusTsp;

/** Interactive campus map for visualizing an exact Bitmask-DP TSP tour. */
public final class CampusAuditorPanel extends ModulePanel {
    private final CampusCanvas canvas = new CampusCanvas();
    private final JLabel status = new JLabel("Press Plan Optimal Tour to solve the campus TSP.");
    private final JButton plan = GuiTheme.primaryButton("Plan Optimal Tour");
    private final JButton play = GuiTheme.secondaryButton("Play");
    private final JButton pause = GuiTheme.secondaryButton("Pause");
    private final JButton reset = GuiTheme.secondaryButton("Reset");
    private final JSlider speed = new JSlider(10, 100, 50);

    public CampusAuditorPanel(DataStore dataStore) {
        super(dataStore);
        add(sectionHeader("Campus Auditor — Bitmask DP",
                "Interactive visualization of the exact O(2^N · N²) Traveling Academic Auditor tour."), BorderLayout.NORTH);

        JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 6));
        controls.setOpaque(false);
        controls.add(plan);
        controls.add(play);
        controls.add(pause);
        controls.add(reset);
        controls.add(new JLabel("Speed"));
        speed.setPreferredSize(new Dimension(120, 24));
        controls.add(speed);
        status.setForeground(GuiTheme.MUTED);

        JPanel south = new JPanel(new BorderLayout(8, 4));
        south.setOpaque(false);
        south.add(controls, BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);

        add(card("Campus map and DP telemetry", canvas), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        plan.addActionListener(e -> {
            M3CampusTsp.TourResult result = M3CampusTsp.solve(distanceMatrix());
            canvas.setTour(result);
            status.setText("Optimal tour: " + result.minCost + " minutes • "
                    + formatPath(result.path));
        });
        play.addActionListener(e -> canvas.play());
        pause.addActionListener(e -> canvas.pause());
        reset.addActionListener(e -> canvas.reset());
        speed.addChangeListener(e -> canvas.setSpeed(speed.getValue()));
    }

    private static String formatPath(int[] path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            if (i > 0) sb.append(" → ");
            sb.append(shortNames()[path[i]]);
        }
        return sb.toString();
    }

    private static String[] shortNames() {
        return new String[] {"CS", "AI", "DS", "MATH", "ELEC", "CYBER"};
    }

    private static int[][] distanceMatrix() {
        return new int[][] {
            {0,10,15,20,25,30},
            {10,0,35,25,18,22},
            {15,35,0,30,28,14},
            {20,25,30,0,12,16},
            {25,18,28,12,0,24},
            {30,22,14,16,24,0}
        };
    }

    private static final class CampusCanvas extends JPanel {
        private static final String[] NAMES = {
            "Computer Science", "Artificial Intelligence", "Data Science",
            "Mathematics", "Electronics", "Cybersecurity"
        };
        private static final double[] X = {0.15, 0.50, 0.85, 0.74, 0.50, 0.26};
        private static final double[] Y = {0.30, 0.17, 0.30, 0.76, 0.86, 0.76};
        private M3CampusTsp.TourResult tour;
        private int step;
        private double progress;
        private boolean playing;
        private Timer timer;
        private int speedPercent = 50;
        private int hovered = -1;

        CampusCanvas() {
            setOpaque(true);
            setBackground(new Color(15, 20, 32));
            setPreferredSize(new Dimension(820, 500));
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int found = -1;
                    for (int i = 0; i < X.length; i++) {
                        double dx = e.getX() - X[i] * getWidth();
                        double dy = e.getY() - Y[i] * getHeight();
                        if (dx * dx + dy * dy <= 32 * 32) { found = i; break; }
                    }
                    if (found != hovered) { hovered = found; repaint(); }
                }
            });
            timer = new Timer(30, e -> tick());
        }

        void setTour(M3CampusTsp.TourResult tour) {
            this.tour = tour;
            step = 0;
            progress = 0;
            playing = false;
            timer.stop();
            repaint();
        }

        void play() {
            if (tour == null) return;
            if (step >= tour.path.length - 1) { step = 0; progress = 0; }
            playing = true;
            timer.start();
        }

        void pause() { playing = false; timer.stop(); repaint(); }

        void reset() { playing = false; timer.stop(); step = 0; progress = 0; repaint(); }

        void setSpeed(int value) { speedPercent = value; }

        private void tick() {
            if (!playing || tour == null) return;
            progress += 0.01 + speedPercent / 1400.0;
            if (progress >= 1.0) {
                progress = 0;
                step++;
                if (step >= tour.path.length - 1) {
                    step = tour.path.length - 1;
                    playing = false;
                    timer.stop();
                }
            }
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setColor(new Color(26,34,52,120));
            for (int x = 0; x < w; x += 40) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 40) g2.drawLine(0, y, w, y);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.setColor(new Color(245,210,165));
            g2.drawString("EDUTRACK UNIVERSITY • CAMPUS AUDITOR", 20, 25);

            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(59,73,103));
            for (int i = 0; i < X.length; i++)
                for (int j = i + 1; j < X.length; j++)
                    g2.drawLine((int)(X[i]*w),(int)(Y[i]*h),(int)(X[j]*w),(int)(Y[j]*h));

            if (tour != null) {
                g2.setStroke(new BasicStroke(4f));
                g2.setColor(new Color(245,210,165,110));
                for (int i = 0; i < tour.path.length - 1; i++) {
                    int a=tour.path[i], b=tour.path[i+1];
                    g2.drawLine((int)(X[a]*w),(int)(Y[a]*h),(int)(X[b]*w),(int)(Y[b]*h));
                }
                g2.setColor(new Color(34,197,94));
                for (int i = 0; i < step && i < tour.path.length-1; i++) {
                    int a=tour.path[i], b=tour.path[i+1];
                    g2.drawLine((int)(X[a]*w),(int)(Y[a]*h),(int)(X[b]*w),(int)(Y[b]*h));
                }
                if (step < tour.path.length - 1) {
                    int a=tour.path[step], b=tour.path[step+1];
                    double ax=X[a]*w, ay=Y[a]*h, bx=X[b]*w, by=Y[b]*h;
                    g2.setColor(new Color(245,210,165));
                    g2.setStroke(new BasicStroke(5f));
                    g2.drawLine((int)ax,(int)ay,(int)(ax+(bx-ax)*progress),(int)(ay+(by-ay)*progress));
                }
            }

            for (int i=0;i<X.length;i++) {
                int cx=(int)(X[i]*w), cy=(int)(Y[i]*h);
                if (i==hovered) {
                    g2.setColor(new Color(245,210,165,80));
                    g2.fillOval(cx-35,cy-35,70,70);
                }
                g2.setColor(new Color(59,130,246));
                g2.fillOval(cx-24,cy-24,48,48);
                g2.setColor(new Color(15,20,32));
                g2.fillOval(cx-20,cy-20,40,40);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI",Font.BOLD,12));
                String label=shortNames()[i];
                int tw=g2.getFontMetrics().stringWidth(label);
                g2.drawString(label,cx-tw/2,cy+4);
                g2.setFont(new Font("Segoe UI",Font.PLAIN,11));
                g2.drawString(NAMES[i],cx-35,cy+42);
            }

            int mask=0,cost=0,current=-1;
            if (tour != null) {
                for (int i=0;i<=step && i<tour.path.length;i++) {
                    int node=tour.path[i];
                    mask |= 1<<node;
                    if (i>0) cost += distanceMatrix()[tour.path[i-1]][node];
                }
                current=tour.path[Math.min(step,tour.path.length-1)];
            }
            int px=w-300, py=h-125;
            g2.setColor(new Color(26,34,52,235));
            g2.fillRoundRect(px,py,275,105,12,12);
            g2.setColor(new Color(245,210,165));
            g2.drawString("BITMASK DP TELEMETRY",px+14,py+20);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Consolas",Font.BOLD,12));
            String binary=String.format("%6s",Integer.toBinaryString(mask)).replace(' ','0');
            g2.drawString("State : "+binary+" ("+mask+"/63)",px+14,py+42);
            g2.setFont(new Font("Segoe UI",Font.PLAIN,11));
            g2.drawString("Current: "+(current<0?"—":shortNames()[current]),px+14,py+62);
            g2.drawString("Cost: "+cost+(tour==null?"":" / "+tour.minCost)+" min",px+14,py+80);
            g2.drawString("Complexity: O(2^N · N²)",px+14,py+97);
            g2.dispose();
        }
    }
}
