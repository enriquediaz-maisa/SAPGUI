package sapgui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Arrays;
import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/** Colours, fonts and vector icons of the blue "Signature"-style desktop theme. */
final class Theme {
    static final Color BG = new Color(0xDCE6F4);
    static final Color BG_DARK = new Color(0xC2D3EA);
    static final Color BG_LIGHT = new Color(0xEDF2FA);
    static final Color LINE = new Color(0x9DB3D1);
    static final Color FIELD_LINE = new Color(0x7F9DB9);
    static final Color RO = new Color(0xE4ECF7);
    static final Color TITLE_FG = new Color(0x1C3A63);
    static final Color SEL = new Color(0xFCD98A);
    static final Color HDR = new Color(0xCBD9EC);
    static final Color GRID = new Color(0xC5D2E4);
    static final Color RED = new Color(0xC8102E);

    static Font FONT, BOLD, TITLE, MONO;

    static void init() {
        var families = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        String fam = "Dialog";
        for (String f : new String[] {"Arial", "Liberation Sans", "Helvetica", "Tahoma"}) if (families.contains(f)) { fam = f; break; }
        FONT = new Font(fam, Font.PLAIN, 12);
        BOLD = FONT.deriveFont(Font.BOLD);
        TITLE = new Font(fam, Font.BOLD, 16);
        String mono = families.contains("Courier New") ? "Courier New" : families.contains("Liberation Mono") ? "Liberation Mono" : Font.MONOSPACED;
        MONO = new Font(mono, Font.PLAIN, 12);
        FontUIResource fr = new FontUIResource(FONT);
        for (Object k : java.util.Collections.list(UIManager.getDefaults().keys()))
            if (UIManager.get(k) instanceof FontUIResource) UIManager.put(k, fr);
        ColorUIResource bg = new ColorUIResource(BG);
        for (String k : new String[] {"Panel.background", "OptionPane.background", "CheckBox.background", "RadioButton.background",
                "ScrollPane.background", "Viewport.background", "MenuBar.background", "Menu.background", "MenuItem.background",
                "PopupMenu.background", "ScrollBar.background", "Button.background", "ComboBox.background", "SplitPane.background"})
            UIManager.put(k, bg);
        UIManager.put("Menu.selectionBackground", new ColorUIResource(SEL));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(SEL));
        UIManager.put("Menu.selectionForeground", new ColorUIResource(Color.BLACK));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(Color.BLACK));
        UIManager.put("TextField.selectionBackground", new ColorUIResource(new Color(0x3A6EA5)));
        UIManager.put("TextField.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("ToolTip.background", new ColorUIResource(new Color(0xFFFFE1)));
        UIManager.put("Tree.background", new ColorUIResource(BG_LIGHT));
        UIManager.put("Tree.textBackground", new ColorUIResource(BG_LIGHT));
        UIManager.put("Tree.selectionBackground", new ColorUIResource(SEL));
        UIManager.put("Tree.selectionBorderColor", new ColorUIResource(SEL));
    }

    static void gradient(Graphics g, int w, int h, Color top, Color bottom) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g2.fillRect(0, 0, w, h);
    }

    static Icon icon(String name) { return new Ico(name, 1f); }
    static Icon icon(String name, float alpha) { return new Ico(name, alpha); }

    private record Ico(String name, float alpha) implements Icon {
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            if (alpha < 1f) g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
            draw(g2, name);
            g2.dispose();
        }
    }

    private static final Color GREEN = new Color(0x3C9A3C), YELLOW = new Color(0xF2B600), BLUE = new Color(0x2F6DB5), GREY = new Color(0x6B7888);

    private static void disc(Graphics2D g, Color c) {
        g.setPaint(new GradientPaint(0, 1, c.brighter(), 0, 15, c.darker()));
        g.fillOval(1, 1, 14, 14);
        g.setColor(c.darker().darker()); g.setStroke(new BasicStroke(0.8f)); g.drawOval(1, 1, 14, 14);
    }

    private static void poly(Graphics2D g, boolean fill, double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        if (fill) { path.closePath(); g.fill(path); } else g.draw(path);
    }

    private static void sheet(Graphics2D g) {
        g.setColor(Color.WHITE); g.fillRect(3, 1, 10, 14);
        g.setColor(GREY); g.setStroke(new BasicStroke(1f)); g.drawRect(3, 1, 10, 14);
    }

    private static void tri(Graphics2D g, boolean right, int x) {
        g.setColor(BLUE);
        if (right) poly(g, true, x, 3, x + 6, 8, x, 13); else poly(g, true, x + 6, 3, x, 8, x + 6, 13);
    }

    private static void draw(Graphics2D g, String n) {
        BasicStroke s2 = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        switch (n) {
            case "ok", "approve" -> { disc(g, GREEN); g.setColor(Color.WHITE); g.setStroke(s2); poly(g, false, 4.5, 8.5, 7, 11, 11.5, 5.5); }
            case "cancel", "reject" -> { disc(g, new Color(0xD23B3B)); g.setColor(Color.WHITE); g.setStroke(s2); g.drawLine(5, 5, 11, 11); g.drawLine(11, 5, 5, 11); }
            case "back" -> { disc(g, GREEN); g.setColor(Color.WHITE); poly(g, true, 4, 8, 9, 4, 9, 6.5, 12, 6.5, 12, 9.5, 9, 9.5, 9, 12); }
            case "exit" -> { disc(g, YELLOW); g.setColor(Color.WHITE); poly(g, true, 8, 3.5, 12, 8.5, 9.5, 8.5, 9.5, 12, 6.5, 12, 6.5, 8.5, 4, 8.5); }
            case "save" -> {
                g.setColor(BLUE); g.fillRoundRect(1, 1, 14, 14, 2, 2);
                g.setColor(Color.WHITE); g.fillRect(4, 2, 8, 5); g.fillRect(4, 10, 8, 5);
                g.setColor(BLUE); g.fillRect(9, 3, 2, 3);
            }
            case "print" -> {
                g.setColor(Color.WHITE); g.fillRect(4, 1, 8, 5); g.setColor(GREY); g.drawRect(4, 1, 8, 5);
                g.fillRoundRect(1, 6, 14, 6, 2, 2);
                g.setColor(Color.WHITE); g.fillRect(4, 10, 8, 5); g.setColor(GREY); g.drawRect(4, 10, 8, 5);
            }
            case "find", "findnext" -> {
                g.setColor(new Color(0x3F4A5A)); g.fillRoundRect(1, 6, 6, 9, 3, 3); g.fillRoundRect(9, 6, 6, 9, 3, 3);
                g.fillRect(2, 2, 4, 5); g.fillRect(10, 2, 4, 5); g.fillRect(6, 7, 4, 3);
                if (n.equals("findnext")) { g.setColor(GREEN); g.setStroke(s2); g.drawLine(12, 1, 12, 5); g.drawLine(10, 3, 14, 3); }
            }
            case "first" -> { tri(g, false, 2); tri(g, false, 8); g.fillRect(1, 3, 2, 10); }
            case "prev" -> tri(g, false, 5);
            case "next" -> tri(g, true, 5);
            case "last" -> { tri(g, true, 2); tri(g, true, 8); g.fillRect(13, 3, 2, 10); }
            case "session" -> {
                g.setColor(Color.WHITE); g.fillRect(2, 3, 12, 10); g.setColor(BLUE); g.drawRect(2, 3, 12, 10); g.fillRect(2, 3, 12, 3);
                g.setColor(YELLOW); poly(g, true, 12, 0, 13, 2.5, 16, 3, 13.5, 4.5, 14, 7, 12, 5.5, 10, 7, 10.5, 4.5, 8, 3, 11, 2.5);
            }
            case "shortcut" -> {
                g.setColor(Color.WHITE); g.fillRect(2, 2, 11, 11); g.setColor(GREY); g.drawRect(2, 2, 11, 11);
                g.setColor(BLUE); g.setStroke(s2); poly(g, false, 6, 10, 10, 6); poly(g, true, 8, 4.5, 11.5, 4.5, 11.5, 8);
            }
            case "help" -> { disc(g, YELLOW); g.setColor(Color.WHITE); g.setFont(new Font("Dialog", Font.BOLD, 11)); g.drawString("?", 5, 12); }
            case "layout" -> {
                g.setColor(Color.WHITE); g.fillRect(1, 2, 14, 11); g.setColor(GREY); g.drawRect(1, 2, 14, 11);
                g.setColor(new Color(0xD23B3B)); g.fillRect(3, 4, 4, 3); g.setColor(GREEN); g.fillRect(8, 4, 5, 3); g.setColor(BLUE); g.fillRect(3, 8, 10, 3);
            }
            case "exec" -> {
                g.setColor(Color.WHITE); g.fillOval(1, 1, 13, 13); g.setColor(GREY); g.setStroke(new BasicStroke(1.3f)); g.drawOval(1, 1, 13, 13);
                g.drawLine(7, 4, 7, 8); g.drawLine(7, 8, 10, 8);
                g.setColor(GREEN); g.setStroke(s2); poly(g, false, 8, 12, 10.5, 14.5, 15, 9);
            }
            case "display" -> {
                g.setColor(new Color(0x3F4A5A)); g.setStroke(new BasicStroke(1.6f));
                g.drawOval(1, 6, 6, 6); g.drawOval(9, 6, 6, 6); g.drawLine(7, 8, 9, 8); g.drawLine(1, 8, 3, 3); g.drawLine(15, 8, 13, 3);
            }
            case "edit" -> {
                g.setColor(YELLOW); poly(g, true, 11, 1, 15, 5, 6, 14, 2, 10);
                g.setColor(new Color(0x3F4A5A)); poly(g, true, 2, 10, 6, 14, 1, 15);
                g.setColor(new Color(0xD23B3B)); poly(g, true, 11, 1, 15, 5, 13.5, 6.5, 9.5, 2.5);
            }
            case "log" -> { sheet(g); g.setColor(BLUE); for (int y = 4; y <= 12; y += 2) g.drawLine(5, y, 11, y); }
            case "delete" -> {
                g.setColor(GREY); g.fillRect(3, 5, 10, 10); g.fillRect(2, 3, 12, 2); g.fillRect(6, 1, 4, 2);
                g.setColor(Color.WHITE); g.drawLine(6, 7, 6, 13); g.drawLine(8, 7, 8, 13); g.drawLine(10, 7, 10, 13);
            }
            case "status" -> { sheet(g); g.setColor(GREEN); g.fillOval(5, 3, 4, 4); g.setColor(YELLOW); g.fillOval(5, 9, 4, 4); }
            case "refresh" -> {
                g.setColor(GREEN); g.setStroke(s2); g.drawArc(2, 2, 12, 12, 40, 250); poly(g, true, 10, 1, 15, 3, 11, 7);
            }
            case "S" -> { g.setColor(GREEN); g.fillRoundRect(1, 1, 14, 14, 3, 3); g.setColor(Color.WHITE); g.setStroke(s2); poly(g, false, 4.5, 8.5, 7, 11, 11.5, 5.5); }
            case "E" -> { disc(g, new Color(0xD23B3B)); g.setColor(Color.WHITE); g.fillRect(7, 4, 2, 5); g.fillRect(7, 10, 2, 2); }
            case "W" -> {
                g.setColor(YELLOW); poly(g, true, 8, 1, 15, 14, 1, 14);
                g.setColor(Color.BLACK); g.fillRect(7, 6, 2, 4); g.fillRect(7, 11, 2, 2);
            }
            case "I" -> { disc(g, BLUE); g.setColor(Color.WHITE); g.fillRect(7, 7, 2, 5); g.fillRect(7, 4, 2, 2); }
            case "Q" -> { disc(g, BLUE); g.setColor(Color.WHITE); g.setFont(new Font("Dialog", Font.BOLD, 11)); g.drawString("?", 5, 12); }
            case "folder", "folderopen" -> {
                g.setColor(new Color(0xE2A81A)); g.fillRect(1, 3, 6, 3); g.fillRect(1, 5, 14, 9);
                g.setColor(new Color(0xFFD766));
                if (n.equals("folderopen")) poly(g, true, 3, 8, 16, 8, 14, 14, 1, 14); else g.fillRect(2, 6, 12, 7);
            }
            case "tx" -> {
                g.setColor(new Color(0x7FA7D9)); poly(g, true, 8, 1, 14, 4, 8, 7, 2, 4);
                g.setColor(BLUE); poly(g, true, 2, 4, 8, 7, 8, 15, 2, 11);
                g.setColor(new Color(0x1F4F8B)); poly(g, true, 14, 4, 8, 7, 8, 15, 14, 11);
            }
            case "fav" -> { g.setColor(YELLOW); poly(g, true, 8, 1, 10, 6, 15, 6, 11, 9.5, 12.5, 15, 8, 11.5, 3.5, 15, 5, 9.5, 1, 6, 6, 6); }
            case "led_g" -> disc(g, GREEN);
            case "led_y" -> disc(g, YELLOW);
            case "led_r" -> disc(g, new Color(0xD23B3B));
            case "block" -> { disc(g, new Color(0xD23B3B)); g.setColor(Color.WHITE); g.fillRect(4, 7, 8, 2); }
            case "arrow" -> { g.setColor(GREY); poly(g, true, 5, 4, 11, 8, 5, 12); }
            default -> { g.setColor(GREY); g.drawRect(2, 2, 11, 11); }
        }
    }
}
