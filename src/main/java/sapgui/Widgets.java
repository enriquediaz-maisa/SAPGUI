package sapgui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

/** Entry field: white with a thin blue border, greyed when display-only, tick mark when required and empty. */
class SapField extends JTextField {
    boolean required;
    SapField(int len, boolean upper) {
        setFont(Theme.FONT);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.FIELD_LINE), BorderFactory.createEmptyBorder(0, 3, 0, 2)));
        setDocument(new PlainDocument() {
            @Override public void insertString(int off, String s, AttributeSet a) throws BadLocationException {
                if (s == null) return;
                if (upper) s = s.toUpperCase();
                int room = len - getLength();
                if (room <= 0) return;
                super.insertString(off, s.length() > room ? s.substring(0, room) : s, a);
            }
        });
        setDisabledTextColor(Color.BLACK);
    }
    void editable(boolean e) { setEditable(e); setBackground(e ? Color.WHITE : Theme.RO); setFocusable(e); }
    String val() { return getText().trim(); }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (required && isEditable() && getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = (getHeight() - 9) / 2;
            g2.setColor(Theme.FIELD_LINE); g2.drawRect(4, y, 9, 9);
            g2.setColor(new Color(0x3C9A3C)); g2.drawPolyline(new int[] {6, 8, 11}, new int[] {y + 5, y + 7, y + 2}, 3);
            g2.dispose();
        }
    }
}

/** Field label with the thin leader line running to the field. */
class SapLabel extends JLabel {
    SapLabel(String t) { super(t); setFont(Theme.FONT); }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Theme.LINE);
        g.drawLine(0, getHeight() - 2, getWidth() - 4, getHeight() - 2);
    }
}

/** Screen painter style canvas: controls are placed on a character grid like a dynpro. */
class Dyn extends JPanel {
    static final int CW = 7, RH = 23, X0 = 12, Y0 = 10;
    private int maxX, maxY;

    Dyn() { super(null); setBackground(Theme.BG); }

    <T extends JComponent> T put(T c, int col, int row, int wCols, int hRows) {
        c.setBounds(X0 + col * CW, Y0 + row * RH, wCols * CW, hRows * RH - 3);
        add(c, 0);
        maxX = Math.max(maxX, c.getX() + c.getWidth());
        maxY = Math.max(maxY, c.getY() + c.getHeight());
        return c;
    }

    JLabel label(String t, int col, int row, int w) { return put(new SapLabel(t), col, row, w, 1); }

    JLabel text(String t, int col, int row, int w) {
        JLabel l = new JLabel(t); l.setFont(Theme.FONT);
        return put(l, col, row, w, 1);
    }

    JLabel header(String t, int col, int row, int w) {
        JLabel l = new JLabel(" " + t); l.setFont(Theme.FONT); l.setOpaque(true); l.setBackground(Theme.HDR);
        l.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        return put(l, col, row, w, 1);
    }

    SapField field(String id, int col, int row, int len) { return field(id, col, row, len, true); }

    SapField field(String id, int col, int row, int len, boolean upper) {
        SapField f = new SapField(len, upper);
        f.setName(id);
        return put(f, col, row, Math.max(len + 2, 4), 1);
    }

    JPasswordField password(String id, int col, int row, int len) {
        JPasswordField f = new JPasswordField();
        f.setName(id);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.FIELD_LINE), BorderFactory.createEmptyBorder(0, 3, 0, 2)));
        return put(f, col, row, len + 2, 1);
    }

    JCheckBox check(String id, String t, int col, int row, int w) {
        JCheckBox c = new JCheckBox(t); c.setName(id); c.setOpaque(false); c.setFont(Theme.FONT); c.setFocusPainted(false);
        return put(c, col, row, w, 1);
    }

    JRadioButton radio(String id, String t, int col, int row, int w) {
        JRadioButton c = new JRadioButton(t); c.setName(id); c.setOpaque(false); c.setFont(Theme.FONT); c.setFocusPainted(false);
        return put(c, col, row, w, 1);
    }

    /** Group box with a title bar; placed underneath the controls added before or after it. */
    void box(String title, int col, int row, int w, int h) {
        JPanel b = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.BG_LIGHT); g.fillRect(0, 0, getWidth(), getHeight());
                Theme.gradient(g, getWidth(), 20, Theme.BG_LIGHT, Theme.BG_DARK);
                g.setColor(Theme.LINE); g.drawRect(0, 0, getWidth() - 1, getHeight() - 1); g.drawLine(0, 20, getWidth(), 20);
                g.setColor(Theme.TITLE_FG); g.setFont(Theme.FONT);
                ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.drawString(title, 8, 15);
            }
        };
        b.setBounds(X0 + col * CW - 8, Y0 + row * RH - 2, w * CW + 16, h * RH + 6);
        add(b);
        maxX = Math.max(maxX, b.getX() + b.getWidth());
        maxY = Math.max(maxY, b.getY() + b.getHeight());
    }

    @Override public Dimension getPreferredSize() { return new Dimension(maxX + 16, maxY + 16); }
}

/** Tabstrip control. */
class Tabs extends JPanel {
    private final JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final List<JButton> buttons = new ArrayList<>();
    int selected;
    IntConsumer onSelect = i -> { };

    Tabs() {
        super(new BorderLayout());
        setOpaque(false);
        head.setOpaque(false);
        body.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        add(head, BorderLayout.NORTH); add(body, BorderLayout.CENTER);
    }

    void add(String id, String title, JComponent content) {
        int idx = buttons.size();
        JButton b = new JButton(title) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                boolean sel = selected == idx;
                int top = sel ? 0 : 3;
                g2.setColor(sel ? Theme.BG : Theme.BG_DARK);
                g2.fillRoundRect(0, top, getWidth() - 2, getHeight() + 6, 8, 8);
                g2.setColor(Theme.LINE);
                g2.drawRoundRect(0, top, getWidth() - 2, getHeight() + 6, 8, 8);
                if (!sel) g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.setColor(Color.BLACK); g2.setFont(sel ? Theme.BOLD : Theme.FONT);
                int tw = g2.getFontMetrics().stringWidth(getText());
                g2.drawString(getText(), (getWidth() - 2 - tw) / 2, top + (getHeight() - top) / 2 + 5);
            }
        };
        b.setName(id);
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(Math.max(90, b.getFontMetrics(Theme.BOLD).stringWidth(title) + 28), 24));
        b.addActionListener(e -> select(idx));
        buttons.add(b); head.add(b);
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null); sp.getViewport().setBackground(Theme.BG);
        body.add(sp, String.valueOf(idx));
    }

    void select(int idx) {
        selected = idx;
        cards.show(body, String.valueOf(idx));
        head.repaint();
        onSelect.accept(idx);
    }
}

/** Buttons and tables shared by all screens. */
final class Ui {
    private Ui() { }

    /** Flat icon button of the standard toolbar. */
    static JButton tool(String id, String icon, String tip, Runnable action) {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                if (isEnabled() && getModel().isRollover()) {
                    g.setColor(getModel().isPressed() ? Theme.BG_DARK : Theme.BG_LIGHT); g.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
                    g.setColor(Theme.LINE); g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
                Theme.icon(icon, isEnabled() ? 1f : 0.3f).paintIcon(this, g, (getWidth() - 16) / 2, (getHeight() - 16) / 2);
            }
        };
        b.setName(id); b.setToolTipText(tip);
        b.setPreferredSize(new Dimension(24, 24));
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusable(false); b.setRolloverEnabled(true);
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    /** Push button of the application toolbar / dialogs. */
    static JButton push(String id, String text, String icon, String tip, Runnable action) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                boolean down = getModel().isPressed();
                g2.setPaint(new java.awt.GradientPaint(0, 0, down ? Theme.BG_DARK : Color.WHITE, 0, getHeight(), down ? Theme.BG : Theme.BG_DARK));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);
                g2.setColor(getModel().isRollover() || isFocusOwner() ? Theme.TITLE_FG : Theme.FIELD_LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);
                int x = 6;
                if (icon != null) { Theme.icon(icon, isEnabled() ? 1f : 0.3f).paintIcon(this, g2, x, (getHeight() - 16) / 2); x += 20; }
                g2.setColor(isEnabled() ? Color.BLACK : Color.GRAY); g2.setFont(Theme.FONT);
                g2.drawString(getText(), x, getHeight() / 2 + 4);
            }
        };
        b.setName(id); b.setToolTipText(tip);
        int tw = text.isEmpty() ? 0 : b.getFontMetrics(Theme.FONT).stringWidth(text) + 8;
        b.setPreferredSize(new Dimension(12 + (icon != null ? 18 : 0) + tw, 22));
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false); b.setRolloverEnabled(true);
        b.setMargin(new Insets(0, 0, 0, 0));
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    /** ALV-style grid. Icon names in a cell value prefixed with '@' are drawn as icons. */
    static JTable table(String id, String[] cols, int[] widths, IntFunction<Color> rowColor) {
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(m);
        t.setName(id);
        t.setFont(Theme.FONT); t.setRowHeight(20); t.setGridColor(Theme.GRID); t.setShowGrid(true);
        t.setSelectionBackground(Theme.SEL); t.setSelectionForeground(Color.BLACK);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setFillsViewportHeight(true); t.setBackground(Color.WHITE);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setPreferredSize(new Dimension(10, 22));
        t.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, false, false, r, c);
                l.setBackground(Theme.HDR); l.setFont(Theme.FONT);
                l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.LINE), BorderFactory.createEmptyBorder(0, 4, 0, 4)));
                return l;
            }
        });
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, s, false, r, c);
                String txt = v == null ? "" : v.toString();
                Icon ic = null;
                if (txt.startsWith("@")) { ic = Theme.icon(txt.substring(1)); txt = ""; }
                l.setIcon(ic); l.setText(txt);
                String h = tb.getColumnName(c);
                boolean num = h.contains("Amount") || h.equals("Items") || h.equals("Payments");
                l.setHorizontalAlignment(ic != null ? SwingConstants.CENTER : num ? SwingConstants.RIGHT : SwingConstants.LEFT);
                l.setFont(h.equals("Message text") ? Theme.MONO : Theme.FONT);
                Color fg = rowColor == null ? null : rowColor.apply(r);
                l.setForeground(fg == null ? Color.BLACK : fg);
                l.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                return l;
            }
        });
        for (int i = 0; i < widths.length; i++) t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        return t;
    }

    static void fill(JTable t, List<Object[]> rows) {
        DefaultTableModel m = (DefaultTableModel) t.getModel();
        m.setRowCount(0);
        for (Object[] r : rows) m.addRow(r);
    }

    static JScrollPane scroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }

    static void onDoubleClick(JTable t, IntConsumer handler) {
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && t.getSelectedRow() >= 0) handler.accept(t.getSelectedRow());
            }
        });
        t.putClientProperty("dbl", handler);
    }
}

/** Modal dialog box (wnd[1], wnd[2], ...). */
class Popup extends JDialog {
    static final Deque<Popup> OPEN = new ArrayDeque<>();
    final Dyn body = new Dyn();
    final String wnd;
    private final JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 5));
    private final JLabel status = new JLabel(" ");
    private JButton dflt;
    int result = -1;

    Popup(SapWindow owner, String title) {
        super(owner, title, true);
        wnd = "wnd[" + (OPEN.size() + 1) + "]";
        setName(wnd);
        JPanel south = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG_DARK);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
        status.setFont(Theme.FONT); status.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        status.setName(wnd + "/sbar"); status.setVisible(false);
        south.add(bar, BorderLayout.NORTH); south.add(status, BorderLayout.SOUTH);
        getContentPane().setBackground(Theme.BG);
        add(body, BorderLayout.CENTER); add(south, BorderLayout.SOUTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e -> { if (dflt != null) dflt.doClick(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        AbstractAction cancel = new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { close(-1); } };
        getRootPane().registerKeyboardAction(cancel, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(cancel, KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Adds a button to the dialog's button row; {@code check} may veto closing by returning an error text. */
    JButton button(String id, String text, String icon, String tip, int res, boolean isDefault, Supplier<String> check) {
        JButton b = Ui.push(wnd + "/" + id, text, icon, tip, null);
        b.addActionListener(e -> {
            String err = check == null ? null : check.get();
            if (err != null) { status.setIcon(Theme.icon("E")); status.setText(err); status.setVisible(true); pack(); return; }
            close(res);
        });
        bar.add(b);
        if (isDefault) dflt = b;
        return b;
    }

    void close(int res) { result = res; dispose(); }
    void pressDefault() { if (dflt != null) dflt.doClick(); }

    int open() {
        pack();
        setMinimumSize(new Dimension(Math.max(getWidth(), 330), getHeight()));
        setLocationRelativeTo(getOwner());
        OPEN.push(this);
        try { setVisible(true); } finally { OPEN.remove(this); }
        return result;
    }

    private static Popup message(SapWindow w, String title, String icon, String... lines) {
        Popup p = new Popup(w, title);
        JLabel ic = new JLabel(Theme.icon(icon));
        p.body.put(ic, 0, 0, 4, 1);
        int width = 30;
        for (String l : lines) width = Math.max(width, l.length() + 4);
        for (int i = 0; i < lines.length; i++) p.body.text(lines[i], 5, i, width).setName(p.wnd + "/usr/txtMESSTXT" + (i + 1));
        return p;
    }

    /** Yes/No(/Cancel) question. Returns the option index, or -1 on cancel. */
    static int confirm(SapWindow w, String title, String[] lines, String... options) {
        Popup p = message(w, title, "Q", lines);
        for (int i = 0; i < options.length; i++)
            p.button("usr/btnSPOP-OPTION" + (i + 1), options[i], null, options[i], i, i == 0, null);
        p.button("usr/btnSPOP-OPTION_CAN", "Cancel", "cancel", "Cancel (F12)", -1, false, null);
        return p.open();
    }

    static void info(SapWindow w, String title, String... lines) {
        Popup p = message(w, title, "I", lines);
        p.button("tbar[0]/btn[0]", "", "ok", "Continue (Enter)", 0, true, null);
        p.open();
    }
}
