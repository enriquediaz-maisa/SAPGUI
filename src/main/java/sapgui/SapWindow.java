package sapgui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/** One screen (dynpro) of a transaction. */
abstract class Screen {
    record Act(String id, String text, String icon, String tip, Runnable run) { }

    SapWindow win;
    JComponent view;

    abstract String title();
    abstract JComponent build();
    String tcode = "", program = "SAPLSMTR_NAVIGATION";
    String tcode() { return tcode; }
    String program() { return program; }
    String[] menus() { return new String[] {"Program", "Edit", "Goto"}; }
    List<Act> menuItems(String menu) { return List.of(); }
    List<Act> buttons() { return List.of(); }
    /** True when the view should fill the work area (lists, trees) instead of being scrolled. */
    boolean fill() { return false; }
    boolean canSave() { return false; }
    void shown() { }
    void onEnter() { }
    void onSave() { }
    void onBack() { win.pop(); }
    void onExit() { win.home(); }
    void onFKey(int vkey) { win.msg("E", "Function code cannot be selected"); }
}

/** Main session window (wnd[0]): menu bar, standard toolbar, title, application toolbar, work area, status bar. */
class SapWindow extends JFrame {
    static final List<SapWindow> ALL = new ArrayList<>();
    static SapWindow current;

    final int session;
    Screen screen;
    private final Deque<Screen> stack = new ArrayDeque<>();
    private final JMenuBar menuBar = new JMenuBar();
    private final JComboBox<String> cmd = new JComboBox<>();
    private final JTextField cmdField;
    private final JLabel title = new JLabel(" ");
    private final JPanel appBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
    private final JPanel work = new JPanel(new BorderLayout());
    private final JLabel status = new JLabel(" ");
    private final JLabel sysInfo = new JLabel();
    private final List<JButton> navButtons = new ArrayList<>();
    private JButton saveBtn;
    String statusType = "", statusText = "";

    SapWindow() {
        session = ALL.size() + 1;
        ALL.add(this); current = this;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { endSession(true); }
            @Override public void windowActivated(WindowEvent e) { current = SapWindow.this; }
        });
        setJMenuBar(menuBar);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));

        JPanel std = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 2)) {
            @Override protected void paintComponent(Graphics g) { Theme.gradient(g, getWidth(), getHeight(), Theme.BG_LIGHT, Theme.BG_DARK); }
        };
        std.add(Ui.tool("wnd[0]/tbar[0]/btn[0]", "ok", "Enter (Enter)", () -> vkey(0)));
        cmd.setEditable(true);
        cmd.setPreferredSize(new Dimension(190, 22));
        cmdField = (JTextField) cmd.getEditor().getEditorComponent();
        cmdField.setName("wnd[0]/tbar[0]/okcd");
        cmdField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.FIELD_LINE), BorderFactory.createEmptyBorder(0, 3, 0, 2)));
        std.add(cmd);
        std.add(Box.createHorizontalStrut(8));
        saveBtn = Ui.tool("wnd[0]/tbar[0]/btn[11]", "save", "Save (Ctrl+S)", () -> vkey(11));
        std.add(saveBtn);
        std.add(Box.createHorizontalStrut(6));
        navButtons.add(Ui.tool("wnd[0]/tbar[0]/btn[3]", "back", "Back (F3)", () -> vkey(3)));
        navButtons.add(Ui.tool("wnd[0]/tbar[0]/btn[15]", "exit", "Exit (Shift+F3)", () -> vkey(15)));
        navButtons.add(Ui.tool("wnd[0]/tbar[0]/btn[12]", "cancel", "Cancel (F12)", () -> vkey(12)));
        navButtons.forEach(std::add);
        std.add(Box.createHorizontalStrut(6));
        std.add(Ui.tool("wnd[0]/tbar[0]/btn[86]", "print", "Print (Ctrl+P)", () -> msg("S", "Function not available in this system")));
        std.add(Ui.tool("wnd[0]/tbar[0]/btn[71]", "find", "Find (Ctrl+F)", () -> msg("S", "Function not available in this system")));
        std.add(Ui.tool("wnd[0]/tbar[0]/btn[84]", "findnext", "Find Next (Ctrl+G)", null)).setEnabled(false);
        std.add(Box.createHorizontalStrut(6));
        for (String[] p : new String[][] {{"80", "first", "First Page (Ctrl+PgUp)"}, {"81", "prev", "Previous Page (PgUp)"}, {"82", "next", "Next Page (PgDn)"}, {"83", "last", "Last Page (Ctrl+PgDn)"}})
            std.add(Ui.tool("wnd[0]/tbar[0]/btn[" + p[0] + "]", p[1], p[2], null)).setEnabled(false);
        std.add(Box.createHorizontalStrut(6));
        std.add(Ui.tool("wnd[0]/tbar[0]/btn[90]", "session", "Create New Session", this::newSession));
        std.add(Ui.tool("wnd[0]/tbar[0]/btn[91]", "shortcut", "Generate Shortcut", () -> msg("S", "Function not available in this system")));
        std.add(Box.createHorizontalStrut(6));
        std.add(Ui.tool("wnd[0]/tbar[0]/btn[1]", "help", "Help (F1)", () -> msg("S", "No documentation available")));
        std.add(Ui.tool("wnd[0]/tbar[0]/btn[92]", "layout", "Customize Local Layout (Alt+F12)", null));

        JPanel titleBar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) { Theme.gradient(g, getWidth(), getHeight(), Color.WHITE, Theme.BG); }
        };
        title.setFont(Theme.TITLE); title.setForeground(Theme.TITLE_FG); title.setName("wnd[0]/titl");
        title.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        titleBar.add(title);
        titleBar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.LINE));

        appBar.setBackground(Theme.BG_LIGHT);
        appBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
        appBar.setMinimumSize(new Dimension(10, 30));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(std); north.add(titleBar); north.add(appBar);

        JPanel south = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) { Theme.gradient(g, getWidth(), getHeight(), Theme.BG_LIGHT, Theme.BG_DARK); }
        };
        south.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
        status.setName("wnd[0]/sbar"); status.setFont(Theme.FONT);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        sysInfo.setFont(Theme.FONT); sysInfo.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 10));
        south.add(status, BorderLayout.CENTER); south.add(sysInfo, BorderLayout.EAST);

        work.setBackground(Theme.BG);
        add(north, BorderLayout.NORTH); add(work, BorderLayout.CENTER); add(south, BorderLayout.SOUTH);
        setSize(1120, 740);
        setLocationByPlatform(true);
        display(new Screens.Login());
    }

    // ---- navigation -------------------------------------------------------------------------------

    private void display(Screen s) {
        screen = s;
        if (s.view == null) { s.win = this; s.view = s.build(); }
        work.removeAll();
        if (s.fill()) work.add(s.view, BorderLayout.CENTER);
        else {
            JScrollPane sp = new JScrollPane(s.view);
            sp.setBorder(null); sp.getViewport().setBackground(Theme.BG);
            work.add(sp, BorderLayout.CENTER);
        }
        s.shown();
        refresh();
        work.revalidate(); work.repaint();
        s.view.requestFocusInWindow();
    }

    /** Rebuilds title, menus and toolbars from the current screen. */
    void refresh() {
        boolean in = Main.user != null && !(screen instanceof Screens.Login);
        setTitle(screen.title());
        title.setText(screen.title());
        appBar.removeAll();
        for (Screen.Act a : screen.buttons()) appBar.add(Ui.push(a.id(), a.text(), a.icon(), a.tip(), () -> { clearMsg(); a.run().run(); }));
        if (appBar.getComponentCount() == 0) appBar.add(Box.createVerticalStrut(22));
        appBar.revalidate(); appBar.repaint();
        saveBtn.setEnabled(screen.canSave());
        boolean nav = !(screen instanceof Screens.EasyAccess);
        navButtons.forEach(b -> b.setEnabled(nav));
        menuBar.removeAll();
        for (String m : screen.menus()) {
            JMenu menu = new JMenu(m); menu.setFont(Theme.FONT);
            for (Screen.Act a : screen.menuItems(m)) menu.add(item(a.text(), a.run()));
            if (menu.getItemCount() == 0) menu.add(item(m.equals(screen.menus()[0]) ? "Exit" : "Cancel", () -> vkey(m.equals(screen.menus()[0]) ? 15 : 12)));
            menuBar.add(menu);
        }
        JMenu sys = new JMenu("System"); sys.setFont(Theme.FONT);
        sys.add(item("Create Session", this::newSession));
        sys.add(item("End Session", () -> endSession(true)));
        sys.addSeparator();
        sys.add(item("User Profile", null)); sys.add(item("Services", null)); sys.add(item("Own Spool Requests", null)); sys.add(item("Own Jobs", null));
        sys.addSeparator();
        sys.add(item("Status...", this::showStatus));
        sys.add(item("Log Off", this::logOff));
        for (int i = 0; i < sys.getItemCount(); i++) if (sys.getItem(i) != null && i >= 3 && i <= 6) sys.getItem(i).setEnabled(false);
        sys.setEnabled(in);
        menuBar.add(sys);
        JMenu help = new JMenu("Help"); help.setFont(Theme.FONT);
        for (String h : new String[] {"Application Help", "SAP Library", "Glossary", "Release Notes", "SAP Service Marketplace", "Settings..."})
            help.add(item(h, () -> msg("S", "No documentation available")));
        menuBar.add(help);
        menuBar.revalidate(); menuBar.repaint();
        sysInfo.setText(Main.SID + " (" + session + ") " + (in ? Main.client : "000") + "   |   " + Main.HOST + "   |   INS  ");
    }

    private JMenuItem item(String text, Runnable r) {
        JMenuItem i = new JMenuItem(text); i.setFont(Theme.FONT);
        if (r != null) i.addActionListener(e -> r.run());
        return i;
    }

    void home() { stack.clear(); display(new Screens.EasyAccess()); }
    void push(Screen s) { s.tcode = screen.tcode(); s.program = screen.program(); stack.push(screen); display(s); }
    void pop() { if (stack.isEmpty()) home(); else display(stack.pop()); }

    void start(String tcode) {
        Screen s = switch (tcode) {
            case "F110" -> new F110Screen();
            case "FBL1N" -> new Screens.Fbl1n();
            case "BNK_APP" -> new Screens.BnkApp();
            case "SESSION_MANAGER", "SMEN", "" -> new Screens.EasyAccess();
            default -> null;
        };
        if (s == null) { msg("E", "Transaction " + tcode + " does not exist"); return; }
        stack.clear();
        clearMsg();
        display(s);
    }

    private void command(String raw) {
        String c = raw.trim().toUpperCase();
        if (((javax.swing.DefaultComboBoxModel<String>) cmd.getModel()).getIndexOf(c) < 0) cmd.insertItemAt(c, 0);
        cmd.setSelectedItem(""); cmdField.setText("");
        if (Main.user == null || screen instanceof Screens.Login) { screen.onEnter(); return; }
        if (c.equals("/NEX")) { for (SapWindow w : new ArrayList<>(ALL)) w.endSession(false); }
        else if (c.equals("/I")) endSession(false);
        else if (c.equals("/N")) { clearMsg(); home(); }
        else if (c.startsWith("/N")) start(c.substring(2));
        else if (c.startsWith("/O")) { SapWindow w = newSession(); if (c.length() > 2) w.start(c.substring(2)); }
        else start(c);
    }

    /** Virtual key as in SAP GUI Scripting: 0 Enter, 3 Back, 8 Execute, 11 Save, 12 Cancel, 15 Exit. */
    void vkey(int k) {
        if (!Popup.OPEN.isEmpty()) return;
        String c = cmdField.getText();
        if (k == 0 && !c.isBlank()) { command(c); return; }
        clearMsg();
        switch (k) {
            case 0 -> screen.onEnter();
            case 3, 12 -> { if (!(screen instanceof Screens.EasyAccess)) screen.onBack(); }
            case 15 -> { if (!(screen instanceof Screens.EasyAccess)) screen.onExit(); }
            case 11 -> { if (screen.canSave()) screen.onSave(); else msg("E", "Function code cannot be selected"); }
            default -> screen.onFKey(k);
        }
    }

    // ---- sessions ---------------------------------------------------------------------------------

    SapWindow newSession() {
        if (Main.user == null) return this;
        if (ALL.size() >= 6) { msg("E", "Maximum number of sessions reached"); return this; }
        SapWindow w = new SapWindow();
        w.home();
        w.setVisible(true);
        return w;
    }

    void endSession(boolean ask) {
        if (ask && ALL.size() == 1 && Main.user != null && !(screen instanceof Screens.Login)) { logOff(); return; }
        ALL.remove(this);
        dispose();
        if (ALL.isEmpty()) Main.loggedOff();
    }

    private void logOff() {
        int r = Popup.confirm(this, "Log Off", new String[] {"Unsaved data will be lost.", "Do you want to log off?"}, "Yes", "No");
        if (r == 0) for (SapWindow w : new ArrayList<>(ALL)) w.endSession(false);
    }

    private void showStatus() {
        Popup p = new Popup(this, "System: Status");
        String[][] rows = {{"Client", Main.client}, {"User", Main.user}, {"Language", "EN"}, {"System date", Data.d(Data.TODAY)},
                {"Transaction", screen.tcode().isEmpty() ? "SESSION_MANAGER" : screen.tcode()}, {"Program (screen)", screen.program()},
                {"Component version", "SAP ECC 6.0"}, {"SAP_BASIS", "740"}, {"SAP_APPL", "617 (EHP7 FOR SAP ERP 6.0)"},
                {"Database system", "ORACLE"}, {"Host name", Main.HOST}, {"System ID", Main.SID}, {"Unicode system", "Yes"}};
        p.body.box("Usage data / SAP System data", 0, 0, 62, rows.length + 1);
        for (int i = 0; i < rows.length; i++) {
            p.body.label(rows[i][0], 0, i + 1, 22);
            SapField f = p.body.field(p.wnd + "/usr/txtSTATUS-" + i, 23, i + 1, 36, false);
            f.setText(rows[i][1]); f.editable(false);
        }
        p.button("tbar[0]/btn[0]", "", "ok", "Continue (Enter)", 0, true, null);
        p.open();
    }

    // ---- status bar -------------------------------------------------------------------------------

    void msg(String type, String text) {
        statusType = type; statusText = text;
        status.setIcon(Theme.icon(type));
        status.setText(text);
    }

    void clearMsg() { statusType = ""; statusText = ""; status.setIcon(null); status.setText(" "); }
}
