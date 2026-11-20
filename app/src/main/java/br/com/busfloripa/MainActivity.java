package br.com.busfloripa;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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
        appUpdater = new AppUpdater(this);
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

        View clearBtn = findViewById(R.id.clearSearch);
        clearBtn.setOnClickListener(v -> {
            clearSearch();
            // O botão some; o foco volta para o campo de busca, pronto para digitar de novo.
            search.requestFocus();
            Ui.say(this, "Busca limpa");
        });
        search.setOnFocusChangeListener((v, has) -> {
            if (!has && shown.isEmpty() && search.getText().length() > 0) clearSearch();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                // Botão Limpar só aparece quando há texto na busca.
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                filter();
            }
        });

        inter = getSharedPreferences("tela", MODE_PRIVATE).getBoolean("inter", false);
        applyMode();
        loadLines(false);
    }

    // ---------- Fênix ou intermunicipal ----------

    /** true = mostrando as linhas intermunicipais (Estrela, Santa Terezinha). */
    private boolean inter;

    private void applyMode() {
        TextView t = findViewById(R.id.linesTitle);
        t.setText(inter ? "Linhas intermunicipais" : "Linhas de ônibus");
        search.setHint(inter ? "Buscar linha intermunicipal por número, nome ou empresa" : "Buscar linha por número ou nome");
        invalidateOptionsMenu();
    }

    private void toggleMode() {
        inter = !inter;
        getSharedPreferences("tela", MODE_PRIVATE).edit().putBoolean("inter", inter).apply();
        clearSearch();
        all.clear();
        filter();
        applyMode();
        Ui.say(this, inter ? "Mostrando linhas intermunicipais" : "Mostrando linhas do Consórcio Fênix");
        loadLines(false);
    }

    private void sortLines() {
        if (inter) all.sort(Providers::compare); else all.sort(MainActivity::byNumber);
    }

    private AppUpdater appUpdater;

    @Override protected void onResume() {
        super.onResume();
        if (appUpdater != null) appUpdater.onResume();
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
        all.addAll(inter ? Store.get(this).loadLines(Store.INTER_FILE) : Store.get(this).loadLines());
        sortLines();
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
            String ok = r.summary();
            status.setText(ok);
            if (manual) Ui.say(this, ok);
            // Deu tudo certo: o aviso some sozinho depois de alguns segundos.
            status.postDelayed(() -> { if (ok.contentEquals(status.getText())) status.setText(""); }, 8000);
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

    private static final String LOADING = "Carregando linhas do Consórcio Fênix…";

    private static final String LOADING_INTER = "Carregando linhas intermunicipais…";

    private void loadLines(boolean force) {
        boolean mode = inter;
        if (all.isEmpty()) status.setText(mode ? LOADING_INTER : LOADING);
        Repo.Callback<List<FenixParser.Line>> cb = (lines, err) -> {
            if (mode != inter) return; // trocou de tela enquanto carregava
            if (lines == null) {
                status.setText("Não foi possível carregar as linhas. " + err
                        + ". Conecte à internet e use Mais opções, Atualizar agora.");
                return;
            }
            all.clear();
            all.addAll(lines);
            sortLines();
            filter();
            // Linhas carregadas: tira o aviso de carregamento (sem mexer em outro aviso, como o da atualização).
            if (LOADING.contentEquals(status.getText()) || LOADING_INTER.contentEquals(status.getText())) status.setText("");
        };
        if (mode) repo.interLines(force, cb); else repo.lines(force, cb);
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
            if (!Providers.isFenix(l.code)) {
                // Intermunicipal: o nome já começa pelo número; busca também pela empresa.
                if (name.startsWith(q)) shown.add(l);
                else if (name.contains(q) || FenixParser.fold(l.category).contains(q)) rest.add(l);
                continue;
            }
            if (code.equals(q) || code.startsWith(q) || code.replace("v ", "").startsWith(q)) shown.add(l);
            else if (name.contains(q) || (code + " " + name).contains(q)) rest.add(l);
        }
        shown.addAll(rest);
        rows.clear();
        for (FenixParser.Line l : shown) rows.add(Providers.rowTitle(l.code, l.name));
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

    private static final int REQ_SAVE = 51, REQ_OPEN = 52;

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        if (req == REQ_SAVE) saveBackup(uri);
        else if (req == REQ_OPEN) openBackup(uri);
    }

    private void saveBackup(android.net.Uri uri) {
        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("não abriu o arquivo");
            out.write(Store.get(this).exportBackup().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int n = Store.get(this).lists().size(), loc = 0;
            for (Store.UserList l : Store.get(this).lists()) if (l.hasLoc) loc++;
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Backup salvo")
                    .setMessage((n == 1 ? "1 lista salva" : n + " listas salvas")
                            + (loc == 0 ? "." : ", " + (loc == 1 ? "1 com local GPS." : loc + " com local GPS.")))
                    .setPositiveButton("Fechar", null)
                    .show();
        } catch (Exception e) {
            Ui.say(this, "Não foi possível salvar o backup: " + e.getMessage());
        }
    }

    private void openBackup(android.net.Uri uri) {
        List<Store.UserList> from;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("não abriu o arquivo");
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0 && buf.size() < 5_000_000) buf.write(b, 0, n);
            from = Store.readBackup(buf.toString("UTF-8"));
        } catch (Exception e) {
            String m = e.getMessage();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Não foi possível restaurar")
                    .setMessage(m != null && m.startsWith("Este arquivo") ? m + "."
                            : "O arquivo escolhido não é um backup válido do Busão Floripa G.")
                    .setPositiveButton("Fechar", null)
                    .show();
            return;
        }
        if (from.isEmpty()) { Ui.say(this, "O backup não tem nenhuma lista"); return; }
        StringBuilder names = new StringBuilder();
        for (Store.UserList l : from) names.append(names.length() == 0 ? "" : ", ").append(l.name);
        int mine = Store.get(this).lists().size();
        String msg = "O backup tem " + (from.size() == 1 ? "1 lista: " : from.size() + " listas: ") + names + ".";
        android.app.AlertDialog.Builder d = new android.app.AlertDialog.Builder(this).setTitle("Restaurar backup");
        if (mine == 0) {
            d.setMessage(msg).setPositiveButton("Restaurar", (x, w) -> applyBackup(from, true));
        } else {
            d.setMessage(msg + " Você tem " + (mine == 1 ? "1 lista" : mine + " listas") + " agora.\n\n"
                            + "Substituir apaga as suas listas atuais e coloca as do backup. "
                            + "Juntar acrescenta as do backup; lista com o mesmo nome é trocada pela do backup.")
                    .setPositiveButton("Substituir", (x, w) -> applyBackup(from, true))
                    .setNeutralButton("Juntar", (x, w) -> applyBackup(from, false));
        }
        d.setNegativeButton("Cancelar", null).show();
    }

    private void applyBackup(List<Store.UserList> from, boolean replace) {
        Store.get(this).applyBackup(from, replace);
        renderLists();
        int n = Store.get(this).lists().size();
        Ui.say(this, "Backup restaurado. Você tem " + (n == 1 ? "1 lista" : n + " listas") + ".");
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        // Botão na barra, à esquerda de Mais opções: alterna entre Fênix e intermunicipal.
        MenuItem sw = menu.add(0, 7, 0, inter ? "Fênix" : "Intermunicipal");
        sw.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        sw.setContentDescription(inter ? "Alternar para Consórcio Fênix" : "Alternar para transporte intermunicipal");
        menu.add(0, 1, 0, "Atualizar agora");
        menu.add(0, 2, 0, "Como usar");
        android.view.SubMenu dados = menu.addSubMenu(0, 6, 0, "Dados");
        dados.setHeaderTitle("Dados");
        dados.add(0, 4, 0, "Fazer backup");
        dados.add(0, 5, 0, "Restaurar backup");
        dados.add(0, 3, 0, "Diagnóstico");
        dados.add(0, 8, 0, "Atualização");
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
        if (item.getItemId() == 8) {
            appUpdater.check();
            return true;
        }
        if (item.getItemId() == 7) {
            toggleMode();
            return true;
        }
        if (item.getItemId() == 2) {
            startActivity(new Intent(this, AjudaActivity.class));
            return true;
        }
        if (item.getItemId() == 4) {
            if (Store.get(this).lists().isEmpty()) { Ui.say(this, "Você ainda não tem listas para salvar"); return true; }
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "busao-floripa-backup-" + java.time.LocalDate.now() + ".json");
            startActivityForResult(i, REQ_SAVE);
            return true;
        }
        if (item.getItemId() == 5) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*");
            startActivityForResult(i, REQ_OPEN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
