package sapgui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

final class Screens {
    private Screens() { }

    // ---- logon screen -----------------------------------------------------------------------------

    static final class Login extends Screen {
        private SapField client, user, lang;
        private JPasswordField pw;

        @Override String title() { return "SAP"; }
        @Override String program() { return "SAPMSYST"; }
        @Override String[] menus() { return new String[] {"User"}; }
        @Override List<Act> buttons() {
            return List.of(new Act("wnd[0]/tbar[1]/btn[5]", "New password", null, "New password (F5)", () -> win.msg("E", "Enter the user name and current password first")));
        }

        @Override JComponent build() {
            Dyn d = new Dyn();
            d.label("Client", 2, 1, 20);
            client = d.field("wnd[0]/usr/txtRSYST-MANDT", 23, 1, 3); client.setText(Main.client);
            d.label("User", 2, 3, 20);
            user = d.field("wnd[0]/usr/txtRSYST-BNAME", 23, 3, 12); user.required = true;
            d.label("Password", 2, 4, 20);
            pw = d.password("wnd[0]/usr/pwdRSYST-BCODE", 23, 4, 12);
            d.label("Logon Language", 2, 6, 20);
            lang = d.field("wnd[0]/usr/txtRSYST-LANGU", 23, 6, 2); lang.setText("EN");
            d.box("Information", 60, 1, 60, 5);
            d.text("Northgate Group - ECC 6.0 EHP7 sandbox (" + Main.SID + ")", 61, 2, 58);
            d.text("Proof-of-concept system. Fictitious data only.", 61, 3, 58);
            d.text("Clients: 100 Finance PoC", 61, 4, 58);
            return d;
        }

        @Override void shown() { user.requestFocusInWindow(); }
        @Override void onBack() { win.endSession(false); }
        @Override void onExit() { win.endSession(false); }

        @Override void onEnter() {
            if (!client.val().equals("100")) { win.msg("E", "Client " + client.val() + " is not available in this system"); return; }
            if (user.val().isEmpty()) { win.msg("E", "Enter a user name"); return; }
            if (pw.getPassword().length == 0) { win.msg("E", "Name or password is incorrect (repeat logon)"); return; }
            Main.user = user.val(); Main.client = client.val();
            win.home();
        }
    }

    // ---- SAP Easy Access --------------------------------------------------------------------------

    static final class EasyAccess extends Screen {
        private JTree tree;

        @Override String title() { return "SAP Easy Access"; }
        @Override String tcode() { return "SESSION_MANAGER"; }
        @Override String[] menus() { return new String[] {"Menu", "Edit", "Favorites", "Extras"}; }
        @Override boolean fill() { return true; }
        @Override List<Act> menuItems(String m) {
            return m.equals("Menu") ? List.of(new Act("", "User Menu", null, "", () -> { }), new Act("", "SAP Menu", null, "", () -> { }), new Act("", "Log Off", null, "", () -> win.endSession(true))) : List.of();
        }
        @Override List<Act> buttons() {
            Runnable no = () -> win.msg("S", "Function not available in this system");
            return List.of(new Act("wnd[0]/tbar[1]/btn[25]", "", "session", "User Menu (Ctrl+F10)", no), new Act("wnd[0]/tbar[1]/btn[26]", "", "folder", "SAP Menu (Ctrl+F11)", no),
                    new Act("wnd[0]/tbar[1]/btn[27]", "", "tx", "SAP Business Workplace (Ctrl+F12)", no), new Act("wnd[0]/tbar[1]/btn[6]", "", "fav", "Add to Favorites (Ctrl+Shift+F6)", no),
                    new Act("wnd[0]/tbar[1]/btn[7]", "", "delete", "Delete Favorites (Shift+F2)", no), new Act("wnd[0]/tbar[1]/btn[8]", "", "edit", "Change Favorites (Ctrl+Shift+F3)", no));
        }

        private static DefaultMutableTreeNode n(String text, DefaultMutableTreeNode... kids) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(text);
            for (DefaultMutableTreeNode k : kids) node.add(k);
            return node;
        }

        @Override JComponent build() {
            DefaultMutableTreeNode root = n("root",
                    n("Favorites", n("F110 - Parameters for Automatic Payment"), n("FBL1N - Vendor Line Items"), n("BNK_APP - Approve Payments")),
                    n("SAP Menu", n("Office"), n("Cross-Application Components"), n("Logistics"),
                            n("Accounting",
                                    n("Financial Accounting", n("General Ledger"), n("Accounts Receivable"),
                                            n("Accounts Payable", n("Document Entry"), n("Document"),
                                                    n("Account", n("FK10N - Display Balances"), n("FBL1N - Display/Change Line Items")),
                                                    n("Master Records"),
                                                    n("Periodic Processing", n("F110 - Payments"), n("F150 - Dunning")),
                                                    n("Reporting"), n("Information System")),
                                            n("Banks"), n("Fixed Assets")),
                                    n("Financial Supply Chain Management",
                                            n("Bank Communication Management", n("Processing", n("BNK_APP - Approve Payments"), n("BNK_MONI - Batch and Payment Monitor")))),
                                    n("Controlling"), n("Project System")),
                            n("Human Resources"), n("Information Systems"), n("Tools")));
            tree = new JTree(root);
            tree.setName("wnd[0]/usr/cntlIMAGE_CONTAINER/shellcont/shell/shellcont[0]/shell");
            tree.setRootVisible(false); tree.setShowsRootHandles(true); tree.setRowHeight(20);
            tree.setBackground(Theme.BG_LIGHT);
            tree.setCellRenderer(new DefaultTreeCellRenderer() {
                @Override public Component getTreeCellRendererComponent(JTree t, Object v, boolean s, boolean e, boolean leaf, int r, boolean f) {
                    JLabel l = (JLabel) super.getTreeCellRendererComponent(t, v, s, e, leaf, r, false);
                    String txt = v.toString();
                    l.setIcon(Theme.icon(txt.equals("Favorites") ? "fav" : txt.contains(" - ") ? "tx" : e ? "folderopen" : "folder"));
                    l.setFont(Theme.FONT); l.setForeground(Color.BLACK);
                    setBackgroundNonSelectionColor(Theme.BG_LIGHT); setBackgroundSelectionColor(Theme.SEL); setTextSelectionColor(Color.BLACK);
                    return l;
                }
            });
            for (int i = 0; i < 2; i++) tree.expandRow(i == 0 ? 0 : 4);
            tree.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) run(); }
            });
            JScrollPane left = new JScrollPane(tree);
            left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE));
            left.setPreferredSize(new java.awt.Dimension(520, 10));
            JPanel p = new JPanel(new BorderLayout());
            p.add(left, BorderLayout.WEST);
            JPanel right = new JPanel(); right.setBackground(Theme.BG);
            p.add(right, BorderLayout.CENTER);
            return p;
        }

        private void run() {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            String txt = path.getLastPathComponent().toString();
            if (txt.contains(" - ")) win.start(txt.substring(0, txt.indexOf(" - ")));
        }

        @Override void onEnter() { run(); }
    }

    // ---- generic ALV list -------------------------------------------------------------------------

    static class ListScreen extends Screen {
        final String title;
        final String[] cols; final int[] widths;
        Supplier<List<Object[]>> rows;
        IntFunction<Color> rowColor;
        IntConsumer onDouble;
        List<String> headerLines = List.of();
        List<Act> acts = new ArrayList<>();
        JTable table;

        ListScreen(String title, String[] cols, int[] widths, Supplier<List<Object[]>> rows) {
            this.title = title; this.cols = cols; this.widths = widths; this.rows = rows;
        }

        @Override String title() { return title; }
        @Override String[] menus() { return new String[] {"List", "Edit", "Goto", "Settings"}; }
        @Override boolean fill() { return true; }
        @Override List<Act> buttons() { return acts; }

        @Override JComponent build() {
            table = Ui.table("wnd[0]/usr/cntlGRID1/shellcont/shell", cols, widths, r -> rowColor == null ? null : rowColor.apply(r));
            if (onDouble != null) Ui.onDoubleClick(table, onDouble);
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(Theme.BG);
            p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            if (!headerLines.isEmpty()) {
                Dyn h = new Dyn();
                for (int i = 0; i < headerLines.size(); i++) h.text(headerLines.get(i), 0, i, 120);
                p.add(h, BorderLayout.NORTH);
            }
            p.add(Ui.scroll(table), BorderLayout.CENTER);
            return p;
        }

        void reload() { Ui.fill(table, rows.get()); }
        @Override void shown() { reload(); }
        @Override void onEnter() { if (onDouble != null && table.getSelectedRow() >= 0) onDouble.accept(table.getSelectedRow()); }
        @Override void onFKey(int k) { if (k == 2) onEnter(); else super.onFKey(k); }
        int selected() {
            if (table.getSelectedRow() < 0) win.msg("E", "Position the cursor on a valid line");
            return table.getSelectedRow();
        }
    }

    // ---- FBL1N ------------------------------------------------------------------------------------

    static final class Fbl1n extends Screen {
        private SapField vFrom, vTo, cc, key;
        private JRadioButton open, cleared, all;

        @Override String title() { return "Vendor Line Item Display"; }
        @Override String tcode() { return "FBL1N"; }
        @Override String program() { return "RFITEMAP"; }
        @Override List<Act> buttons() {
            return List.of(new Act("wnd[0]/tbar[1]/btn[8]", "", "exec", "Execute (F8)", this::execute));
        }

        @Override JComponent build() {
            Dyn d = new Dyn();
            d.box("Vendor selection", 1, 0, 100, 3);
            d.label("Vendor account", 2, 1, 26);
            vFrom = d.field("wnd[0]/usr/ctxtKD_LIFNR-LOW", 30, 1, 10);
            d.text("to", 46, 1, 4);
            vTo = d.field("wnd[0]/usr/ctxtKD_LIFNR-HIGH", 51, 1, 10);
            d.label("Company code", 2, 2, 26);
            cc = d.field("wnd[0]/usr/ctxtKD_BUKRS-LOW", 30, 2, 4);
            d.box("Line item selection", 1, 4, 100, 6);
            d.text("Status", 2, 5, 20).setFont(Theme.BOLD);
            open = d.radio("wnd[0]/usr/radX_OPSEL", "Open items", 2, 6, 24);
            d.label("Open at key date", 30, 6, 22);
            key = d.field("wnd[0]/usr/ctxtPA_STIDA", 53, 6, 10); key.setText(Data.d(Data.TODAY));
            cleared = d.radio("wnd[0]/usr/radX_CLSEL", "Cleared items", 2, 7, 24);
            all = d.radio("wnd[0]/usr/radX_AISEL", "All items", 2, 8, 24);
            ButtonGroup g = new ButtonGroup(); g.add(open); g.add(cleared); g.add(all);
            open.setSelected(true);
            d.box("Type", 1, 11, 100, 3);
            d.check("wnd[0]/usr/chkX_NORM", "Normal items", 2, 12, 30).setSelected(true);
            d.check("wnd[0]/usr/chkX_SHBV", "Special G/L transactions", 2, 13, 30);
            return d;
        }

        @Override void onFKey(int k) { if (k == 8) execute(); else super.onFKey(k); }

        private void execute() {
            String from = vFrom.val(), to = vTo.val(), bukrs = cc.val();
            if (!bukrs.isEmpty() && !Data.COMPANIES.containsKey(bukrs)) { win.msg("E", "Company code " + bukrs + " is not defined"); return; }
            int mode = open.isSelected() ? 0 : cleared.isSelected() ? 1 : 2;
            Supplier<List<Object[]>> rows = () -> {
                List<Object[]> out = new ArrayList<>();
                for (Data.Item i : Data.ITEMS) {
                    if (!bukrs.isEmpty() && !i.bukrs.equals(bukrs)) continue;
                    if (!(from.isEmpty() && to.isEmpty()) && !Data.inRange(i.vendor, from, to)) continue;
                    boolean isOpen = i.clearing.isEmpty();
                    if (mode == 0 && !isOpen || mode == 1 && isOpen) continue;
                    out.add(new Object[] {isOpen ? "@led_r" : "@led_g", i.vendor, Data.VENDORS.get(i.vendor).name, i.bukrs, i.ref, i.belnr, i.blart,
                            Data.d(i.docDate), Data.d(i.due), Data.amt(i.amount.negate()), i.curr, i.clearing, Data.d(i.clearDate), i.block, i.text});
                }
                return out;
            };
            if (rows.get().isEmpty()) { win.msg("S", "No items selected (see long text)"); return; }
            ListScreen l = new ListScreen("Vendor Line Item Display", new String[] {"St", "Vendor", "Name", "CoCd", "Reference", "DocumentNo", "Type", "Doc. Date", "Net due dt",
                    "Amount in doc. curr.", "Curr.", "Clrng doc.", "Clearing", "PBk", "Text"},
                    new int[] {30, 60, 210, 50, 90, 90, 40, 80, 80, 130, 45, 90, 80, 35, 160}, rows);
            l.headerLines = List.of("Vendor " + (from.isEmpty() ? "*" : from) + (to.isEmpty() ? "" : " to " + to) + "     Company Code " + (bukrs.isEmpty() ? "*" : bukrs),
                    (mode == 0 ? "Open items at key date " + key.val() : mode == 1 ? "Cleared items" : "All items"));
            win.push(l);
            l.win.msg("S", l.table.getRowCount() + " items displayed");
        }
    }

    // ---- BNK_APP: Bank Communication Management approval ------------------------------------------

    static final class BnkApp extends ListScreen {
        BnkApp() {
            super("Approve Payments: Batches", new String[] {"St", "Batch", "CoCd", "PM", "Payment run", "Payments", "Batch Amount", "Crcy", "Status", "Release rule", "Approved by"},
                    new int[] {30, 90, 50, 35, 130, 70, 120, 45, 210, 230, 120}, null);
            rows = () -> {
                List<Object[]> out = new ArrayList<>();
                for (Data.Batch b : Data.BATCHES)
                    out.add(new Object[] {b.status.startsWith("Approved") ? "@led_g" : b.status.startsWith("Rejected") ? "@led_r" : "@led_y",
                            b.id, b.bukrs, b.method, b.runKey, b.count, Data.amt(b.amount), b.curr, b.status, b.rule, b.approvers});
                return out;
            };
            acts.add(new Act("wnd[0]/tbar[1]/btn[5]", "Approve", "approve", "Approve batch (F5)", () -> decide(true)));
            acts.add(new Act("wnd[0]/tbar[1]/btn[6]", "Reject", "reject", "Reject batch (F6)", () -> decide(false)));
            acts.add(new Act("wnd[0]/tbar[1]/btn[7]", "Refresh", "refresh", "Refresh (F7)", this::reload));
        }

        @Override String tcode() { return "BNK_APP"; }
        @Override String program() { return "SAPLBNK_COM_APP"; }

        private void decide(boolean approve) {
            int r = selected();
            if (r < 0) return;
            Data.Batch b = Data.BATCHES.get(r);
            if (b.status.startsWith("Approved") || b.status.startsWith("Rejected")) { win.msg("E", "Batch " + b.id + " has already been processed"); return; }
            if (b.approvers.contains(Main.user)) { win.msg("E", "Dual control: user " + Main.user + " has already approved batch " + b.id); return; }
            int ok = Popup.confirm(win, approve ? "Approve Batch" : "Reject Batch",
                    new String[] {"Batch " + b.id + "  -  " + b.count + " payment(s), " + b.curr + " " + Data.amt(b.amount), "Do you want to " + (approve ? "approve" : "reject") + " the batch?"}, "Yes", "No");
            if (ok != 0) return;
            if (!approve) { b.status = "Rejected"; b.approvers = Main.user; }
            else {
                b.approvals++;
                b.approvers = b.approvers.isEmpty() ? Main.user : b.approvers + ", " + Main.user;
                b.status = b.approvals >= b.required ? "Approved - sent to bank" : "Partially approved (" + b.approvals + " of " + b.required + ")";
            }
            reload();
            win.msg("S", "Batch " + b.id + (approve ? " approved" : " rejected"));
        }

        @Override void onFKey(int k) {
            if (k == 5) decide(true); else if (k == 6) decide(false); else if (k == 7) reload(); else super.onFKey(k);
        }
    }

    static LocalDate date(SapWindow w, SapField f, String name, boolean required) {
        try {
            LocalDate d = Data.pd(f.val());
            if (d == null && required) { w.msg("E", "Fill in all required entry fields"); f.requestFocusInWindow(); throw new IllegalStateException(name); }
            if (d != null) f.setText(Data.d(d));
            return d;
        } catch (IllegalArgumentException e) {
            w.msg("E", e.getMessage()); f.requestFocusInWindow();
            throw new IllegalStateException(name);
        }
    }
}
