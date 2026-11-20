package br.com.busfloripa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Opções de uma lista, num menu só: vincular à posição, desvincular da posição, renomear e excluir. Usadas pelo botão Opções da lista e pelas ações do TalkBack na tela inicial.
 */
public final class ListOptions {

    private ListOptions() { }

    /** O que a tela faz depois de cada opção. */
    public interface Host {
        /** Mostra um aviso na tela (lido pelo TalkBack). */
        void status(String text);
        /** Algo mudou na lista (nome, local): atualizar a tela. */
        void changed();
        /** A lista foi excluída. */
        void deleted();
    }

    /** Lista esperando a permissão de localização para gravar o local. */
    private static String pendingId;

    public static final String REALTIME = "Verificar horário em tempo real no Google Maps";

    /** Ações do TalkBack em cima da lista na tela inicial. */
    public static List<Ui.Opt> actions(Activity a, Store.UserList list, Host host) {
        List<Ui.Opt> out = new ArrayList<>();
        out.add(new Ui.Opt(REALTIME, () -> chooseStop(a, list, host)));
        out.add(new Ui.Opt("Opções da lista", () -> show(a, list, host)));
        return out;
    }

    // ---------- pontos do Google Maps ----------

    /** Menu com os pontos salvos (pelo nome, na ordem em que foram adicionados); abre o escolhido no Maps. */
    public static void chooseStop(Activity a, Store.UserList list, Host host) {
        if (list.stops.isEmpty()) {
            new AlertDialog.Builder(a)
                    .setTitle(REALTIME)
                    .setMessage("A lista " + list.name + " ainda não tem pontos do Google Maps. Adicione o link do ponto "
                            + "onde você pega o ônibus.")
                    .setPositiveButton("Adicionar ponto", (d, w) -> addStop(a, list, host))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        // Lista própria (em vez de setItems) para ter as ações do TalkBack Mover para cima/baixo.
        List<String> names = new ArrayList<>();
        for (Store.Stop s : list.stops) names.add(s.name);
        android.widget.ListView lv = new android.widget.ListView(a);
        final AlertDialog[] dlg = {null};
        final ActionAdapter[] ad = {null};
        ad[0] = new ActionAdapter(a, names, pos -> {
            List<Ui.Opt> o = new ArrayList<>();
            if (pos > 0) o.add(new Ui.Opt("Mover para cima", () -> moveStop(a, list, pos, pos - 1, names, ad[0], lv)));
            if (pos < list.stops.size() - 1) o.add(new Ui.Opt("Mover para baixo", () -> moveStop(a, list, pos, pos + 1, names, ad[0], lv)));
            return o;
        });
        lv.setAdapter(ad[0]);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            if (dlg[0] != null) dlg[0].dismiss();
            openStop(a, list.stops.get(pos));
        });
        dlg[0] = new AlertDialog.Builder(a)
                .setTitle("Horário em tempo real: escolha o ponto")
                .setView(lv)
                .setNegativeButton("Fechar", null)
                .show();
    }

    /** Troca a ordem de um ponto, salva e leva o foco do TalkBack junto com ele. */
    private static void moveStop(Activity a, Store.UserList list, int from, int to, List<String> names,
                                 ActionAdapter ad, android.widget.ListView lv) {
        if (to < 0 || to >= list.stops.size()) return;
        Store.Stop s = list.stops.remove(from);
        list.stops.add(to, s);
        names.remove(from);
        names.add(to, s.name);
        Store.get(a).saveLists();
        ad.notifyDataSetChanged();
        Ui.say(a, s.name + " movido para a posição " + (to + 1) + " de " + list.stops.size());
        lv.post(() -> {
            android.view.View v = lv.getChildAt(to - lv.getFirstVisiblePosition());
            if (v != null) v.performAccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        });
    }

    /** Abre o ponto no app do Google Maps (ou no navegador, se não tiver o app). */
    public static void openStop(Activity a, Store.Stop s) {
        android.net.Uri uri = android.net.Uri.parse(s.url);
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"));
            return;
        } catch (Exception ignored) { }
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception e) { Ui.say(a, "Não encontrei um app para abrir o link"); }
    }

    /** Nome e link do ponto, num quadro só. */
    public static void addStop(Activity a, Store.UserList list, Host host) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = Ui.dp(a, 20);
        box.setPadding(p, Ui.dp(a, 8), p, 0);
        android.widget.EditText name = new android.widget.EditText(a);
        name.setHint("Nome do ponto, por exemplo Ponto da padaria");
        name.setContentDescription("Nome do ponto");
        name.setTextSize(20);
        name.setMinHeight(Ui.dp(a, 56));
        name.setSingleLine(true);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        android.widget.EditText link = new android.widget.EditText(a);
        link.setHint("Link do ponto no Google Maps");
        link.setContentDescription("Link do ponto no Google Maps");
        link.setTextSize(20);
        link.setMinHeight(Ui.dp(a, 56));
        link.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        link.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        box.addView(name);
        box.addView(link);
        new AlertDialog.Builder(a)
                .setTitle("Adicionar ponto do Google Maps")
                .setMessage("No Google Maps, abra o ponto de ônibus, toque em Compartilhar e copie o link. "
                        + "Dê um nome ao ponto e cole o link aqui.")
                .setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    String url = MapsLink.firstUrl(link.getText().toString());
                    String n = name.getText().toString().trim();
                    if (url == null) { Ui.say(a, "Ponto não salvo: não encontrei um link"); return; }
                    if (n.isEmpty()) n = "Ponto " + (list.stops.size() + 1);
                    list.stops.add(new Store.Stop(n, url));
                    Store.get(a).saveLists();
                    Ui.say(a, "Ponto " + n + " adicionado à lista " + list.name);
                    host.changed();
                })
                .setNegativeButton("Cancelar", null)
                .show();
        name.requestFocus();
    }

    public static void removeStop(Activity a, Store.UserList list, Host host) {
        String[] names = new String[list.stops.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.stops.get(i).name;
        new AlertDialog.Builder(a)
                .setTitle("Remover qual ponto?")
                .setItems(names, (d, w) -> {
                    Store.Stop s = list.stops.remove(w);
                    Store.get(a).saveLists();
                    Ui.say(a, "Ponto " + s.name + " removido");
                    host.changed();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** As quatro opções da lista, num menu só. */
    static List<Ui.Opt> options(Activity a, Store.UserList list, Host host) {
        List<Ui.Opt> opts = new ArrayList<>();
        opts.add(new Ui.Opt(list.hasPlace() ? "Vincular a lista à posição onde estou agora, troca a posição salva"
                : "Vincular a lista à posição onde estou agora", () -> link(a, list, host)));
        opts.add(new Ui.Opt("Desvincular a lista da posição", () -> {
            if (!list.hasPlace()) { Ui.say(a, "A lista " + list.name + " não está vinculada a nenhuma posição"); return; }
            list.hasLoc = false;
            list.cells.clear();
            Store.get(a).saveLists();
            Ui.say(a, "Lista " + list.name + " desvinculada da posição");
            host.changed();
        }));
        opts.add(new Ui.Opt(REALTIME, () -> chooseStop(a, list, host)));
        opts.add(new Ui.Opt("Adicionar ponto do Google Maps", () -> addStop(a, list, host)));
        if (!list.stops.isEmpty()) opts.add(new Ui.Opt("Remover ponto do Google Maps", () -> removeStop(a, list, host)));
        opts.add(new Ui.Opt("Renomear lista", () ->
                Ui.askText(a, "Renomear lista", null, list.name, "Nome da lista", false, name -> {
                    if (name.isEmpty()) return;
                    list.name = name;
                    Store.get(a).saveLists();
                    Ui.say(a, "Lista renomeada");
                    host.changed();
                })));
        opts.add(new Ui.Opt("Excluir lista", () -> new AlertDialog.Builder(a)
                .setTitle("Excluir a lista " + list.name + "?")
                .setMessage("As linhas saem da lista. Os horários salvos continuam no celular.")
                .setPositiveButton("Excluir", (d2, w2) -> {
                    Store.get(a).lists().remove(list);
                    Store.get(a).saveLists();
                    Ui.say(a, "Lista excluída");
                    host.deleted();
                })
                .setNegativeButton("Cancelar", null)
                .show()));
        return opts;
    }

    /** Menu Opções da lista (botão na tela da lista e ação do TalkBack na tela inicial). */
    public static void show(Activity a, Store.UserList list, Host host) {
        List<Ui.Opt> opts = options(a, list, host);
        String[] labels = new String[opts.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = opts.get(i).label;
        new AlertDialog.Builder(a)
                .setTitle("Opções da lista " + list.name)
                .setItems(labels, (d, w) -> opts.get(w).run.run())
                .setNegativeButton("Fechar", null)
                .show();
    }

    /** Vincular à posição: pede a permissão se precisar e grava. */
    public static void link(Activity a, Store.UserList list, Host host) {
        if (Loc.granted(a)) { saveLocation(a, list, host); return; }
        pendingId = list.id;
        Loc.request(a);
    }

    /** Chamar no onRequestPermissionsResult da tela. */
    public static void onPermission(Activity a, int code, Host host) {
        if (code != Loc.REQUEST || pendingId == null) return;
        Store.UserList list = Store.get(a).list(pendingId);
        pendingId = null;
        if (list == null) return;
        if (Loc.granted(a)) saveLocation(a, list, host);
        else host.status("Sem permissão de localização, a lista não foi ligada a um local.");
    }

    /**
     * Grava o local da lista: as células da operadora na hora (funciona mesmo sem sinal de GPS)
     * e, se o GPS ou a rede responderem, também a posição.
     */
    static void saveLocation(Activity a, Store.UserList list, Host host) {
        if (!Cells.locationOn(a)) {
            new AlertDialog.Builder(a)
                    .setTitle("Localização desligada")
                    .setMessage("A localização do celular está desligada. Com ela desligada, o Android não deixa nenhum app "
                            + "ler o GPS nem as células da operadora. Ligue a localização e tente de novo.")
                    .setPositiveButton("Abrir configuração", (d, w) -> {
                        try { a.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                        catch (Exception e) { Ui.say(a, "Não consegui abrir a configuração"); }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        List<String> now = Cells.current(a);
        if (!now.isEmpty()) {
            list.cells.clear();
            list.cells.addAll(now);
            list.hasLoc = false;
            Store.get(a).saveLists();
        }
        final int nCells = now.size();
        host.status(nCells > 0 ? "Células da operadora salvas para a lista " + list.name + ". Buscando também a posição GPS…"
                : "Buscando sua localização…");
        Loc.current(a, 60 * 1000, loc -> {
            if (a.isFinishing() || a.isDestroyed()) return;
            if (loc != null) {
                list.hasLoc = true;
                list.lat = loc.getLatitude();
                list.lon = loc.getLongitude();
                Store.get(a).saveLists();
            }
            String cellsTxt = nCells == 0 ? "" : nCells == 1 ? "1 célula da operadora" : nCells + " células da operadora";
            String who = "Lista " + list.name + ": ";
            if (loc != null && nCells > 0) host.status(who + "local salvo com a posição GPS e " + cellsTxt + ". Quando você estiver aqui, ela aparece primeiro.");
            else if (loc != null) host.status(who + "local salvo com a posição GPS. As células da operadora não puderam ser lidas neste celular.");
            else if (nCells > 0) host.status(who + "local salvo com " + cellsTxt + ". O GPS não respondeu, mas as células já bastam para reconhecer este lugar.");
            else host.status("Não consegui sua localização nem as células da operadora. Verifique se a localização está ligada e tente de novo.");
            host.changed();
        });
    }
}
