package br.com.busfloripa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor das empresas intermunicipais (Estrela, Santa Terezinha). Separado do leitor do Fênix,
 * que não muda. Lê páginas em que a saída vem antes dos dias ("Saída Sertão" > "SEGUNDA A SEXTA" >
 * horários), listas ou tabelas, horários "05:40" ou "05.40", com letras do lado ("UN. B.", "SC EXP").
 */
public final class GenericParser {

    private GenericParser() { }

    private static final Pattern TOKEN = Pattern.compile(
            "<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>|([^<]+)|<", Pattern.DOTALL);
    private static final Pattern STRIP = Pattern.compile(
            "<!--.*?-->|<(script|style|noscript|svg|template)\\b.*?</\\1\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // ---------- índices ----------

    /** Estrela (insulartc.com.br/estrela/): links para .../linhas/horarios/.../NNNN.htm; código na célula ao lado. */
    public static List<FenixParser.Line> parseIndexEstrela(String html, String baseUrl) {
        html = STRIP.matcher(html).replaceAll(" ");
        Map<String, FenixParser.Line> out = new LinkedHashMap<>();
        Matcher m = TOKEN.matcher(html);
        String href = null, lastCode = null;
        StringBuilder buf = null;
        Pattern file = Pattern.compile("/linhas/horarios/[^\"']*?([0-9A-Za-z]+)\\.htm", Pattern.CASE_INSENSITIVE);
        while (m.find()) {
            String tag = m.group(2);
            if (tag != null) {
                tag = tag.toLowerCase(Locale.ROOT);
                boolean close = !m.group(1).isEmpty();
                if (tag.equals("tr") && !close) lastCode = null;
                if (tag.equals("a")) {
                    if (!close) {
                        String h = FenixParser.attr(m.group(3), "href");
                        href = h != null && file.matcher(h).find() ? h : null;
                        buf = href != null ? new StringBuilder() : null;
                    } else if (href != null) {
                        Matcher fm = file.matcher(href);
                        fm.find();
                        String name = FenixParser.norm(buf.toString());
                        String shown = lastCode != null ? lastCode : fm.group(1).replaceFirst("^0+(?=\\d)", "");
                        String key = "EST#" + fm.group(1);
                        // O site redireciona http -> https, e o Android não segue essa troca: usa https direto.
                        String url = absolute(href, baseUrl).replaceFirst("^http://", "https://");
                        if (!name.isEmpty() && !out.containsKey(key))
                            out.put(key, new FenixParser.Line(key, shown + " " + name, url, "Estrela"));
                        href = null; buf = null;
                    }
                }
            } else if (m.group(4) != null) {
                String t = FenixParser.decode(m.group(4));
                if (buf != null) { buf.append(t); continue; }
                String n = FenixParser.norm(t);
                if (n.length() <= 20 && n.matches(".*\\d.*") && n.matches("[0-9.,/ e-]+")) lastCode = n;
            }
        }
        return new ArrayList<>(out.values());
    }

    /** Santa Terezinha (santaterezinha.com/horarios/): links /horarios/nome-da-linha/. */
    public static List<FenixParser.Line> parseIndexSantaTerezinha(String html) {
        html = STRIP.matcher(html).replaceAll(" ");
        Map<String, FenixParser.Line> out = new LinkedHashMap<>();
        Matcher m = TOKEN.matcher(html);
        String href = null, slug = null;
        StringBuilder buf = null;
        Pattern p = Pattern.compile("/horarios/([a-z0-9-]+)/?$");
        while (m.find()) {
            String tag = m.group(2);
            if (tag != null) {
                if (!tag.equalsIgnoreCase("a")) continue;
                if (m.group(1).isEmpty()) {
                    String h = FenixParser.attr(m.group(3), "href");
                    Matcher pm = h == null ? null : p.matcher(h.replaceFirst("[?#].*$", ""));
                    if (pm != null && pm.find()) { href = h; slug = pm.group(1); buf = new StringBuilder(); }
                    else { href = null; buf = null; }
                } else if (href != null) {
                    String name = FenixParser.norm(buf.toString());
                    String key = "STE#" + slug;
                    if (!name.isEmpty() && !out.containsKey(key))
                        out.put(key, new FenixParser.Line(key, name, absolute(href, "https://santaterezinha.com/"), "Santa Terezinha"));
                    href = null; buf = null;
                }
            } else if (m.group(4) != null && buf != null) buf.append(FenixParser.decode(m.group(4)));
        }
        return new ArrayList<>(out.values());
    }

    static String absolute(String href, String base) {
        if (href.startsWith("http")) return href;
        try { return new java.net.URL(new java.net.URL(base), href).toString(); } catch (Exception e) { return base + href; }
    }

    // ---------- página de horários ----------

    /** Um horário com as letras do lado; o resto do texto não pode ter outras palavras. */
    private static final Pattern TIME = Pattern.compile(
            "(?<![\\d/])(\\d{1,2})[:.h](\\d{2})(?![\\d:/])((?:\\s*(?:[A-Za-z]{1,6}\\.?\\*?|\\*|\\^[\\d,]+))*)");
    private static final Pattern FLAGS_ONLY = Pattern.compile("^(?:[A-Z][A-Z.]{0,5}\\*?\\s*|\\*\\s*){1,5}$");
    private static final Pattern LEGEND = Pattern.compile("^([A-Z][A-Z0-9]{0,5})\\.?\\*?\\s*[-–=:]\\s*(.{3,90})$");

    static int dayOf(String text) {
        String f = FenixParser.fold(text);
        if (f.isEmpty() || f.length() > 35) return -1;
        if (f.matches("(de )?segunda a sexta( feira)?|dias? ute?is|dia util|seg a sex|segunda a sexta feira")
                || f.matches("2a? a 6a?( feira)?( dias? ute?is)?") || f.endsWith(" dias uteis")) return FenixParser.UTEIS;
        if (f.matches("sabados?|sabados? e vesperas?.*")) return FenixParser.SABADO;
        if (f.matches("dom[ia]n?gos?( e feriados?)?|dom[ia]n?gos? feriados?|feriados?|domingos?/feriados?")) return FenixParser.DOMINGO;
        return -1;
    }

    /** "Saída Sertão", "Saídas de Biguaçu", "PARTIDAS DO TICEN / FPOLIS. (VOLTA)" -> nome da saída. */
    static String saidaName(String text) {
        String n = FenixParser.norm(text);
        if (n.length() > 80) return null;
        Matcher m = Pattern.compile("^(?i)(?:sa[ií]das?|partidas?)\\s*(?:(?:d[aoe]s?)\\s+)?[:\\-–]?\\s*(.+)$").matcher(n);
        if (!m.find()) return null;
        String s = m.group(1).replaceAll("(?i)\\s*\\((ida|volta)\\)\\s*$", "").replaceAll("[.\\s]+$", "").trim();
        if (s.isEmpty() || TIME.matcher(s).find()) return null;
        return s;
    }

    /** Horários de um texto, ou nulo se o texto tiver outras palavras além de horários e letras. */
    static List<int[]> times(String n, List<String> flagsOut) {
        Matcher m = TIME.matcher(n);
        List<int[]> out = new ArrayList<>();
        StringBuilder rest = new StringBuilder();
        int last = 0;
        while (m.find()) {
            int h = Integer.parseInt(m.group(1)), mi = Integer.parseInt(m.group(2));
            if (h > 29 || mi > 59) return null;
            rest.append(n, last, m.start());
            last = m.end();
            out.add(new int[]{h * 60 + mi});
            flagsOut.add(cleanFlags(m.group(3)));
        }
        rest.append(n.substring(last));
        if (out.isEmpty()) return null;
        if (rest.toString().matches(".*[\\p{L}\\d].*")) return null;
        return out;
    }

    static String cleanFlags(String raw) {
        if (raw == null) return "";
        List<String> parts = new ArrayList<>();
        for (String p : raw.trim().split("\\s+")) {
            String c = p.replaceAll("\\^[\\d,]+", "").replace(".", "").trim();
            if (!c.isEmpty() && !parts.contains(c)) parts.add(c);
        }
        return String.join(" ", parts);
    }

    /**
     * Ordena; horários da madrugada no fim da lista (ex.: 00:15) contam como +24h.
     * Dois ônibus no mesmo minuto com letras diferentes (vias diferentes) ficam os dois.
     */
    static List<FenixParser.Trip> wrapAndSort(List<FenixParser.Trip> in) {
        List<FenixParser.Trip> out = new ArrayList<>();
        int offset = 0, prev = -1;
        for (FenixParser.Trip t : in) {
            int v = t.min + offset;
            if (prev >= 0 && v < prev - 180 && t.min < 6 * 60) { offset += 1440; v = t.min + offset; }
            out.add(new FenixParser.Trip(v, t.flags));
            prev = v;
        }
        java.util.Collections.sort(out);
        List<FenixParser.Trip> dedup = new ArrayList<>();
        for (FenixParser.Trip t : out) {
            boolean dup = false;
            for (FenixParser.Trip d : dedup) if (d.min == t.min && d.flags.equals(t.flags)) { dup = true; break; }
            if (!dup) dedup.add(t);
        }
        return dedup;
    }

    public static FenixParser.Timetable parseLine(String html) {
        html = STRIP.matcher(html).replaceAll(" ");
        FenixParser.Timetable tt = new FenixParser.Timetable();
        Matcher up = Pattern.compile("(?i)atualiza\\S*\\s+em\\s*:?\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})")
                .matcher(html.replaceAll("<[^>]*>", " "));
        if (up.find()) tt.updated = up.group(1);

        String dep = null;
        int day = -1;
        int tableDepth = 0, col = -1, colStart = -1;
        boolean headerRow = false;
        Map<Integer, Integer> colDay = new LinkedHashMap<>();
        Map<Integer, String> colDep = new LinkedHashMap<>();
        FenixParser.Trip[] lastTrip = {null};
        String lastDep = null;
        int lastDay = -1;
        boolean inAnchor = false;
        StringBuilder anchor = new StringBuilder();
        // Saída -> dia -> horários
        Map<String, List<List<FenixParser.Trip>>> data = new LinkedHashMap<>();

        Matcher m = TOKEN.matcher(html);
        while (m.find()) {
            String tag = m.group(2), text = null;
            if (tag != null) {
                tag = tag.toLowerCase(Locale.ROOT);
                boolean close = !m.group(1).isEmpty();
                switch (tag) {
                    case "table":
                        if (!close) { if (tableDepth++ == 0) { colDay.clear(); colDep.clear(); } }
                        else if (tableDepth > 0) tableDepth--;
                        continue;
                    case "tr":
                        if (!close) { col = -1; headerRow = false; }
                        continue;
                    case "td": case "th":
                        if (!close) {
                            // Célula larga (colspan) cobre várias colunas: colStart..col.
                            int span = 1;
                            String sp = FenixParser.attr(m.group(3), "colspan");
                            if (sp != null) try { span = Math.max(1, Integer.parseInt(sp.trim())); } catch (NumberFormatException ignored) { }
                            colStart = col + 1;
                            col += span;
                            if (tag.equals("th")) headerRow = true;
                        }
                        continue;
                    case "a":
                        if (!close) { inAnchor = true; anchor.setLength(0); continue; }
                        if (!inAnchor) continue;
                        inAnchor = false;
                        text = anchor.toString();
                        break;
                    case "br":
                        if (inAnchor) anchor.append(' ');
                        else text = "\n";
                        break;
                    default:
                        continue;
                }
            } else if (m.group(4) != null) {
                String t = FenixParser.decode(m.group(4));
                if (inAnchor) { anchor.append(t); continue; }
                text = t;
            } else continue;

            // Um texto pode ter várias linhas; trata cada uma.
            for (String piece : text.split("\\n")) {
                String n = FenixParser.norm(piece);
                if (n.isEmpty()) continue;
                int d = dayOf(n);
                if (d >= 0) {
                    if (tableDepth > 0 && col >= 0) for (int k = Math.max(colStart, 0); k <= col; k++) colDay.put(k, d);
                    day = d;
                    lastTrip[0] = null;
                    continue;
                }
                String s = saidaName(n);
                if (s != null) {
                    if (tableDepth > 0 && col >= 0) for (int k = Math.max(colStart, 0); k <= col; k++) colDep.put(k, s);
                    if (!s.equals(dep)) day = -1;
                    dep = s;
                    lastTrip[0] = null;
                    continue;
                }
                List<String> fl = new ArrayList<>();
                List<int[]> ts = times(n, fl);
                if (ts != null) {
                    String useDep = tableDepth > 0 && colDep.containsKey(colStart) ? colDep.get(colStart) : dep;
                    int useDay = tableDepth > 0 && colDay.containsKey(colStart) ? colDay.get(colStart) : day;
                    if (useDep == null) useDep = "Saída";
                    if (useDay < 0) useDay = FenixParser.UTEIS;
                    List<List<FenixParser.Trip>> days = data.get(useDep);
                    if (days == null) {
                        days = new ArrayList<>();
                        for (int k = 0; k < 3; k++) days.add(new ArrayList<>());
                        data.put(useDep, days);
                    }
                    for (int k = 0; k < ts.size(); k++) {
                        FenixParser.Trip trip = new FenixParser.Trip(ts.get(k)[0], fl.get(k));
                        days.get(useDay).add(trip);
                        lastTrip[0] = trip;
                    }
                    lastDep = useDep;
                    lastDay = useDay;
                    continue;
                }
                if (lastTrip[0] != null && FLAGS_ONLY.matcher(n).matches() && lastDep != null) {
                    // Letras numa marcação separada do horário: junta ao último horário.
                    List<FenixParser.Trip> l = data.get(lastDep).get(lastDay);
                    FenixParser.Trip t = l.remove(l.size() - 1);
                    String merged = (t.flags + " " + cleanFlags(n)).trim();
                    FenixParser.Trip nt = new FenixParser.Trip(t.min, merged);
                    l.add(nt);
                    lastTrip[0] = nt;
                    continue;
                }
                Matcher star = Pattern.compile("^\\*\\s*(.{3,90})$").matcher(n);
                if (star.find()) { tt.legend.put("*", star.group(1).trim()); lastTrip[0] = null; continue; }
                Matcher lg = LEGEND.matcher(n);
                if (lg.find() && !n.contains("R$")) {
                    String key = lg.group(1).replace(".", "");
                    if (!tt.legend.containsKey(key)) tt.legend.put(key, lg.group(2).trim());
                }
                lastTrip[0] = null;
            }
        }
        for (Map.Entry<String, List<List<FenixParser.Trip>>> e : data.entrySet()) {
            FenixParser.Departure d = new FenixParser.Departure(e.getKey());
            for (int k = 0; k < 3; k++) d.days.set(k, wrapAndSort(e.getValue().get(k)));
            if (d.total() > 0) tt.departures.add(d);
        }
        return tt;
    }
}
