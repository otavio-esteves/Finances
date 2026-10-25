# Validação de otimização e novos gráficos

Este relatório registra a rodada anterior à inclusão do OCR/senha/revisão de PDF. A evolução posterior está em [PDF_IMPORTACAO.md](PDF_IMPORTACAO.md).

Rodada de 25/10/2026. O objetivo foi reduzir consultas, alocações e trabalho na thread principal, mantendo paleta, tipografia, transparências, desfoque, espaçamentos e navegação existentes. Os novos controles de tipo de gráfico ficam nos cabeçalhos dos cards; rosca e barras continuam sendo as apresentações iniciais.

## Mudanças verificáveis

- **Gráficos:** Room retorna agregados por mês/categoria, em vez de materializar todas as transações do ano. A assinatura anual é compartilhada; trocar mês dentro do mesmo ano ou trocar categoria não abre uma nova consulta anual. Alterações no banco continuam atualizando a série. Não houve alteração de schema nem migração.
- **Histórico:** linhas intactas reaproveitam o mesmo `TransactionItem` e seus textos já formatados. Uma exclusão ou inserção não reformata todo o mês. O cache guarda apenas as linhas atuais e é descartado ao trocar o período. Retomar o mesmo mês depois da suspensão dos coletores mantém as linhas visíveis enquanto Room atualiza, preservando a rolagem. O período acompanha o resultado, evitando associar registros antigos ao mês recém-selecionado enquanto a consulta está pendente.
- **Navegação:** páginas adjacentes continuam pré-compostas para o gesto horizontal, mas recebem um lifecycle limitado à visibilidade. Os coletores param fora de vista, respeitando a tolerância de cinco segundos dos StateFlows. O estado salvo das telas é preservado ao visitar Categorias/Configurações.
- **Chat:** contexto e respostas determinísticas, conversões do histórico e sua preparação rodam fora da thread principal. Emissões Room iguais não reconvertem as mensagens. Retornar à página preserva a posição de leitura; novas mensagens continuam levando ao fim da conversa.
- **Novos gráficos:** Categorias alterna Rosca/Pizza; as séries anuais e por categoria alternam Barras/Linhas/Área. Os modos compartilham os mesmos valores e mantêm meses zerados na posição correta. Seleções são salvas; descrições acessíveis incluem os valores.
- **PDF:** datas abreviadas/meses por nome exigem período, ano ou referência mensal explícita no documento, inclusive para resolver dezembro/janeiro. Aceita até seis linhas por lançamento, sinais à direita, parênteses e marcadores C/D/Crédito/Débito. Uma coluna de saldo exige cabeçalho identificado e nunca vira lançamento. Datas e sinais ambíguos recusam o lote. O texto expandido tem limite de 4 Mi caracteres e 100 páginas; os recursos do PDFBox são inicializados uma vez.

## Testes automatizados

A suíte JVM verifica resultados numéricos e reutilização de assinaturas/linhas, além das regressões existentes. Os testes instrumentados verificam as consultas SQL contra os totais de domínio, PDFs efetivamente gerados, ciclos de vida, navegação por botão/gesto, escolha de gráfico, ano vazio e restauração das seleções.

Resultado JVM desta rodada: **185 testes passaram, sem falhas**. O build do APK e do APK de testes também passou. A suíte instrumentada completa terminou com **28 testes aprovados e dois testes de inferência real ignorados por ausência de bundle**. Lint terminou com **zero erros e 33 avisos**.

Na base sintética de 12 mil lançamentos, a consulta anual passou a retornar **72 grupos de mês/categoria**, em vez de materializar 12 mil registros. Esse número mede o volume retornado; não representa um percentual de melhoria de FPS.

Comandos de validação:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew testStandaloneDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew lintStandaloneDebug assembleStandaloneDebug assembleStandaloneDebugAndroidTest
```

Os testes de aparelho foram executados em emulador dedicado Android 15/API 35, x86_64, 1080 × 2400, densidade 420, com renderização por software. Nenhum teste instrumentado foi executado em aparelho pessoal.

## Comparação visual com histórico grande

`FinancesVisualCaptureTest` é opt-in e exige emulador. Ele prepara 12 mil transações sintéticas (mil por mês em 2026) e 500 mensagens, sem dados financeiros reais. As capturas cobrem Início, Histórico, Chat e Gráficos, utilizando o APK anterior à rodada como referência. Somente a Activity de teste tem `FLAG_SECURE` removido para permitir capturas; o código de produção mantém a proteção.

Exemplo para um emulador dedicado já preparado:

```bash
adb -s emulator-5554 shell am instrument -w \
  -e class br.com.otavioesteves.finances.ui.FinancesVisualCaptureTest \
  -e visualCapture true -e captureLabel current \
  br.com.otavioesteves.finances.test/androidx.test.runner.AndroidJUnitRunner
```

A comparação utilizou páginas estabilizadas, depois de um ciclo de navegação. Excluindo 100 pixels no topo e no rodapé para desconsiderar as barras do sistema, o resultado foi:

| Tela | Pixels diferentes |
|---|---:|
| Início | 0 |
| Histórico | 0 |
| Chat | 0 |
| Gráficos | 425 (setas dos novos seletores de tipo) |

O controle novo foi colocado sobre o cabeçalho, sem aumentar sua altura ou deslocar os gráficos existentes. A comparação cobre o tema/paleta das capturas; os testes de aparência também percorrem os modos e paletas disponíveis.

As capturas ficam em `Android/data/br.com.otavioesteves.finances/files/visual-validation` no emulador. Sem `visualCapture=true`, esse teste não insere dados. Ele também recusa execução fora de um emulador.

## Limites

Não há medição confiável de melhoria percentual de quadros ou latência no Galaxy S21 nesta rodada. Comparações de tempo no emulador com renderização por software não comprovam fluidez em aparelho real. Os ganhos demonstrados são a redução estrutural de resultados SQL e de formatação/recomposição de linhas, acompanhada por testes de comportamento.

O Chat continua consultando um mês por vez e o carregamento de mensagens anteriores continua em blocos de cem. O runtime LiteRT-LM real exige modelo e aparelho compatíveis; os testes sem bundle validam o fallback e os fluxos do aplicativo. PDFs digitalizados continuam sem OCR e colunas separadas de débito/crédito ou outras diagramações não identificadas são recusadas.
