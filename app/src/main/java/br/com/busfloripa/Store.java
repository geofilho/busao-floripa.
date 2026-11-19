package br.com.busfloripa;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tudo fica salvo no celular em arquivos JSON pequenos: funciona sem internet. */
public final class Store {

    public static final class Item {
        public String code, name, url, dep;
        public int depIdx, offset;
        public String title() { return code + " " + name; }
    }

    public static final class UserList {
        public String id = UUID.randomUUID().toString(), name = "", maps = "";
        public boolean hasLoc;
        public double lat, lon;
        public final List<Item> items = new ArrayList<>();
    }

    private final File dir;
    private List<UserList> lists;

    private static Store instance;
    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }
    private Store(Context c) { dir = c.getFilesDir(); }

    // ---------- arquivos ----------

    private synchronized String read(String name) {
        File f = new File(dir, name);
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return new String(b, 0, off, StandardCharsets.UTF_8);
        } catch (IOException e) { return null; }
    }

    private synchronized void write(String name, String data) {
        File tmp = new File(dir, name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { return; }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(new File(dir, name));
    }

    public long age(String name) {
        File f = new File(dir, name);
        return f.exists() ? System.currentTimeMillis() - f.lastModified() : Long.MAX_VALUE;
    }

    // ---------- linhas ----------

    public static final String LINES_FILE = "linhas.json";

    public void saveLines(List<FenixParser.Line> lines) {
        try {
            JSONArray a = new JSONArray();
            for (FenixParser.Line l : lines)
                a.put(new JSONObject().put("c", l.code).put("n", l.name).put("u", l.url).put("g", l.category));
            write(LINES_FILE, a.toString());
        } catch (Exception ignored) { }
    }

    public List<FenixParser.Line> loadLines() {
        List<FenixParser.Line> out = new ArrayList<>();
        String s = read(LINES_FILE);
        if (s == null) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new FenixParser.Line(o.getString("c"), o.getString("n"), o.getString("u"), o.optString("g")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ---------- horários ----------

    public static String ttFile(String code) { return "linha_" + code.replaceAll("[^A-Za-z0-9-]", "_") + ".json"; }

    public void saveTimetable(FenixParser.Timetable tt) {
        try {
            JSONArray deps = new JSONArray();
            for (FenixParser.Departure d : tt.departures) {
                JSONArray days = new JSONArray();
                for (List<FenixParser.Trip> day : d.days) {
                    JSONArray ta = new JSONArray();
                    for (FenixParser.Trip t : day) ta.put(new JSONArray().put(t.min).put(t.flags));
                    days.put(ta);
                }
                deps.put(new JSONObject().put("n", d.name).put("d", days));
            }
            write(ttFile(tt.code), new JSONObject().put("c", tt.code).put("n", tt.name)
                    .put("u", tt.updated).put("deps", deps).toString());
        } catch (Exception ignored) { }
    }

    public FenixParser.Timetable loadTimetable(String code) {
        String s = read(ttFile(code));
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            FenixParser.Timetable tt = new FenixParser.Timetable();
            tt.code = o.getString("c"); tt.name = o.optString("n"); tt.updated = o.optString("u");
            JSONArray deps = o.getJSONArray("deps");
            for (int i = 0; i < deps.length(); i++) {
                JSONObject d = deps.getJSONObject(i);
                FenixParser.Departure dep = new FenixParser.Departure(d.getString("n"));
                JSONArray days = d.getJSONArray("d");
                for (int k = 0; k < 3 && k < days.length(); k++) {
                    JSONArray ta = days.getJSONArray(k);
                    for (int j = 0; j < ta.length(); j++) {
                        JSONArray t = ta.getJSONArray(j);
                        dep.days.get(k).add(new FenixParser.Trip(t.getInt(0), t.optString(1)));
                    }
                }
                tt.departures.add(dep);
            }
            return tt;
        } catch (Exception e) { return null; }
    }

    /** Todas as linhas com horários salvos no celular. */
    public List<FenixParser.Timetable> cachedTimetables() {
        List<FenixParser.Timetable> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName();
            if (!n.startsWith("linha_") || !n.endsWith(".json")) continue;
            String s = read(n);
            if (s == null) continue;
            try {
                FenixParser.Timetable tt = loadTimetable(new JSONObject(s).getString("c"));
                if (tt != null) out.add(tt);
            } catch (Exception ignored) { }
        }
        return out;
    }

    // ---------- listas do usuário ----------

    private static final String LISTS_FILE = "listas.json";

    public synchronized List<UserList> lists() {
        if (lists != null) return lists;
        lists = new ArrayList<>();
        String s = read(LISTS_FILE);
        if (s == null) return lists;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                UserList l = new UserList();
                l.id = o.getString("id"); l.name = o.getString("name");
                l.hasLoc = o.optBoolean("hasLoc"); l.maps = o.optString("maps"); l.lat = o.optDouble("lat", 0); l.lon = o.optDouble("lon", 0);
                JSONArray items = o.getJSONArray("items");
                for (int j = 0; j < items.length(); j++) {
                    JSONObject x = items.getJSONObject(j);
                    Item it = new Item();
                    it.code = x.getString("code"); it.name = x.getString("name"); it.url = x.getString("url");
                    it.dep = x.getString("dep"); it.depIdx = x.optInt("depIdx"); it.offset = x.optInt("offset");
                    l.items.add(it);
                }
                lists.add(l);
            }
        } catch (Exception ignored) { }
        return lists;
    }

    public synchronized void saveLists() {
        try {
            JSONArray a = new JSONArray();
            for (UserList l : lists()) {
                JSONArray items = new JSONArray();
                for (Item it : l.items)
                    items.put(new JSONObject().put("code", it.code).put("name", it.name).put("url", it.url)
                            .put("dep", it.dep).put("depIdx", it.depIdx).put("offset", it.offset));
                JSONObject o = new JSONObject().put("id", l.id).put("name", l.name).put("hasLoc", l.hasLoc).put("maps", l.maps).put("items", items);
                if (l.hasLoc) o.put("lat", l.lat).put("lon", l.lon);
                a.put(o);
            }
            write(LISTS_FILE, a.toString());
        } catch (Exception ignored) { }
    }

    public UserList list(String id) {
        for (UserList l : lists()) if (l.id.equals(id)) return l;
        return null;
    }
}
