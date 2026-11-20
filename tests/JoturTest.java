import br.com.busfloripa.FenixParser;
import br.com.busfloripa.FenixParser.*;
import br.com.busfloripa.JoturParser;
import java.util.List;

/** Testes do leitor da Jotur com HTML no formato do site (www2.jotur.com.br/linhas). */
public class JoturTest {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "OK   " : "FALHA") + " " + what);
        if (!ok) fails++;
    }
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"feather\"><line x1=\"5\"></line><polyline points=\"12 5\"></polyline></svg>";
    static String link(String code, String nome, String text) {
        return "<li><a href=\"?linha=" + code + "&amp;nome=" + nome + "\">\n " + SVG + "\n <span><strong>" + code + "</strong> - " + text + "</span>\n</a></li>";
    }
    static String col(String day, String... times) {
        StringBuilder b = new StringBuilder("<div class=\"column\"> <h4>" + day + "</h4> <div class=\"times-list\">");
        for (String t : times) b.append("<div class=\"time-item\"> ").append(t).append("</div>");
        return b.append("</div></div>").toString();
    }
    static String item(String head, String cols) {
        return "<div class=\"accordion-item\"> <div class=\"accordion-header\" onclick=\"toggleAccordion(this)\"> <span class=\"seta\">" + SVG
                + "</span> <span>" + head + "</span> </div> <div class=\"accordion-content\"> <div class=\"accordion-body\"> <div class=\"columns-container\">"
                + cols + "</div></div></div></div>";
    }

    public static void main(String[] args) {
        String idx = "<h1>Linhas</h1><ul>" + link("0355", "ARIRIU+%2F+FLORIAN%C3%93POLIS", "ARIRIU / FLORIANÓPOLIS VIA PONTE DO IMARUIM/IVO SILVEIRA")
                + link("8800", "Bigua%C3%A7u", "Biguaçu / Palhoça - Sentido Palhoça") + link("0355", "x", "repetida") + "</ul><a href=\"/\">Início</a>";
        List<Line> ls = JoturParser.parseIndex(idx);
        check(ls.size() == 2, "índice: 2 linhas, sem repetir " + ls.size());
        check(ls.get(0).code.equals("JOT#0355") && ls.get(0).name.equals("0355 ARIRIU / FLORIANÓPOLIS VIA PONTE DO IMARUIM/IVO SILVEIRA"), "índice: nome " + ls.get(0).name);
        check(ls.get(0).url.equals("https://www2.jotur.com.br/linhas/?linha=0355&nome=ARIRIU+%2F+FLORIAN%C3%93POLIS"), "índice: endereço " + ls.get(0).url);
        check(ls.get(1).category.equals("Jotur"), "índice: empresa");

        String page = "<html><body><h1> 0355 - ARIRIU / FLORIANÓPOLIS VIA PONTE DO IMARUIM/IVO SILVEIRA </h1><p><strong>Cartão:</strong> R$ 10,10</p>"
                + "<!-- <h3>Horários por saída:</h3> --><div class=\"accordion\">"
                + item("IDA", col("Dias Úteis", "05:15", "05:50", "23:40", "00:10") + col("Sábados", "06:00") + col("Domingos e Feriados"))
                + item("VOLTA", col("Dias Úteis", "06:05") + col("Sábados") + col("Domingos e Feriados", "06:25", "22:20"))
                + item("Volta", col("Dias Úteis", "07:00"))
                + "</div><a href=\"?\">← Voltar para lista de linhas</a></body></html>";
        Timetable tt = JoturParser.parseLine(page);
        check(tt.departures.size() == 2, "duas saídas " + tt.departures.size());
        check(tt.departures.get(0).name.equals("ARIRIU") && tt.departures.get(1).name.equals("FLORIANÓPOLIS"), "nomes das saídas " + tt.departures.get(0).name + " / " + tt.departures.get(1).name);
        Departure ida = tt.departures.get(0), volta = tt.departures.get(1);
        check(ida.days.get(0).size() == 4 && ida.days.get(1).size() == 1 && ida.days.get(2).isEmpty(), "ida: úteis 4, sábado 1, domingo 0");
        check(ida.days.get(0).get(3).min == 1440 + 10, "00:10 é madrugada");
        check(volta.days.get(0).size() == 2 && volta.days.get(2).size() == 2, "volta junta o bloco repetido 'Volta'");

        Timetable t2 = JoturParser.parseLine("<h1>8800 - Biguaçu / Palhoça - Sentido Palhoça</h1>" + item("IDA", col("Dias Úteis", "05:00")));
        check(t2.departures.size() == 1 && t2.departures.get(0).name.equals("Biguaçu"), "sentido no nome: saída Biguaçu");
        Timetable t3 = JoturParser.parseLine("<h1>223 - Warning: Undefined array key \"nome\"</h1>" + item("VOLTA", col("Sábados", "04:45")));
        check(t3.departures.size() == 1 && t3.departures.get(0).name.equals("Volta"), "sem nome: saída Volta");
        check(JoturParser.parseLine("<h1>222 - X / Y</h1><p>nada</p>").departures.isEmpty(), "linha sem horários");
        if (fails > 0) { System.out.println(fails + " falha(s)"); System.exit(1); }
        System.out.println("Jotur: tudo certo");
    }
}
