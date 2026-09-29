package edutrack.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

public final class GuiTheme {

    // Calm, high-contrast palette designed for long desktop sessions.
    public static final Color BG = new Color(0xF7F8FC);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color CARD_BORDER = new Color(0xE5E7EB);
    public static final Color CARD_BORDER_STRONG = new Color(0xD5DAE3);

    public static final Color SIDEBAR_BG = new Color(0x111827);
    public static final Color SIDEBAR_LINE = new Color(0x263244);
    public static final Color SIDEBAR_FG = new Color(0xB8C2D1);
    public static final Color SIDEBAR_HOVER = new Color(0x1B2638);
    public static final Color SIDEBAR_SELECTED = new Color(0x2563EB);

    public static final Color ACCENT = new Color(0x2563EB);
    public static final Color ACCENT_DARK = new Color(0x1D4ED8);
    public static final Color ACCENT_SOFT = new Color(0xEFF6FF);
    public static final Color TEXT = new Color(0x111827);
    public static final Color MUTED = new Color(0x667085);
    public static final Color SUCCESS = new Color(0x078A62);
    public static final Color ERROR = new Color(0xD92D20);
    public static final Color WARNING = new Color(0xB54708);
    public static final Color HIGHLIGHT = new Color(0xFEF0C7);
    public static final Color CONSOLE_BG = new Color(0x0B1220);
    public static final Color CONSOLE_FG = new Color(0xD7E0EE);

    public static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font H1 = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font H2 = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font MONO = new Font("Consolas", Font.PLAIN, 13);

    private GuiTheme() {
    }

    public static void applyGlobalDefaults() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // Keep the platform look and feel when Nimbus is unavailable.
        }

        UIManager.put("defaultFont", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("TableHeader.font", SMALL_BOLD);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.showGrid", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("ToolTip.font", SMALL);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("TabbedPane.font", BODY_BOLD);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("TextField.font", BODY);
        UIManager.put("Label.font", BODY);
    }

    public static JButton primaryButton(String text) {
        JButton button = flatButton(text, ACCENT, Color.WHITE);
        button.setFont(BODY_BOLD);
        installButtonBehavior(button, ACCENT, ACCENT_DARK, Color.WHITE);
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = flatButton(text, ACCENT_SOFT, ACCENT_DARK);
        installButtonBehavior(button, ACCENT_SOFT, new Color(0xE0ECFF), ACCENT_DARK);
        return button;
    }

    public static JButton ghostButton(String text) {
        JButton button = flatButton(text, CARD_BG, TEXT);
        button.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        installButtonBehavior(button, CARD_BG, new Color(0xF2F4F7), TEXT);
        return button;
    }

    public static JButton flatButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(BODY);
        button.setForeground(foreground);
        button.setBackground(background);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static void installButtonBehavior(JButton button, Color normal, Color hover, Color foreground) {
        button.setForeground(foreground);
        button.addChangeListener(e -> {
            if (!button.isEnabled()) {
                button.setBackground(new Color(0xEAECF0));
                button.setForeground(new Color(0x98A2B3));
            } else if (button.getModel().isPressed()) {
                button.setBackground(button == null ? hover : hover.darker());
            } else if (button.getModel().isRollover()) {
                button.setBackground(hover);
            } else {
                button.setBackground(normal);
            }
        });
    }

    public static void styleTextField(JTextField field) {
        field.setFont(BODY);
        field.setForeground(TEXT);
        field.setBackground(CARD_BG);
        field.setCaretColor(ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER_STRONG),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        field.setMargin(new Insets(0, 0, 0, 0));
    }

    public static void styleTable(JTable table) {
        table.setFont(BODY);
        table.setForeground(TEXT);
        table.setBackground(CARD_BG);
        table.setSelectionBackground(ACCENT_SOFT);
        table.setSelectionForeground(TEXT);
        table.setGridColor(CARD_BORDER);
        table.setRowHeight(30);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(SMALL_BOLD);
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(new Color(0xF9FAFB));
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(18, 18, 18, 18));
    }

    public static Border compactCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16));
    }

    public static TitledBorder sectionBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(CARD_BORDER), title);
        border.setTitleFont(H2);
        border.setTitleColor(TEXT);
        return border;
    }

    public static void styleComponentBackground(JComponent component) {
        component.setBackground(CARD_BG);
        component.setForeground(TEXT);
    }
}
