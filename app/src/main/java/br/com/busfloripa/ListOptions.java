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

    /** Ação do TalkBack em cima da lista na tela inicial: abre o mesmo menu do botão Opções da lista. */
    public static List<Ui.Opt> actions(Activity a, Store.UserList list, Host host) {
        List<Ui.Opt> out = new ArrayList<>();
        out.add(new Ui.Opt("Opções da lista", () -> show(a, list, host)));
        return out;
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
