package br.com.busfloripa;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ArrayAdapter;

import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Lista de textos em que cada item tem ações do TalkBack (deslizar para cima ou para baixo
 * até "Ações" e tocar duas vezes). As ações são as mesmas da janela que abre ao tocar no item.
 */
public final class ActionAdapter extends ArrayAdapter<String> {

    private static final int[] IDS = {R.id.action_1, R.id.action_2, R.id.action_3, R.id.action_4,
            R.id.action_5, R.id.action_6, R.id.action_7, R.id.action_8};

    private final IntFunction<List<Ui.Opt>> actionsFor;

    public ActionAdapter(Context c, List<String> rows, IntFunction<List<Ui.Opt>> actionsFor) {
        super(c, R.layout.row, rows);
        this.actionsFor = actionsFor;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            private List<Ui.Opt> opts = Collections.emptyList();

            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                List<Ui.Opt> o = null;
                try { o = actionsFor.apply(position); } catch (Exception ignored) { }
                opts = o == null ? Collections.emptyList() : o;
                for (int i = 0; i < opts.size() && i < IDS.length; i++)
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(IDS[i], opts.get(i).label));
            }

            @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                for (int i = 0; i < opts.size() && i < IDS.length; i++) {
                    if (IDS[i] == action) { opts.get(i).run.run(); return true; }
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
        return v;
    }
}
