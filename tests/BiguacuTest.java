import br.com.busfloripa.BiguacuParser;
import br.com.busfloripa.FenixParser;
import br.com.busfloripa.FenixParser.*;
import java.util.List;

/** Testes do leitor da Biguaçu com HTML no formato do site (tcbiguacu.com.br). */
public class BiguacuTest {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "OK   " : "FALHA") + " " + what);
        if (!ok) fails++;
    }
    static String li(String code, String name, String sub) {
        return "<li><div class=\"star-line\" id=\"" + code + "\"></div><div class=\"desc\" id=\"desc" + code + "\"><div class=\"code-line\">" + code
                + "</div><div class=\"name-line\">\n  " + name + "\n  <span>" + sub + "</span>\n</div></div></li>";
    }
    static String hour(String h, boolean adapt, String leg) {
        return "<li" + (leg != null ? " class=\"itinerario_diferenciado\"" : "") + "><div class=\"icones\">" + (leg != null ? "<div class=\"ico itinerario\"></div>" : "")
                + (adapt ? "<div class=\"ico adaptado\"></div>" : "") + "</div><div class=\"hour\" style=\"background:none\">" + h + "</div>"
                + (leg != null ? "<div class=\"leg_dif\">" + leg + "</div>" : "") + "</li>";
    }

    public static void main(String[] args) {
        String idx = li("64100", "Antônio Carlos / Biguaçu", "Até Viaduto Janaína") + li("44300", "Biguaçu", "Via BR101 / Estreito")
                + li("10301", "Armação da Piedade", "Partida Biguaçu_|Viaduto Janaina sentido Biguaçu");
        List<Line> ls = BiguacuParser.parseIndex(idx, false);
        check(ls.size() == 3, "índice: 3 linhas");
        check(ls.get(0).code.equals("BIG#64100") && ls.get(0).name.equals("64100 Antônio Carlos / Biguaçu, Até Viaduto Janaína"), "índice: nome " + ls.get(0).name);
        check(ls.get(1).url.endsWith("AJAX_get_line_hours.php?id_line_bus=44300") && ls.get(1).category.equals("Biguaçu"), "índice: endereço");
        check(ls.get(2).name.contains("Partida Biguaçu, Viaduto"), "índice: separador estranho do site " + ls.get(2).name);
        check(BiguacuParser.parseIndex(li("76403", "Executivo Biguaçu", ""), true).get(0).name.equals("76403 Executivo Biguaçu, executivo"), "índice executivo");

        String d1 = "<div class=\"info-hours-itinerario\"><div class=\"text-hours-itinerario\"><p>Veja aqui os hor&aacute;rios</p></div>"
                + "<div class=\"hours-line\"><div class=\"title\">Partida: Biguaçu (Previsão de Horário)</div><ul class=\"col-line-hours\">"
                + hour("04:25", true, "Horario via<br/>Ponte Pedro Ivo Campos") + hour("05:00", false, null) + hour("05:50", true, null) + hour("00:05", false, null) + "</ul></div>"
                + "<div class=\"hours-line\"><div class=\"title\">Partida: Ticen - Plataforma D - Fpolis</div><ul class=\"col-line-hours\">"
                + hour("05:05", false, "Saida Via Praia<br/>Joao Rosa") + hour("06:10", false, null) + "</ul></div></div>";
        String d2 = "<div class=\"hours-line\"><div class=\"title\">Partida: Biguaçu (Previsão de Horário)</div><ul>" + hour("04:30", false, "Saida Via Praia<br/>joao Rosa") + "</ul></div>";
        Timetable tt = new Timetable();
        BiguacuParser.parseDay(d1, 1, tt);
        BiguacuParser.parseDay(d2, 2, tt);
        BiguacuParser.parseDay("<p>Sem horários</p>", 3, tt);
        BiguacuParser.finish(tt);
        check(tt.departures.size() == 2 && tt.departures.get(0).name.equals("Biguaçu") && tt.departures.get(1).name.equals("Ticen - Plataforma D - Fpolis"), "saídas " + tt.departures.size());
        Departure b = tt.departures.get(0);
        check(b.days.get(0).size() == 4 && b.days.get(1).size() == 1 && b.days.get(2).isEmpty(), "dias úteis 4, sábado 1, domingo 0");
        check(b.days.get(0).get(0).flags.equals("AD V1") && "Horario via Ponte Pedro Ivo Campos".equals(tt.legend.get("V1")), "adaptado e observação " + b.days.get(0).get(0).flags + " " + tt.legend);
        check(b.days.get(0).get(3).min == 1440 + 5, "00:05 é madrugada");
        check(tt.departures.get(1).days.get(0).get(0).flags.equals("V2") && b.days.get(1).get(0).flags.equals("V2"), "mesma observação reaproveita o código, sem diferença de maiúsculas");

        System.out.println(fails == 0 ? "\nTodos os testes da Biguaçu passaram." : "\n" + fails + " falha(s).");
        System.exit(fails == 0 ? 0 : 1);
    }
}
