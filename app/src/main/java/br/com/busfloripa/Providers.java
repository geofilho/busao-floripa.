package br.com.busfloripa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Empresas de ônibus. Linhas do Fênix têm o código normal ("174"); as intermunicipais têm
 * um prefixo com "#" ("EST#0317", "STE#sertao-florianopolis") e o nome já traz o número, se houver.
 */
public final class Providers {

    private Providers() { }

    public static final class Source {
        public final String prefix, company, indexUrl;
        Source(String prefix, String company, String indexUrl) {
            this.prefix = prefix; this.company = company; this.indexUrl = indexUrl;
        }
    }

    /** Empresas intermunicipais com leitura pronta. */
    public static final Source[] INTER = {
            new Source("EST", "Estrela", "https://www.insulartc.com.br/estrela/"),
            new Source("STE", "Santa Terezinha", "https://santaterezinha.com/horarios/"),
            new Source("BIG", "Biguaçu", "https://www.tcbiguacu.com.br/horarios"),
    };

    public static boolean isFenix(String code) { return code == null || code.indexOf('#') < 0; }

    public static String company(String code) {
        if (isFenix(code)) return "Consórcio Fênix";
        String p = code.substring(0, code.indexOf('#'));
        for (Source s : INTER) if (s.prefix.equals(p)) return s.company;
        return "";
    }

    /** Nome para mostrar: Fênix "174 Saco Grande..."; intermunicipal só o nome (que já tem o número). */
    public static String title(String code, String name) {
        return isFenix(code) ? code + " " + name : name;
    }

    /** Nome para listas, com vírgula: "174, Saco Grande..." ou "317 Campinas via Kobrasol, Estrela". */
    public static String rowTitle(String code, String name) {
        return isFenix(code) ? code + ", " + name : name + ", " + company(code);
    }

    /** Baixa e lê os horários de uma linha de qualquer empresa. Faz acesso à internet. */
    public static FenixParser.Timetable load(String code, String url) throws java.io.IOException {
        if (code != null && code.startsWith("BIG#")) {
            // Biguaçu: uma consulta por tipo de dia (1 = dias úteis, 2 = sábado, 3 = domingo).
            FenixParser.Timetable tt = new FenixParser.Timetable();
            StringBuilder seen = new StringBuilder();
            for (int d = 1; d <= 3; d++) {
                String html = Repo.fetch(url + "&id_day_week=" + d, true);
                if (seen.length() == 0) seen.append(html.length()).append(" caracteres: ")
                        .append(FenixParser.norm(html.replaceAll("<[^>]*>", " ")).replaceAll("\\s+", " ")
                                .substring(0, Math.min(120, FenixParser.norm(html.replaceAll("<[^>]*>", " ")).length())));
                BiguacuParser.parseDay(html, d, tt);
            }
            BiguacuParser.finish(tt);
            int total = 0;
            for (FenixParser.Departure dep : tt.departures) total += dep.total();
            if (total == 0) throw new java.io.IOException("O site da Biguaçu respondeu sem horários que eu consiga ler (" + seen + ")");
            return tt;
        }
        if (!isFenix(code)) return parseLine(code, Repo.fetch(url, false));
        return parseLine(code, Repo.download(url));
    }

    /** Baixa e lê a lista de linhas de uma empresa intermunicipal. Faz acesso à internet. */
    public static List<FenixParser.Line> loadIndex(Source s) throws java.io.IOException {
        if (s.prefix.equals("BIG")) {
            List<FenixParser.Line> out = new ArrayList<>(BiguacuParser.parseIndex(Repo.fetch(BiguacuParser.searchUrl(0), true), false));
            try {
                for (FenixParser.Line l : BiguacuParser.parseIndex(Repo.fetch(BiguacuParser.searchUrl(5), true), true)) {
                    boolean dup = false;
                    for (FenixParser.Line o : out) if (o.code.equals(l.code)) { dup = true; break; }
                    if (!dup) out.add(l);
                }
            } catch (java.io.IOException e) {
                if (out.isEmpty()) throw e; // sem as executivas, segue com as convencionais
            }
            return out;
        }
        return parseIndex(s, Repo.fetch(s.indexUrl, false));
    }

    public static FenixParser.Timetable parseLine(String code, String html) {
        return isFenix(code) ? FenixParser.parseLine(html) : GenericParser.parseLine(html);
    }

    public static List<FenixParser.Line> parseIndex(Source s, String html) {
        if (s.prefix.equals("EST")) return GenericParser.parseIndexEstrela(html, s.indexUrl);
        if (s.prefix.equals("STE")) return GenericParser.parseIndexSantaTerezinha(html);
        return new ArrayList<>();
    }

    /** Letras do horário em palavras. Fênix: E, M, R, *. Intermunicipal: legenda da própria página. */
    public static String flagsText(String code, FenixParser.Timetable tt, String flags) {
        if (isFenix(code)) return Schedule.flagsText(flags);
        if (flags == null || flags.isEmpty()) return "";
        Map<String, String> lg = tt == null ? null : tt.legend;
        List<String> out = new ArrayList<>();
        for (String f : flags.split("\\s+")) {
            String key = f.equals("*") ? "*" : f.replace("*", "");
            String desc = lg == null ? null : lg.get(key);
            if (f.equals("*") && desc == null) desc = "horário com observação";
            out.add(desc != null ? desc.toLowerCase(java.util.Locale.ROOT) : f);
        }
        return String.join(", ", out);
    }

    /** Ordem da lista intermunicipal: empresa, depois número, depois nome. */
    public static int compare(FenixParser.Line a, FenixParser.Line b) {
        int c = company(a.code).compareTo(company(b.code));
        if (c != 0) return c;
        int na = leadingNumber(a.name), nb = leadingNumber(b.name);
        if (na != nb) return Integer.compare(na, nb);
        return a.name.compareToIgnoreCase(b.name);
    }

    private static int leadingNumber(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }
}
