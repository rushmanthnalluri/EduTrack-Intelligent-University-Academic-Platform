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
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

public final class GuiTheme {

    // Production desktop palette: warm canvas, white surfaces, dark navigation,
    // and a single blue action color with clear semantic states.
    public static final Color BG = new Color(0xF1F4F8);
    public static final Color SURFACE = new Color(0xFFFFFF);
    public static final Color CARD_BG = SURFACE;
    public static final Color CARD_BORDER = new Color(0xD7DEE8);
    public static final Color CARD_BORDER_STRONG = new Color(0xB9C4D2);
    public static final Color DIVIDER = new Color(0xE3E8EF);

    public static final Color SIDEBAR_BG = new Color(0x172033);
    public static final Color SIDEBAR_LINE = new Color(0x2B3850);
    public static final Color SIDEBAR_FG = new Color(0xC7D0DF);
    public static final Color SIDEBAR_HOVER = new Color(0x25324A);
    public static final Color SIDEBAR_SELECTED = new Color(0x2563EB);
    public static final Color SIDEBAR_SELECTED_TEXT = Color.WHITE;

    public static final Color ACCENT = new Color(0x2563EB);
    public static final Color ACCENT_DARK = new Color(0x1D4ED8);
    public static final Color ACCENT_SOFT = new Color(0xEAF2FF);
    public static final Color ACCENT_BORDER = new Color(0xAFC9FF);
    public static final Color TEXT = new Color(0x172033);
    public static final Color MUTED = new Color(0x667085);
    public static final Color SUCCESS = new Color(0x087A5B);
    public static final Color ERROR = new Color(0xC9362B);
    public static final Color WARNING = new Color(0xA15C00);
    public static final Color HIGHLIGHT = new Color(0xFFF4CC);
    public static final Color CONSOLE_BG = new Color(0x101827);
    public static final Color CONSOLE_FG = new Color(0xDCE5F2);

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
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.font", BODY);
        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.focus", ACCENT_SOFT);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", ACCENT_SOFT);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("Spinner.background", SURFACE);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("Table.font", BODY);
        UIManager.put("TableHeader.font", SMALL_BOLD);
        UIManager.put("Table.rowHeight", 32);
        UIManager.put("Table.showGrid", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("TableHeader.height", 36);
        UIManager.put("ToolTip.font", SMALL);
        UIManager.put("ToolTip.background", TEXT);
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("TabbedPane.font", BODY_BOLD);
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(8, 8, 8, 8));
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("TextField.font", BODY);
        UIManager.put("Spinner.font", BODY);
        UIManager.put("Label.font", BODY);
    }

    public static JButton primaryButton(String text) {
        JButton button = flatButton(text, ACCENT, Color.WHITE);
        button.setFont(BODY_BOLD);
        installButtonBehavior(button, ACCENT, ACCENT_DARK, Color.WHITE);
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = flatButton(text, SURFACE, ACCENT_DARK);
        button.setBorder(new CompoundBorder(
                new LineBorder(ACCENT_BORDER, 1, true),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        installButtonBehavior(button, SURFACE, ACCENT_SOFT, ACCENT_DARK);
        return button;
    }

    public static JButton ghostButton(String text) {
        JButton button = flatButton(text, SURFACE, TEXT);
        button.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        installButtonBehavior(button, SURFACE, new Color(0xE9EDF3), TEXT);
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
        button.setBorder(new CompoundBorder(
                new LineBorder(background.equals(ACCENT) ? ACCENT : CARD_BORDER_STRONG, 1, true),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static void installButtonBehavior(JButton button, Color normal, Color hover, Color foreground) {
        button.setForeground(foreground);
        button.addChangeListener(e -> {
            if (!button.isEnabled()) {
                button.setBackground(new Color(0xE6EAF0));
                button.setForeground(new Color(0x98A2B3));
            } else if (button.getModel().isPressed()) {
                button.setBackground(hover.darker());
                button.setForeground(Color.WHITE);
            } else if (button.getModel().isRollover()) {
                button.setBackground(hover);
                button.setForeground(foreground);
            } else {
                button.setBackground(normal);
                button.setForeground(foreground);
            }
        });
    }

    public static void styleTextField(JTextField field) {
        field.setFont(BODY);
        field.setForeground(TEXT);
        field.setBackground(SURFACE);
        field.setCaretColor(ACCENT);
        field.setBorder(new CompoundBorder(
                new LineBorder(CARD_BORDER_STRONG, 1, true),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        field.setMargin(new Insets(0, 0, 0, 0));
    }

    public static void styleTable(JTable table) {
        table.setFont(BODY);
        table.setForeground(TEXT);
        table.setBackground(SURFACE);
        table.setSelectionBackground(ACCENT_SOFT);
        table.setSelectionForeground(TEXT);
        table.setGridColor(DIVIDER);
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setFont(SMALL_BOLD);
        table.getTableHeader().setForeground(TEXT);
        table.getTableHeader().setBackground(new Color(0xEEF2F7));
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));
    }

    public static Border cardBorder() {
        return new CompoundBorder(
                new LineBorder(CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(18, 18, 18, 18));
    }

    public static Border compactCardBorder() {
        return new CompoundBorder(
                new LineBorder(CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16));
    }

    public static TitledBorder sectionBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                new LineBorder(CARD_BORDER), title);
        border.setTitleFont(H2);
        border.setTitleColor(TEXT);
        return border;
    }

    public static void styleComponentBackground(JComponent component) {
        component.setBackground(SURFACE);
        component.setForeground(TEXT);
    }
}
