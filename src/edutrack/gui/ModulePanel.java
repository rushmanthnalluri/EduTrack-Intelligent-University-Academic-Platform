package edutrack.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import edutrack.data.DataStore;

public abstract class ModulePanel extends JPanel {

    protected final DataStore dataStore;

    protected ModulePanel(DataStore dataStore) {
        this.dataStore = dataStore;
        setLayout(new BorderLayout(16, 16));
        setBackground(GuiTheme.BG);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
    }

    protected JPanel card(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(GuiTheme.CARD_BG);
        panel.setBorder(GuiTheme.cardBorder());

        if (title != null && !title.isEmpty()) {
            JLabel heading = new JLabel(title);
            heading.setFont(GuiTheme.H2);
            heading.setForeground(GuiTheme.TEXT);
            heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
            panel.add(heading, BorderLayout.NORTH);
        }
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    protected <T> void runAsync(Callable<T> work, Consumer<T> onDone) {
        runAsync(work, onDone, this::showError);
    }

    protected <T> void runAsync(Callable<T> work, Consumer<T> onDone, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    onDone.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    onError.accept(cause);
                }
            }
        }.execute();
    }

    protected void showError(Throwable t) {
        t.printStackTrace();
        JOptionPane.showMessageDialog(this,
                t.getMessage() == null ? t.toString() : t.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}
