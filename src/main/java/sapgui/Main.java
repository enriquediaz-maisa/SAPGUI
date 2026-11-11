package sapgui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point. Options: --direct (skip the logon pad), --user NAME (log on automatically),
 * --tcode CODE (start a transaction), --scripting PORT (enable the local scripting interface).
 */
public final class Main {
    static final String SID = "DEV", HOST = "sapeccdev01";
    static String user, client = "100";
    private static JFrame pad;

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        boolean direct = false;
        String autoUser = null, tcode = null;
        int port = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--direct" -> direct = true;
                case "--user" -> autoUser = args[++i];
                case "--tcode" -> tcode = args[++i].toUpperCase();
                case "--scripting" -> port = Integer.parseInt(args[++i]);
                default -> { System.err.println("Usage: [--direct] [--user NAME] [--tcode CODE] [--scripting PORT]"); return; }
            }
        }
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        final boolean skipPad = direct || autoUser != null;
        final String u = autoUser, t = tcode;
        SwingUtilities.invokeAndWait(() -> {
            Theme.init();
            installKeys();
            if (skipPad) openSession(u, t); else showPad();
        });
        if (port > 0) ScriptServer.start(port);
    }

    private static void installKeys() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || !Popup.OPEN.isEmpty()) return false;
            Window w = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (!(w instanceof SapWindow sw)) return false;
            int k = switch (e.getKeyCode()) {
                case KeyEvent.VK_ENTER -> 0;
                case KeyEvent.VK_F3 -> e.isShiftDown() ? 15 : 3;
                case KeyEvent.VK_F12 -> 12;
                case KeyEvent.VK_S -> e.isControlDown() ? 11 : -1;
                case KeyEvent.VK_F1 -> e.isShiftDown() ? 13 : -1;
                case KeyEvent.VK_F2 -> e.isShiftDown() ? 14 : 2;
                case KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9 -> e.isShiftDown() ? -1 : e.getKeyCode() - KeyEvent.VK_F1 + 1;
                default -> -1;
            };
            if (k < 0) return false;
            e.consume();
            SwingUtilities.invokeLater(() -> sw.vkey(k));
            return true;
        });
    }

    static void openSession(String autoUser, String tcode) {
        SapWindow w = new SapWindow();
        if (autoUser != null) { user = autoUser.toUpperCase(); w.home(); if (tcode != null) w.start(tcode); }
        w.setVisible(true);
    }

    static void loggedOff() {
        user = null;
        if (pad == null) System.exit(0);
        pad.toFront();
    }

    private static void showPad() {
        pad = new JFrame("SAP Logon 740");
        pad.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 5));
        bar.setBackground(Theme.BG_LIGHT);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
        JTable t = Ui.table("logon/connections", new String[] {"Name", "System Description", "SID", "Group/Server", "Instance No.", "Message Server", "Router"},
                new int[] {230, 250, 50, 130, 90, 120, 80}, null);
        Ui.fill(t, List.of(
                new Object[] {"ECC 6.0 EHP7 - Finance PoC [DEV]", "Sandbox for payment automation (fictitious data)", SID, HOST, "00", "", ""},
                new Object[] {"ECC 6.0 EHP7 - Quality [QAS]", "Not reachable from this network", "QAS", "sapeccqas01", "00", "", ""}));
        t.setRowSelectionInterval(0, 0);
        Runnable logon = () -> {
            if (t.getSelectedRow() != 0) {
                javax.swing.JOptionPane.showMessageDialog(pad, "partner 'sapeccqas01:3200' not reached\n\nWSAECONNREFUSED: Connection refused", "SAP GUI for Java", javax.swing.JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (SapWindow.ALL.isEmpty()) openSession(null, null); else SapWindow.ALL.get(0).toFront();
        };
        bar.add(Ui.push("logon/btnLogOn", "Log On", "exec", "Log On", logon));
        bar.add(Ui.push("logon/btnVariable", "Variable Logon...", null, "Variable Logon", null));
        bar.add(Ui.push("logon/btnNew", "New", null, "New connection", null));
        bar.add(Ui.push("logon/btnEdit", "Edit", "edit", "Edit connection", null));
        bar.add(Ui.push("logon/btnDelete", "Delete", "delete", "Delete connection", null));
        Ui.onDoubleClick(t, r -> logon.run());
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG);
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.add(Ui.scroll(t));
        pad.add(bar, BorderLayout.NORTH);
        pad.add(body, BorderLayout.CENTER);
        pad.setSize(980, 420);
        pad.setLocationByPlatform(true);
        pad.setVisible(true);
    }
}
