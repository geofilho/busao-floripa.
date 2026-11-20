package br.com.busfloripa;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê as páginas de horários do Consórcio Fênix (www.consorciofenix.com.br/horarios).
 * Java puro, sem dependências, para ser leve e testável fora do Android.
 *
 * O leitor não depende de classes CSS: procura textos "Saída ...", nomes de dias
 * ("Dias Úteis", "Sábado", "Domingos e Feriados") e horários no formato HH:MM,
 * funcionando com tabelas (uma coluna por saída) ou com blocos em sequência.
 */
public final class FenixParser {

    public static final String BASE = "https://www.consorciofenix.com.br";
    public static final String INDEX_URL = BASE + "/horarios";

    public static final int UTEIS = 0, SABADO = 1, DOMINGO = 2;
    public static final String[] DAY_NAMES = {"Dias úteis", "Sábado", "Domingos e feriados"};

    // ---------- Modelos ----------

    public static final class Line {
        public final String code, name, url, category;
        public Line(String code, String name, String url, String category) {
            this.code = code; this.name = name; this.url = url; this.category = category;
        }
        public String title() { return code + " " + name; }
    }

    public static final class Trip implements Comparable<Trip> {
        /** Minutos desde a meia-noite do dia de operação (pode passar de 1440 após a meia-noite). */
        public final int min;
        /** Letras do site: E (elevador), M (meia viagem), R (recolhe), * (previsto). */
        public final String flags;
        public Trip(int min, String flags) { this.min = min; this.flags = flags == null ? "" : flags; }
        @Override public int compareTo(Trip o) { return Integer.compare(min, o.min); }
    }

    public static final class Departure {
        public final String name;
        public final List<List<Trip>> days = new ArrayList<>();
        public Departure(String name) {
            this.name = name;
            for (int i = 0; i < 3; i++) days.add(new ArrayList<>());
        }
        public int total() { return days.get(0).size() + days.get(1).size() + days.get(2).size(); }
    }

    public static final class Timetable {
        public String code = "", name = "", updated = "";
        public final List<Departure> departures = new ArrayList<>();
        /** Ruas e lugares por onde a linha passa, em ordem (ida e volta). */
        public final List<String> itinerary = new ArrayList<>();
        /** Significado das letras dos horários (só nas empresas intermunicipais). */
        public final Map<String, String> legend = new LinkedHashMap<>();
        public int indexOf(String depName) {
            for (int i = 0; i < departures.size(); i++) if (departures.get(i).name.equals(depName)) return i;
            return -1;
        }
    }

    // ---------- Tokenização simples de HTML ----------

    private static final Pattern TOKEN = Pattern.compile(
            "<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>|([^<]+)|<", Pattern.DOTALL);
    private static final Pattern STRIP = Pattern.compile(
            "<!--.*?-->|<(script|style|noscript|svg|template)\\b.*?</\\1\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    static String attr(String attrs, String name) {
        Matcher m = Pattern.compile("(?:^|\\s)" + Pattern.quote(name) + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
                Pattern.CASE_INSENSITIVE).matcher(attrs);
        if (!m.find()) return null;
        String v = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
        return decode(v);
    }

    static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder out = new StringBuilder();
        Matcher m = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            String e = m.group(1), r = null;
            try {
                if (e.startsWith("#x") || e.startsWith("#X")) r = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                else if (e.startsWith("#")) r = new String(Character.toChars(Integer.parseInt(e.substring(1))));
            } catch (Exception ignored) { }
            if (r == null) r = ENTITIES.get(e);
            out.append(r != null ? r : m.group());
            last = m.end();
        }
        out.append(s.substring(last));
        return out.toString();
    }

    private static final Map<String, String> ENTITIES = new LinkedHashMap<>();
    static {
        String[] p = {"amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ",
                "aacute", "á", "Aacute", "Á", "agrave", "à", "acirc", "â", "Acirc", "Â", "atilde", "ã", "Atilde", "Ã",
                "eacute", "é", "Eacute", "É", "ecirc", "ê", "Ecirc", "Ê", "iacute", "í", "Iacute", "Í",
                "oacute", "ó", "Oacute", "Ó", "ocirc", "ô", "Ocirc", "Ô", "otilde", "õ", "Otilde", "Õ",
                "uacute", "ú", "Uacute", "Ú", "uuml", "ü", "ccedil", "ç", "Ccedil", "Ç", "ndash", "–", "mdash", "—",
                "ordm", "º", "ordf", "ª", "deg", "°"};
        for (int i = 0; i < p.length; i += 2) ENTITIES.put(p[i], p[i + 1]);
    }

    static String norm(String s) { return s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim(); }

    /** Minúsculas, sem acentos, sem pontuação: para comparar e buscar. */
    public static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    // ---------- Lista de linhas ----------

    private static final Pattern LINE_HREF = Pattern.compile("/horarios/([^\"',/?#]+),([^\"'/?#]+)");
    private static final Pattern CODE_NAME = Pattern.compile("^(\\S+)\\s*[-–]\\s*(.+)$");

    public static List<Line> parseIndex(String html) {
        html = STRIP.matcher(html).replaceAll(" ");
        Map<String, Line> out = new LinkedHashMap<>();
        Matcher m = TOKEN.matcher(html);
        String category = "", href = null;
        StringBuilder buf = null, heading = null;
        while (m.find()) {
            String tag = m.group(2);
            if (tag != null) {
                tag = tag.toLowerCase(Locale.ROOT);
                boolean close = !m.group(1).isEmpty();
                if (tag.matches("h[1-6]")) {
                    if (!close) heading = new StringBuilder();
                    else if (heading != null) {
                        String h = norm(heading.toString());
                        if (!h.isEmpty()) category = h.replaceFirst("(?i)^linhas\\s+", "");
                        heading = null;
                    }
                } else if (tag.equals("a")) {
                    if (!close) {
                        String h = attr(m.group(3), "href");
                        href = (h != null && LINE_HREF.matcher(h).find()) ? h : null;
                        buf = href != null ? new StringBuilder() : null;
                    } else if (href != null && buf != null) {
                        Matcher hm = LINE_HREF.matcher(href);
                        hm.find();
                        String code = decodeUrl(hm.group(2));
                        String text = norm(buf.toString()), name = text;
                        Matcher cn = CODE_NAME.matcher(text);
                        if (cn.find() && cn.group(1).equalsIgnoreCase(code)) name = cn.group(2);
                        if (name.isEmpty()) name = hm.group(1).replace('-', ' ');
                        String url = href.startsWith("http") ? href : BASE + (href.startsWith("/") ? "" : "/") + href;
                        if (!out.containsKey(code)) out.put(code, new Line(code, name, url, category));
                        href = null; buf = null;
                    }
                }
            } else if (m.group(4) != null) {
                String t = decode(m.group(4));
                if (buf != null) buf.append(t);
                if (heading != null) heading.append(t);
            }
        }
        return new ArrayList<>(out.values());
    }

    private static String decodeUrl(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    // ---------- Página de horários ----------

    private static final class T { int min; final StringBuilder flags = new StringBuilder(); }

    private static final class Block {
        final LinkedHashMap<String, List<T>> map = new LinkedHashMap<>();
        int day = -1;
        boolean hasTimes() { for (List<T> l : map.values()) if (!l.isEmpty()) return true; return false; }
        String signature() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<T>> e : map.entrySet()) {
                sb.append(e.getKey()).append('|');
                for (T t : e.getValue()) sb.append(t.min).append(',');
            }
            return sb.toString();
        }
    }

    private static final Pattern TIME_FULL = Pattern.compile("^(\\d{1,2})[:h](\\d{2})((?:\\s*[A-Z*]{1,2})*)\\s*$");
    private static final Pattern FLAGS_ONLY = Pattern.compile("^(?:[EMR*]\\s*){1,4}$");
    private static final Pattern UPDATED = Pattern.compile("(?i)atualiza\\S*\\s+em\\s*:?\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})");

    static int dayOf(String text) {
        String f = fold(text);
        if (f.isEmpty() || f.length() > 30) return -1;
        if (f.matches("dias? ute?is|dia util|segunda a sexta( feira)?")) return UTEIS;
        if (f.matches("sabados?")) return SABADO;
        if (f.matches("domingos?( e feriados?)?|domingos? feriados?|feriados?")) return DOMINGO;
        return -1;
    }

    static int dayOfAttr(String attrs) {
        if (attrs == null) return -1;
        String[] keys = {"id", "aria-labelledby", "data-day", "data-dia"};
        for (String k : keys) {
            String v = attr(attrs, k);
            if (v == null) continue;
            String f = fold(v);
            if (f.contains("util") || f.contains("uteis")) return UTEIS;
            if (f.contains("sabado")) return SABADO;
            if (f.contains("domingo") || f.contains("feriado")) return DOMINGO;
        }
        return -1;
    }

    static String saidaName(String text) {
        String n = norm(text);
        if (n.length() > 90) return null;
        Matcher m = Pattern.compile("^(?i)sa[ií]da(?:s)?\\s*(?:(?:d[aoe]s?)\\s+)?[:\\-–]?\\s*(.+)$").matcher(n);
        return m.find() ? m.group(1).trim() : null;
    }

    public static Timetable parseLine(String html) {
        html = STRIP.matcher(html).replaceAll(" ");
        Timetable tt = new Timetable();
        Matcher up = UPDATED.matcher(html.replaceAll("<[^>]*>", " "));
        if (up.find()) tt.updated = up.group(1);

        List<Block> blocks = new ArrayList<>();
        List<Integer> dayOrder = new ArrayList<>();
        int lastDay = -1;

        int tableDepth = 0, col = -1;
        Block tableBlock = null, current = null;
        Map<Integer, String> tableHeaders = new LinkedHashMap<>();
        List<String> pending = new ArrayList<>();
        String currentSaida = null;
        T lastTrip = null;

        boolean inAnchor = false;
        StringBuilder anchorBuf = null;
        String anchorTitle = null;

        Matcher m = TOKEN.matcher(html);
        while (m.find()) {
            String tag = m.group(2);
            String text = null;
            if (tag != null) {
                tag = tag.toLowerCase(Locale.ROOT);
                boolean close = !m.group(1).isEmpty();
                String attrs = m.group(3);
                if (!close) {
                    int d = dayOfAttr(attrs);
                    if (d >= 0) lastDay = d;
                }
                switch (tag) {
                    case "table":
                        if (!close) {
                            if (tableDepth == 0) {
                                if (current != null && current.hasTimes()) { addBlock(blocks, current); pending.clear(); }
                                current = null;
                                tableBlock = new Block();
                                tableBlock.day = lastDay;
                                tableHeaders.clear();
                                col = -1;
                            }
                            tableDepth++;
                        } else if (tableDepth > 0) {
                            tableDepth--;
                            if (tableDepth == 0 && tableBlock != null) {
                                if (tableBlock.hasTimes()) { addBlock(blocks, tableBlock); pending.clear(); }
                                tableBlock = null;
                            }
                        }
                        continue;
                    case "tr":
                        if (!close) col = -1;
                        continue;
                    case "td": case "th":
                        if (!close) {
                            String span = attr(attrs, "colspan");
                            col++;
                            if (span != null) try { col += Math.max(0, Integer.parseInt(span.trim()) - 1); } catch (NumberFormatException ignored) { }
                        }
                        continue;
                    case "a":
                        if (!close) {
                            inAnchor = true; anchorBuf = new StringBuilder();
                            anchorTitle = attr(attrs, "title");
                            if (anchorTitle == null) anchorTitle = attr(attrs, "data-original-title");
                            if (anchorTitle == null) anchorTitle = attr(attrs, "data-bs-original-title");
                            continue;
                        } else if (inAnchor) {
                            inAnchor = false;
                            text = anchorBuf.toString();
                        } else continue;
                        break;
                    case "br":
                        if (inAnchor) anchorBuf.append(' ');
                        continue;
                    default:
                        continue;
                }
            } else if (m.group(4) != null) {
                String t = decode(m.group(4));
                if (inAnchor) { anchorBuf.append(t); continue; }
                text = t;
            } else continue;

            String n = norm(text);
            if (n.isEmpty()) continue;
            boolean fromAnchor = tag != null;

            Matcher tm = TIME_FULL.matcher(n);
            boolean isTime = tm.find();
            if (isTime && !fromAnchor && tableDepth == 0 && currentSaida == null) isTime = false;
            if (isTime) {
                int h = Integer.parseInt(tm.group(1)), mi = Integer.parseInt(tm.group(2));
                if (h > 29 || mi > 59) continue;
                T trip = new T();
                trip.min = h * 60 + mi;
                appendFlags(trip.flags, tm.group(3));
                if (fromAnchor && anchorTitle != null && fold(anchorTitle).contains("previsto")
                        && trip.flags.indexOf("*") < 0) appendFlags(trip.flags, "*");
                String depName;
                Block target;
                if (tableDepth > 0 && tableBlock != null) {
                    depName = tableHeaders.get(col);
                    if (depName == null) depName = col >= 0 && col < pending.size() ? pending.get(col) : null;
                    if (depName == null) depName = "Sentido " + (Math.max(col, 0) + 1);
                    target = tableBlock;
                    if (target.day < 0) target.day = lastDay;
                } else {
                    if (current == null) { current = new Block(); current.day = lastDay; }
                    depName = currentSaida != null ? currentSaida : "Sentido 1";
                    target = current;
                }
                List<T> list = target.map.get(depName);
                if (list == null) { list = new ArrayList<>(); target.map.put(depName, list); }
                list.add(trip);
                lastTrip = trip;
                continue;
            }
            if (FLAGS_ONLY.matcher(n).matches()) {
                if (lastTrip != null) appendFlags(lastTrip.flags, n);
                continue;
            }
            lastTrip = null;

            int d = dayOf(n);
            if (d >= 0) {
                if (!dayOrder.contains(d)) dayOrder.add(d);
                lastDay = d;
                continue;
            }
            String saida = saidaName(n);
            if (saida != null) {
                if (tableDepth > 0 && tableBlock != null) {
                    tableHeaders.put(Math.max(col, 0), saida);
                } else {
                    if (current != null && current.map.containsKey(saida) && current.hasTimes()) {
                        addBlock(blocks, current);
                        pending.clear();
                        current = null;
                    }
                    if (current != null && !current.hasTimes()) current.day = lastDay;
                    currentSaida = saida;
                    if (!pending.contains(saida)) pending.add(saida);
                }
            }
        }
        if (current != null && current.hasTimes()) addBlock(blocks, current);
        if (tableBlock != null && tableBlock.hasTimes()) addBlock(blocks, tableBlock);

        // Página responsiva às vezes repete tudo (versão desktop + celular).
        if (blocks.size() >= 2 && blocks.size() % 2 == 0) {
            int h = blocks.size() / 2;
            boolean same = true;
            for (int i = 0; i < h && same; i++) same = blocks.get(i).signature().equals(blocks.get(i + h).signature());
            if (same) blocks = new ArrayList<>(blocks.subList(0, h));
        }

        // Decide a qual dia pertence cada bloco.
        int[] days = new int[blocks.size()];
        boolean distinct = true;
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            int d = blocks.get(i).day;
            if (d < 0 || seen.contains(d)) { distinct = false; break; }
            seen.add(d);
        }
        List<Integer> order = new ArrayList<>(dayOrder);
        for (int d = 0; d < 3; d++) if (!order.contains(d)) order.add(d);
        for (int i = 0; i < blocks.size(); i++) days[i] = distinct ? blocks.get(i).day : (i < 3 ? order.get(i) : -1);

        for (int i = 0; i < blocks.size(); i++) {
            if (days[i] < 0) continue;
            for (Map.Entry<String, List<T>> e : blocks.get(i).map.entrySet()) {
                int idx = tt.indexOf(e.getKey());
                Departure dep;
                if (idx < 0) { dep = new Departure(e.getKey()); tt.departures.add(dep); } else dep = tt.departures.get(idx);
                List<Trip> target = dep.days.get(days[i]);
                for (T t : e.getValue()) target.add(new Trip(t.min, cleanFlags(t.flags.toString())));
            }
        }
        for (Departure dep : tt.departures) for (int d = 0; d < 3; d++) dep.days.set(d, fixWrap(dep.days.get(d)));
        tt.itinerary.addAll(parseItinerary(html));
        return tt;
    }

    /**
     * Itinerário: itens de lista depois do título "Itinerário", até o próximo título ou a legenda.
     * Se a página repetir a mesma lista (versão para celular), fica só uma.
     */
    static List<String> parseItinerary(String html) {
        List<List<String>> lists = new ArrayList<>();
        List<String> cur = null, loose = new ArrayList<>();
        boolean inItin = false, inHeading = false, inLi = false;
        StringBuilder head = new StringBuilder(), li = new StringBuilder();
        Matcher m = TOKEN.matcher(html);
        while (m.find()) {
            String tag = m.group(2);
            if (tag != null) {
                tag = tag.toLowerCase(Locale.ROOT);
                boolean close = !m.group(1).isEmpty();
                if (tag.matches("h[1-6]")) {
                    if (!close) { inHeading = true; head.setLength(0); }
                    else if (inHeading) {
                        inHeading = false;
                        String f = fold(head.toString());
                        if (f.equals("itinerario") || f.startsWith("itinerario ")) {
                            inItin = true;
                            cur = new ArrayList<>();
                            lists.add(cur);
                        } else if (!f.isEmpty()) inItin = false;
                    }
                } else if (tag.equals("li") && inItin) {
                    if (!close) { inLi = true; li.setLength(0); }
                    else if (inLi) {
                        inLi = false;
                        String t = cleanStop(li.toString());
                        if (!t.isEmpty() && cur != null) cur.add(t);
                    }
                } else if (tag.equals("br") && inLi) li.append(' ');
            } else if (m.group(4) != null) {
                String t = decode(m.group(4));
                if (inHeading) { head.append(t); continue; }
                if (!inItin) continue;
                String f = fold(t);
                if (f.startsWith("legenda") || f.startsWith("atendimento")) { inItin = false; continue; }
                if (inLi) li.append(t);
                else if (cur != null && cur.isEmpty()) {
                    String c = cleanStop(t);
                    if (!c.isEmpty()) loose.add(c);
                }
            }
        }
        List<String> best = new ArrayList<>();
        for (List<String> l : lists) if (l.size() > best.size()) best = l;
        if (best.isEmpty() && loose.size() >= 2) best = loose;
        return best;
    }

    /** Tira a numeração do início ("1.", "2 -") e espaços extras. */
    static String cleanStop(String raw) {
        return norm(raw).replaceFirst("^\\d{1,3}\\s*[.)\\-–:]?\\s+", "");
    }

    /** Junta tabelas lado a lado (uma por saída) no mesmo bloco. */
    private static void addBlock(List<Block> blocks, Block b) {
        if (!blocks.isEmpty()) {
            Block last = blocks.get(blocks.size() - 1);
            boolean overlap = false;
            for (String k : b.map.keySet()) if (last.map.containsKey(k)) { overlap = true; break; }
            boolean generic = false;
            for (String k : b.map.keySet()) if (k.startsWith("Sentido ")) generic = true;
            if (!overlap && !generic && (b.day < 0 || last.day < 0 || b.day == last.day)) {
                last.map.putAll(b.map);
                if (last.day < 0) last.day = b.day;
                return;
            }
        }
        blocks.add(b);
    }

    private static void appendFlags(StringBuilder sb, String raw) {
        if (raw == null) return;
        for (String p : raw.trim().split("\\s+")) {
            if (p.isEmpty()) continue;
            for (char c : p.toCharArray()) {
                String s = String.valueOf(c);
                if ("EMR*".contains(s) && sb.indexOf(s) < 0) sb.append(s);
            }
        }
    }

    private static String cleanFlags(String f) {
        StringBuilder sb = new StringBuilder();
        for (char c : "EMR*".toCharArray()) if (f.indexOf(c) >= 0) sb.append(c);
        return sb.toString();
    }

    /** Horários depois da meia-noite no fim da tabela (ex.: 00:15) contam como +24h. */
    static List<Trip> fixWrap(List<Trip> in) {
        List<Trip> out = new ArrayList<>();
        int offset = 0, prev = -1;
        for (Trip t : in) {
            int v = t.min + offset;
            if (prev >= 0 && v < prev - 180 && t.min < 6 * 60) { offset += 1440; v = t.min + offset; }
            if (t.min >= 1440) v = t.min;
            out.add(new Trip(v, t.flags));
            prev = v;
        }
        Collections.sort(out);
        List<Trip> dedup = new ArrayList<>();
        for (Trip t : out) {
            if (!dedup.isEmpty() && dedup.get(dedup.size() - 1).min == t.min) continue;
            dedup.add(t);
        }
        return dedup;
    }
}
