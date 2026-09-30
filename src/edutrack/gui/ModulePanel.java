package edutrack.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;

import edutrack.data.DataStore;

public abstract class ModulePanel extends JPanel {

    protected final DataStore dataStore;

    protected ModulePanel(DataStore dataStore) {
        super(new BorderLayout(16, 16));
        this.dataStore = dataStore;
        setBackground(GuiTheme.BG);
        setBorder(BorderFactory.createEmptyBorder(24, 26, 22, 26));
    }

    protected JPanel card(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(GuiTheme.CARD_BG);
        panel.setMinimumSize(new Dimension(0, 0));

        if (title != null && !title.isEmpty()) {
            JLabel heading = new JLabel(title);
            heading.setFont(GuiTheme.H2);
            heading.setForeground(GuiTheme.TEXT);
            heading.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, GuiTheme.DIVIDER),
                    BorderFactory.createEmptyBorder(0, 0, 10, 0)));
            panel.add(heading, BorderLayout.NORTH);
        }

        panel.add(content, BorderLayout.CENTER);
        panel.setBorder(new CompoundBorder(
                new LineBorder(GuiTheme.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        return panel;
    }

    protected JPanel sectionHeader(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(8, 2));
        header.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(GuiTheme.H1);
        titleLabel.setForeground(GuiTheme.TEXT);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(GuiTheme.BODY);
        subtitleLabel.setForeground(GuiTheme.MUTED);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        text.add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            text.add(subtitleLabel);
        }
        header.add(text, BorderLayout.WEST);
        return header;
    }

    protected JPanel toolbar(Component... components) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        for (Component component : components) {
            bar.add(component);
        }
        bar.setPreferredSize(new Dimension(0, 40));
        return bar;
    }

    protected <T> void runAsync(Callable<T> work, Consumer<T> onDone) {
        runAsync(work, onDone, this::showError);
    }

    protected <T> void runAsync(Callable<T> work, Consumer<T> onDone, Consumer<Throwable> onError) {
        runAsync(work, onDone, onError, new AbstractButton[0]);
    }

    protected <T> void runAsync(Callable<T> work, Consumer<T> onDone,
            Consumer<Throwable> onError, AbstractButton... busyButtons) {
        final long submittedRevision = dataStore.revision();
        new SwingWorker<T, Void>() {
            private void restoreBusyButtons() {
                for (AbstractButton button : busyButtons) {
                    if (button != null) {
                        button.setEnabled(true);
                    }
                }
            }

            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    if (dataStore.revision() == submittedRevision) {
                        onDone.accept(result);
                    } else {
                        restoreBusyButtons();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    restoreBusyButtons();
                    onError.accept(e);
                } catch (CancellationException e) {
                    restoreBusyButtons();
                    onError.accept(e);
                } catch (ExecutionException e) {
                    restoreBusyButtons();
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    onError.accept(cause);
                }
            }
        }.execute();
    }

    /** Captures every button's state so stale workers can restore, rather than guess, UI state. */
    protected void showError(Throwable t) {
        t.printStackTrace();
        JOptionPane.showMessageDialog(this,
                t.getMessage() == null ? t.toString() : t.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}
