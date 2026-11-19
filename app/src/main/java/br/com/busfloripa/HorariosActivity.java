package br.com.busfloripa;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Horários de uma saída da linha, por tipo de dia. */
public class HorariosActivity extends Activity {

    private String code, name, url;
    private int depIdx;
    private int day;
    private boolean showAll;
    private FenixParser.Timetable tt;
    private Repo repo;

    private TextView title, subtitle, info;
    private Button earlier, other;
    private final Button[] dayBtns = new Button[3];
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_horarios);
        repo = new Repo(this);
        code = getIntent().getStringExtra("code");
        name = getIntent().getStringExtra("name");
        url = getIntent().getStringExtra("url");
        depIdx = getIntent().getIntExtra("dep", 0);
        day = Schedule.dayType(LocalDate.now());

        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        info = findViewById(R.id.info);
        earlier = findViewById(R.id.earlier);
        other = findViewById(R.id.other);
        dayBtns[0] = findViewById(R.id.dayUteis);
        dayBtns[1] = findViewById(R.id.daySabado);
        dayBtns[2] = findViewById(R.id.dayDomingo);
        Ui.heading(title);
        title.setText(code + " " + name);
        setTitle(code + " " + name);

        ListView lv = findViewById(R.id.times);
        adapter = Ui.adapter(this, rows);
        lv.setAdapter(adapter);

        for (int i = 0; i < 3; i++) {
            final int d = i;
            dayBtns[i].setOnClickListener(v -> { day = d; showAll = false; render(); });
        }
        earlier.setOnClickListener(v -> { showAll = true; render(); });
        other.setOnClickListener(v -> {
            if (tt == null || tt.departures.size() < 2) return;
            depIdx = (depIdx + 1) % tt.departures.size();
            showAll = false;
            render();
            Ui.say(this, Schedule.sentidoLabel(tt, depIdx));
        });

        info.setText("Carregando horários…");
        repo.timetable(code, name, url, (t, err) -> {
            if (isFinishing()) return;
            if (t == null) { info.setText("Não foi possível abrir os horários. " + err + "."); return; }
            tt = t;
            if (depIdx >= tt.departures.size()) depIdx = 0;
            render();
        });
    }

    private void render() {
        if (tt == null) return;
        FenixParser.Departure dep = tt.departures.get(depIdx);
        subtitle.setText(Schedule.sentidoLabel(tt, depIdx));
        other.setVisibility(tt.departures.size() > 1 ? View.VISIBLE : View.GONE);

        boolean isToday = day == Schedule.dayType(LocalDate.now());
        for (int i = 0; i < 3; i++) {
            boolean sel = i == day;
            String label = FenixParser.DAY_NAMES[i] + (i == Schedule.dayType(LocalDate.now()) ? " (hoje)" : "");
            dayBtns[i].setText(label);
            dayBtns[i].setContentDescription(label + (sel ? ", selecionado" : ""));
            dayBtns[i].setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            dayBtns[i].setBackgroundTintList(sel ? ColorStateList.valueOf(getColor(R.color.selected)) : null);
            dayBtns[i].setSelected(sel);
        }

        LocalDateTime now = LocalDateTime.now();
        int nowMin = now.getHour() * 60 + now.getMinute();
        List<FenixParser.Trip> trips = dep.days.get(day);
        rows.clear();
        int hidden = 0;
        boolean first = true;
        for (FenixParser.Trip t : trips) {
            if (isToday && !showAll && t.min < nowMin) { hidden++; continue; }
            StringBuilder sb = new StringBuilder();
            if (isToday && first && t.min >= nowMin) {
                sb.append("Próximo: ").append(Schedule.clock(t.min)).append(", ").append(Schedule.inMinutes(t.min - nowMin));
                first = false;
            } else sb.append(Schedule.clock(t.min));
            if (t.min >= 1440) sb.append(", madrugada");
            String f = Schedule.flagsText(t.flags);
            if (!f.isEmpty()) sb.append(", ").append(f);
            rows.add(sb.toString());
        }
        if (rows.isEmpty()) rows.add(trips.isEmpty() ? "Sem horários nesta tabela." : "Não há mais saídas hoje.");
        adapter.notifyDataSetChanged();

        earlier.setVisibility(hidden > 0 ? View.VISIBLE : View.GONE);
        earlier.setText("Mostrar " + (hidden == 1 ? "1 horário anterior" : hidden + " horários anteriores"));
        String upd = tt.updated.isEmpty() ? "" : " Tabela do Fênix atualizada em " + tt.updated + ".";
        info.setText(trips.size() + " saídas em " + FenixParser.DAY_NAMES[day].toLowerCase() + "." + upd);
    }
}
