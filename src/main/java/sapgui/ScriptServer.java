package sapgui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Local scripting interface modelled on the SAP GUI Scripting object model (findById, press, sendVKey...),
 * exposed over HTTP on the loopback interface. Every call is appended to logs/scripting-audit.log.
 */
final class ScriptServer {
    private static final Path AUDIT = Path.of("logs", "scripting-audit.log");

    static void start(int port) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        s.createContext("/", ScriptServer::handle);
        s.setExecutor(Executors.newSingleThreadExecutor());
        s.start();
        System.out.println("Scripting interface listening on http://127.0.0.1:" + port);
    }

    private static void handle(HttpExchange ex) throws IOException {
        Map<String, String> q = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        String bodyIn = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        for (String src : new String[] {raw, bodyIn}) {
            if (src == null || src.isEmpty()) continue;
            for (String kv : src.split("&")) {
                int i = kv.indexOf('=');
                if (i > 0) q.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        String path = ex.getRequestURI().getPath();
        byte[] out;
        String type = "application/json; charset=utf-8";
        int code = 200;
        try {
            if (path.equals("/screenshot")) { out = screenshot(); type = "image/png"; }
            else out = route(path, q).getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            code = 400; out = ("{\"error\":" + js(e.getMessage()) + "}").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            code = 500; out = ("{\"error\":" + js(String.valueOf(e)) + "}").getBytes(StandardCharsets.UTF_8);
        }
        audit(path, q, code);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private static void audit(String path, Map<String, String> q, int code) {
        if (path.equals("/screenshot") || path.equals("/session") || path.equals("/objects")) return;
        try {
            Files.createDirectories(AUDIT.getParent());
            Map<String, String> safe = new HashMap<>(q);
            if (String.valueOf(q.get("id")).contains("pwd")) safe.put("value", "********");
            Files.writeString(AUDIT, LocalDateTime.now() + " user=" + Main.user + " " + path + " " + safe + " -> " + code + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    /** Runs an action on the event thread without blocking if it opens a modal dialog. */
    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeLater(r);
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(80);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static Container root() {
        Popup p = Popup.OPEN.peek();
        if (p != null) return p;
        if (SapWindow.current == null) throw new IllegalArgumentException("No session available");
        return SapWindow.current;
    }

    private static void collect(Container c, List<Component> out) {
        for (Component k : c.getComponents()) {
            if (k.getName() != null && k.getName().startsWith("wnd[")) out.add(k);
            if (k instanceof Container cc) collect(cc, out);
        }
    }

    private static Component find(String id) {
        if (id == null) throw new IllegalArgumentException("Parameter 'id' is missing");
        List<Component> all = new ArrayList<>();
        collect(root(), all);
        for (Component c : all) if (c.getName().equals(id) || c.getName().endsWith("/" + id)) return c;
        throw new IllegalArgumentException("The control could not be found by id: " + id);
    }

    private static String route(String path, Map<String, String> q) throws Exception {
        switch (path) {
            case "/session", "/" -> { }
            case "/objects" -> { return objects(); }
            case "/table" -> { return table((JTable) find(q.get("id"))); }
            case "/transaction" -> {
                String t = String.valueOf(q.get("tcode")).toUpperCase();
                edt(() -> { if (Popup.OPEN.isEmpty() && Main.user != null) SapWindow.current.start(t); });
            }
            case "/vkey" -> {
                int k = Integer.parseInt(q.getOrDefault("key", "0"));
                edt(() -> {
                    Popup p = Popup.OPEN.peek();
                    if (p == null) SapWindow.current.vkey(k);
                    else if (k == 0) p.pressDefault();
                    else if (k == 12) p.close(-1);
                });
            }
            case "/set" -> {
                Component c = find(q.get("id"));
                String v = q.getOrDefault("value", "");
                if (c instanceof JTextComponent tc) {
                    if (!tc.isEditable() && !(tc instanceof JPasswordField)) throw new IllegalArgumentException("The control is not changeable: " + q.get("id"));
                    edt(() -> tc.setText(v));
                } else if (c instanceof JToggleButton tb) edt(() -> tb.setSelected(v.equalsIgnoreCase("true") || v.equalsIgnoreCase("x")));
                else throw new IllegalArgumentException("The control does not support 'text': " + q.get("id"));
            }
            case "/press", "/select" -> {
                Component c = find(q.get("id"));
                if (!(c instanceof AbstractButton b)) throw new IllegalArgumentException("The control cannot be pressed: " + q.get("id"));
                if (!b.isEnabled()) throw new IllegalArgumentException("The control is disabled: " + q.get("id"));
                edt(b::doClick);
            }
            case "/selectRow", "/doubleClickRow" -> {
                JTable t = (JTable) find(q.getOrDefault("id", "wnd[0]/usr/cntlGRID1/shellcont/shell"));
                int row = Integer.parseInt(q.getOrDefault("row", "0"));
                if (row < 0 || row >= t.getRowCount()) throw new IllegalArgumentException("Row " + row + " does not exist");
                edt(() -> {
                    t.setRowSelectionInterval(row, row);
                    if (path.equals("/doubleClickRow") && t.getClientProperty("dbl") instanceof IntConsumer h) h.accept(row);
                });
            }
            default -> throw new IllegalArgumentException("Unknown method " + path);
        }
        return session();
    }

    private static String session() throws Exception {
        String[] out = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            SapWindow w = SapWindow.current;
            Popup p = Popup.OPEN.peek();
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"system\":").append(js(Main.SID)).append(",\"client\":").append(js(Main.client)).append(",\"user\":").append(js(Main.user));
            if (w != null) {
                sb.append(",\"session\":").append(w.session).append(",\"transaction\":").append(js(w.screen.tcode().isEmpty() ? "SESSION_MANAGER" : w.screen.tcode()))
                        .append(",\"program\":").append(js(w.screen.program())).append(",\"title\":").append(js(w.screen.title()))
                        .append(",\"messageType\":").append(js(w.statusType)).append(",\"message\":").append(js(w.statusText));
            }
            sb.append(",\"activeWindow\":").append(js(p == null ? "wnd[0]" : p.wnd));
            if (p != null) {
                sb.append(",\"popupTitle\":").append(js(p.getTitle()));
                List<Component> all = new ArrayList<>();
                collect(p, all);
                all.sort(java.util.Comparator.comparingInt(Component::getY));
                StringBuilder txt = new StringBuilder();
                for (Component c : all) if (c instanceof JLabel l && l.isVisible() && l.getText() != null && !l.getText().isBlank()) txt.append(txt.length() > 0 ? " " : "").append(l.getText().trim());
                sb.append(",\"popupText\":").append(js(txt.toString()));
            }
            out[0] = sb.append("}").toString();
        });
        return out[0];
    }

    private static String objects() throws Exception {
        String[] out = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            List<Component> all = new ArrayList<>();
            collect(root(), all);
            StringBuilder sb = new StringBuilder("[");
            for (Component c : all) {
                String id = c.getName(), leaf = id.substring(id.lastIndexOf('/') + 1), text = "", t;
                boolean changeable = false;
                if (c instanceof JPasswordField) { t = "GuiPasswordField"; changeable = true; }
                else if (c instanceof JTextComponent tc) { t = leaf.startsWith("ctxt") ? "GuiCTextField" : leaf.equals("okcd") ? "GuiOkCodeField" : "GuiTextField"; text = tc.getText(); changeable = tc.isEditable(); }
                else if (c instanceof javax.swing.JCheckBox cb) { t = "GuiCheckBox"; text = cb.getText(); changeable = cb.isEnabled(); }
                else if (c instanceof javax.swing.JRadioButton rb) { t = "GuiRadioButton"; text = rb.getText(); changeable = rb.isEnabled(); }
                else if (c instanceof AbstractButton b) { t = leaf.startsWith("tabp") ? "GuiTab" : "GuiButton"; text = b.getText() == null || b.getText().isEmpty() ? String.valueOf(b.getToolTipText()) : b.getText(); changeable = b.isEnabled(); }
                else if (c instanceof JTable) t = "GuiGridView";
                else if (c instanceof javax.swing.JTree) t = "GuiTree";
                else if (c instanceof JLabel l) { t = leaf.equals("sbar") ? "GuiStatusbar" : leaf.equals("titl") ? "GuiTitlebar" : "GuiLabel"; text = l.getText() == null ? "" : l.getText().trim(); }
                else continue;
                if (sb.length() > 1) sb.append(',');
                sb.append("{\"id\":").append(js(id)).append(",\"type\":").append(js(t)).append(",\"text\":").append(js(text))
                        .append(",\"changeable\":").append(changeable).append(",\"visible\":").append(c.isShowing());
                if (c instanceof JToggleButton tb) sb.append(",\"selected\":").append(tb.isSelected());
                sb.append('}');
            }
            out[0] = sb.append("]").toString();
        });
        return out[0];
    }

    private static String table(JTable t) throws Exception {
        String[] out = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            StringBuilder sb = new StringBuilder("{\"columns\":[");
            for (int c = 0; c < t.getColumnCount(); c++) sb.append(c > 0 ? "," : "").append(js(t.getColumnName(c)));
            sb.append("],\"selectedRow\":").append(t.getSelectedRow()).append(",\"rows\":[");
            for (int r = 0; r < t.getRowCount(); r++) {
                sb.append(r > 0 ? ",[" : "[");
                for (int c = 0; c < t.getColumnCount(); c++) sb.append(c > 0 ? "," : "").append(js(String.valueOf(t.getValueAt(r, c))));
                sb.append(']');
            }
            out[0] = sb.append("]}").toString();
        });
        return out[0];
    }

    private static byte[] screenshot() throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            Container r = root();
            img[0] = new BufferedImage(Math.max(1, r.getWidth()), Math.max(1, r.getHeight()), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics g = img[0].getGraphics();
            r.paint(g);
            g.dispose();
        });
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img[0], "png", bos);
        return bos.toByteArray();
    }

    private static String js(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
