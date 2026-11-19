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
- **GPS:** em Opções da lista, ligue a lista a um local. Quando você estiver
  a até 500 m dali, ela aparece primeiro na tela inicial.
- **Link do Google Maps:** em Opções da lista, cole o link compartilhado do ponto
  de ônibus. O app tira a localização do link (não precisa estar no ponto) e
  ganha a opção "Abrir o ponto no Google Maps", onde aparecem as próximas passagens.
- **Offline:** linhas e horários ficam salvos no celular e funcionam sem internet.
- **Atualização diária:** na primeira vez que o app é aberto no dia, baixa a lista de
  linhas e os horários de todas as linhas salvas. Sem internet, tenta de novo na
  próxima abertura (no mínimo 30 minutos depois). Dia sem abrir o app, sem atualização.
- **Aviso e diagnóstico:** se a atualização tiver problema (site fora do ar, página
  mudou, horários que não fazem sentido, saída de uma lista que sumiu), o app avisa
  e mantém os horários antigos. Detalhes em Mais opções, Diagnóstico, com botão
  para copiar o relatório.
- **Backup:** Mais opções, Fazer backup salva num arquivo .json todas as listas, com
  linhas, minutos, local GPS e link do Google Maps. Restaurar backup traz de volta
  (substituindo ou juntando com as listas atuais).
- **Ações do TalkBack:** nas linhas da busca e das listas, as mesmas opções da janela.
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
