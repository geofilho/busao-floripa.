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
            "#Barra de cima",
            "O botão Pontos abre o Google Maps já pesquisando pontos de ônibus, mostrando os mais próximos de onde você está.",
            "À direita dele fica o botão Intermunicipal. Ele troca a lista de linhas para as empresas intermunicipais. Na tela intermunicipal, o mesmo botão se chama Fênix e volta para o Consórcio Fênix. A escolha fica memorizada: se você sair do aplicativo na tela intermunicipal, ele abre de novo nela, e o mesmo vale para a tela Fênix.",
            "Em Mais opções ficam três opções: Atualizar linhas e horários, Como usar e Dados.",
            "Atualizar linhas e horários baixa na hora, dos sites das empresas, a lista de linhas e os horários das linhas que você usa. O app já faz isso sozinho uma vez por dia; use quando quiser os horários mais novos agora.",
            "Dados abre Fazer backup, Restaurar backup, Diagnóstico e Atualizar aplicativo.",

            "#Tela inicial",
            "No topo ficam suas listas. Embaixo, a busca de linhas por número ou nome, sem precisar de acento.",
            "Quando há texto na busca, aparece à direita do campo o botão Limpar, que apaga o que foi digitado e mostra todas as linhas de novo. Com uma busca feita, o gesto de voltar também limpa a busca.",

            "#Transporte intermunicipal",
            "Empresas: Biguaçu, Estrela e Santa Terezinha. Na Biguaçu entram também as linhas executivas.",
            "Funciona igual ao Fênix: busque a linha por número, nome ou empresa, toque nela, veja as saídas ou adicione a uma lista. Uma lista pode ter linhas do Fênix e intermunicipais juntas.",
            "As observações dos horários, como via Univali, via Ponte Pedro Ivo Campos ou adaptado, vêm do site da empresa e aparecem por extenso quando o site informa.",
            "As linhas intermunicipais não têm itinerário no site, então o botão Itinerário avisa isso.",

            "#Criar uma lista",
            "1. Toque em Criar nova lista e dê um nome, por exemplo Saídas de casa.",
            "2. Busque uma linha, toque nela e escolha Adicionar a uma lista.",
            "3. Escolha a saída que você pega e a lista.",
            "4. Informe quantos minutos o ônibus leva da saída até o seu ponto. Se você pega na própria saída, deixe 0.",
            "Digite só o número de minutos, sem vírgula: 1 para um minuto, 30 para meia hora, 60 para uma hora, 75 para uma hora e quinze. O máximo é 300. Campo vazio vale 0.",
            "Com 0 minutos, a lista mostra o horário programado de saída. Use assim para as linhas que você pega no terminal ou no ponto de saída.",

            "#Minutos certos até o seu ponto",
            "Se você pega o ônibus em outro ponto, deixe 0 ao adicionar. Quando o ônibus chegar no seu ponto, use O ônibus chegou agora naquela linha da lista.",
            "O app vê a que horas ele saiu, calcula o tempo real de viagem e passa a mostrar a chegada no seu ponto. A cada vez que você marca, ele usa a média das últimas medições.",
            "Se ele não tiver certeza de qual ônibus chegou, pergunta, mostrando os horários de saída.",
            "Para voltar ao horário programado, use Alterar minutos e coloque 0.",

            "#Dentro da lista",
            "Cada linha já mostra quando o ônibus chega no seu ponto, da mais próxima para a mais distante.",
            "Toque numa linha para ver os horários de cada saída, marcar que o ônibus chegou, alterar os minutos ou remover da lista.",
            "O botão Tabela de hoje troca entre dias úteis, sábado e domingo. Use em feriados que o app não conhece.",
            "O botão Opções da lista tem: Vincular a lista à posição, que abre Vincular à posição onde estou agora e Desvincular da posição; Verificar horário em tempo real no Google Maps; Pontos no Google Maps; Renomear lista; e Excluir lista.",
            "Quando você estiver a até 700 metros do local ligado à lista, ela aparece primeiro na tela inicial.",
            "Vincular à posição grava as células da rede da operadora, as antenas que o celular está vendo, e também a posição GPS, se o GPS responder. As células são lidas na hora, mesmo dentro de casa ou sem sinal de GPS.",
            "Para isso, a localização do celular precisa estar ligada: com ela desligada, o Android não deixa nenhum app ler o GPS nem as células. Se estiver desligada, o app avisa e abre a configuração.",
            "Com o tempo, sempre que o GPS confirmar que você está no local, o app aprende as outras antenas daquele lugar, e a lista passa a ser reconhecida com mais facilidade.",

            "#Itinerário",
            "Na tela de horários de uma linha, o botão Itinerário mostra, em ordem, as ruas e lugares por onde ela passa, na ida e na volta. O site do Fênix não informa os pontos de parada, só as ruas.",
            "A lista começa na saída que você escolheu: no terminal, ou no ponto final do bairro. Se o app não conseguir achar a saída na lista, mostra na ordem do site.",

            "#Horário em tempo real no Google Maps",
            "O Google Maps mostra as partidas em tempo real dos ônibus em cada ponto. O Busão Floripa G abre o Maps direto no ponto certo.",
            "Para isso, salve os pontos de cada lista: em Opções da lista, Pontos no Google Maps, toque em Adicionar ponto. Dê um nome ao ponto e cole o link dele. Para pegar o link, no Google Maps abra o ponto de ônibus e toque em Compartilhar.",
            "Pode adicionar quantos pontos quiser em cada lista, por exemplo quando linhas diferentes passam em pontos diferentes.",
            "Para ver o tempo real, use Verificar horário em tempo real no Google Maps: no botão Tempo real, no canto de cima da tela da lista, ou nas ações do TalkBack. Abre um menu com os pontos pelo nome, na ordem em que foram adicionados. Escolha um e o Google Maps abre nele.",
            "Para mudar a ordem dos pontos, nesse menu use as ações do TalkBack em cima do ponto: Mover para cima ou Mover para baixo. A nova ordem fica salva. A ação Editar, ali mesmo, muda o nome ou o link do ponto.",
            "A página Pontos no Google Maps mostra os pontos salvos logo abaixo de Adicionar ponto. Em cima de cada ponto, as ações do TalkBack são Editar, Remover, Mover para cima e Mover para baixo. Tocando no ponto, abre um menu com Abrir no Google Maps e essas mesmas opções.",

            "#Ações do TalkBack",
            "Em cima de uma linha, deslize para cima ou para baixo até Ações e toque duas vezes na ação desejada.",
            "Na busca de linhas: uma ação para cada saída e Adicionar a uma lista.",
            "Na tela inicial, em cima de cada lista: Verificar horário em tempo real no Google Maps, e Opções da lista, que abre o mesmo menu do botão Opções da lista, sem precisar abrir a lista.",
            "Dentro das suas listas, em cima de cada linha: uma ação para cada saída, O ônibus chegou agora, Verificar horário em tempo real no Google Maps, Alterar minutos até o meu ponto e Remover da lista.",

            "#Atualização",
            "Na primeira vez que você abre o app no dia, os horários são atualizados.",
            "Sem internet, o app usa os horários salvos no celular e tenta atualizar de novo depois.",
            "Para atualizar na hora, use Mais opções, Atualizar linhas e horários.",

            "#Quando der problema",
            "Se a atualização falhar, aparece um aviso e os horários antigos continuam salvos.",
            "Em Mais opções, Dados, Diagnóstico, toque em Copiar relatório e envie o texto para quem cuida do app.",

            "#Backup",
            "Mais opções, Dados, Fazer backup: salva num arquivo suas listas, com as linhas, os minutos e as medições, os locais das listas, com posição GPS e células da operadora, e os pontos do Google Maps. Você escolhe onde salvar, por exemplo no Google Drive.",
            "Mais opções, Dados, Restaurar backup: abre esse arquivo e traz as listas de volta, por exemplo num celular novo. Você escolhe se substitui suas listas ou junta com elas.",

            "#Sobre os horários",
            "Os horários vêm dos sites do Consórcio Fênix, da Biguaçu, da Estrela e da Santa Terezinha e podem mudar. Em caso de dúvida, confira no site da empresa.",

            "#Atualização do aplicativo",
            "Em Mais opções, Dados, Atualizar aplicativo, o app procura no GitHub se existe uma versão nova.",
            "Se existir, ele mostra a versão instalada, a versão nova e as novidades, e pergunta se você quer baixar. Toque em OK, baixar.",
            "Depois de baixar, o app confere se o arquivo é mesmo do Busão Floripa G e abre o instalador do Android. Toque em Atualizar ou Instalar.",
            "Na primeira atualização, o Android pede permissão para o Busão Floripa G instalar aplicativos. O app explica e abre a tela certa: ative Permitir desta fonte e volte. A instalação continua sozinha.",
            "Suas listas, minutos, medições e locais continuam salvos depois de atualizar.",
            "Se aparecer que não foi possível verificar, confira a internet. Se disser que não encontrou versões, o repositório do app no GitHub precisa estar público.",

            "#Como instalar o aplicativo pela primeira vez",
            "1. No navegador do celular, abra a página de versões do app no GitHub, na seção Releases do repositório busao-floripa, e toque em BusaoFloripa.apk para baixar.",
            "2. Se o navegador avisar que o arquivo pode ser perigoso, toque em Baixar mesmo assim. O aviso aparece para qualquer app de fora da Play Store.",
            "3. Abra o arquivo baixado, pela notificação de download ou pelo app Arquivos.",
            "4. Se o Android disser que, por segurança, seu celular não pode instalar apps desta fonte, toque em Configurações, ative Permitir desta fonte e volte.",
            "5. Toque em Instalar. Depois, em Abrir.",
            "Para atualizar depois, use Mais opções, Dados, Atualizar aplicativo. Não precisa baixar pelo navegador de novo.",

            "#Permissões do Android e do Google Play",
            "Internet: usada para baixar linhas e horários e buscar atualizações. O Android libera sozinho, não pergunta.",
            "Localização: só é pedida quando você vincula uma lista à posição onde está. Escolha Durante o uso do app e, se perguntar, Localização exata. Ela é usada para a posição GPS e para ler as células da operadora; tudo fica só no celular e serve para colocar a lista certa no topo.",
            "Instalar aplicativos: só é pedida na primeira atualização pelo próprio app. Serve para abrir o instalador com a versão nova.",
            "Google Play Protect: como o app não vem da Play Store, o Play Protect pode avisar que o app não foi verificado ou oferecer verificar o app. Toque em Mais detalhes e depois em Instalar mesmo assim. Se perguntar se quer enviar o app para verificação, tanto faz a resposta.",
            "Verificação de desenvolvedor: desde 30 de setembro de 2026, no Brasil, o Android pode recusar apps de desenvolvedores não verificados. Se aparecer essa recusa, é preciso ativar uma vez o fluxo avançado.",
            "Fluxo avançado, passo 1: em Configurações, Sobre o telefone, toque 7 vezes em Número da versão para ativar as Opções do desenvolvedor.",
            "Passo 2: em Configurações, Sistema, Opções do desenvolvedor, ative a opção que permite apps de desenvolvedores não verificados. O nome pode mudar um pouco conforme a marca do celular.",
            "Passo 3: confirme que ninguém está pedindo para você fazer isso, reinicie o celular e espere 24 horas. Depois disso, a instalação funciona normalmente.",
            "Isso só é feito uma vez por celular. As atualizações seguintes do app não pedem de novo.",
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
