import br.com.busfloripa.FenixParser;
import br.com.busfloripa.FenixParser.*;
import br.com.busfloripa.GenericParser;
import java.util.List;

/** Testes do leitor intermunicipal (Estrela, Santa Terezinha) com HTML nos formatos vistos. */
public class InterTest {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "OK   " : "FALHA") + " " + what);
        if (!ok) fails++;
    }
    static String a(String t) { return "<a href=\"#\">" + t + "</a>"; }

    public static void main(String[] args) {
        // Índice Estrela: tabela com código numa célula e link na outra
        String ie = "<h3>Intermunicipal</h3><table><tr><td>317</td><td><a href=\"http://www.insulartc.com.br/estrela/ws1/linhas/horarios/intermunicipal/0317.htm\">Campinas Via Kobrasol</a></td></tr>"
                + "<tr><td>039.0/1/3/4</td><td><a href=\"ws1/linhas/horarios/intermunicipal/0039.htm\">Forquilhas Via Los Angeles</a></td></tr>"
                + "<tr><td><a href=\"ws1/linhas/horarios/intermunicipal/7633.htm\">Executivo San Marino</a></td></tr></table>";
        List<Line> le = GenericParser.parseIndexEstrela(ie, "http://www.insulartc.com.br/estrela/");
        check(le.size() == 3, "Estrela: 3 linhas (" + le.size() + ")");
        check(le.get(0).code.equals("EST#0317") && le.get(0).name.equals("317 Campinas Via Kobrasol") && le.get(0).category.equals("Estrela"), "Estrela: código e nome " + le.get(0).name);
        check(le.get(1).name.startsWith("039.0/1/3/4 ") && le.get(1).url.equals("http://www.insulartc.com.br/estrela/ws1/linhas/horarios/intermunicipal/0039.htm"), "Estrela: código composto e endereço " + le.get(1).url);
        check(le.get(2).name.equals("7633 Executivo San Marino"), "Estrela: sem célula de código usa o arquivo " + le.get(2).name);

        // Índice Santa Terezinha: menu repetido, links com e sem /horarios/
        String is = "<nav><a href=\"https://santaterezinha.com/horarios/\">Horários</a><a href=\"https://santaterezinha.com/horarios/sertao-florianopolis/\">Sertão &#8211; Florianópolis</a>"
                + "<a href=\"https://santaterezinha.com/horarios/diretao/\">Diretão</a></nav><a href=\"https://santaterezinha.com/horarios/sertao-florianopolis/\">Sertão – Florianópolis</a>"
                + "<a href=\"https://santaterezinha.com/sertao-florianopolis/\">Detalhes</a>";
        List<Line> ls = GenericParser.parseIndexSantaTerezinha(is);
        check(ls.size() == 2 && ls.get(0).code.equals("STE#sertao-florianopolis") && ls.get(0).name.equals("Sertão – Florianópolis"), "Santa Terezinha: 2 linhas sem repetir " + ls.size());

        // Linha Santa Terezinha: saída > dias > horários com letras
        String st = "<p>TARIFAS A PARTIR 22/07/26</p><p><strong>Saída Sertão</strong></p><h4>SEGUNDA A SEXTA</h4><ul><li>" + a("05:40 UN. B.") + "</li><li>" + a("05:50 SC IFSC") + "</li><li>" + a("20:10") + "</li></ul>"
                + "<h4>SÁBADO</h4><ul><li>" + a("15:00 SC* SD") + "</li></ul><h4>DOMINGOS E FERIADOS</h4><ul><li>" + a("18:00 SD") + "</li></ul>"
                + "<p>S. Maruim - Florianópolis Cartão R$ 10,23</p><p><strong>Saída TICEN</strong></p><h4>SEGUNDA A SEXTA</h4><ul><li>" + a("07:15 SC EXP") + "</li><li>" + a("07:55 SC EXP BR") + "</li></ul>"
                + "<h4>Legenda</h4><p>UN – VIA UNIVALI</p><p>SC - Via Shopping Continente</p><p>EXP – VIA EXPRESSA</p>";
        Timetable t1 = GenericParser.parseLine(st);
        check(t1.departures.size() == 2 && t1.departures.get(0).name.equals("Sertão") && t1.departures.get(1).name.equals("TICEN"), "ST: 2 saídas " + t1.departures.size());
        Departure se = t1.departures.get(0);
        check(se.days.get(0).size() == 3 && se.days.get(1).size() == 1 && se.days.get(2).size() == 1, "ST: dias certos " + se.days.get(0).size() + "/" + se.days.get(1).size() + "/" + se.days.get(2).size());
        check(se.days.get(0).get(0).flags.equals("UN B") && se.days.get(1).get(0).flags.equals("SC* SD"), "ST: letras " + se.days.get(0).get(0).flags + " | " + se.days.get(1).get(0).flags);
        check(t1.departures.get(1).days.get(0).size() == 2 && t1.departures.get(1).days.get(1).isEmpty(), "ST: TICEN só dias úteis");
        check("VIA UNIVALI".equals(t1.legend.get("UN")) && "Via Shopping Continente".equals(t1.legend.get("SC")), "ST: legenda " + t1.legend);

        // Linha Estrela em lista: PARTIDAS DO BAIRRO (IDA) / DIA ÚTIL / SÁBADOS, horários 05.20RNBSL e 08:45*
        String es = "<h3>PARTIDAS DO BAIRRO (IDA)</h3><p>DIA ÚTIL</p><p>05.00A<br>05.20RNBSL<br>08:45*<br>23:30<br>00:20</p>"
                + "<p>SÁBADOS</p><p>06.15 07.30 09.10*</p><h3>PARTIDAS DO TICEN / FPOLIS. (VOLTA)</h3><p>DOMINGOS E FERIADOS</p><ul><li>07.00A</li></ul>"
                + "<p>Quadro de horários atualizado em 29/10/2026</p><p>Atendimento 08:00 às 18:00</p>";
        Timetable t2 = GenericParser.parseLine(es);
        check(t2.departures.size() == 2 && t2.departures.get(0).name.equals("BAIRRO") && t2.departures.get(1).name.equals("TICEN / FPOLIS"), "Estrela: saídas " + (t2.departures.isEmpty() ? "-" : t2.departures.get(0).name + " | " + t2.departures.get(t2.departures.size() - 1).name));
        Departure b = t2.departures.get(0);
        check(b.days.get(0).size() == 5 && b.days.get(1).size() == 3, "Estrela: dia útil 5, sábado 3 (" + b.days.get(0).size() + "/" + b.days.get(1).size() + ")");
        check(b.days.get(0).get(1).flags.equals("RNBSL") && b.days.get(0).get(2).flags.equals("*"), "Estrela: letras grudadas e asterisco");
        check(b.days.get(0).get(4).min == 1440 + 20, "Estrela: 00:20 é madrugada");
        check(t2.updated.equals("29/10/2026"), "Estrela: data de atualização");
        check(t2.departures.get(1).days.get(2).size() == 1, "Estrela: domingo na volta");

        // Linha em tabela: colunas por dia
        String tb = "<p>Saída Biguaçu</p><table><tr><th>Dias úteis</th><th>Sábado</th><th>Domingo</th></tr>"
                + "<tr><td>05:05</td><td>05:05</td><td>06:45</td></tr><tr><td>06:10</td><td>08:55</td><td>-</td></tr></table>";
        Timetable t3 = GenericParser.parseLine(tb);
        Departure bg = t3.departures.get(0);
        check(bg.name.equals("Biguaçu") && bg.days.get(0).size() == 2 && bg.days.get(1).size() == 2 && bg.days.get(2).size() == 1, "tabela com colunas por dia");

        // Dois ônibus no mesmo minuto com vias diferentes
        Timetable t4 = GenericParser.parseLine("<p>Saída Centro</p><p>Segunda a sexta</p><p>05.50LA</p><p>05.50VF</p>");
        check(t4.departures.get(0).days.get(0).size() == 2, "mesmo minuto, vias diferentes: os dois ficam");

        System.out.println(fails == 0 ? "\nTodos os testes intermunicipais passaram." : "\n" + fails + " falha(s).");
        System.exit(fails == 0 ? 0 : 1);
    }
}
