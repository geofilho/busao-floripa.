package br.com.busfloripa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Diálogos e ajudas de acessibilidade usados nas telas. */
public final class Ui {

    private Ui() { }

    public static ArrayAdapter<String> adapter(Context c, List<String> rows) {
        return new ArrayAdapter<>(c, R.layout.row, rows);
    }

    public static void heading(View v) {
        if (Build.VERSION.SDK_INT >= 28) v.setAccessibilityHeading(true);
    }

    /** Mensagem curta, lida pelo TalkBack. */
    public static void say(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_SHORT).show();
    }

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------- entrada de texto e número ----------

    public static void askText(Activity a, String title, String message, String initial, String hint,
                               boolean number, Consumer<String> ok) {
        askText(a, title, message, initial, hint, number, null, null, ok);
    }

    /** Igual, com um terceiro botão opcional (extraLabel / extra). */
    public static void askText(Activity a, String title, String message, String initial, String hint,
                               boolean number, String extraLabel, Runnable extra, Consumer<String> ok) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 20);
        box.setPadding(p, dp(a, 8), p, 0);
        EditText input = new EditText(a);
        input.setTextSize(20);
        input.setMinHeight(dp(a, 56));
        input.setHint(hint);
        input.setContentDescription(hint);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        if (initial != null) { input.setText(initial); input.setSelection(initial.length()); }
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog.Builder bld = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(message)
                .setView(box)
                .setPositiveButton("Salvar", (di, w) -> ok.accept(input.getText().toString().trim()))
                .setNegativeButton("Cancelar", null);
        if (extraLabel != null && extra != null) bld.setNeutralButton(extraLabel, (di, w) -> extra.run());
        AlertDialog d = bld.create();
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE) {
                d.dismiss();
                ok.accept(input.getText().toString().trim());
                return true;
            }
            return false;
        });
        d.show();
        input.requestFocus();
    }

    private static final String MEDIR = " Se você pega em outro ponto, deixe 0 e, quando o ônibus chegar lá, use O ônibus chegou agora: o app mede o tempo real e ajusta sozinho.";

    private static int parseMinutes(String s) {
        try { return Math.max(0, Math.min(300, Integer.parseInt(s.trim()))); } catch (NumberFormatException e) { return 0; }
    }

    public static void askOffset(Activity a, int current, Consumer<Integer> ok) {
        askText(a, "Minutos até o seu ponto",
                "Quantos minutos o ônibus leva da saída até o ponto onde você pega? "
                        + "Com 0, a lista mostra o horário programado de saída." + MEDIR,
                String.valueOf(current), "Minutos, por exemplo 10", true, s -> ok.accept(parseMinutes(s)));
    }

    /** "O ônibus chegou agora": mede o tempo real de viagem e ajusta os minutos da linha. */
    public static void busArrived(Activity a, Store.Item item, FenixParser.Timetable tt, Runnable changed) {
        int idx = tt.indexOf(item.dep);
        if (idx < 0 && item.depIdx < tt.departures.size()) idx = item.depIdx;
        if (idx < 0) { say(a, "Não encontrei a saída desta linha"); return; }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Integer> cands = Schedule.travelCandidates(tt.departures.get(idx), now,
                Schedule.dayType(now.toLocalDate()));
        if (cands.isEmpty()) {
            new AlertDialog.Builder(a).setTitle("O ônibus chegou agora")
                    .setMessage("Nenhum " + item.title() + " saiu de " + item.dep + " nas últimas 2 horas. "
                            + "Confira se é a linha e o sentido certos.")
                    .setPositiveButton("Fechar", null).show();
            return;
        }
        Integer ref = !item.samples.isEmpty() ? Integer.valueOf(Schedule.median(item.samples))
                : item.offset > 0 ? Integer.valueOf(item.offset) : null;
        Integer pick = ref == null ? null : Schedule.pickTravel(cands, ref);
        Consumer<Integer> record = v -> {
            item.samples.add(v);
            while (item.samples.size() > 5) item.samples.remove(0);
            item.offset = Schedule.median(item.samples);
            Store.get(a).saveLists();
            int n = item.samples.size();
            say(a, "Anotado: " + v + " minutos desta vez. Usando " + item.offset + " minutos"
                    + (n > 1 ? ", a média de " + n + " medições." : "."));
            if (changed != null) changed.run();
        };
        if (pick != null) { record.accept(pick); return; }
        int max = Math.min(6, cands.size());
        String[] labels = new String[max];
        for (int k = 0; k < max; k++) {
            int v = cands.get(k);
            labels[k] = "Saiu às " + Schedule.clock(now.getHour() * 60 + now.getMinute() - v) + ", "
                    + v + (v == 1 ? " minuto" : " minutos") + " de viagem";
        }
        new AlertDialog.Builder(a)
                .setTitle("Qual destes chegou?")
                .setItems(labels, (d, w) -> record.accept(cands.get(w)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------- listas ----------

    public static void newList(Activity a, Consumer<Store.UserList> created) {
        askText(a, "Nova lista", "Dê um nome, por exemplo Saídas de casa.", "", "Nome da lista", false, name -> {
            if (name.isEmpty()) { say(a, "Lista não criada: o nome ficou vazio"); return; }
            Store s = Store.get(a);
            Store.UserList l = new Store.UserList();
            l.name = name;
            s.lists().add(l);
            s.saveLists();
            created.accept(l);
        });
    }

    public static void chooseList(Activity a, Consumer<Store.UserList> chosen) {
        List<Store.UserList> lists = Store.get(a).lists();
        if (lists.isEmpty()) { newList(a, chosen); return; }
        String[] names = new String[lists.size() + 1];
        for (int i = 0; i < lists.size(); i++) names[i] = lists.get(i).name;
        names[lists.size()] = "Criar nova lista";
        new AlertDialog.Builder(a)
                .setTitle("Adicionar em qual lista?")
                .setItems(names, (d, w) -> {
                    if (w == lists.size()) newList(a, chosen);
                    else chosen.accept(lists.get(w));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------- ações de uma linha ----------

    /** Ordem das saídas: a que vai para o centro primeiro, depois a que vai para o bairro. */
    static List<Integer> depOrder(FenixParser.Timetable tt) {
        List<Integer> first = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = 0; i < tt.departures.size(); i++) {
            if (Schedule.direction(tt, i) == Schedule.CENTRO) first.add(i); else rest.add(i);
        }
        first.addAll(rest);
        return first;
    }

    /**
     * Janela da linha. Sem item: sentidos + "Adicionar a uma lista".
     * Com item (linha dentro de uma lista): sentidos + alterar minutos + remover.
     */
    /** Uma opção da linha: aparece na janela e também como ação do TalkBack. */
    public static final class Opt {
        public final String label;
        public final Runnable run;
        Opt(String label, Runnable run) { this.label = label; this.run = run; }
    }

    /** Pega os horários (salvos ou baixando) e entrega; avisa se não der. */
    public static void withTimetable(Activity a, String code, String name, String url,
                                     Consumer<FenixParser.Timetable> ok) {
        Repo repo = new Repo(a);
        if (repo.cached(code) == null) say(a, "Baixando horários da linha " + Providers.title(code, name));
        repo.timetable(code, name, url, (tt, err) -> {
            if (a.isFinishing() || a.isDestroyed()) return;
            if (tt == null) {
                new AlertDialog.Builder(a)
                        .setTitle(Providers.title(code, name))
                        .setMessage("Não foi possível abrir os horários. " + err + ".")
                        .setPositiveButton("Tentar de novo", (d, w) -> withTimetable(a, code, name, url, ok))
                        .setNegativeButton("Fechar", null)
                        .show();
                return;
            }
            ok.accept(tt);
        });
    }

    /**
     * Opções de uma linha com horários já em mãos. Sem item: saídas + "Adicionar a uma lista".
     * Com item (linha dentro de uma lista): saídas + alterar minutos + remover.
     */
    public static List<Opt> options(Activity a, FenixParser.Timetable tt, String url,
                                    Store.UserList list, Store.Item item, Runnable changed) {
        List<Opt> out = new ArrayList<>();
        for (int dep : depOrder(tt)) {
            out.add(new Opt(Schedule.sentidoLabel(tt, dep), () -> a.startActivity(new Intent(a, HorariosActivity.class)
                    .putExtra("code", tt.code).putExtra("name", tt.name).putExtra("url", url).putExtra("dep", dep))));
        }
        if (item == null) {
            out.add(new Opt("Adicionar a uma lista", () -> addToList(a, tt, url, changed)));
        } else {
            out.add(new Opt("O ônibus chegou agora", () -> busArrived(a, item, tt, changed)));
            int n = item.samples.size();
            out.add(new Opt("Alterar minutos até o meu ponto, agora " + item.offset
                    + (n == 0 ? "" : n == 1 ? ", medido 1 vez" : ", medido " + n + " vezes"),
                    () -> askOffset(a, item.offset, v -> setMinutes(a, item, v, changed))));
            out.add(new Opt("Remover da lista", () -> {
                list.items.remove(item);
                Store.get(a).saveLists();
                say(a, Providers.title(tt.code, tt.name) + " removida da lista");
                if (changed != null) changed.run();
            }));
        }
        return out;
    }

    /** Minutos digitados ou estimados. Valor diferente do atual descarta as medições antigas. */
    static void setMinutes(Activity a, Store.Item item, int v, Runnable changed) {
        if (v != item.offset) item.samples.clear();
        item.offset = v;
        Store.get(a).saveLists();
        say(a, "Salvo: " + v + (v == 1 ? " minuto" : " minutos"));
        if (changed != null) changed.run();
    }

    /** Opções sem precisar dos horários agora (linha ainda não baixada): baixam na hora de usar. */
    public static List<Opt> optionsLazy(Activity a, String code, String name, String url, Runnable changed) {
        FenixParser.Timetable tt = new Repo(a).cached(code);
        if (tt != null) {
            tt.name = name;
            return options(a, tt, url, null, null, changed);
        }
        List<Opt> out = new ArrayList<>();
        out.add(new Opt("Ver as saídas da linha", () -> lineActions(a, code, name, url, null, null, changed)));
        out.add(new Opt("Adicionar a uma lista", () -> withTimetable(a, code, name, url, t -> {
            t.name = name;
            addToList(a, t, url, changed);
        })));
        return out;
    }

    /** Janela da linha (toque duplo): as mesmas opções das ações do TalkBack. */
    public static void lineActions(Activity a, String code, String name, String url,
                                   Store.UserList list, Store.Item item, Runnable changed) {
        withTimetable(a, code, name, url, tt -> {
            tt.name = name;
            List<Opt> opts = options(a, tt, url, list, item, changed);
            String[] labels = new String[opts.size()];
            for (int k = 0; k < labels.length; k++) labels[k] = opts.get(k).label;
            new AlertDialog.Builder(a)
                    .setTitle(Providers.title(code, name))
                    .setItems(labels, (d, w) -> opts.get(w).run.run())
                    .setNegativeButton("Fechar", null)
                    .show();
        });
    }

    static void addToList(Activity a, FenixParser.Timetable tt, String url, Runnable changed) {
        List<Integer> order = depOrder(tt);
        Consumer<Integer> withDep = dep -> chooseList(a, list -> askOffset(a, 0, off -> {
            Store.Item it = new Store.Item();
            it.code = tt.code; it.name = tt.name; it.url = url;
            it.dep = tt.departures.get(dep).name; it.depIdx = dep; it.offset = off;
            list.items.add(it);
            Store.get(a).saveLists();
            say(a, Providers.title(tt.code, tt.name) + " adicionada à lista " + list.name);
            if (changed != null) changed.run();
        }));
        if (order.size() == 1) { withDep.accept(order.get(0)); return; }
        String[] labels = new String[order.size()];
        for (int i = 0; i < order.size(); i++) labels[i] = Schedule.sentidoLabel(tt, order.get(i));
        new AlertDialog.Builder(a)
                .setTitle("Qual sentido você pega?")
                .setItems(labels, (d, w) -> withDep.accept(order.get(w)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    public static TextView text(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        return t;
    }
}
