package br.com.busfloripa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor da Biguaçu Transportes Coletivos (tcbiguacu.com.br). O site busca os dados por baixo:
 * - lista de linhas: /sistema/sys/AJAX_search_line.php?busca=%25%25%25&id_grupo_bus=0 (5 = executivo)
 * - horários: /sistema/sys/AJAX_get_line_hours.php?id_line_bus=CODIGO&id_day_week=1|2|3
 *   (1 = dias úteis, 2 = sábado, 3 = domingo), com blocos "Partida: X" e horários em div.hour.
 */
public final class BiguacuParser {

    private BiguacuParser() { }

    public static final String BASE = "https://www.tcbiguacu.com.br/sistema/sys/";

    public static String searchUrl(int group) {
        return BASE + "AJAX_search_line.php?busca=%25%25%25&id_grupo_bus=" + group;
    }

    public static String hoursUrl(String lineId) {
        return BASE + "AJAX_get_line_hours.php?id_line_bus=" + lineId;
    }

    private static final Pattern LI = Pattern.compile("<li\\b.*?</li>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE = Pattern.compile("class\\s*=\\s*\"code-line[^\"]*\"[^>]*>\\s*([^<]+?)\\s*<", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("class\\s*=\\s*\"name-line\"[^>]*>(.*?)</div>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAN = Pattern.compile("<span[^>]*>(.*?)</span>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    static String text(String html) {
        return FenixParser.norm(FenixParser.decode(html.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", " ")));
    }

    /** Linhas da busca. executive = true para o grupo executivo. */
    public static List<FenixParser.Line> parseIndex(String html, boolean executive) {
        List<FenixParser.Line> out = new ArrayList<>();
        Matcher li = LI.matcher(html);
        while (li.find()) {
            String item = li.group();
            Matcher c = CODE.matcher(item), n = NAME.matcher(item);
            if (!c.find() || !n.find()) continue;
            String code = FenixParser.norm(FenixParser.decode(c.group(1)));
            if (!code.matches("[0-9A-Za-z.-]{1,10}")) continue;
            String inner = n.group(1);
            Matcher sp = SPAN.matcher(inner);
            String sub = sp.find() ? text(sp.group(1)) : "";
            String main = text(SPAN.matcher(inner).replaceAll(" "));
            if (main.isEmpty()) continue;
            String name = code + " " + main + (sub.isEmpty() ? "" : ", " + sub.replace("_|", ", ").replace("_", " ").replaceAll("\\s+", " "))
                    + (executive ? ", executivo" : "");
            out.add(new FenixParser.Line("BIG#" + code, name, hoursUrl(code), "Biguaçu"));
        }
        return out;
    }

    private static final Pattern BLOCK = Pattern.compile("class\\s*=\\s*\"hours-line\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("class\\s*=\\s*\"title\"[^>]*>(.*?)</div>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HOUR = Pattern.compile("class\\s*=\\s*\"hour\"[^>]*>\\s*(\\d{1,2}):(\\d{2})\\s*<", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEG = Pattern.compile("class\\s*=\\s*\"leg_dif\"[^>]*>(.*?)</div>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** Nome da saída: "Partida: Biguaçu (Previsão de Horário)" -> "Biguaçu". */
    static String departureName(String title) {
        String t = text(title).replaceFirst("(?i)^partida\\s*:?\\s*", "");
        t = t.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        return t.isEmpty() ? "Saída" : t;
    }

    /**
     * Junta os horários de um dia (1 = dias úteis, 2 = sábado, 3 = domingo) na tabela.
     * Letras: AD = adaptado; V1, V2... = observações do horário (via, saída diferente), na legenda.
     */
    public static void parseDay(String html, int dayWeek, FenixParser.Timetable tt) {
        int day = dayWeek == 2 ? FenixParser.SABADO : dayWeek == 3 ? FenixParser.DOMINGO : FenixParser.UTEIS;
        String[] blocks = BLOCK.split(html);
        for (int b = 1; b < blocks.length; b++) {
            String block = blocks[b];
            Matcher tm = TITLE.matcher(block);
            String dep = tm.find() ? departureName(tm.group(1)) : "Saída";
            int idx = tt.indexOf(dep);
            FenixParser.Departure d;
            if (idx < 0) { d = new FenixParser.Departure(dep); tt.departures.add(d); } else d = tt.departures.get(idx);
            Matcher li = LI.matcher(block);
            while (li.find()) {
                String item = li.group();
                Matcher h = HOUR.matcher(item);
                if (!h.find()) continue;
                int hh = Integer.parseInt(h.group(1)), mm = Integer.parseInt(h.group(2));
                if (hh > 29 || mm > 59) continue;
                List<String> flags = new ArrayList<>();
                if (item.contains("ico adaptado")) {
                    flags.add("AD");
                    tt.legend.put("AD", "adaptado");
                }
                Matcher lg = LEG.matcher(item);
                if (lg.find()) {
                    String note = text(lg.group(1));
                    if (!note.isEmpty()) flags.add(legendKey(tt.legend, note));
                }
                d.days.get(day).add(new FenixParser.Trip(hh * 60 + mm, String.join(" ", flags)));
            }
        }
    }

    /** Código curto para uma observação (V1, V2...), reaproveitando quando o texto se repete. */
    private static String legendKey(Map<String, String> legend, String note) {
        String fold = FenixParser.fold(note);
        for (Map.Entry<String, String> e : legend.entrySet())
            if (FenixParser.fold(e.getValue()).equals(fold)) return e.getKey();
        int n = 1;
        while (legend.containsKey("V" + n)) n++;
        legend.put("V" + n, note);
        return "V" + n;
    }

    /** Depois dos 3 dias: ordena, trata a madrugada e tira saídas vazias. */
    public static void finish(FenixParser.Timetable tt) {
        List<FenixParser.Departure> keep = new ArrayList<>();
        for (FenixParser.Departure d : tt.departures) {
            for (int k = 0; k < 3; k++) d.days.set(k, GenericParser.wrapAndSort(d.days.get(k)));
            if (d.total() > 0) keep.add(d);
        }
        tt.departures.clear();
        tt.departures.addAll(keep);
    }

    static Map<String, String> emptyLegend() { return new LinkedHashMap<>(); }
}
