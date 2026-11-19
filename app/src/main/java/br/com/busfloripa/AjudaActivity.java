package br.com.busfloripa;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Como usar. Cada parágrafo é um item separado e cada título é marcado como título,
 * para navegar pelo TalkBack por títulos ou parágrafo por parágrafo.
 */
public class AjudaActivity extends Activity {

    /** "#" no início = título. */
    private static final String[] TEXT = {
            "#Tela inicial",
            "No topo ficam suas listas. Embaixo, a busca de linhas por número ou nome, sem precisar de acento.",
            "Com uma busca feita, o gesto de voltar limpa a busca.",

            "#Criar uma lista",
            "1. Toque em Criar nova lista e dê um nome, por exemplo Saídas de casa.",
            "2. Busque uma linha, toque nela e escolha Adicionar a uma lista.",
            "3. Escolha a saída que você pega e a lista.",
            "4. Informe quantos minutos o ônibus leva da saída até o seu ponto. Se você pega na própria saída, deixe 0.",

            "#Dentro da lista",
            "Cada linha já mostra quando o ônibus chega no seu ponto, da mais próxima para a mais distante.",
            "Toque numa linha para ver os horários de cada saída, alterar os minutos ou remover da lista.",
            "O botão Tabela de hoje troca entre dias úteis, sábado e domingo. Use em feriados que o app não conhece.",
            "Em Opções da lista você renomeia, exclui e liga a lista a um local: onde você está agora ou o link do Google Maps do ponto. Perto desse local, a lista aparece primeiro na tela inicial.",

            "#Ações do TalkBack",
            "Em cima de uma linha, deslize para cima ou para baixo até Ações e toque duas vezes na ação desejada.",
            "Na busca de linhas: uma ação para cada saída e Adicionar a uma lista.",
            "Nas suas listas: uma ação para cada saída, Alterar minutos até o meu ponto e Remover da lista.",

            "#Atualização",
            "Na primeira vez que você abre o app no dia, os horários são atualizados.",
            "Sem internet, o app usa os horários salvos no celular e tenta atualizar de novo depois.",
            "Para atualizar na hora, use Mais opções, Atualizar agora.",

            "#Quando der problema",
            "Se a atualização falhar, aparece um aviso e os horários antigos continuam salvos.",
            "Em Mais opções, Diagnóstico, toque em Copiar relatório e envie o texto para quem cuida do app.",

            "#Backup",
            "Mais opções, Fazer backup: salva num arquivo suas listas, com as linhas, os minutos, os locais GPS e os links do Google Maps. Você escolhe onde salvar, por exemplo no Google Drive.",
            "Mais opções, Restaurar backup: abre esse arquivo e traz as listas de volta, por exemplo num celular novo. Você escolhe se substitui suas listas ou junta com elas.",

            "#Sobre os horários",
            "Os horários vêm do site do Consórcio Fênix e podem mudar. Em caso de dúvida, confira no site.",
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Como usar");
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        box.setPadding(p, p / 2, p, p);
        for (String t : TEXT) {
            TextView tv = new TextView(this);
            boolean h = t.startsWith("#");
            tv.setText(h ? t.substring(1) : t);
            tv.setTextSize(h ? 22 : 18);
            if (h) {
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
                Ui.heading(tv);
            } else {
                tv.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            }
            tv.setFocusable(true);
            box.addView(tv);
        }
        scroll.addView(box);
        setContentView(scroll);
    }
}
