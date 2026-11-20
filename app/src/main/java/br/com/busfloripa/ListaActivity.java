package br.com.busfloripa;

import android.app.Activity;
import android.app.AlertDialog;
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
        findViewById(R.id.options).setOnClickListener(v -> ListOptions.show(this, list, host));
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

    /** O que acontece na tela depois das opções da lista. */
    private final ListOptions.Host host = new ListOptions.Host() {
        @Override public void status(String text) { status.setText(text); }
        @Override public void changed() {
            title.setText(list.name);
            setTitle(list.name);
            rebuild(true);
        }
        @Override public void deleted() { finish(); }
    };

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        ListOptions.onPermission(this, code, host);
    }
}
