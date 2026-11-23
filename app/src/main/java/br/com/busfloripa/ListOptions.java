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
        out.add(new Ui.Opt("Pontos no Google Maps", () -> stopsPage(a, list, host)));
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
        // Um ponto só: abre direto no Google Maps, sem menu.
        if (list.stops.size() == 1) { openStop(a, list.stops.get(0)); return; }
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
            o.add(new Ui.Opt("Editar", () -> {
                if (dlg[0] != null) dlg[0].dismiss();
                stopDialog(a, list, host, list.stops.get(pos), null);
            }));
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
        Ui.say(a, s.name + (to < from ? " movido para cima" : " movido para baixo"));
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

    public static void addStop(Activity a, Store.UserList list, Host host) { stopDialog(a, list, host, null, null); }

    /** Nome e link do ponto, num quadro só. Com existing, edita esse ponto; sem, adiciona um novo. */
    static final String ADD_HELP =
            "Se você estiver perto do ponto ou no próprio ponto que quer adicionar:\n\n"
            + "1. Na tela inicial do aplicativo, toque no botão Pontos, que o TalkBack lê como Localizar pontos de ônibus próximos no Google Maps. "
            + "Ele abre o Google Maps já com o ponto mais próximo localizado.\n\n"
            + "2. O Google Maps mostra o ponto num painel na metade de baixo da tela. No topo do painel fica o nome do ponto, por exemplo Ticen. "
            + "Abaixo vêm os botões Rotas, Detalhes e Salvar, depois os números das linhas que passam no ponto, como 100, 102, 104, "
            + "e depois a lista das próximas partidas em tempo real, por exemplo: 210 TICEN - TICAN Direto, Previsto, Plataforma B, 1 min.\n\n"
            + "Os números das linhas funcionam como abas de filtro. No começo, todas as linhas estão selecionadas e a lista mostra as partidas de todas elas. "
            + "Tocando no número de uma linha, por exemplo 210, a lista passa a mostrar só os horários dessa linha. "
            + "Deslize para a direita para passar pelos números das linhas; a fileira continua além da borda direita da tela.\n\n"
            + "3. O botão Compartilhar fica na mesma linha do nome do ponto, à direita dele. Depois dele, já na borda direita, fica o botão Fechar. "
            + "Com o TalkBack: toque no nome do ponto e deslize uma vez para a direita, o próximo item é Compartilhar. "
            + "Se ouvir Fechar, você passou: deslize uma vez para a esquerda. "
            + "Toque duas vezes em Compartilhar e escolha Copiar link.\n\n"
            + "4. Volte ao aplicativo, em Adicionar ponto, dê um nome ao ponto e cole o link no campo Link do ponto no Google Maps. Toque em Salvar.\n\n"
            + "Se você não estiver no ponto que quer adicionar:\n\n"
            + "No Google Maps, pesquise por ponto de ônibus e o nome do lugar, por exemplo: ponto de ônibus TICEN, o Terminal de Integração do Centro. "
            + "Abra o ponto e siga os mesmos passos acima para copiar o link e colar no aplicativo.";

    /** Ajuda do botão Ajuda para adicionar, com atalho para os pontos próximos no Google Maps. */
    static void addHelp(Activity a) {
        new AlertDialog.Builder(a)
                .setTitle("Ajuda para adicionar ponto")
                .setMessage(ADD_HELP)
                .setPositiveButton("Localizar pontos próximos", (d, w) -> openNearbyStops(a))
                .setNegativeButton("Fechar", null)
                .show();
    }

    /** Google Maps com a busca "pontos de ônibus" perto de onde você está. */
    static void openNearbyStops(Activity a) {
        android.net.Uri geo = android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode("pontos de ônibus"));
        android.content.Intent maps = new android.content.Intent(android.content.Intent.ACTION_VIEW, geo).setPackage("com.google.android.apps.maps");
        try { a.startActivity(maps); return; } catch (Exception ignored) { }
        try { a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, geo)); return; } catch (Exception ignored) { }
        try {
            a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(
                    "https://www.google.com/maps/search/?api=1&query=" + android.net.Uri.encode("pontos de ônibus"))));
        } catch (Exception e) {
            Ui.say(a, "Não encontrei o Google Maps neste celular");
        }
    }

    public static void stopDialog(Activity a, Store.UserList list, Host host, Store.Stop existing, Runnable after) {
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
        android.widget.Button help = new android.widget.Button(a);
        help.setText("Ajuda para adicionar");
        help.setTextSize(18);
        help.setMinHeight(Ui.dp(a, 56));
        help.setOnClickListener(v -> addHelp(a));
        box.addView(help);
        if (existing != null) { name.setText(existing.name); link.setText(existing.url); }
        new AlertDialog.Builder(a)
                .setTitle(existing == null ? "Adicionar ponto" : "Editar ponto " + existing.name)
                .setMessage(existing == null ? "No Google Maps, abra o ponto de ônibus, toque em Compartilhar e copie o link. "
                        + "Dê um nome ao ponto e cole o link aqui." : "Altere o nome, o link ou os dois.")
                .setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    String url = MapsLink.firstUrl(link.getText().toString());
                    String n = name.getText().toString().trim();
                    if (url == null) { Ui.say(a, "Ponto não salvo: não encontrei um link"); return; }
                    if (existing != null) {
                        if (!n.isEmpty()) existing.name = n;
                        existing.url = url;
                        Store.get(a).saveLists();
                        Ui.say(a, "Ponto " + existing.name + " alterado");
                        if (host != null) host.changed();
                        if (after != null) after.run();
                        return;
                    }
                    if (n.isEmpty()) n = "Ponto " + (list.stops.size() + 1);
                    list.stops.add(new Store.Stop(n, url));
                    Store.get(a).saveLists();
                    Ui.say(a, "Ponto " + n + " adicionado à lista " + list.name);
                    if (host != null) host.changed();
                    if (after != null) after.run();
                })
                .setNegativeButton("Cancelar", null)
                .show();
        name.requestFocus();
    }

    /** As quatro opções da lista, num menu só. */
    static List<Ui.Opt> options(Activity a, Store.UserList list, Host host) {
        List<Ui.Opt> opts = new ArrayList<>();
        opts.add(new Ui.Opt("Vincular a lista à posição", () -> positionMenu(a, list, host)));
        opts.add(new Ui.Opt(REALTIME, () -> chooseStop(a, list, host)));
        opts.add(new Ui.Opt("Pontos no Google Maps", () -> stopsPage(a, list, host)));
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

    /** Vincular à posição onde estou agora, ou desvincular. */
    static void positionMenu(Activity a, Store.UserList list, Host host) {
        String[] items = {
                list.hasPlace() ? "Vincular à posição onde estou agora, troca a posição salva" : "Vincular à posição onde estou agora",
                "Desvincular da posição"};
        new AlertDialog.Builder(a)
                .setTitle("Posição da lista " + list.name + (list.hasPlace() ? ", vinculada" : ", sem posição"))
                .setItems(items, (d, w) -> {
                    if (w == 0) { link(a, list, host); return; }
                    if (!list.hasPlace()) { Ui.say(a, "A lista " + list.name + " não está vinculada a nenhuma posição"); return; }
                    list.hasLoc = false;
                    list.cells.clear();
                    Store.get(a).saveLists();
                    Ui.say(a, "Lista " + list.name + " desvinculada da posição");
                    host.changed();
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    /**
     * Página Pontos no Google Maps: "Adicionar ponto" no topo e os pontos embaixo, só com o nome.
     * Itens simples (sem lista numerada), para o TalkBack não anunciar "1 de 4".
     * Em cima de cada ponto, ações do TalkBack: Editar, Remover, Mover para cima, Mover para baixo.
     * Tocar num ponto abre um menu com as mesmas opções e Abrir no Google Maps.
     */
    static void stopsPage(Activity a, Store.UserList list, Host host) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        scroll.addView(box);
        final Runnable[] fill = {null};
        fill[0] = () -> {
            box.removeAllViews();
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            box.addView(row(a, "Adicionar ponto", v -> stopDialog(a, list, host, null, fill[0])), lp);
            for (int k = 0; k < list.stops.size(); k++) {
                final int i = k;
                android.widget.TextView t = row(a, list.stops.get(i).name, v -> {
                    List<Ui.Opt> o = new ArrayList<>();
                    o.add(new Ui.Opt("Abrir no Google Maps", () -> openStop(a, list.stops.get(i))));
                    o.addAll(stopActions(a, list, i, fill[0], box));
                    String[] labels = new String[o.size()];
                    for (int n = 0; n < labels.length; n++) labels[n] = o.get(n).label;
                    new AlertDialog.Builder(a)
                            .setTitle(list.stops.get(i).name)
                            .setItems(labels, (d, w) -> o.get(w).run.run())
                            .setNegativeButton("Fechar", null)
                            .show();
                });
                ActionAdapter.attach(t, () -> stopActions(a, list, i, fill[0], box));
                box.addView(t, new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            host.changed();
        };
        fill[0].run();
        new AlertDialog.Builder(a)
                .setTitle("Pontos no Google Maps da lista " + list.name)
                .setView(scroll)
                .setNegativeButton("Fechar", null)
                .show();
    }

    /** Um item da página de pontos: texto grande, tocável. */
    private static android.widget.TextView row(Activity a, String text, android.view.View.OnClickListener click) {
        android.widget.TextView t = (android.widget.TextView) a.getLayoutInflater().inflate(R.layout.row, null, false);
        t.setText(text);
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(click);
        return t;
    }

    /** Ações de um ponto na página Pontos no Google Maps. */
    private static List<Ui.Opt> stopActions(Activity a, Store.UserList list, int i, Runnable refresh, android.widget.LinearLayout box) {
        List<Ui.Opt> o = new ArrayList<>();
        if (i < 0 || i >= list.stops.size()) return o;
        o.add(new Ui.Opt("Editar", () -> stopDialog(a, list, null, list.stops.get(i), refresh)));
        o.add(new Ui.Opt("Remover", () -> new AlertDialog.Builder(a)
                .setTitle("Remover o ponto " + list.stops.get(i).name + "?")
                .setPositiveButton("Remover", (d, w) -> {
                    Store.Stop s = list.stops.remove(i);
                    Store.get(a).saveLists();
                    Ui.say(a, "Ponto " + s.name + " removido");
                    refresh.run();
                })
                .setNegativeButton("Cancelar", null)
                .show()));
        if (i > 0) o.add(new Ui.Opt("Mover para cima", () -> move(a, list, i, i - 1, refresh, box)));
        if (i < list.stops.size() - 1) o.add(new Ui.Opt("Mover para baixo", () -> move(a, list, i, i + 1, refresh, box)));
        return o;
    }

    /** Troca a ordem e leva o foco do TalkBack junto (o item 0 da página é "Adicionar ponto"). */
    private static void move(Activity a, Store.UserList list, int from, int to, Runnable refresh, android.widget.LinearLayout box) {
        Store.Stop s = list.stops.remove(from);
        list.stops.add(to, s);
        Store.get(a).saveLists();
        refresh.run();
        Ui.say(a, s.name + (to < from ? " movido para cima" : " movido para baixo"));
        box.post(() -> {
            android.view.View v = box.getChildAt(to + 1);
            if (v != null) v.performAccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        });
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
