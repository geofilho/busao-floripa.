package br.com.busfloripa;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Tela inicial: suas listas no topo, busca de linhas embaixo. */
public class MainActivity extends Activity {

    private LinearLayout listsBox;
    private ScrollView listsScroll;
    private EditText search;
    private TextView status;
    private final List<FenixParser.Line> all = new ArrayList<>();
    private final List<FenixParser.Line> shown = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Repo repo;
    private Location lastLoc;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        repo = new Repo(this);
        listsBox = findViewById(R.id.listsBox);
        listsScroll = findViewById(R.id.listsScroll);
        search = findViewById(R.id.search);
        status = findViewById(R.id.status);
        Ui.heading(findViewById(R.id.listsTitle));
        Ui.heading(findViewById(R.id.linesTitle));

        ListView lv = findViewById(R.id.lines);
        adapter = new ActionAdapter(this, rows, pos -> {
            if (pos >= shown.size()) return null;
            FenixParser.Line l = shown.get(pos);
            return Ui.optionsLazy(this, l.code, l.name, l.url, this::renderLists);
        });
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            FenixParser.Line l = shown.get(pos);
            Ui.lineActions(this, l.code, l.name, l.url, null, null, this::renderLists);
        });

        search.setOnFocusChangeListener((v, has) -> {
            if (!has && shown.isEmpty() && search.getText().length() > 0) clearSearch();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) { filter(); }
        });

        loadLines(false);
    }

    @Override protected void onResume() {
        super.onResume();
        renderLists();
        dailyUpdate();
        boolean anyLoc = false;
        for (Store.UserList l : Store.get(this).lists()) anyLoc |= l.hasLoc;
        if (anyLoc && Loc.granted(this)) {
            Loc.current(this, 3 * 60 * 1000, loc -> {
                if (loc == null || isFinishing()) return;
                lastLoc = loc;
                renderLists();
            });
        }
    }

    /** Primeira abertura do dia: atualiza tudo. Sem internet, tenta de novo na próxima abertura. */
    private void dailyUpdate() {
        if (!Updater.shouldRun(this)) return;
        status.setText("Atualizando os horários do dia…");
        Updater.run(this, r -> onUpdated(r, false));
    }

    private void onUpdated(Updater.Report r, boolean manual) {
        if (isFinishing() || isDestroyed()) return;
        all.clear();
        all.addAll(Store.get(this).loadLines());
        all.sort(MainActivity::byNumber);
        filter();
        renderLists();
        searchStatus = false;
        if (r.noInternet) {
            status.setText(r.summary());
            if (manual) problemDialog(r);
            return;
        }
        if (r.errors > 0 || r.warnings > 0) {
            status.setText(r.summary());
            problemDialog(r);
        } else {
            status.setText(r.summary());
            if (manual) Ui.say(this, r.summary());
        }
    }

    /** Aviso de que a atualização teve problemas, com os primeiros itens e o caminho para o diagnóstico. */
    private void problemDialog(Updater.Report r) {
        StringBuilder msg = new StringBuilder(r.summary());
        int n = 0;
        for (String l : r.lines) {
            if (!l.startsWith("Erro") && !l.startsWith("Atenção")) continue;
            if (n++ == 3) { msg.append("\n\nVeja o resto no diagnóstico."); break; }
            msg.append("\n\n").append(l);
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(r.noInternet ? "Sem internet" : "Não foi possível atualizar")
                .setMessage(msg.toString())
                .setPositiveButton("Ver diagnóstico", (d, w) -> openDiagnostico())
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void openDiagnostico() {
        startActivity(new Intent(this, DiagnosticoActivity.class));
    }

    private void loadLines(boolean force) {
        if (all.isEmpty()) status.setText("Carregando linhas do Consórcio Fênix…");
        repo.lines(force, (lines, err) -> {
            if (lines == null) {
                status.setText("Não foi possível carregar as linhas. " + err
                        + ". Conecte à internet e use o menu Mais opções, Atualizar linhas.");
                return;
            }
            all.clear();
            all.addAll(lines);
            all.sort(MainActivity::byNumber);
            filter();
        });
    }

    /** Ordem numérica: 174 antes de V-174, 175 depois; códigos sem número no fim. */
    static int byNumber(FenixParser.Line a, FenixParser.Line b) {
        int na = number(a.code), nb = number(b.code);
        if (na != nb) return Integer.compare(na, nb);
        boolean va = !Character.isDigit(a.code.charAt(0)), vb = !Character.isDigit(b.code.charAt(0));
        if (va != vb) return va ? 1 : -1;
        return a.code.compareToIgnoreCase(b.code);
    }

    static int number(String code) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(code);
        if (!m.find()) return Integer.MAX_VALUE;
        try { return Integer.parseInt(m.group()); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }

    /** Voltar com uma busca feita: limpa a busca e mostra todas as linhas, em vez de sair do app. */
    @Override public void onBackPressed() {
        if (search.getText().length() > 0) {
            clearSearch();
            Ui.say(this, "Busca limpa. " + all.size() + " linhas");
            return;
        }
        super.onBackPressed();
    }

    private boolean quiet, searchStatus;

    /** Limpa a busca em silêncio: o leitor de tela não fala "linha não encontrada" nem a contagem. */
    private void clearSearch() {
        quiet = true;
        search.setText("");
        quiet = false;
        search.clearFocus();
    }

    /** Ao voltar de outra tela ou de outro app, a busca começa limpa. */
    @Override protected void onRestart() {
        super.onRestart();
        if (search.getText().length() > 0) clearSearch();
    }

    private void filter() {
        String q = FenixParser.fold(search.getText().toString());
        shown.clear();
        List<FenixParser.Line> rest = new ArrayList<>();
        for (FenixParser.Line l : all) {
            String code = FenixParser.fold(l.code), name = FenixParser.fold(l.name);
            if (q.isEmpty()) { shown.add(l); continue; }
            if (code.equals(q) || code.startsWith(q) || code.replace("v ", "").startsWith(q)) shown.add(l);
            else if (name.contains(q) || (code + " " + name).contains(q)) rest.add(l);
        }
        shown.addAll(rest);
        rows.clear();
        for (FenixParser.Line l : shown) rows.add(l.code + ", " + l.name);
        adapter.notifyDataSetChanged();
        // Só mexe no aviso quando ele é da busca, para não apagar o aviso da atualização diária.
        if (quiet || q.isEmpty() || all.isEmpty()) {
            if (searchStatus) { status.setText(""); searchStatus = false; }
            return;
        }
        searchStatus = true;
        status.setText(shown.isEmpty() ? "Nenhuma linha encontrada"
                : shown.size() == 1 ? "1 linha encontrada" : shown.size() + " linhas encontradas");
    }

    private void renderLists() {
        listsBox.removeAllViews();
        List<Store.UserList> lists = new ArrayList<>(Store.get(this).lists());
        // Listas perto de você vão para o topo.
        List<Store.UserList> near = new ArrayList<>();
        if (lastLoc != null) {
            for (Store.UserList l : lists)
                if (l.hasLoc && Loc.distance(lastLoc, l.lat, l.lon) <= Loc.NEAR_METERS) near.add(l);
            near.sort((x, y) -> Float.compare(Loc.distance(lastLoc, x.lat, x.lon), Loc.distance(lastLoc, y.lat, y.lon)));
            lists.removeAll(near);
            lists.addAll(0, near);
        }
        for (Store.UserList l : lists) {
            Button btn = new Button(this);
            int n = l.items.size();
            String text = l.name + ", " + (n == 1 ? "1 linha" : n + " linhas") + (near.contains(l) ? ", perto de você" : "");
            btn.setText(text);
            btn.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            btn.setOnClickListener(v -> startActivity(new Intent(this, ListaActivity.class).putExtra("id", l.id)));
            listsBox.addView(btn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        Button add = new Button(this);
        add.setText(lists.isEmpty() ? "Criar sua primeira lista" : "Criar nova lista");
        add.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        add.setOnClickListener(v -> Ui.newList(this, l -> {
            renderLists();
            startActivity(new Intent(this, ListaActivity.class).putExtra("id", l.id));
        }));
        listsBox.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Se houver muitas listas, a área rola e deixa espaço para a busca.
        listsScroll.post(() -> {
            int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.4);
            ViewGroup.LayoutParams lp = listsScroll.getLayoutParams();
            int wanted = listsBox.getHeight() > max ? max : ViewGroup.LayoutParams.WRAP_CONTENT;
            if (lp.height != wanted) { lp.height = wanted; listsScroll.setLayoutParams(lp); }
        });
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Atualizar agora");
        menu.add(0, 3, 0, "Diagnóstico");
        menu.add(0, 2, 0, "Como usar");
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            if (Updater.isRunning()) { Ui.say(this, "A atualização já está em andamento"); return true; }
            status.setText("Atualizando…");
            Updater.run(this, r -> onUpdated(r, true));
            return true;
        }
        if (item.getItemId() == 3) {
            openDiagnostico();
            return true;
        }
        if (item.getItemId() == 2) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Como usar")
                    .setMessage("1. Crie uma lista, por exemplo Saídas de casa.\n"
                            + "2. Busque uma linha, toque nela e escolha Adicionar a uma lista.\n"
                            + "3. Informe quantos minutos o ônibus leva da saída até o seu ponto.\n"
                            + "4. Abra a lista: os ônibus aparecem na ordem de chegada.\n\n"
                            + "Os horários ficam salvos no celular e funcionam sem internet. "
                            + "Na primeira vez que você abre o app no dia, ele atualiza tudo; se der problema, avisa "
                            + "e mostra os detalhes em Mais opções, Diagnóstico. "
                            + "Nas opções da lista você pode ligar a lista a um local: "
                            + "quando estiver perto dele, ela aparece primeiro.\n\n"
                            + "Horários: Consórcio Fênix. Podem mudar; confira em caso de dúvida.")
                    .setPositiveButton("Fechar", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
