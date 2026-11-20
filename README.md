# Busão Floripa G

App Android leve e acessível (TalkBack) para consultar horários de ônibus do
Consórcio Fênix (Florianópolis), inspirado no antigo BusDroid Floripa.

## Como funciona
- **Tela inicial:** suas listas no topo ("Saídas de casa", "TICEN para casa"…) e
  a busca de linhas embaixo (número ou nome, sem precisar de acento).
- **Tocar numa linha:** Sentido centro, Sentido bairro, Adicionar a uma lista.
- **Lista:** mostra direto, em ordem de chegada:
  `174, Saco Grande via João Paulo. Chega em 8 minutos, às 15:32. Depois às 15:52. Sentido centro.`
  Tocar numa linha da lista: Sentido centro, Sentido bairro, Alterar minutos, Remover da lista.
- **Minutos até o seu ponto:** ao adicionar, informe quantos minutos o ônibus leva
  da saída até onde você pega. O app soma e mostra a chegada no seu ponto.
- **Minutos até o ponto:** 0 = horário programado de saída. Para pegar em outro
  ponto, use "O ônibus chegou agora": o app mede o tempo real de viagem e usa a
  mediana das últimas 5 medições.
- **Local da lista:** em Opções da lista, "Vincular a lista à posição onde estou agora" grava as
  células da operadora (`Cells.java`, lidas na hora) e a posição GPS se houver. A lista sobe
  para o topo quando uma célula bate ou o GPS está a até 700 m. Exige a localização do
  aparelho ligada (regra do Android 9+).
- **Pontos:** botão na barra abre o Google Maps com a busca "pontos de ônibus".

- **Offline:** linhas e horários ficam salvos no celular e funcionam sem internet.
- **Atualização diária:** na primeira vez que o app é aberto no dia, baixa a lista de
  linhas e os horários de todas as linhas salvas. Sem internet, tenta de novo na
  próxima abertura (no mínimo 30 minutos depois). Dia sem abrir o app, sem atualização.
- **Aviso e diagnóstico:** se a atualização tiver problema (site fora do ar, página
  mudou, horários que não fazem sentido, saída de uma lista que sumiu), o app avisa
  e mantém os horários antigos. Detalhes em Mais opções, Dados, Diagnóstico, com botão
  para copiar o relatório.
- **Backup:** Mais opções, Dados, Backup, Fazer backup salva num arquivo .json todas as listas, com
  linhas, minutos, local GPS e link do Google Maps. Restaurar backup traz de volta
  (substituindo ou juntando com as listas atuais).
- **Ações do TalkBack:** nas linhas da busca e das listas, as mesmas opções da janela.
- **Intermunicipal:** botão na barra de cima alterna entre o Fênix e as linhas da Biguaçu (via as consultas internas do site tcbiguacu.com.br, `BiguacuParser.java`),
  Estrela (insulartc.com.br/estrela) e da Santa Terezinha (santaterezinha.com/horarios),
  lidas por `GenericParser.java`, e da Jotur (www2.jotur.com.br/linhas, `JoturParser.java`). O leitor do Fênix não muda.
- **Atualização do app:** Mais opções, Dados, Atualizar aplicativo lê a última versão em
  `api.github.com/repos/geofilho/busao-floripa./releases/latest` (tag `build-N` =
  versionCode N), mostra as novidades (mensagem do commit), baixa o APK e abre o
  instalador (mesmo esquema do Gerenciador de TTS G). O repositório precisa ser público.
- **Feriados:** feriados nacionais, Sexta-feira Santa e 23/03 usam a tabela de
  domingo. Na lista, o botão "Tabela de hoje" troca a tabela manualmente.

## Técnica
- Java, só componentes nativos do Android, **nenhuma biblioteca externa**. Android 8 ou mais novo.
- Horários lidos de `consorciofenix.com.br/horarios` (`FenixParser.java`).
  Se o site mudar o layout, o ajuste fica só nesse arquivo; `tests/run.sh` testa o leitor.
- O APK é gerado pelo GitHub Actions a cada envio (aba Releases).
- `app/busfloripa.keystore.b64` é a chave de assinatura em texto (senha `busfloripa`);
  o Gradle recria o arquivo `.keystore` sozinho. Ela é
  fixa para que cada versão nova instale por cima sem apagar suas listas.
  Mantenha o repositório privado.

Horários: Consórcio Fênix. Podem mudar; em caso de dúvida, confira no site.
