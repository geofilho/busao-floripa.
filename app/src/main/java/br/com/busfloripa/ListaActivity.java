package br.com.busfloripa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Uma lista: mostra direto quando cada ônibus passa no seu ponto, do mais próximo ao mais distante. */
public class ListaActivity extends Activity {

    private static final class Row {
        Store.Item item;
        String text;
        int sort;
    }

    private Store.UserList list;
    private Repo repo;
    private TextView title, status;
    private Button dayBtn;
    private final List<Row> rowData = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int dayOverride = -1;
    private LocalDate overrideDate;
    private int pendingDownloads;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            rebuild(false);
            handler.postDelayed(this, 30000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_lista);
        repo = new Repo(this);
        list = Store.get(this).list(getIntent().getStringExtra("id"));
        if (list == null) { finish(); return; }
        title = findViewById(R.id.title);
        status = findViewById(R.id.status);
        dayBtn = findViewById(R.id.dayBtn);
        Ui.heading(title);

        ListView lv = findViewById(R.id.arrivals);
        adapter = new ActionAdapter(this, rows, pos -> {
            if (pos >= rowData.size() || rowData.get(pos).item == null) return null;
            Store.Item it = rowData.get(pos).item;
            FenixParser.Timetable tt = repo.cached(it.code);
            if (tt == null) return null;
            tt.name = it.name;
            return Ui.options(this, tt, it.url, list, it, () -> rebuild(true));
        });
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= rowData.size()) return;
            Store.Item it = rowData.get(pos).item;
            if (it == null) return;
            Ui.lineActions(this, it.code, it.name, it.url, list, it, () -> rebuild(true));
        });

        dayBtn.setOnClickListener(v -> {
            int auto = Schedule.dayType(LocalDate.now());
            int cur = todayType();
            int next = (cur + 1) % 3;
            dayOverride = next == auto ? -1 : next;
            overrideDate = LocalDate.now();
            rebuild(true);
            Ui.say(this, "Usando tabela de " + FenixParser.DAY_NAMES[todayType()]);
        });
        findViewById(R.id.refresh).setOnClickListener(v -> refresh(true));
        findViewById(R.id.options).setOnClickListener(v -> options());
    }

    @Override protected void onResume() {
        super.onResume();
        title.setText(list.name);
        setTitle(list.name);
        rebuild(true);
        refresh(false);
        handler.postDelayed(tick, 30000);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private int todayType() {
        if (dayOverride >= 0 && LocalDate.now().equals(overrideDate)) return dayOverride;
        return Schedule.dayType(LocalDate.now());
    }

    /** Monta as frases. Só mexe na tela se algo mudou, para não atrapalhar o leitor de tela. */
    private void rebuild(boolean force) {
        LocalDateTime now = LocalDateTime.now();
        int nowMin = now.getHour() * 60 + now.getMinute();
        int type = todayType();
        boolean manual = type != Schedule.dayType(now.toLocalDate());
        dayBtn.setText("Tabela de hoje: " + FenixParser.DAY_NAMES[type] + (manual ? " (escolhida por você)" : "")
                + ". Toque para trocar");

        List<Row> out = new ArrayList<>();
        for (Store.Item it : list.items) {
            Row r = new Row();
            r.item = it;
            FenixParser.Timetable tt = repo.cached(it.code);
            int idx = tt == null ? -1 : tt.indexOf(it.dep);
            if (tt != null && idx < 0 && it.depIdx < tt.departures.size()) idx = it.depIdx;
            if (tt == null || idx < 0) {
                r.text = Providers.rowTitle(it.code, it.name) + ". Horários ainda não baixados. Toque em Atualizar com internet.";
                r.sort = Integer.MAX_VALUE;
                out.add(r);
                continue;
            }
            List<Schedule.Arrival> next = Schedule.upcoming(tt.departures.get(idx), it.offset, now, type, 2);
            StringBuilder sb = new StringBuilder(Providers.rowTitle(it.code, it.name)).append(". ");
            if (next.isEmpty()) {
                sb.append("Sem horários nesta tabela. ").append(capital(Schedule.sentidoShort(tt, idx))).append('.');
                r.sort = Integer.MAX_VALUE - 1;
            } else {
                Schedule.Arrival a = next.get(0);
                sb.append(it.offset > 0 ? "Chega " : "Sai ").append(Schedule.inMinutes(a.at - nowMin))
                        .append(", às ").append(Schedule.clock(a.at)).append(". ");
                if (next.size() > 1) sb.append("Depois às ").append(Schedule.clock(next.get(1).at)).append(". ");
                sb.append(capital(Schedule.sentidoShort(tt, idx)));
                String f = Providers.flagsText(it.code, tt, a.trip.flags);
                if (!f.isEmpty()) sb.append(", ").append(f);
                sb.append('.');
                r.sort = a.at;
            }
            r.text = sb.toString();
            out.add(r);
        }
        out.sort((x, y) -> Integer.compare(x.sort, y.sort));

        List<String> texts = new ArrayList<>();
        for (Row r : out) texts.add(r.text);
        if (out.isEmpty()) {
            Row r = new Row();
            r.text = "Lista vazia. Volte à tela inicial, busque uma linha, toque nela e escolha Adicionar a uma lista.";
            out.add(r);
            texts.add(r.text);
        }
        if (!force && texts.equals(rows)) return;
        rowData.clear();
        rowData.addAll(out);
        rows.clear();
        rows.addAll(texts);
        adapter.notifyDataSetChanged();
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Baixa horários que faltam. Com force (botão Atualizar), baixa todos. */
    private void refresh(boolean force) {
        List<Store.Item> todo = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Store.Item it : list.items) {
            if (seen.contains(it.code)) continue;
            seen.add(it.code);
            if (force || repo.cached(it.code) == null) todo.add(it);
        }
        if (todo.isEmpty()) {
            if (force) status.setText(list.items.isEmpty() ? "Nada para atualizar" : "Horários já estão atualizados");
            return;
        }
        status.setText("Atualizando horários…");
        pendingDownloads = todo.size();
        final int[] failed = {0};
        final String[] lastErr = {null};
        for (Store.Item it : todo) {
            repo.download(it.code, it.name, it.url, (tt, err) -> {
                if (tt == null) { failed[0]++; lastErr[0] = err; }
                if (--pendingDownloads > 0 || isFinishing()) return;
                rebuild(true);
                if (failed[0] == 0) status.setText("Horários atualizados");
                else status.setText(lastErr[0] + ". Usando os horários salvos no celular.");
            });
        }
    }

    private void options() {
        List<String> opts = new ArrayList<>();
        opts.add("Renomear lista");
        opts.add(list.hasPlace() ? "Ligar a lista ao local onde estou agora (troca o local salvo)"
                : "Ligar a lista ao local onde estou agora");
        if (list.hasPlace()) opts.add("Desligar a lista do local");
        opts.add("Excluir lista");
        new AlertDialog.Builder(this)
                .setTitle("Opções da lista " + list.name)
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    String o = opts.get(w);
                    if (o.startsWith("Renomear")) {
                        Ui.askText(this, "Renomear lista", null, list.name, "Nome da lista", false, name -> {
                            if (name.isEmpty()) return;
                            list.name = name;
                            Store.get(this).saveLists();
                            title.setText(name);
                            setTitle(name);
                            Ui.say(this, "Lista renomeada");
                        });
                    } else if (o.startsWith("Ligar")) {
                        if (Loc.granted(this)) saveLocation(); else Loc.request(this);
                    } else if (o.startsWith("Desligar")) {
                        list.hasLoc = false;
                        list.cells.clear();
                        Store.get(this).saveLists();
                        Ui.say(this, "Local removido");
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Excluir a lista " + list.name + "?")
                                .setMessage("As linhas saem da lista. Os horários salvos continuam no celular.")
                                .setPositiveButton("Excluir", (d2, w2) -> {
                                    Store.get(this).lists().remove(list);
                                    Store.get(this).saveLists();
                                    Ui.say(this, "Lista excluída");
                                    finish();
                                })
                                .setNegativeButton("Cancelar", null)
                                .show();
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    /**
     * Grava o local da lista: as células da operadora na hora (funciona mesmo sem sinal de GPS)
     * e, se o GPS ou a rede responderem, também a posição.
     */
    private void saveLocation() {
        if (!Cells.locationOn(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Localização desligada")
                    .setMessage("A localização do celular está desligada. Com ela desligada, o Android não deixa nenhum app "
                            + "ler o GPS nem as células da operadora. Ligue a localização e tente de novo.")
                    .setPositiveButton("Abrir configuração", (d, w) -> {
                        try { startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                        catch (Exception e) { Ui.say(this, "Não consegui abrir a configuração"); }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        List<String> now = Cells.current(this);
        if (!now.isEmpty()) {
            list.cells.clear();
            list.cells.addAll(now);
            list.hasLoc = false;
            Store.get(this).saveLists();
        }
        final int nCells = now.size();
        status.setText(nCells > 0 ? "Células da operadora salvas (" + nCells + "). Buscando também a posição GPS…"
                : "Buscando sua localização…");
        Loc.current(this, 60 * 1000, loc -> {
            if (isFinishing()) return;
            if (loc != null) {
                list.hasLoc = true;
                list.lat = loc.getLatitude();
                list.lon = loc.getLongitude();
                Store.get(this).saveLists();
            }
            String cellsTxt = nCells == 0 ? "" : nCells == 1 ? "1 célula da operadora" : nCells + " células da operadora";
            if (loc != null && nCells > 0) status.setText("Local salvo: posição GPS e " + cellsTxt + ". Quando você estiver aqui, esta lista aparece primeiro.");
            else if (loc != null) status.setText("Local salvo: posição GPS. As células da operadora não puderam ser lidas neste celular.");
            else if (nCells > 0) status.setText("Local salvo com " + cellsTxt + ". O GPS não respondeu, mas as células já bastam para reconhecer este lugar.");
            else status.setText("Não consegui sua localização nem as células da operadora. Verifique se a localização está ligada e tente de novo.");
        });
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != Loc.REQUEST) return;
        if (Loc.granted(this)) saveLocation();
        else status.setText("Sem permissão de localização, a lista não foi ligada a um local.");
    }
}
