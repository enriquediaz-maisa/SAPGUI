package sapgui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** In-memory, entirely fictitious FI-AP data set and the payment program engine (F110). */
final class Data {
    static final LocalDate TODAY = LocalDate.now();
    static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    static String d(LocalDate x) { return x == null ? "" : DF.format(x); }

    /** Parses a user date (MM/DD/YYYY, M/D/YYYY or MMDDYYYY). Blank gives null. */
    static LocalDate pd(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return null;
        try {
            if (s.matches("\\d{8}")) s = s.substring(0, 2) + "/" + s.substring(2, 4) + "/" + s.substring(4);
            String[] p = s.split("[/.-]");
            return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid date format (please enter date in the format MM/DD/YYYY)");
        }
    }

    static String amt(BigDecimal a) { return String.format(Locale.US, "%,.2f", a); }

    record CompanyCode(String id, String name, String city, String curr, String houseBank) {}

    static final class Vendor {
        String id, name, city, country, methods, bank, email;
        Vendor(String id, String name, String city, String country, String methods, String bank, String email) {
            this.id = id; this.name = name; this.city = city; this.country = country;
            this.methods = methods; this.bank = bank; this.email = email;
        }
    }

    static final class Item {
        String bukrs, vendor, belnr, blart = "KR", ref, block = "", clearing = "", text = "";
        LocalDate docDate, due, clearDate;
        BigDecimal amount;
        String curr;
    }

    static final class Payment {
        String bukrs, vendor, method, houseBank, curr, docNo = "";
        BigDecimal amount = BigDecimal.ZERO;
        List<Item> items = new ArrayList<>();
    }

    static final class Exc {
        Item item; String code, text;
        Exc(Item i, String c, String t) { item = i; code = c; text = t; }
    }

    static final class ParamRow { String bukrs = "", methods = ""; LocalDate nedat; }

    record LogLine(String time, String text, String id, String no, String type) {}

    static final class Run {
        LocalDate laufd; String laufi;
        LocalDate budat = TODAY, grdat = TODAY;
        List<ParamRow> rows = new ArrayList<>();
        String vendFrom = "", vendTo = "", custFrom = "", custTo = "";
        boolean logDue, logMethAll, logMethFail, logItems;
        String logVendFrom = "", logVendTo = "";
        Map<String, String> variants = new LinkedHashMap<>();
        boolean saved, edited, medium;
        /** 0 = none, 1 = scheduled/running, 2 = finished. */
        int prop, pay;
        long scheduledAt;
        List<Payment> payments = new ArrayList<>();
        List<Exc> exceptions = new ArrayList<>();
        List<LogLine> propLog = new ArrayList<>(), payLog = new ArrayList<>();
        int postings;
        String key() { return d(laufd) + " " + laufi; }
    }

    static final class Batch {
        String id, bukrs, method, curr, status = "New", rule, runKey, approvers = "";
        int count, approvals, required;
        BigDecimal amount = BigDecimal.ZERO;
    }

    static final Map<String, CompanyCode> COMPANIES = new LinkedHashMap<>();
    static final Map<String, Vendor> VENDORS = new TreeMap<>();
    static final List<Item> ITEMS = new ArrayList<>();
    static final List<Run> RUNS = new ArrayList<>();
    static final List<Batch> BATCHES = new ArrayList<>();
    static final Map<String, String> METHODS = new LinkedHashMap<>();
    static final BigDecimal DUAL_APPROVAL_LIMIT = new BigDecimal("50000");
    private static long nextInvoice = 1900000101L, nextPayDoc = 2000000041L, nextBatch = 1000000317L;

    static {
        METHODS.put("C", "Check");
        METHODS.put("T", "ACH / Bank transfer");
        METHODS.put("W", "Wire transfer");
        COMPANIES.put("US01", new CompanyCode("US01", "Northgate Construction US Inc.", "Austin", "USD", "CITI1"));
        COMPANIES.put("US02", new CompanyCode("US02", "Northgate Highways LLC", "Dallas", "USD", "JPM01"));
        COMPANIES.put("CA01", new CompanyCode("CA01", "Northgate Infrastructure Canada Ltd.", "Toronto", "CAD", "RBC01"));

        v("1000", "Lone Star Aggregates LLC", "San Antonio", "US", "T", "021000089 / 4410098812", "ar@lonestar-aggregates.example");
        v("1001", "Great Lakes Steel Supply Inc.", "Cleveland", "US", "TC", "041001039 / 7781203345", "remit@greatlakessteel.example");
        v("1002", "Pacific Rim Equipment Rental", "Portland", "US", "C", "", "billing@pacrim-rental.example");
        v("1003", "Maple Leaf Paving Ltd.", "Mississauga", "CA", "T", "0003-00102 / 5512048", "ap@mapleleafpaving.example");
        v("1004", "Blue Ridge Surveying Co.", "Asheville", "US", "", "", "office@blueridgesurvey.example");
        v("1005", "Sunbelt Concrete Works", "Phoenix", "US", "T", "122105155 / 2087714090", "ar@sunbeltconcrete.example");
        v("1006", "Hudson Valley Electric Corp.", "Albany", "US", "WT", "021300077 / 9034415567", "treasury@hudsonvalleyelectric.example");
        v("1007", "Prairie Fuel Distributors", "Omaha", "US", "T", "104000016 / 3320981174", "invoices@prairiefuel.example");
        v("1008", "Keystone Safety Supply", "Pittsburgh", "US", "T", "", "sales@keystonesafety.example");
        v("1009", "Northern Lights Logistics Inc.", "Calgary", "CA", "T", "0010-08952 / 1002277", "ar@northernlightslog.example");

        // Historic items, already settled by last week's run.
        it("US01", "1000", "LSA-88213", 40, -12, "9840.00", "");
        it("US01", "1001", "GLS-20417", 38, -12, "31275.60", "");
        it("US02", "1001", "GLS-20388", 41, -13, "11020.00", "");
        Run hist = new Run();
        hist.laufd = TODAY.minusDays(7); hist.laufi = "WK-1";
        hist.budat = hist.laufd; hist.grdat = hist.laufd;
        ParamRow hr = new ParamRow(); hr.bukrs = "US01,US02"; hr.methods = "T"; hr.nedat = TODAY.minusDays(7);
        hist.rows.add(hr); hist.vendFrom = "1000"; hist.vendTo = "1009";
        hist.variants.put("RFFOUS_T", "ACH_US"); hist.variants.put("RFFOAVIS_FPAYM", "REMIT_MAIL");
        hist.saved = true; RUNS.add(hist);
        proposal(hist); payment(hist);
        for (Batch b : BATCHES) { b.status = "Approved - sent to bank"; b.approvals = b.required; b.approvers = "TREASURY1"; }

        it("US01", "1000", "LSA-88301", 34, -6, "12480.00", "");
        it("US01", "1000", "LSA-88340", 30, -2, "8915.50", "");
        it("US01", "1000", "LSA-88412", 12, 18, "4200.00", "");
        it("US01", "1001", "GLS-20455", 35, -5, "48300.00", "");
        it("US01", "1001", "GLS-20461", 33, -3, "27150.25", "");
        it("US01", "1002", "PRE-7741", 31, -1, "6720.00", "");
        it("US01", "1004", "BRS-1190", 36, -6, "3450.00", "");
        it("US01", "1005", "SCW-55102", 32, -4, "15900.00", "A");
        it("US01", "1005", "SCW-55140", 30, -2, "9300.00", "");
        it("US01", "1006", "HVE-300981", 33, -3, "182400.00", "");
        it("US01", "1007", "PFD-61877", 31, -1, "5610.40", "");
        it("US02", "1000", "LSA-88355", 32, -2, "7300.00", "");
        it("US02", "1001", "GLS-20470", 34, -4, "22000.00", "");
        it("US02", "1008", "KSS-4402", 30, 0, "2180.00", "");
        it("CA01", "1003", "MLP-9015", 33, -3, "31500.00", "");
        it("CA01", "1009", "NLL-27730", 31, -1, "12250.00", "");
        it("CA01", "1009", "NLL-27790", 9, 21, "6400.00", "");

        // A proposal left open by a colleague: it locks vendor 1007 for other runs.
        Run test = new Run();
        test.laufd = TODAY.minusDays(1); test.laufi = "TEST1";
        test.budat = test.laufd; test.grdat = test.laufd;
        ParamRow tr = new ParamRow(); tr.bukrs = "US01"; tr.methods = "T"; tr.nedat = TODAY.plusDays(6);
        test.rows.add(tr); test.vendFrom = "1007"; test.vendTo = "";
        test.saved = true; RUNS.add(test);
        proposal(test);
    }

    private static void v(String id, String n, String c, String ctry, String m, String bank, String mail) {
        VENDORS.put(id, new Vendor(id, n, c, ctry, m, bank, mail));
    }

    private static void it(String bukrs, String vendor, String ref, int docDaysAgo, int dueOffset, String amount, String block) {
        Item i = new Item();
        i.bukrs = bukrs; i.vendor = vendor; i.ref = ref; i.belnr = String.valueOf(nextInvoice++);
        i.docDate = TODAY.minusDays(docDaysAgo); i.due = TODAY.plusDays(dueOffset);
        i.amount = new BigDecimal(amount); i.curr = COMPANIES.get(bukrs).curr(); i.block = block;
        i.text = "Invoice " + ref;
        ITEMS.add(i);
    }

    static Run findRun(LocalDate d, String id) {
        for (Run r : RUNS) if (r.laufd.equals(d) && r.laufi.equalsIgnoreCase(id)) return r;
        return null;
    }

    /** Expands "US01,US02" or "US01-US02" into existing company codes; unknown single values are returned as is. */
    static List<String> companyCodes(String spec) {
        List<String> out = new ArrayList<>();
        for (String part : spec.toUpperCase().split("[,; ]+")) {
            if (part.isEmpty()) continue;
            if (part.contains("-")) {
                String[] r = part.split("-", 2);
                for (String c : COMPANIES.keySet())
                    if (c.compareTo(r[0]) >= 0 && c.compareTo(r[1]) <= 0 && !out.contains(c)) out.add(c);
            } else if (!out.contains(part)) out.add(part);
        }
        return out;
    }

    static boolean inRange(String v, String from, String to) {
        if (from.isEmpty() && to.isEmpty()) return false;
        if (to.isEmpty()) return v.equals(from);
        return v.compareTo(from) >= 0 && v.compareTo(to) <= 0;
    }

    private static String now() { return TF.format(LocalTime.now()); }
    private static void log(List<LogLine> l, String text, String id, String no) { l.add(new LogLine(now(), text, id, no, "S")); }

    private static Run lockedBy(Item i, Run self) {
        for (Run o : RUNS) {
            if (o == self || o.prop != 2 || o.pay == 2) continue;
            for (Payment p : o.payments) if (p.items.contains(i)) return o;
            for (Exc e : o.exceptions) if (e.item == i) return o;
        }
        return null;
    }

    /** Payment proposal (program SAPF110S, proposal mode). */
    static void proposal(Run r) {
        r.payments.clear(); r.exceptions.clear(); r.edited = false;
        List<LogLine> l = r.propLog = new ArrayList<>();
        log(l, "Job started", "00", "516");
        log(l, "Step 001 started (program SAPF110S, variant &0000000000" + (410 + RUNS.indexOf(r)) + ", user ID " + Main.user + ")", "00", "550");
        log(l, "Log for proposal run for payment on " + d(r.laufd) + ", identification " + r.laufi, "FZ", "401");
        for (ParamRow row : r.rows) for (String cc : companyCodes(row.bukrs)) {
            CompanyCode co = COMPANIES.get(cc);
            if (co == null) continue;
            for (Vendor v : VENDORS.values()) {
                if (!inRange(v.id, r.vendFrom, r.vendTo)) continue;
                boolean detail = inRange(v.id, r.logVendFrom, r.logVendTo);
                List<Item> due = new ArrayList<>();
                boolean any = false;
                for (Item i : ITEMS) {
                    if (!i.bukrs.equals(cc) || !i.vendor.equals(v.id) || !i.clearing.isEmpty() || i.docDate.isAfter(r.grdat)) continue;
                    if (!any) { log(l, ">", "FZ", "693"); log(l, "> Vendor " + v.id + " / paying company code " + cc + " (" + v.name + ")", "FZ", "691"); any = true; }
                    Run lock = lockedBy(i, r);
                    if (lock != null) {
                        r.exceptions.add(new Exc(i, "003", "Account blocked by payment proposal " + d(lock.laufd) + " " + lock.laufi));
                        log(l, ">  Document " + i.belnr + ": account " + v.id + " " + cc + " is blocked by payment proposal " + d(lock.laufd) + " " + lock.laufi, "FZ", "614");
                    } else if (!i.due.isBefore(row.nedat)) {
                        if (detail && r.logDue) log(l, ">  Document " + i.belnr + " line item 001 is due on " + d(i.due) + ": not yet due, next payment run on " + d(row.nedat), "FZ", "701");
                    } else if (!i.block.isEmpty()) {
                        r.exceptions.add(new Exc(i, "026", "Item is blocked for payment (payment block " + i.block + ")"));
                        log(l, ">  Document " + i.belnr + " line item 001 is blocked for payment (payment block " + i.block + ")", "FZ", "725");
                    } else {
                        due.add(i);
                        if (detail && r.logDue) log(l, ">  Document " + i.belnr + " line item 001 via " + i.curr + " " + amt(i.amount) + ", due on " + d(i.due) + ": item is paid", "FZ", "700");
                    }
                }
                if (due.isEmpty()) continue;
                String m = "";
                for (char c : row.methods.toUpperCase().toCharArray()) if (v.methods.indexOf(c) >= 0) { m = String.valueOf(c); break; }
                String err = null, code = null;
                if (m.isEmpty()) { code = "006"; err = "No valid payment method found"; }
                else if (!m.equals("C") && v.bank.isEmpty()) { code = "016"; err = "Payment method " + m + ": bank details missing in vendor master record"; }
                if (err != null) {
                    for (Item i : due) r.exceptions.add(new Exc(i, code, err));
                    log(l, ">  " + err + " - see exception list", "FZ", "6" + code.substring(1));
                    continue;
                }
                Payment p = new Payment();
                p.bukrs = cc; p.vendor = v.id; p.method = m; p.houseBank = co.houseBank(); p.curr = co.curr();
                for (Item i : due) { p.items.add(i); p.amount = p.amount.add(i.amount); }
                r.payments.add(p);
                if (detail && (r.logMethAll || r.logMethFail))
                    log(l, ">  Payment method " + m + " selected, house bank " + p.houseBank + ", account CHK01", "FZ", "650");
                log(l, ">  Payment proposed: " + p.items.size() + " item(s), " + p.curr + " " + amt(p.amount) + ", payment method " + m, "FZ", "740");
            }
        }
        log(l, "> " + r.payments.size() + " payment(s) proposed, " + r.exceptions.size() + " item(s) in exception list", "FZ", "742");
        log(l, "End of log", "FZ", "398");
        log(l, "Job finished", "00", "517");
        r.prop = 2;
    }

    /** Payment run (program SAPF110S, update mode): posts payment documents, clears items, creates BCM batches. */
    static void payment(Run r) {
        List<LogLine> l = r.payLog = new ArrayList<>();
        log(l, "Job started", "00", "516");
        log(l, "Step 001 started (program SAPF110S, variant &0000000000" + (510 + RUNS.indexOf(r)) + ", user ID " + Main.user + ")", "00", "550");
        log(l, "Log for payment run for payment on " + d(r.laufd) + ", identification " + r.laufi, "FZ", "402");
        Map<String, Batch> batches = new LinkedHashMap<>();
        boolean advice = !r.variants.getOrDefault("RFFOAVIS_FPAYM", "").isEmpty();
        for (Payment p : r.payments) {
            p.docNo = String.valueOf(nextPayDoc++);
            for (Item i : p.items) { i.clearing = p.docNo; i.clearDate = r.budat; }
            log(l, ">", "FZ", "693");
            log(l, "> Posting documents additional log", "FZ", "691");
            log(l, "> Document " + p.docNo + " company code " + p.bukrs + " currency " + p.curr + " payment method " + p.method, "FZ", "741");
            log(l, ">  LItm PK Acct       RA        Amount", "FZ", "743");
            log(l, ">  001  25 " + String.format("%-10s", p.vendor) + "      " + String.format("%14s", amt(p.amount)), "FZ", "744");
            log(l, ">  002  50 0000113100      " + String.format("%14s", amt(p.amount)) + "-", "FZ", "744");
            if (advice) log(l, ">  Payment advice note for vendor " + p.vendor + " sent by e-mail to " + VENDORS.get(p.vendor).email, "FZ", "760");
            Batch b = batches.computeIfAbsent(p.bukrs + p.method, k -> {
                Batch nb = new Batch();
                nb.id = String.valueOf(nextBatch++); nb.bukrs = p.bukrs; nb.method = p.method; nb.curr = p.curr; nb.runKey = r.key();
                return nb;
            });
            b.count++; b.amount = b.amount.add(p.amount);
        }
        for (Batch b : batches.values()) {
            b.required = b.amount.compareTo(DUAL_APPROVAL_LIMIT) > 0 ? 2 : 1;
            b.rule = b.required == 2 ? "BNK_02 Dual approval > 50,000.00" : "BNK_01 Single approval";
            BATCHES.add(b);
            log(l, "> Payment batch " + b.id + " created in Bank Communication Management (" + b.count + " payment(s), " + b.curr + " " + amt(b.amount) + ")", "BNK_COM", "025");
        }
        r.postings = r.payments.size();
        log(l, "> Posting orders: " + r.postings + " generated, " + r.postings + " completed", "FZ", "745");
        log(l, "End of log", "FZ", "398");
        if (r.medium) log(l, "Step 002 started (program SAPFPAYM_SCHEDULE, variant &0000000000" + (610 + RUNS.indexOf(r)) + ", user ID " + Main.user + ")", "00", "550");
        log(l, "Job finished", "00", "517");
        r.pay = 2;
    }

    /** Moves a proposed payment to the exception list (Edit Proposal: set payment block). */
    static void blockPayment(Run r, Payment p) {
        r.payments.remove(p);
        for (Item i : p.items) r.exceptions.add(new Exc(i, "098", "Payment blocked in proposal by user " + Main.user));
        r.edited = true;
    }
}
