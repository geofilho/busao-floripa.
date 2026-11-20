package br.com.busfloripa;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jotur (www2.jotur.com.br/linhas). A lista tem links "?linha=0355&nome=..." com
 * "<strong>0355</strong> - NOME". A página da linha tem blocos IDA e VOLTA, cada um com
 * as colunas Dias Úteis, Sábados e Domingos e Feriados, e os horários em div.time-item.
 */
public final class JoturParser {

    private JoturParser() { }

    public static final String INDEX = "https://www2.jotur.com.br/linhas/";

    private static final Pattern LINK = Pattern.compile(
            "<a\\b[^>]*href\\s*=\\s*\"([^\"]*linha=[^\"]*)\"[^>]*>(.*?)</a>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern STRONG = Pattern.compile("<strong[^>]*>(.*?)</strong>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern H1 = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM = Pattern.compile("class\\s*=\\s*\"accordion-item\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER = Pattern.compile("class\\s*=\\s*\"accordion-header\"[^>]*>(.*?)</div>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern H4 = Pattern.compile("<h4[^>]*>(.*?)</h4>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile(">\\s*(\\d{1,2})[:h.](\\d{2})\\s*<");

    static String text(String html) {
        return FenixParser.norm(FenixParser.decode(html.replaceAll("(?is)<svg\\b.*?</svg>", " ").replaceAll("<[^>]*>", " ")))
                .replaceAll("\\s+", " ").trim();
    }

    /** Lista de linhas: código "JOT#0355", nome "0355 ARIRIU / FLORIANÓPOLIS VIA ...". */
    public static List<FenixParser.Line> parseIndex(String html) {
        List<FenixParser.Line> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        Matcher a = LINK.matcher(html);
        while (a.find()) {
            String href = FenixParser.decode(a.group(1));
            Matcher c = Pattern.compile("[?&]linha=([^&]+)").matcher(href);
            if (!c.find()) continue;
            String code;
            try { code = java.net.URLDecoder.decode(c.group(1), "UTF-8").trim(); } catch (Exception e) { continue; }
            if (code.isEmpty() || seen.contains(code)) continue;
            String inner = a.group(2);
            Matcher s = STRONG.matcher(inner);
            String rest = text(s.find() ? inner.substring(s.end()) : inner).replaceFirst("^[-–:\\s]+", "");
            if (rest.isEmpty() || rest.startsWith(code)) rest = rest.replaceFirst("^" + Pattern.quote(code) + "[-–:\\s]*", "");
            seen.add(code);
            out.add(new FenixParser.Line("JOT#" + code, rest.isEmpty() ? code : code + " " + rest,
                    href.startsWith("http") ? href : INDEX + (href.startsWith("?") ? href : "?" + href.replaceFirst("^.*\\?", "")),
                    "Jotur"));
        }
        return out;
    }

    /** Lugar de partida de cada sentido: "ARIRIU / FLORIANÓPOLIS VIA X" -> [ARIRIU, FLORIANÓPOLIS]. */
    static String[] ends(String lineName) {
        String n = lineName.replaceFirst("^\\s*[0-9A-Za-z]+\\s*[-–]\\s*", "")
                .replaceAll("(?i)\\s+-\\s+sentido\\s+.*$", "")
                .replaceAll("(?i)\\s+via\\s+.*$", "").trim();
        String[] p = n.split("\\s*/\\s*");
        if (p.length != 2 || p[0].isEmpty() || p[1].isEmpty()) return null;
        return new String[]{p[0].trim(), p[1].trim()};
    }

    /** Horários de uma linha. Cada bloco IDA ou VOLTA vira uma saída. */
    public static FenixParser.Timetable parseLine(String html) {
        FenixParser.Timetable tt = new FenixParser.Timetable();
        Matcher h = H1.matcher(html);
        if (h.find()) {
            String t = text(h.group(1)).replaceAll("(?i)\\s*Warning:.*$", "").trim();
            Matcher m = Pattern.compile("^([0-9A-Za-z]+)\\s*[-–]\\s*(.*)$").matcher(t);
            if (m.find()) { tt.code = m.group(1); tt.name = m.group(2).trim(); } else tt.name = t;
        }
        String[] ends = ends(tt.code + " - " + tt.name);
        String[] blocks = ITEM.split(html);
        for (int b = 1; b < blocks.length; b++) {
            String block = blocks[b];
            Matcher hd = HEADER.matcher(block);
            String label = hd.find() ? text(hd.group(1)) : "";
            boolean volta = label.toLowerCase(java.util.Locale.ROOT).startsWith("volta");
            String dep = ends != null ? (volta ? ends[1] : ends[0]) : (volta ? "Volta" : "Ida");
            int idx = tt.indexOf(dep);
            FenixParser.Departure d;
            if (idx < 0) { d = new FenixParser.Departure(dep); tt.departures.add(d); } else d = tt.departures.get(idx);
            // Cada coluna começa num <h4> com o tipo de dia.
            Matcher col = H4.matcher(block);
            List<int[]> starts = new ArrayList<>();
            while (col.find()) starts.add(new int[]{col.start(), col.end(), dayOf(text(col.group(1)))});
            for (int k = 0; k < starts.size(); k++) {
                int day = starts.get(k)[2];
                if (day < 0) continue;
                String part = block.substring(starts.get(k)[1], k + 1 < starts.size() ? starts.get(k + 1)[0] : block.length());
                Matcher t = TIME.matcher(part);
                while (t.find()) {
                    int hh = Integer.parseInt(t.group(1)), mm = Integer.parseInt(t.group(2));
                    if (hh <= 29 && mm <= 59) d.days.get(day).add(new FenixParser.Trip(hh * 60 + mm, ""));
                }
            }
        }
        List<FenixParser.Departure> keep = new ArrayList<>();
        for (FenixParser.Departure d : tt.departures) {
            for (int k = 0; k < 3; k++) d.days.set(k, GenericParser.wrapAndSort(d.days.get(k)));
            if (d.total() > 0) keep.add(d);
        }
        tt.departures.clear();
        tt.departures.addAll(keep);
        return tt;
    }

    static int dayOf(String t) {
        String f = FenixParser.fold(t);
        if (f.contains("domingo") || f.contains("feriado")) return FenixParser.DOMINGO;
        if (f.contains("sabado")) return FenixParser.SABADO;
        if (f.contains("uteis") || f.contains("util") || f.contains("segunda")) return FenixParser.UTEIS;
        return -1;
    }
}
