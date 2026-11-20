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
        /** Tempos medidos com "O ônibus chegou agora" (últimos 5). */
        public final List<Integer> samples = new ArrayList<>();
        public String title() { return Providers.title(code, name); }
    }

    /** Ponto de ônibus no Google Maps, com o nome dado por você. */
    public static final class Stop {
        public String name, url;
        public Stop(String name, String url) { this.name = name; this.url = url; }
    }

    public static final class UserList {
        public String id = UUID.randomUUID().toString(), name = "", maps = "";
        public boolean hasLoc;
        public double lat, lon;
        /** Células da operadora vistas no local da lista (ver Cells). */
        public final List<String> cells = new ArrayList<>();
        /** Pontos no Google Maps, na ordem em que foram adicionados. */
        public final List<Stop> stops = new ArrayList<>();
        /** A lista está ligada a um local (GPS ou células)? */
        public boolean hasPlace() { return hasLoc || !cells.isEmpty(); }
        public final List<Item> items = new ArrayList<>();
    }

    private final File dir;
    private List<UserList> lists;
    private final java.util.Map<String, FenixParser.Timetable> ttCache = new java.util.HashMap<>();

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

    public void saveLines(List<FenixParser.Line> lines) { saveLines(LINES_FILE, lines); }

    public List<FenixParser.Line> loadLines() { return loadLines(LINES_FILE); }

    /** Linhas intermunicipais (todas as empresas juntas). */
    public static final String INTER_FILE = "linhas_intermunicipais.json";

    public void saveLines(String file, List<FenixParser.Line> lines) {
        try {
            JSONArray a = new JSONArray();
            for (FenixParser.Line l : lines)
                a.put(new JSONObject().put("c", l.code).put("n", l.name).put("u", l.url).put("g", l.category));
            write(file, a.toString());
        } catch (Exception ignored) { }
    }

    public List<FenixParser.Line> loadLines(String file) {
        List<FenixParser.Line> out = new ArrayList<>();
        String s = read(file);
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
        synchronized (ttCache) { ttCache.put(tt.code, tt); }
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
            JSONArray itin = new JSONArray();
            for (String st : tt.itinerary) itin.put(st);
            write(ttFile(tt.code), new JSONObject().put("c", tt.code).put("n", tt.name)
                    .put("u", tt.updated).put("deps", deps).put("it", itin).put("lg", new JSONObject(tt.legend)).toString());
        } catch (Exception ignored) { }
    }

    public FenixParser.Timetable loadTimetable(String code) {
        synchronized (ttCache) {
            if (ttCache.containsKey(code)) return ttCache.get(code);
        }
        FenixParser.Timetable tt = loadTimetableFile(code);
        if (tt != null) synchronized (ttCache) { ttCache.put(code, tt); }
        return tt;
    }

    private FenixParser.Timetable loadTimetableFile(String code) {
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
            JSONObject lg = o.optJSONObject("lg");
            if (lg != null) {
                java.util.Iterator<String> keys = lg.keys();
                while (keys.hasNext()) { String k = keys.next(); tt.legend.put(k, lg.optString(k)); }
            }
            JSONArray itin = o.optJSONArray("it");
            if (itin != null) for (int i = 0; i < itin.length(); i++) tt.itinerary.add(itin.optString(i));
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

    private static List<UserList> listsFromJson(JSONArray a) throws org.json.JSONException {
        List<UserList> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            UserList l = new UserList();
            l.id = o.getString("id"); l.name = o.getString("name");
            l.hasLoc = o.optBoolean("hasLoc"); l.maps = o.optString("maps"); l.lat = o.optDouble("lat", 0); l.lon = o.optDouble("lon", 0);
            JSONArray st = o.optJSONArray("stops");
            if (st != null) for (int k = 0; k < st.length(); k++) {
                JSONObject so = st.optJSONObject(k);
                if (so != null && !so.optString("url").isEmpty()) l.stops.add(new Stop(so.optString("name"), so.optString("url")));
            }
            // Link antigo (versões anteriores tinham um link só): vira o primeiro ponto.
            if (l.stops.isEmpty() && l.maps != null && !l.maps.isEmpty()) l.stops.add(new Stop("Ponto", l.maps));
            JSONArray cl = o.optJSONArray("cells");
            if (cl != null) for (int k = 0; k < cl.length(); k++) l.cells.add(cl.optString(k));
            JSONArray items = o.getJSONArray("items");
            for (int j = 0; j < items.length(); j++) {
                JSONObject x = items.getJSONObject(j);
                Item it = new Item();
                it.code = x.getString("code"); it.name = x.getString("name"); it.url = x.getString("url");
                it.dep = x.getString("dep"); it.depIdx = x.optInt("depIdx"); it.offset = x.optInt("offset");
                JSONArray sm = x.optJSONArray("samples");
                if (sm != null) for (int k = 0; k < sm.length(); k++) it.samples.add(sm.optInt(k));
                l.items.add(it);
            }
            out.add(l);
        }
        return out;
    }

    private static JSONArray listsToJson(List<UserList> lists) throws org.json.JSONException {
        JSONArray a = new JSONArray();
        for (UserList l : lists) {
            JSONArray items = new JSONArray();
            for (Item it : l.items)
            {
                JSONArray sm = new JSONArray();
                for (int v : it.samples) sm.put(v);
                items.put(new JSONObject().put("code", it.code).put("name", it.name).put("url", it.url)
                        .put("dep", it.dep).put("depIdx", it.depIdx).put("offset", it.offset)
                        .put("samples", sm));
            }
            JSONArray cl = new JSONArray();
            for (String c : l.cells) cl.put(c);
            JSONObject o = new JSONObject().put("id", l.id).put("name", l.name).put("hasLoc", l.hasLoc).put("maps", l.maps)
                    .put("cells", cl).put("stops", stopsJson(l.stops)).put("items", items);
            if (l.hasLoc) o.put("lat", l.lat).put("lon", l.lon);
            a.put(o);
        }
        return a;
    }

    private static JSONArray stopsJson(List<Stop> stops) throws org.json.JSONException {
        JSONArray a = new JSONArray();
        for (Stop s : stops) a.put(new JSONObject().put("name", s.name).put("url", s.url));
        return a;
    }

    public synchronized List<UserList> lists() {
        if (lists != null) return lists;
        lists = new ArrayList<>();
        String s = read(LISTS_FILE);
        if (s == null) return lists;
        try { lists.addAll(listsFromJson(new JSONArray(s))); } catch (Exception ignored) { }
        return lists;
    }

    public synchronized void saveLists() {
        try { write(LISTS_FILE, listsToJson(lists()).toString()); } catch (Exception ignored) { }
    }

    // ---------- backup ----------

    /** Texto do backup: todas as listas, com linhas, minutos, local GPS e link do Google Maps. */
    public synchronized String exportBackup() throws org.json.JSONException {
        return new JSONObject().put("app", "busao-floripa").put("versao", 1)
                .put("criado", java.time.LocalDateTime.now().toString())
                .put("listas", listsToJson(lists())).toString(2);
    }

    /** Lê um backup sem aplicar. Lança erro se o arquivo não for um backup válido. */
    public static List<UserList> readBackup(String text) throws Exception {
        JSONObject o = new JSONObject(text);
        if (!"busao-floripa".equals(o.optString("app"))) throw new Exception("Este arquivo não é um backup do Busão Floripa G");
        return listsFromJson(o.getJSONArray("listas"));
    }

    /** Aplica um backup. Substituir: troca tudo. Juntar: acrescenta, e a lista com o mesmo nome é trocada. */
    public synchronized void applyBackup(List<UserList> from, boolean replace) {
        List<UserList> cur = lists();
        if (replace) cur.clear();
        for (UserList l : from) {
            for (int i = cur.size() - 1; i >= 0; i--) {
                UserList c = cur.get(i);
                if (c.id.equals(l.id) || c.name.equalsIgnoreCase(l.name)) cur.remove(i);
            }
            cur.add(l);
        }
        saveLists();
    }

    public UserList list(String id) {
        for (UserList l : lists()) if (l.id.equals(id)) return l;
        return null;
    }
}
