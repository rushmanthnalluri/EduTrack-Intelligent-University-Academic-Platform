package edutrack.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

public final class GuiTheme {

    public static final Color BG = new Color(0xF3F5F9);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color CARD_BORDER = new Color(0xE2E6EE);
    public static final Color SIDEBAR_BG = new Color(0x182231);
    public static final Color SIDEBAR_LINE = new Color(0x232F42);
    public static final Color SIDEBAR_FG = new Color(0xC9D2E0);
    public static final Color SIDEBAR_SELECTED = new Color(0x2563EB);
    public static final Color ACCENT = new Color(0x2563EB);
    public static final Color ACCENT_DARK = new Color(0x1D4ED8);
    public static final Color ACCENT_SOFT = new Color(0xE8EEFC);
    public static final Color TEXT = new Color(0x1F2937);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color SUCCESS = new Color(0x0E9F6E);
    public static final Color ERROR = new Color(0xE02424);
    public static final Color HIGHLIGHT = new Color(0xFDE68A);
    public static final Color CONSOLE_BG = new Color(0x0F172A);
    public static final Color CONSOLE_FG = new Color(0xD7E0EE);

    public static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font H1 = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font H2 = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
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
            // keep the default look and feel
        }
        UIManager.put("defaultFont", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("TableHeader.font", BODY_BOLD);
        UIManager.put("Table.rowHeight", 26);
        UIManager.put("ToolTip.font", BODY);
    }

    public static JButton primaryButton(String text) {
        JButton button = flatButton(text, ACCENT, Color.WHITE);
        button.setFont(BODY_BOLD);
        return button;
    }

    public static JButton secondaryButton(String text) {
        return flatButton(text, ACCENT_SOFT, ACCENT_DARK);
    }

    public static JButton flatButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(BODY);
        button.setForeground(foreground);
        button.setBackground(background);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(14, 14, 14, 14));
    }

    public static TitledBorder sectionBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(CARD_BORDER), title);
        border.setTitleFont(H2);
        border.setTitleColor(TEXT);
        return border;
    }
}
