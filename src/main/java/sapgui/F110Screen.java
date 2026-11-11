package sapgui;

import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** F110 - Automatic Payment Transactions (program SAPF110V). */
final class F110Screen extends Screen {
    private static final String T = "wnd[0]/usr/tabsF110_TABSTRIP/";
    private static final String PAR = T + "tabpPAR/ssubSUBSCREEN_BODY:SAPF110V:0202/";
    private static final String SEL = T + "tabpSEL/ssubSUBSCREEN_BODY:SAPF110V:0203/";
    private static final String PRO = T + "tabpPRO/ssubSUBSCREEN_BODY:SAPF110V:0204/";
    private static final String PRI = T + "tabpPRI/ssubSUBSCREEN_BODY:SAPF110V:0205/";
    private static final String[] TITLES = {"Status", "Parameters", "Free Selection", "Additional Log", "Printout/Data Medium"};
    private static final String[][] PROGRAMS = {{"RFFOUS_C", "Checks (with check management)"}, {"RFFOUS_T", "ACH / bank transfer (DME file)"},
            {"RFFOAVIS_FPAYM", "Payment advice notes (remittance by e-mail)"}, {"RFFOEDI1", "EDI / IDoc payment orders"}};
    private static final long JOB_MILLIS = 1500;

    private SapField laufd, laufi, budat, grdat, custDue, vFrom, vTo, cFrom, cTo, kurst, logVFrom, logVTo, logCFrom, logCTo;
    private final SapField[] bukls = new SapField[3], zwels = new SapField[3], nedat = new SapField[3];
    private final Map<String, SapField> variants = new LinkedHashMap<>();
    private JCheckBox xDue, xMethAll, xMethFail, xItems;
    private final List<JComponent> inputs = new ArrayList<>();
    private final Tabs tabs = new Tabs();
    private final Dyn statusDyn = new Dyn();
    private Data.Run run;
    private String loaded = "", snap = "";
    private boolean guard;

    @Override String title() { return "Automatic Payment Transactions: " + TITLES[tabs.selected]; }
    @Override String tcode() { return "F110"; }
    @Override String program() { return "SAPF110V"; }
    @Override String[] menus() { return new String[] {"Payment run", "Edit", "Goto", "Environment"}; }
    @Override boolean fill() { return true; }
    @Override boolean canSave() { return true; }

    @Override List<Act> menuItems(String m) {
        return switch (m) {
            case "Payment run" -> List.of(new Act("", "Save parameters", null, "", () -> win.vkey(11)), new Act("", "Exit", null, "", () -> win.vkey(15)));
            case "Edit" -> List.of(new Act("", "Proposal > Display", null, "", () -> showPayments(false)), new Act("", "Proposal > Edit", null, "", () -> showPayments(true)),
                    new Act("", "Proposal > Display log", null, "", () -> showLog(false)), new Act("", "Proposal > Delete", null, "", this::deleteProposal),
                    new Act("", "Payments > Display log", null, "", () -> showLog(true)));
            case "Goto" -> List.of(new Act("", "Status", null, "", this::status));
            default -> List.of();
        };
    }

    @Override List<Act> buttons() {
        List<Act> b = new ArrayList<>();
        b.add(new Act("wnd[0]/tbar[1]/btn[14]", "Status", "status", "Status (Shift+F2)", this::status));
        if (run == null || !run.saved || run.prop == 1 || run.pay == 1) return b;
        if (run.prop == 0) b.add(new Act("wnd[0]/tbar[1]/btn[13]", "Proposal", "exec", "Schedule Proposal (Shift+F1)", () -> schedule(false)));
        else if (run.pay == 0) {
            b.add(new Act("wnd[0]/tbar[1]/btn[21]", "Proposal", "display", "Display Proposal (Shift+F9)", () -> showPayments(false)));
            b.add(new Act("wnd[0]/tbar[1]/btn[18]", "Proposal", "edit", "Edit Proposal (Shift+F6)", () -> showPayments(true)));
            b.add(new Act("wnd[0]/tbar[1]/btn[22]", "Proposal", "log", "Display Proposal Log (Shift+F10)", () -> showLog(false)));
            b.add(new Act("wnd[0]/tbar[1]/btn[16]", "Proposal", "delete", "Delete Proposal (Shift+F4)", this::deleteProposal));
            b.add(new Act("wnd[0]/tbar[1]/btn[7]", "Pmnt Run", "exec", "Schedule Payment Run (F7)", () -> schedule(true)));
        } else {
            b.add(new Act("wnd[0]/tbar[1]/btn[21]", "Payment", "display", "Display Payments (Shift+F9)", () -> showPayments(false)));
            b.add(new Act("wnd[0]/tbar[1]/btn[23]", "Payment", "log", "Display Payment Run Log (Shift+F11)", () -> showLog(true)));
            b.add(new Act("wnd[0]/tbar[1]/btn[19]", "Printout", "print", "Schedule Print (F9)", () -> win.msg("S", "Print job F110-" + run.laufi + " has been scheduled")));
        }
        return b;
    }

    // ---- layout -----------------------------------------------------------------------------------

    private SapField in(SapField f) { inputs.add(f); return f; }
    private JCheckBox in(JCheckBox c) { inputs.add(c); return c; }

    @Override JComponent build() {
        Dyn top = new Dyn();
        top.label("Run Date", 1, 0, 20);
        laufd = top.field("wnd[0]/usr/ctxtF110V-LAUFD", 22, 0, 10); laufd.required = true;
        top.label("Identification", 1, 1, 20);
        laufi = top.field("wnd[0]/usr/ctxtF110V-LAUFI", 22, 1, 6); laufi.required = true;

        statusDyn.box("Status", 1, 0, 110, 7);

        Dyn p = new Dyn();
        p.label("Posting Date", 1, 0, 22);
        budat = in(p.field(PAR + "ctxtF110V-BUDAT", 24, 0, 10)); budat.required = true;
        p.label("Docs entered up to", 52, 0, 24);
        grdat = in(p.field(PAR + "ctxtF110V-GRDAT", 77, 0, 10));
        p.label("Customer items due by", 52, 1, 24);
        custDue = in(p.field(PAR + "ctxtF110V-FDEBI", 77, 1, 10));
        p.box("Payments control", 1, 3, 110, 5);
        p.header("Company codes", 2, 4, 56); p.header("Pmt meths", 58, 4, 16); p.header("Next p/date", 74, 4, 14);
        for (int i = 0; i < 3; i++) {
            bukls[i] = in(p.field(PAR + "tblSAPF110VCTRL_FKTTAB/txtF110V-BUKLS[0," + i + "]", 2, 5 + i, 54));
            zwels[i] = in(p.field(PAR + "tblSAPF110VCTRL_FKTTAB/ctxtF110V-ZWELS[1," + i + "]", 58, 5 + i, 14));
            nedat[i] = in(p.field(PAR + "tblSAPF110VCTRL_FKTTAB/ctxtF110V-NEDAT[2," + i + "]", 74, 5 + i, 12));
        }
        p.box("Accounts", 1, 10, 110, 3);
        p.label("Vendor", 2, 11, 20);
        vFrom = in(p.field(PAR + "tblSAPF110VCTRL_SELTAB/ctxtF110V-VONKK[0,0]", 23, 11, 10));
        p.text("to", 38, 11, 4);
        vTo = in(p.field(PAR + "tblSAPF110VCTRL_SELTAB/ctxtF110V-BISKK[1,0]", 42, 11, 10));
        p.label("Customer", 2, 12, 20);
        cFrom = in(p.field(PAR + "tblSAPF110VCTRL_SELTAB/ctxtF110V-VONKD[0,1]", 23, 12, 10));
        p.text("to", 38, 12, 4);
        cTo = in(p.field(PAR + "tblSAPF110VCTRL_SELTAB/ctxtF110V-BISKD[1,1]", 42, 12, 10));
        p.box("Foreign currencies", 1, 15, 110, 2);
        p.label("Exchange rate type", 2, 16, 20);
        kurst = in(p.field(PAR + "ctxtF110V-KURST", 23, 16, 4));

        Dyn s = new Dyn();
        s.box("Free selection", 1, 0, 110, 7);
        for (int i = 0; i < 3; i++) {
            s.label("Field Name", 2, 1 + i * 2, 20);
            in(s.field(SEL + "ctxtF110V-LIST" + (i + 1), 23, 1 + i * 2, 30));
            in(s.check(SEL + "chkF110V-XTXT" + (i + 1), "Exclude values", 60, 1 + i * 2, 24));
            s.label("Values", 2, 2 + i * 2, 20);
            in(s.field(SEL + "txtF110V-TEXT" + (i + 1), 23, 2 + i * 2, 60));
        }

        Dyn l = new Dyn();
        l.box("Required logging type", 1, 0, 110, 5);
        xDue = in(l.check(PRO + "chkF110V-XTRFA", "Due date check", 2, 1, 60));
        xMethAll = in(l.check(PRO + "chkF110V-XTRZW", "Payment method selection in all cases", 2, 2, 60));
        xMethFail = in(l.check(PRO + "chkF110V-XTRZE", "Pmnt method selection if not successful", 2, 3, 60));
        xItems = in(l.check(PRO + "chkF110V-XTRBL", "Line items of the payment documents", 2, 4, 60));
        l.box("Accounts required", 1, 7, 110, 3);
        l.label("Vendors (from/to)", 2, 8, 24);
        logVFrom = in(l.field(PRO + "tblSAPF110VCTRL_KTOTAB/ctxtF110V-VONKL[0,0]", 27, 8, 10));
        logVTo = in(l.field(PRO + "tblSAPF110VCTRL_KTOTAB/ctxtF110V-BISKL[1,0]", 42, 8, 10));
        l.label("Customers (from/to)", 2, 9, 24);
        logCFrom = in(l.field(PRO + "tblSAPF110VCTRL_KTOTAB/ctxtF110V-VONDL[0,1]", 27, 9, 10));
        logCTo = in(l.field(PRO + "tblSAPF110VCTRL_KTOTAB/ctxtF110V-BISDL[1,1]", 42, 9, 10));

        Dyn d = new Dyn();
        d.box("Required print / DME programs", 1, 0, 110, PROGRAMS.length + 2);
        d.header("Program", 2, 1, 24); d.header("Variant", 26, 1, 18); d.header("Description", 44, 1, 60);
        for (int i = 0; i < PROGRAMS.length; i++) {
            SapField prog = d.field(PRI + "tblSAPF110VCTRL_DRPTAB/txtF110V-PROGR[0," + i + "]", 2, 2 + i, 22);
            prog.setText(PROGRAMS[i][0]); prog.editable(false);
            variants.put(PROGRAMS[i][0], in(d.field(PRI + "tblSAPF110VCTRL_DRPTAB/ctxtF110V-VARI1[1," + i + "]", 26, 2 + i, 14)));
            d.text(PROGRAMS[i][1], 45, 2 + i, 58);
        }

        tabs.add(T + "tabpSTA", "Status", statusDyn);
        tabs.add(T + "tabpPAR", "Parameter", p);
        tabs.add(T + "tabpSEL", "Free selection", s);
        tabs.add(T + "tabpPRO", "Additional Log", l);
        tabs.add(T + "tabpPRI", "Printout/data medium", d);
        tabs.onSelect = i -> {
            if (guard) return;
            win.clearMsg();
            if (!resolve() && i != 0) { guard = true; tabs.select(0); guard = false; }
            if (tabs.selected == 0) { advance(); refreshStatus(); }
            win.refresh();
        };

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(4, 12, 10, 12));
        center.add(tabs);
        body.add(top, BorderLayout.NORTH); body.add(center, BorderLayout.CENTER);
        refreshStatus();
        return body;
    }

    @Override void shown() { if (run != null) { advance(); refreshStatus(); } else laufd.requestFocusInWindow(); }

    // ---- run handling -----------------------------------------------------------------------------

    /** Validates run date / identification and loads the matching run into the tab pages. */
    private boolean resolve() {
        LocalDate d;
        try { d = Screens.date(win, laufd, "LAUFD", true); } catch (IllegalStateException e) { return false; }
        String id = laufi.val();
        if (id.isEmpty()) { win.msg("E", "Fill in all required entry fields"); laufi.requestFocusInWindow(); return false; }
        String key = Data.d(d) + " " + id;
        if (!key.equals(loaded)) {
            run = Data.findRun(d, id);
            if (run == null) { run = new Data.Run(); run.laufd = d; run.laufi = id; run.rows.add(new Data.ParamRow()); }
            loaded = key;
            toFields();
        }
        return true;
    }

    private void toFields() {
        boolean edit = run.prop == 0;
        for (JComponent c : inputs) { if (c instanceof SapField f) { f.setText(""); f.editable(edit); } else { ((JCheckBox) c).setSelected(false); c.setEnabled(edit); } }
        budat.setText(Data.d(run.budat)); grdat.setText(Data.d(run.grdat));
        for (int i = 0; i < 3 && i < run.rows.size(); i++) {
            bukls[i].setText(run.rows.get(i).bukrs); zwels[i].setText(run.rows.get(i).methods); nedat[i].setText(Data.d(run.rows.get(i).nedat));
        }
        vFrom.setText(run.vendFrom); vTo.setText(run.vendTo); cFrom.setText(run.custFrom); cTo.setText(run.custTo);
        xDue.setSelected(run.logDue); xMethAll.setSelected(run.logMethAll); xMethFail.setSelected(run.logMethFail); xItems.setSelected(run.logItems);
        logVFrom.setText(run.logVendFrom); logVTo.setText(run.logVendTo);
        variants.forEach((prog, f) -> f.setText(run.variants.getOrDefault(prog, "")));
        snap = snapshot();
    }

    private String snapshot() {
        StringBuilder sb = new StringBuilder();
        for (JComponent c : inputs) sb.append(c instanceof SapField f ? f.getText() : String.valueOf(((JCheckBox) c).isSelected())).append('|');
        return sb.toString();
    }

    private boolean dirty() { return run != null && run.prop == 0 && !snapshot().equals(snap); }

    @Override void onSave() {
        if (!resolve()) return;
        if (run.prop != 0) { win.msg("E", "Parameters cannot be changed: a payment proposal already exists for " + run.key()); return; }
        List<Data.ParamRow> rows = new ArrayList<>();
        LocalDate bu, gr;
        try {
            bu = Screens.date(win, budat, "BUDAT", true);
            gr = Screens.date(win, grdat, "GRDAT", false);
            Screens.date(win, custDue, "FDEBI", false);
            for (int i = 0; i < 3; i++) {
                if (bukls[i].val().isEmpty() && zwels[i].val().isEmpty() && nedat[i].val().isEmpty()) continue;
                if (bukls[i].val().isEmpty()) { fail(bukls[i], "Enter at least one company code"); return; }
                List<String> ccs = Data.companyCodes(bukls[i].val());
                if (ccs.isEmpty()) { fail(bukls[i], "No company code found for selection " + bukls[i].val()); return; }
                for (String cc : ccs) if (!Data.COMPANIES.containsKey(cc)) { fail(bukls[i], "Company code " + cc + " is not defined"); return; }
                if (zwels[i].val().isEmpty()) { fail(zwels[i], "Enter the payment methods"); return; }
                for (char m : zwels[i].val().toCharArray())
                    if (!Data.METHODS.containsKey(String.valueOf(m))) { fail(zwels[i], "Payment method " + m + " is not defined for country US"); return; }
                Data.ParamRow r = new Data.ParamRow();
                r.bukrs = bukls[i].val(); r.methods = zwels[i].val();
                r.nedat = Screens.date(win, nedat[i], "NEDAT", false);
                if (r.nedat == null) { fail(nedat[i], "Enter the posting date of the next payment run"); return; }
                if (!r.nedat.isAfter(run.laufd)) { fail(nedat[i], "Next payment run date must be after the run date " + Data.d(run.laufd)); return; }
                rows.add(r);
            }
        } catch (IllegalStateException e) { return; }
        if (rows.isEmpty()) { fail(bukls[0], "Enter at least one company code"); return; }
        if (vFrom.val().isEmpty() && cFrom.val().isEmpty()) { fail(vFrom, "Specify at least one vendor or customer account"); return; }
        if (!vTo.val().isEmpty() && vTo.val().compareTo(vFrom.val()) < 0) { fail(vTo, "Lower limit is greater than upper limit"); return; }
        run.budat = bu; run.grdat = gr == null ? bu : gr; run.rows = rows;
        run.vendFrom = vFrom.val(); run.vendTo = vTo.val(); run.custFrom = cFrom.val(); run.custTo = cTo.val();
        run.logDue = xDue.isSelected(); run.logMethAll = xMethAll.isSelected(); run.logMethFail = xMethFail.isSelected(); run.logItems = xItems.isSelected();
        run.logVendFrom = logVFrom.val(); run.logVendTo = logVTo.val();
        run.variants.clear();
        variants.forEach((prog, f) -> { if (!f.val().isEmpty()) run.variants.put(prog, f.val()); });
        if (!Data.RUNS.contains(run)) Data.RUNS.add(run);
        run.saved = true;
        grdat.setText(Data.d(run.grdat));
        snap = snapshot();
        refreshStatus();
        win.refresh();
        win.msg("S", "Details have been saved for the run on " + run.key());
    }

    private void fail(SapField f, String text) {
        for (int t = 1; t <= 4; t++) if (isOnTab(f, t)) { guard = true; tabs.select(t); guard = false; win.refresh(); }
        win.msg("E", text);
        f.requestFocusInWindow();
    }

    private boolean isOnTab(SapField f, int tab) {
        String[] keys = {"", "tabpPAR", "tabpSEL", "tabpPRO", "tabpPRI"};
        return f.getName().contains(keys[tab]) && tabs.selected != tab;
    }

    private void advance() {
        if (run == null || System.currentTimeMillis() - run.scheduledAt < JOB_MILLIS) return;
        if (run.prop == 1) { Data.proposal(run); toFields(); }
        if (run.pay == 1) Data.payment(run);
    }

    private void refreshStatus() {
        List<String> lines = new ArrayList<>();
        if (run == null || !run.saved) lines.add("No parameters entered as yet");
        else {
            lines.add("Parameters have been entered");
            if (run.prop == 1) lines.add("Proposal is running");
            if (run.prop == 2) lines.add("Payment proposal has been created");
            if (run.prop == 2 && run.edited) lines.add("Payment proposal has been edited");
            if (run.pay == 1) lines.add("Payment run is running");
            if (run.pay == 2) {
                lines.add("Payment run has been carried out");
                lines.add("Posting orders: " + run.postings + " generated, " + run.postings + " completed");
            }
        }
        for (java.awt.Component c : statusDyn.getComponents()) if (c instanceof javax.swing.JLabel) statusDyn.remove(c);
        for (int i = 0; i < lines.size(); i++)
            statusDyn.text(lines.get(i), 3, 1 + i, 100).setName(T + "tabpSTA/ssubSUBSCREEN_BODY:SAPF110V:0201/txtF110V-TEXT[" + i + "]");
        statusDyn.revalidate(); statusDyn.repaint();
    }

    private void status() {
        if (!resolve()) return;
        advance();
        guard = true; tabs.select(0); guard = false;
        refreshStatus();
        win.refresh();
    }

    @Override void onEnter() {
        if (!resolve()) return;
        advance();
        refreshStatus();
        win.refresh();
    }

    @Override void onFKey(int k) {
        if (k == 7 && run != null && run.prop == 2 && run.pay == 0) schedule(true);
        else if (k == 13 && run != null && run.saved && run.prop == 0) schedule(false);
        else if (k == 14) status();
        else super.onFKey(k);
    }

    private boolean leave() {
        if (!dirty()) return true;
        int r = Popup.confirm(win, "Exit Editing", new String[] {"Parameters were changed.", "Do you want to save the changes?"}, "Yes", "No");
        if (r < 0) return false;
        if (r == 0) { onSave(); return !dirty() && win.statusType.equals("S"); }
        return true;
    }

    @Override void onBack() { if (leave()) win.home(); }
    @Override void onExit() { if (leave()) win.home(); }

    // ---- scheduling -------------------------------------------------------------------------------

    private void schedule(boolean pay) {
        if (!resolve()) return;
        if (dirty()) { win.msg("E", "Parameters were changed: save the parameters first"); return; }
        if (pay && run.payments.isEmpty()) { win.msg("E", "Payment proposal " + run.key() + " contains no payments"); return; }
        Popup p = new Popup(win, pay ? "Schedule Payment" : "Schedule Proposal");
        p.body.label("Start date", 1, 0, 20);
        SapField date = p.body.field(p.wnd + "/usr/ctxtF110V-STRDT", 22, 0, 10); date.setText(Data.d(Data.TODAY));
        p.body.label("Start time", 1, 1, 20);
        SapField time = p.body.field(p.wnd + "/usr/ctxtF110V-STRZT", 22, 1, 8); time.setText("00:00:00");
        JCheckBox now = p.body.check(p.wnd + "/usr/chkF110V-XSTRF", "Start immediately", 1, 2, 30);
        p.body.label("Target computer", 1, 4, 20);
        p.body.field(p.wnd + "/usr/ctxtF110V-BHOST", 22, 4, 20, false);
        JCheckBox medium = pay ? p.body.check(p.wnd + "/usr/chkF110V-XMITD", "Create payment medium", 1, 6, 30) : null;
        if (medium != null) medium.setSelected(true);
        p.button("tbar[0]/btn[0]", "", "ok", "Schedule (Enter)", 0, true, () -> {
            LocalDate d;
            try { d = Data.pd(date.val()); } catch (IllegalArgumentException e) { return e.getMessage(); }
            if (d == null) return "Enter a start date";
            if (now.isSelected()) return null;
            if (!time.val().matches("\\d{2}:\\d{2}(:\\d{2})?")) return "Enter a valid start time (HH:MM:SS)";
            if (d.isAfter(Data.TODAY)) return "Start date lies in the future: select 'Start immediately' in this system";
            return null;
        });
        p.button("tbar[0]/btn[12]", "", "cancel", "Cancel (F12)", -1, false, null);
        if (p.open() != 0) return;
        run.scheduledAt = System.currentTimeMillis();
        if (pay) { run.pay = 1; run.medium = medium.isSelected(); } else run.prop = 1;
        guard = true; tabs.select(0); guard = false;
        refreshStatus();
        win.refresh();
        win.msg("S", pay ? "Payment run has been scheduled" : "Proposal has been scheduled");
    }

    private void deleteProposal() {
        if (run == null || run.prop != 2 || run.pay != 0) { win.msg("E", "No payment proposal exists that could be deleted"); return; }
        if (Popup.confirm(win, "Delete Proposal", new String[] {"Payment proposal " + run.key(), "Do you really want to delete the proposal?"}, "Yes", "No") != 0) return;
        run.prop = 0; run.edited = false; run.payments.clear(); run.exceptions.clear(); run.propLog.clear();
        toFields(); refreshStatus(); win.refresh();
        win.msg("S", "Payment proposal " + run.key() + " has been deleted");
    }

    // ---- proposal / payment lists -----------------------------------------------------------------

    private void showPayments(boolean edit) {
        if (run == null || run.prop != 2) { win.msg("E", "No payment proposal exists for " + (run == null ? "this run" : run.key())); return; }
        if (edit && run.pay != 0) { win.msg("E", "Payment run has already been carried out: proposal can no longer be edited"); return; }
        final Data.Run r = run;
        List<Object> objects = new ArrayList<>();
        Screens.ListScreen[] ref = new Screens.ListScreen[1];
        Screens.ListScreen l = new Screens.ListScreen((edit ? "Edit" : "Display") + (r.pay == 2 ? " Payment Run: Payments" : " Payment Proposal: Payments"),
                new String[] {"St", "Vendor", "Name", "Paying CoCd", "PM", "HBk", "Amount paid", "Crcy", "Items", "Payment doc.", "Note"},
                new int[] {30, 60, 230, 85, 35, 55, 120, 45, 50, 100, 330}, null);
        l.rows = () -> {
            objects.clear();
            List<Object[]> out = new ArrayList<>();
            for (Data.Payment p : r.payments) {
                objects.add(p);
                out.add(new Object[] {"@led_g", p.vendor, Data.VENDORS.get(p.vendor).name, p.bukrs, p.method, p.houseBank, Data.amt(p.amount), p.curr, p.items.size(), p.docNo, ""});
            }
            Map<String, List<Data.Exc>> groups = new LinkedHashMap<>();
            for (Data.Exc e : r.exceptions) groups.computeIfAbsent(e.item.vendor + "/" + e.item.bukrs, k -> new ArrayList<>()).add(e);
            for (List<Data.Exc> g : groups.values()) {
                Data.Item first = g.get(0).item;
                java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
                for (Data.Exc e : g) sum = sum.add(e.item.amount);
                objects.add(g);
                out.add(new Object[] {"@led_r", first.vendor, Data.VENDORS.get(first.vendor).name, first.bukrs, "", "", Data.amt(sum), first.curr, g.size(), "", "Exception: " + g.get(0).text});
            }
            return out;
        };
        l.rowColor = row -> row < objects.size() && objects.get(row) instanceof List ? Theme.RED : null;
        l.headerLines = List.of("Run date " + Data.d(r.laufd) + "     Identification " + r.laufi, r.payments.size() + " payment(s), " + r.exceptions.size() + " item(s) in exception list");
        l.onDouble = row -> showItems(r, objects.get(row));
        l.acts.add(new Act("wnd[0]/tbar[1]/btn[2]", "Choose", "display", "Choose (F2)", () -> { int row = ref[0].selected(); if (row >= 0) showItems(r, objects.get(row)); }));
        if (edit) l.acts.add(new Act("wnd[0]/tbar[1]/btn[5]", "Block", "block", "Block payment (F5)", () -> {
            int row = ref[0].selected();
            if (row < 0) return;
            if (!(objects.get(row) instanceof Data.Payment pm)) { win.msg("E", "Line is already in the exception list"); return; }
            Data.blockPayment(r, pm);
            ref[0].reload();
            win.msg("S", "Payment for vendor " + pm.vendor + " blocked: items moved to the exception list");
        }));
        ref[0] = l;
        win.push(l);
    }

    @SuppressWarnings("unchecked")
    private void showItems(Data.Run r, Object o) {
        List<Object[]> rows = new ArrayList<>();
        String vendor;
        if (o instanceof Data.Payment p) {
            vendor = p.vendor;
            for (Data.Item i : p.items) rows.add(itemRow(i, "", ""));
        } else {
            List<Data.Exc> g = (List<Data.Exc>) o;
            vendor = g.get(0).item.vendor;
            for (Data.Exc e : g) rows.add(itemRow(e.item, e.code, e.text));
        }
        Screens.ListScreen l = new Screens.ListScreen("Display Payment Proposal: Open Items", new String[] {"DocumentNo", "CoCd", "Reference", "Doc. Date", "Net due dt", "Amount", "Crcy", "PBk", "Err", "Error text"},
                new int[] {95, 50, 100, 85, 85, 120, 45, 35, 40, 420}, () -> rows);
        l.headerLines = List.of("Vendor " + vendor + "  " + Data.VENDORS.get(vendor).name, "Run date " + Data.d(r.laufd) + "     Identification " + r.laufi);
        l.rowColor = row -> rows.get(row)[8].toString().isEmpty() ? null : Theme.RED;
        win.push(l);
    }

    private static Object[] itemRow(Data.Item i, String code, String text) {
        return new Object[] {i.belnr, i.bukrs, i.ref, Data.d(i.docDate), Data.d(i.due), Data.amt(i.amount), i.curr, i.block, code, text};
    }

    private void showLog(boolean pay) {
        List<Data.LogLine> log = run == null ? List.of() : pay ? run.payLog : run.propLog;
        if (log.isEmpty()) { win.msg("E", "No log exists for " + (pay ? "the payment run" : "the proposal run")); return; }
        String job = "F110-" + run.laufd.toString().replace("-", "") + "-" + run.laufi + (pay ? "" : "-X");
        Screens.ListScreen l = new Screens.ListScreen("Job Log Entries for " + job, new String[] {"Date", "Time", "Message text", "Message class", "Msg no.", "Ty"},
                new int[] {85, 70, 640, 100, 60, 30}, () -> {
                    List<Object[]> out = new ArrayList<>();
                    for (Data.LogLine x : log) out.add(new Object[] {Data.d(Data.TODAY), x.time(), x.text(), x.id(), x.no(), x.type()});
                    return out;
                });
        win.push(l);
    }
}
