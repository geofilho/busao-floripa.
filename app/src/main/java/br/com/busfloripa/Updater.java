package br.com.busfloripa;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Atualização diária: na primeira vez que o app é aberto no dia, baixa a lista de linhas e os
 * horários de todas as linhas salvas, confere se o que veio do site faz sentido e guarda um relatório.
 * Sem internet, tenta de novo na próxima vez que o app for aberto.
 */
public final class Updater {

    public static final String OK = "OK", AVISO = "AVISO", ERRO = "ERRO";

    public static final class Report {
        public String when = "";
        public boolean noInternet;
        public int errors, warnings, updated;
        /** Uma frase por item, já pronta para o leitor de tela. */
        public final List<String> lines = new ArrayList<>();

        void add(String level, String text) {
            if (level.equals(ERRO)) errors++;
            if (level.equals(AVISO)) warnings++;
            lines.add((level.equals(OK) ? "" : level.equals(ERRO) ? "Erro: " : "Atenção: ") + text);
        }

        public String summary() {
            if (noInternet) return "Sem internet: os horários não foram atualizados. "
                    + "Vou tentar de novo quando você abrir o app.";
            if (errors == 0 && warnings == 0) return "Horários atualizados: " + updated + (updated == 1 ? " linha." : " linhas.");
            StringBuilder sb = new StringBuilder("Não foi possível atualizar tudo: ");
            if (errors > 0) sb.append(errors).append(errors == 1 ? " erro" : " erros");
            if (errors > 0 && warnings > 0) sb.append(" e ");
            if (warnings > 0) sb.append(warnings).append(warnings == 1 ? " aviso" : " avisos");
            return sb.append(". Os horários antigos continuam salvos onde houve problema.").toString();
        }

        public String toText() {
            StringBuilder sb = new StringBuilder("Diagnóstico Busão Floripa, ").append(when).append('\n')
                    .append(summary()).append('\n');
            for (String l : lines) sb.append("- ").append(l).append('\n');
            return sb.toString();
        }
    }

    private static final String PREFS = "atualizacao", DAY = "dia", REPORT = "relatorio", TRY = "tentativa";
    private static final long RETRY_MS = 30L * 60 * 1000;
    private static boolean running;
    private static final List<Consumer<Report>> waiting = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Updater() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Já atualizou hoje? */
    public static boolean doneToday(Context c) {
        return LocalDate.now().toString().equals(prefs(c).getString(DAY, ""));
    }

    /** Deve atualizar agora? Uma vez por dia; sem internet, nova tentativa só depois de 30 minutos. */
    public static boolean shouldRun(Context c) {
        if (running || doneToday(c)) return false;
        long last = prefs(c).getLong(TRY, 0);
        return System.currentTimeMillis() - last > RETRY_MS;
    }

    public static boolean isRunning() { return running; }

    /** Último relatório salvo, ou nulo se nunca rodou. */
    public static Report last(Context c) {
        String s = prefs(c).getString(REPORT, null);
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            Report r = new Report();
            r.when = o.optString("when");
            r.noInternet = o.optBoolean("noInternet");
            r.errors = o.optInt("errors");
            r.warnings = o.optInt("warnings");
            r.updated = o.optInt("updated");
            JSONArray a = o.getJSONArray("lines");
            for (int i = 0; i < a.length(); i++) r.lines.add(a.getString(i));
            return r;
        } catch (Exception e) { return null; }
    }

    private static void save(Context c, Report r) {
        try {
            JSONArray a = new JSONArray();
            for (String l : r.lines) a.put(l);
            JSONObject o = new JSONObject().put("when", r.when).put("noInternet", r.noInternet)
                    .put("errors", r.errors).put("warnings", r.warnings).put("updated", r.updated).put("lines", a);
            SharedPreferences.Editor e = prefs(c).edit().putString(REPORT, o.toString());
            // Só conta como "feito hoje" se houve internet; senão tenta de novo na próxima abertura.
            if (!r.noInternet) e.putString(DAY, LocalDate.now().toString()).putLong(TRY, 0);
            else e.putLong(TRY, System.currentTimeMillis());
            e.apply();
        } catch (Exception ignored) { }
    }

    /** Roda a atualização em segundo plano e devolve o relatório na tela principal. */
    public static void run(Context context, Consumer<Report> cb) {
        Context c = context.getApplicationContext();
        if (cb != null) waiting.add(cb);
        if (running) return;
        running = true;
        new Thread(() -> {
            Report r = work(c);
            save(c, r);
            MAIN.post(() -> {
                running = false;
                List<Consumer<Report>> list = new ArrayList<>(waiting);
                waiting.clear();
                for (Consumer<Report> k : list) k.accept(r);
            });
        }).start();
    }

    private static Report work(Context c) {
        Store store = Store.get(c);
        Report r = new Report();
        r.when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));

        // 1. Lista de linhas
        List<FenixParser.Line> oldLines = store.loadLines();
        List<FenixParser.Line> lines = oldLines;
        try {
            List<FenixParser.Line> fresh = FenixParser.parseIndex(Repo.download(FenixParser.INDEX_URL));
            if (fresh.isEmpty()) {
                r.add(ERRO, "Lista de linhas: a página do Fênix abriu, mas não encontrei nenhuma linha. "
                        + "O site pode ter mudado.");
            } else if (oldLines.size() > 20 && fresh.size() < oldLines.size() / 2) {
                r.add(AVISO, "Lista de linhas: o site mostrou só " + fresh.size() + " linhas, antes eram "
                        + oldLines.size() + ". Mantive a lista antiga. Confira o site.");
            } else {
                store.saveLines(fresh);
                lines = fresh;
                r.add(OK, "Lista de linhas: " + fresh.size() + " linhas.");
            }
        } catch (Exception e) {
            if (Repo.isNetwork(e)) { r.noInternet = true; return r; }
            r.add(ERRO, "Lista de linhas: " + Repo.message(e) + ".");
        }

        // 2. Quais linhas atualizar: as que estão nas listas e as que já têm horários salvos.
        Map<String, String[]> todo = new LinkedHashMap<>(); // código -> {nome, endereço}
        Map<String, String> urlByCode = new LinkedHashMap<>();
        for (FenixParser.Line l : lines) urlByCode.put(l.code, l.url);
        for (Store.UserList ul : store.lists())
            for (Store.Item it : ul.items)
                if (!todo.containsKey(it.code)) todo.put(it.code, new String[]{it.name, it.url});
        for (FenixParser.Timetable tt : store.cachedTimetables()) {
            if (todo.containsKey(tt.code)) continue;
            String url = urlByCode.get(tt.code);
            if (url != null) todo.put(tt.code, new String[]{tt.name, url});
        }
        if (todo.isEmpty()) {
            r.add(OK, "Nenhuma linha salva para atualizar ainda.");
            return r;
        }

        // 3. Horários de cada linha, com conferência
        for (Map.Entry<String, String[]> e : todo.entrySet()) {
            String code = e.getKey(), name = e.getValue()[0];
            String url = urlByCode.containsKey(code) ? urlByCode.get(code) : e.getValue()[1];
            String label = code + " " + name + ": ";
            FenixParser.Timetable old = store.loadTimetable(code);
            FenixParser.Timetable tt;
            try {
                tt = FenixParser.parseLine(Repo.download(url));
            } catch (Exception ex) {
                if (Repo.isNetwork(ex)) {
                    r.noInternet = true;
                    r.add(AVISO, label + "a internet caiu durante a atualização.");
                    return r;
                }
                if (ex instanceof IOException && String.valueOf(ex.getMessage()).contains("404")) {
                    r.add(ERRO, label + "a página desta linha não existe mais no site. "
                            + "A linha pode ter sido extinta ou mudado de endereço.");
                } else r.add(ERRO, label + Repo.message(ex) + ".");
                continue;
            }
            tt.code = code;
            tt.name = name;
            int total = total(tt), oldTotal = old == null ? 0 : total(old);
            if (total == 0) {
                r.add(ERRO, label + "a página abriu, mas não consegui ler nenhum horário. O site pode ter mudado.");
                continue;
            }
            if (oldTotal >= 20 && total < oldTotal * 0.4) {
                r.add(AVISO, label + "vieram só " + total + " horários, antes eram " + oldTotal
                        + ". Mantive os horários antigos. Confira no site.");
                continue;
            }
            List<String> notes = new ArrayList<>();
            if (old != null) {
                for (FenixParser.Departure od : old.departures) {
                    int ni = tt.indexOf(od.name);
                    if (ni < 0) continue;
                    for (int d = 0; d < 3; d++)
                        if (!od.days.get(d).isEmpty() && tt.departures.get(ni).days.get(d).isEmpty())
                            notes.add("a saída " + od.name + " ficou sem horários de " + FenixParser.DAY_NAMES[d].toLowerCase());
                }
            }
            for (Store.UserList ul : store.lists())
                for (Store.Item it : ul.items)
                    if (it.code.equals(code) && tt.indexOf(it.dep) < 0)
                        notes.add("a saída " + it.dep + ", usada na lista " + ul.name
                                + ", não existe mais. Remova a linha da lista e adicione de novo");
            store.saveTimetable(tt);
            r.updated++;
            if (notes.isEmpty()) {
                r.add(OK, label + "atualizada, " + total + " horários em " + tt.departures.size()
                        + (tt.departures.size() == 1 ? " saída." : " saídas."));
            } else {
                r.add(AVISO, label + "atualizada, mas " + String.join("; ", notes) + ".");
            }
        }
        return r;
    }

    private static int total(FenixParser.Timetable tt) {
        int t = 0;
        for (FenixParser.Departure d : tt.departures) t += d.total();
        return t;
    }
}
