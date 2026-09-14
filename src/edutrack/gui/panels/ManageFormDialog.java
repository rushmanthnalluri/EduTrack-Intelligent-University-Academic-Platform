package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;

import edutrack.gui.GuiTheme;

/**
 * Small modal OK/Cancel dialog shell for the Manage Records add-forms. The OK
 * button runs the ConfirmHandler; the dialog only closes when the handler
 * accepts the input, so validation errors can keep it open.
 */
final class ManageFormDialog extends JDialog {

    interface ConfirmHandler {
        /** @return true to accept and close, false to keep the dialog open. */
        boolean onConfirm();
    }

    private boolean confirmed;

    ManageFormDialog(Window owner, String title, JComponent form, ConfirmHandler handler) {
        super(owner, title, ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(GuiTheme.CARD_BG);
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        form.setOpaque(false);
        content.add(form, BorderLayout.CENTER);

        JButton ok = GuiTheme.primaryButton("Add");
        JButton cancel = GuiTheme.secondaryButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, BorderLayout.SOUTH);

        ok.addActionListener(e -> {
            if (handler.onConfirm()) {
                confirmed = true;
                dispose();
            }
        });
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(ok);

        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    boolean confirmed() {
        return confirmed;
    }
}
