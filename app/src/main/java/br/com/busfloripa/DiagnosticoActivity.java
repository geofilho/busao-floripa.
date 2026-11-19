package br.com.busfloripa;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Mostra o resultado da última atualização, item por item, e permite copiar o relatório. */
public class DiagnosticoActivity extends Activity {

    private TextView summary;
    private Button runBtn;
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_diagnostico);
        setTitle("Diagnóstico");
        Ui.heading(findViewById(R.id.title));
        summary = findViewById(R.id.summary);
        runBtn = findViewById(R.id.run);
        ListView lv = findViewById(R.id.items);
        adapter = Ui.adapter(this, rows);
        lv.setAdapter(adapter);

        runBtn.setOnClickListener(v -> {
            runBtn.setEnabled(false);
            summary.setText("Atualizando e verificando…");
            Updater.run(this, r -> {
                if (isFinishing()) return;
                runBtn.setEnabled(true);
                show(r);
            });
        });
        findViewById(R.id.copy).setOnClickListener(v -> {
            Updater.Report r = Updater.last(this);
            if (r == null) { Ui.say(this, "Ainda não há relatório"); return; }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Diagnóstico", r.toText()));
            Ui.say(this, "Relatório copiado. Cole numa mensagem para enviar.");
        });

        if (Updater.isRunning()) {
            runBtn.setEnabled(false);
            summary.setText("Atualização em andamento…");
            Updater.run(this, r -> { if (!isFinishing()) { runBtn.setEnabled(true); show(r); } });
        } else show(Updater.last(this));
    }

    private void show(Updater.Report r) {
        rows.clear();
        if (r == null) {
            summary.setText("Ainda não houve atualização. Toque em Atualizar e verificar agora.");
        } else {
            summary.setText("Última atualização: " + r.when + ". " + r.summary());
            rows.addAll(r.lines);
        }
        adapter.notifyDataSetChanged();
    }
}
