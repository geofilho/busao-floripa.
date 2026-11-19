import br.com.busfloripa.FenixParser;
import br.com.busfloripa.FenixParser.*;
import br.com.busfloripa.Schedule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Testes do leitor com HTML sintético nos formatos prováveis do site. Rodar: ver tests/run.sh */
public class ParserTest {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "OK   " : "FALHA") + " " + what);
        if (!ok) fails++;
    }

    static String a(String t, String f, String title) {
        return "<a href=\"#\" title=\"" + title + "\" data-toggle=\"tooltip\">" + t + (f.isEmpty() ? "" : " <b>" + f + "</b>") + "</a>";
    }

    public static void main(String[] args) {
        // Índice
        String idx = "<html><body><h4>Linhas Alimentadora TICEN</h4><ul>"
                + "<li><a href=\"https://www.consorciofenix.com.br/horarios/abraao,665\">665 - Abra&atilde;o</a></li>"
                + "<li><a href=\"/horarios/bairro-de-fatima,V-844\">V-844 - Bairro de Fátima</a></li></ul>"
                + "<h4>Linhas Convencional</h4><a href=\"/horarios/saco-grande-via-joao-paulo,174\">174 - Saco Grande via João Paulo</a>"
                + "<a href=\"/horarios\">Horários</a></body></html>";
        List<Line> lines = FenixParser.parseIndex(idx);
        check(lines.size() == 3, "índice: 3 linhas (" + lines.size() + ")");
        check(lines.get(0).name.equals("Abraão") && lines.get(0).category.equals("Alimentadora TICEN"), "índice: nome e categoria com acento");
        check(lines.get(1).code.equals("V-844") && lines.get(1).url.startsWith("https://"), "índice: código V- e URL absoluta");
        check(lines.get(2).category.equals("Convencional"), "índice: categoria muda");

        // Formato A: abas no topo + 3 tabelas com cabeçalho por coluna (linhas intercaladas)
        StringBuilder A = new StringBuilder("<p>Atualizado em 19/11/2026</p><ul class=nav><li><a href='#t1'>Dias Úteis</a></li><li><a href='#t2'>Sábado</a></li><li><a href='#t3'>Domingos e Feriados</a></li></ul>");
        String[][] tab = {{"05:30", "05:50"}, {"06:27", "06:10"}, {"23:59", "00:15"}};
        for (int b = 0; b < 3; b++) {
            A.append("<div class=tab-pane><table><thead><tr><th>Saída Abraão</th><th>Saída TICEN - Plataforma B - Box 9</th></tr></thead><tbody>");
            for (String[] r : tab) A.append("<tr><td>").append(a(r[0], b == 0 && r[0].equals("05:30") ? "E M" : "E", "Veículo com elevador")).append("</td><td>")
                    .append(a(r[1], r[1].equals("00:15") ? "E R" : "E", "Veículo com elevador<br>Recolhe")).append("</td></tr>");
            if (b == 1) A.append("<tr><td>").append(a("12:00", "E *", "Horário previsto")).append("</td><td></td></tr>");
            A.append("</tbody></table></div>");
        }
        A.append("<h3>Itinerário</h3><ol><li>TICEN - TERMINAL</li></ol><p>SAC 07:00 às 19:15</p>");
        Timetable ta = FenixParser.parseLine(A.toString());
        check(ta.departures.size() == 2, "A: 2 saídas (" + ta.departures.size() + ")");
        check(ta.updated.equals("19/11/2026"), "A: data de atualização");
        Departure ab = ta.departures.get(0), ti = ta.departures.get(1);
        check(ab.name.equals("Abraão") && ti.name.startsWith("TICEN"), "A: nomes das saídas");
        check(ab.days.get(0).size() == 3 && ab.days.get(1).size() == 4 && ab.days.get(2).size() == 3, "A: horários por dia na coluna certa");
        check(ab.days.get(0).get(0).flags.equals("EM"), "A: marcas E M");
        check(ab.days.get(1).get(2).flags.contains("*"), "A: horário previsto");
        Trip last = ti.days.get(0).get(ti.days.get(0).size() - 1);
        check(last.min == 1440 + 15 && last.flags.equals("ER"), "A: 00:15 vira depois da meia-noite");

        // Itinerário (título repetido, lista numerada, legenda depois)
        String it = "<h4>Itinerário</h4><h4>Itinerário</h4><ol><li>TICEN - TERMINAL DE INTEGRAÇÃO DO CENTRO</li>"
                + "<li>PONTE GOVERNADOR COLOMBO SALLES</li><li>RUA CAMPOLINO ALVES (PONTO FINAL)</li>"
                + "<li>TICEN - TERMINAL DE INTEGRAÇÃO DO CENTRO</li></ol><h4>Legenda</h4><ul><li>* Horário previsto</li></ul>";
        Timetable ti2 = FenixParser.parseLine(A.toString().replace("<h3>Itinerário</h3><ol><li>TICEN - TERMINAL</li></ol>", it));
        check(ti2.itinerary.size() == 4 && ti2.itinerary.get(2).equals("RUA CAMPOLINO ALVES (PONTO FINAL)"), "itinerário: 4 itens em ordem " + ti2.itinerary);
        check(ti2.departures.get(0).days.get(0).size() == 3, "itinerário não atrapalha os horários");
        List<String> num = FenixParser.parseLine("<h4>Itinerário</h4><div>1. RUA A</div><div>2 - RUA B</div><p>Legenda</p>").itinerary;
        check(num.equals(java.util.Arrays.asList("RUA A", "RUA B")), "itinerário sem lista, numerado " + num);
        List<String> dup = FenixParser.parseLine("<h4>Itinerário</h4><ol><li>X</li><li>Y</li></ol><h4>Itinerário</h4><ol><li>X</li><li>Y</li></ol>").itinerary;
        check(dup.size() == 2, "itinerário repetido fica uma vez");

        // Formato B: blocos em div com títulos de dia e de saída, em sequência
        StringBuilder B = new StringBuilder();
        String[] dn = {"Dias Úteis", "Sábado", "Domingos e Feriados"};
        for (int b = 0; b < 3; b++) {
            B.append("<div id='").append(new String[]{"uteis", "sabado", "domingo"}[b]).append("'><h4>").append(dn[b]).append("</h4>");
            B.append("<div class=col><h5>Saída Abraão</h5>");
            for (int i = 0; i < b + 2; i++) B.append("<span>").append(String.format("%02d:00", 6 + i)).append("</span>");
            B.append("</div><div class=col><h5>Saída TICEN</h5>");
            for (int i = 0; i < b + 2; i++) B.append(a(String.format("%02d:30", 6 + i), "", ""));
            B.append("</div></div>");
        }
        Timetable tb = FenixParser.parseLine(B.toString());
        check(tb.departures.size() == 2, "B: 2 saídas");
        check(tb.departures.get(0).days.get(0).size() == 2 && tb.departures.get(0).days.get(2).size() == 4, "B: dias separados");
        check(tb.departures.get(1).days.get(1).size() == 3, "B: TICEN sábado");

        // Formato C: uma tabela por saída lado a lado, abas no topo
        StringBuilder C = new StringBuilder("<a>Dias Úteis</a><a>Sábado</a><a>Domingos e Feriados</a>");
        for (int b = 0; b < 3; b++) {
            C.append("<div class=row><table><tr><th>Saída Abraão</th></tr><tr><td>07:00</td></tr><tr><td>08:00 E</td></tr></table>");
            C.append("<table><tr><th>Saída TICEN</th></tr><tr><td>07:30</td></tr></table></div>");
        }
        Timetable tc = FenixParser.parseLine(C.toString());
        check(tc.departures.size() == 2 && tc.departures.get(0).days.get(2).size() == 2 && tc.departures.get(1).days.get(1).size() == 1, "C: tabelas lado a lado");
        check(tc.departures.get(0).days.get(0).get(1).flags.equals("E"), "C: marca fora de link");

        // Formato D: página duplicada (versão desktop + celular)
        Timetable td = FenixParser.parseLine(A.toString() + A.toString());
        check(td.departures.get(0).days.get(0).size() == 3 && td.departures.get(0).days.get(2).size() == 3, "D: duplicação ignorada");

        // Schedule
        check(Schedule.dayType(LocalDate.of(2026, 11, 20)) == FenixParser.DOMINGO, "20/11 é feriado");
        check(Schedule.dayType(LocalDate.of(2026, 4, 3)) == FenixParser.DOMINGO, "Sexta-feira Santa 2026");
        check(Schedule.dayType(LocalDate.of(2026, 11, 21)) == FenixParser.SABADO, "sábado");
        check(Schedule.dayType(LocalDate.of(2026, 11, 19)) == FenixParser.UTEIS, "quinta é dia útil");
        List<Schedule.Arrival> up = Schedule.upcoming(ti, 5, LocalDateTime.of(2026, 11, 19, 6, 0), 0, 2);
        check(up.size() == 2 && up.get(0).at == 6 * 60 + 15 && up.get(1).at == 23 * 60 + 60 + 15 + 5, "próximas com deslocamento");
        List<Schedule.Arrival> late = Schedule.upcoming(ti, 0, LocalDateTime.of(2026, 11, 20, 0, 5), 2, 1);
        check(late.size() == 1 && late.get(0).at == 15 && late.get(0).departs == 15, "ônibus das 00:15 da noite anterior aparece (" + (late.isEmpty() ? "-" : late.get(0).at) + ")");
        check(Schedule.inMinutes(65).equals("em 1 hora e 5 minutos") && Schedule.inMinutes(8).equals("em 8 minutos"), "texto de minutos");
        check(Schedule.direction(ta, 0) == Schedule.CENTRO && Schedule.direction(ta, 1) == Schedule.BAIRRO, "sentido de cada saída");
        check(Schedule.sentidoLabel(ta, 1).equals("Saída TICEN - Plataforma B - Box 9"), "rótulo só com a saída (" + Schedule.sentidoLabel(ta, 1) + ")");

        // O ônibus chegou agora
        Departure cal = new Departure("Saco Grande");
        for (int m : new int[]{14 * 60, 14 * 60 + 40, 15 * 60 + 20, 15 * 60 + 40}) cal.days.get(0).add(new Trip(m, ""));
        List<Integer> cands = Schedule.travelCandidates(cal, LocalDateTime.of(2026, 11, 19, 15, 32), 0);
        check(cands.equals(java.util.Arrays.asList(12, 52, 92)), "chegou agora: candidatos " + cands);
        check(Integer.valueOf(12).equals(Schedule.pickTravel(cands, 10)), "chegou agora: escolhe 12 perto da referência 10");
        check(Schedule.pickTravel(cands, 30) == null, "chegou agora: nada combina com 30, pergunta ao usuário");
        check(Schedule.median(java.util.Arrays.asList(12, 15, 13)) == 13, "média (mediana) das medições");

        // Links do Google Maps
        double[] g1 = br.com.busfloripa.MapsLink.find("https://www.google.com/maps/place/Ponto/@-27.5953,-48.5482,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d-27.5961!4d-48.5491");
        check(g1 != null && g1[0] == -27.5961 && g1[1] == -48.5491, "Maps: usa a posição exata do ponto (!3d!4d)");
        double[] g2 = br.com.busfloripa.MapsLink.find("https://maps.google.com/?q=-27.58,-48.51");
        check(g2 != null && g2[1] == -48.51, "Maps: link com q=");
        check(br.com.busfloripa.MapsLink.find("https://maps.app.goo.gl/TWkYJXYodqpXFxqQA?g_st=am") == null, "Maps: link curto precisa da internet");
        check("https://maps.app.goo.gl/abc?g_st=am".equals(br.com.busfloripa.MapsLink.firstUrl("Ponto Saco Grande https://maps.app.goo.gl/abc?g_st=am")), "Maps: tira o link do texto colado");

        System.out.println(fails == 0 ? "\nTodos os testes passaram." : "\n" + fails + " falha(s).");
        System.exit(fails == 0 ? 0 : 1);
    }
}
