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
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(message)
                .setView(box)
                .setPositiveButton("Salvar", (di, w) -> ok.accept(input.getText().toString().trim()))
                .setNegativeButton("Cancelar", null)
                .create();
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

    public static void askOffset(Activity a, int current, Consumer<Integer> ok) {
        askText(a, "Minutos até o seu ponto",
                "Quantos minutos o ônibus leva da saída até o ponto onde você pega? "
                        + "Se você pega no ponto de saída, deixe 0.",
                String.valueOf(current), "Minutos, por exemplo 10", true, s -> {
                    int v = 0;
                    try { v = Math.max(0, Math.min(300, Integer.parseInt(s))); } catch (NumberFormatException ignored) { }
                    ok.accept(v);
                });
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

    /** Ordem das saídas: sentido centro primeiro, depois sentido bairro. */
    static List<Integer> depOrder(FenixParser.Timetable tt) {
        List<Integer> first = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = 0; i < tt.departures.size(); i++) {
            if (Schedule.sentidoLabel(tt, i).startsWith("Sentido centro")) first.add(i); else rest.add(i);
        }
        first.addAll(rest);
        return first;
    }

    /**
     * Janela da linha. Sem item: sentidos + "Adicionar a uma lista".
     * Com item (linha dentro de uma lista): sentidos + alterar minutos + remover.
     */
    public static void lineActions(Activity a, String code, String name, String url,
                                   Store.UserList list, Store.Item item, Runnable changed) {
        Repo repo = new Repo(a);
        if (repo.cached(code) == null) say(a, "Baixando horários da linha " + code);
        repo.timetable(code, name, url, (tt, err) -> {
            if (a.isFinishing() || a.isDestroyed()) return;
            if (tt == null) {
                new AlertDialog.Builder(a)
                        .setTitle(code + " " + name)
                        .setMessage("Não foi possível abrir os horários. " + err + ".")
                        .setPositiveButton("Tentar de novo", (d, w) -> lineActions(a, code, name, url, list, item, changed))
                        .setNegativeButton("Fechar", null)
                        .show();
                return;
            }
            List<Integer> order = depOrder(tt);
            List<String> opts = new ArrayList<>();
            for (int i : order) opts.add(Schedule.sentidoLabel(tt, i));
            int base = opts.size();
            if (item == null) opts.add("Adicionar a uma lista");
            else { opts.add("Alterar minutos até o meu ponto, agora " + item.offset); opts.add("Remover da lista"); }
            new AlertDialog.Builder(a)
                    .setTitle(code + " " + name)
                    .setItems(opts.toArray(new String[0]), (d, w) -> {
                        if (w < base) {
                            Intent i = new Intent(a, HorariosActivity.class)
                                    .putExtra("code", code).putExtra("name", name).putExtra("url", url)
                                    .putExtra("dep", order.get(w));
                            a.startActivity(i);
                        } else if (item == null) {
                            addToList(a, tt, url, changed);
                        } else if (w == base) {
                            askOffset(a, item.offset, v -> {
                                item.offset = v;
                                Store.get(a).saveLists();
                                say(a, "Salvo: " + v + " minutos");
                                if (changed != null) changed.run();
                            });
                        } else {
                            list.items.remove(item);
                            Store.get(a).saveLists();
                            say(a, code + " removida da lista");
                            if (changed != null) changed.run();
                        }
                    })
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
            say(a, tt.code + " adicionada à lista " + list.name);
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
