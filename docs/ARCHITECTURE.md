# Arquitetura do Finances

## Produto e fluxo principal

O Finances recebe **faturas de cartão e extratos bancários**, transforma seus lançamentos em transações categorizadas e ajuda a pessoa a entender os gastos. A Início apresenta os dados organizados; Gráficos e Transações ficam à sua direita, seguidos do **Chat** para perguntas sobre os dados. Insights proativos e perguntas detalhadas por período são o próximo passo do produto.

```text
Arquivo → parser → lançamentos brutos → categorizador → revisão humana
                                                        ↓
                                              Room: transações confirmadas
                                                        ↓
                                     Início / Gráficos / Transações / Chat
```

**Estado atual:** CSV/OFX e PDFs com texto extraível em linhas de data, descrição e valor, revisão de categorias, dashboard e Chat com resumo de um mês selecionado pela pergunta. **Alvo:** ampliar PDF para formatos reais de instituições e imagens com OCR local, gerar insights a partir de números verificáveis e permitir ao Chat consultar intervalos e transações específicas. Início, Gráficos, Transações e Chat são as quatro páginas do `HorizontalPager`, acessíveis por gesto horizontal e por uma barra inferior compacta.

## Camadas e responsabilidades

| Camada | Código principal | Responsabilidade |
|---|---|---|
| UI | `ui/` | Telas Compose, componentes e navegação. Exibe estado e encaminha eventos. |
| Apresentação | `presentation/` | ViewModels e `UiState`; orquestra casos de uso e expõe `StateFlow`. |
| Domínio | `domain/` | `Money`, transações, sugestões, contratos e casos de uso; sem dependência de Android ou LiteRT-LM. |
| Dados | `data/` | Room, parsers, implementação de repositórios e adaptadores de IA local. |
| Composição | `di/AppContainer.kt` e `presentation/AppViewModelProvider.kt` | Injeção manual das implementações nos consumidores. |

O valor monetário usa `Money` em centavos (`Long`), evitando ponto flutuante para contas financeiras. `TransactionOrigin` distingue lançamentos manuais dos importados. `CategorySuggestion` carrega categoria, confiança e origem da sugestão (`AI`, `RULE` ou `USER`).

## Navegação e experiência

`FinancesNavHost` abre `MainTabsScreen`. Um `HorizontalPager` coloca `DashboardScreen` (Início), `ChartsScreen` (Gráficos), `TransactionsScreen` (Transações) e `ChatScreen` nessa ordem; uma pill flutuante alterna entre as quatro páginas. A troca entre páginas adjacentes usa animação curta; a seleção de uma página distante salta diretamente para evitar compor páginas intermediárias. O menu lateral fecha ao tocar fora. Ele também abre Categorias, Configurações, histórico, importação e lançamento manual. Importação, histórico, inclusão/edição de transações, detalhes de categoria e gerenciamento do modelo abrem como rotas secundárias do `NavHost`.

Gráficos está **imediatamente à direita da Início**; Transações e Chat vêm em seguida no pager e na pill inferior. Menu e importação são botões circulares independentes, ao lado da pill; o lançamento manual continua acessível pelo menu. Gráficos oferece a inserção e remoção explícita de 60 transações fictícias de janeiro a outubro de 2026, identificadas pelo marcador `EXEMPLO_GRAFICOS_2026` nas notas. O formulário de inclusão/edição permite escolher um ícone por transação; sem escolha, o ícone é identificado pela descrição e categoria.

## Fluxo de importação e categorização

1. `ImportStatementScreen` recebe a escolha entre extrato e fatura, obtém o arquivo pelo seletor do Android e lê seus bytes. `ImportStatementViewModel` inicia o fluxo.
2. `ImportStatementUseCase` chama `StatementParserRepository` com o tipo escolhido. `FinancialStatementParser` direciona CSV/OFX para `CsvOfxStatementParser` e PDF para `PdfStatementParser`. Em CSV de fatura, valor positivo é compra e negativo é estorno; em CSV de extrato, negativo é despesa e positivo é receita. OFX usa seus valores assinados. PDFBox-Android extrai o texto no aparelho; o parser aceita data completa, descrição e valor no fim do lançamento, mesmo quando ocupam até três linhas consecutivas. Extratos precisam de sinal ou marcador C/D; faturas identificadas ou selecionadas aceitam compras sem sinal. Arquivos sem texto, protegidos por senha ou com linhas monetárias não reconhecidas são recusados antes da revisão. PDF digitalizado e tabelas com colunas reordenadas ainda precisam de suporte específico.
3. `SynthesizeStatementUseCase` consulta as categorias conhecidas e chama `TransactionCategorizer`. `EngineAwareTransactionCategorizer` escolhe LiteRT-LM se o estado do modelo for `Ready`; caso contrário, usa `RuleBasedLocalAiRepository`. O adaptador LiteRT-LM também recorre às regras quando falha ou quando uma resposta tem baixa confiança. Descrições reconhecidas de pagamento de fatura recebem sugestão de transferência.
4. A tela mostra sugestões, contagem e totais do lote. A pessoa pode corrigir tipo e categoria de cada lançamento; essa correção passa a ter `source = USER`. Possíveis sobreposições com o histórico exigem confirmação explícita. Só é possível confirmar quando todos têm categoria compatível.
5. `ConfirmStatementImportUseCase` calcula uma impressão digital dos lançamentos extraídos. `RoomStatementImportRepository` grava o registro e todas as `Transaction(origin = IMPORTED)` em uma transação Room. Se o mesmo conjunto de lançamentos de um lote v2 for confirmado novamente, mesmo em outra ordem, a importação é recusada sem novos inserts.

O fluxo guarda as transações confirmadas e os metadados, sem copiar o arquivo original para o armazenamento permanente do app. A impressão digital ignora as categorias e correções de tipo, e usa os lançamentos extraídos ordenados, preservando compras idênticas repetidas. A v2 independe da ordem. Hashes v1 continuam verificados na ordem original; registros antigos reordenados dependem do aviso de sobreposição, porque os metadados não preservam as linhas originais. Ela impede repetir um lote idêntico. Um segundo controle avisa quando data, valor e descrição já aparecem no histórico; a pessoa pode confirmar compras legítimas repetidas. Essa correspondência não prova que duas linhas representam a mesma compra nem identifica descrições alteradas entre instituições.

## Visualização e insights

`DashboardViewModel` combina saldo, resumos por categoria e os seis lançamentos mais recentes para o período selecionado. A lista recente usa `LIMIT 6` no Room; o Histórico continua carregando o mês completo. `CategoryUsageChart` apresenta a composição por categoria; `CategoriesViewModel` e `TransactionsViewModel` permitem navegar por mês. Os dados são lidos dos repositórios Room por casos de uso como `GetMonthlyBalanceUseCase`, `GetCategorySummariesUseCase` e `GetTransactionsByMonthUseCase`.

O gráfico e os totais já fornecem um resumo visual. **Insights proativos em linguagem natural não existem ainda.** A implementação futura deve derivá-los das transações confirmadas, registrar o período e os números usados e permitir que a pessoa confira cada afirmação no histórico. Um insight não deve tratar uma categoria sugerida mas ainda não confirmada como fato.

## Chat depois de Transações

`ChatViewModel` usa `SendChatMessageUseCase` e `GetChatHistoryUseCase`. Ao abrir a tela, consulta as 100 mensagens mais recentes; a pessoa pode carregar blocos anteriores no topo. O estado do campo de texto é coletado separadamente da lista para evitar recomposição da conversa a cada tecla. Ao enviar uma pergunta, o caso de uso resolve um único mês por nome ou formato numérico e monta `FinancialContext` com saldo e totais por categoria daquele período. Sem mês explícito, usa o mês corrente; mês sem ano usa a ocorrência mais recente. Perguntas sobre dias, intervalos, anos isolados ou períodos ambíguos recebem uma explicação sem enviar números à IA. Perguntas numéricas reconhecidas são respondidas pelos totais consultados; pedidos por estabelecimento sem dados individuais recebem uma limitação explícita. Outras perguntas seguem para `LocalAiRepository.sendMessage`. `EngineAwareLocalAiRepository` escolhe LiteRT-LM se o modelo estiver `Ready`; caso contrário, responde pelo fallback por regras.

O Chat **não lê lançamentos individuais**. Não há recuperação contextual por transação nem streaming. O motor é mantido em memória entre perguntas enquanto a sessão permanecer ativa. “Quanto gastei com Alimentação em fevereiro?” usa o total da categoria no fevereiro mais recente, consultado no Room. “Quanto gastei com Mercado?” recebe uma limitação se Mercado não for uma categoria; não é apresentado outro total como resposta. Perguntas sobre estabelecimentos, dias e intervalos ainda precisam de consultas próprias. O Chat permanece **somente leitura**: respostas não alteram transações nem categorias.

## Motor de IA local

O domínio separa `TransactionCategorizer` (lote de lançamentos) de `LocalAiRepository` (resposta do Chat). `LocalAiEngine` expõe o estado do modelo; `ModelImporter` permite importá-lo ou removê-lo. `ImportedModelLocalAiEngine` copia um `.litertlm` escolhido via seletor do Android para `filesDir`, calcula SHA-256 e verifica capacidade do aparelho. Antes de substituir um modelo instalado, tenta inicializar o runtime com o candidato; `warmUp()` repete a inicialização do modelo instalado. A lista de hashes conhecidos está vazia, então nenhum modelo importado é marcado como verificado hoje. A inicialização não substitui os testes de resposta e categorização em aparelho.

`LiteRtTransactionCategorizer` e `LiteRtLocalAiRepository` fazem as chamadas ao LiteRT-LM. O primeiro processa lotes, tenta interpretar JSON e recorre às regras em caso de falha ou baixa confiança. O segundo recebe um contexto agregado e produz uma resposta sem streaming, reutilizando o motor compartilhado em `LiteRtEngineSession`. Um mutex serializa Chat, categorização, troca e remoção do modelo. As conversas são fechadas após cada chamada; o motor é fechado antes de mutações do arquivo e quando o app recebe notificação de segundo plano ou pressão de memória. A integração de inferência ainda precisa de teste de ponta a ponta e medição em aparelhos reais; os testes JVM dos roteadores não validam o runtime.

`DeviceCapabilityPolicy` usa limites provisórios de **6 GB de RAM, 2 GB livres e ABI `arm64-v8a`**. Assim, o aparelho intermediário de aproximadamente 4 GB previsto para medição não pode importar o modelo com a política atual. A revisão desses limites depende de um modelo menor e de medições reais; o fallback por regras mantém a importação utilizável nesse aparelho.

O projeto possui flavors `standalone` e `play`, atualmente equivalentes. A distribuição do modelo por Play Asset Delivery, sua resolução no flavor `play` e a preferência por um modelo importado são **decisões de produto ainda sem implementação**. O [plano do motor local](PLANO_MOTOR_IA_LOCAL.md) registra a sequência e os critérios de aceite.

## Persistência, privacidade e limites

Room está na versão **7**. `MIGRATION_1_2` acrescentou `origin` às transações, as tabelas de importações e de histórico de Chat. `MIGRATION_2_3` acrescentou a impressão digital única às importações. `MIGRATION_3_4` acrescentou um índice por data e id para as listas ordenadas; `MIGRATION_4_5` indexou data e id do histórico de Chat. `MIGRATION_5_6` adiciona uma categoria de transferência quando necessário. `MIGRATION_6_7` adiciona a escolha opcional de ícone às transações existentes sem alterar seus dados. O schema fica em `app/schemas` e `MigrationTest` verifica as migrações em teste instrumentado. O backup JSON está na versão 3 e preserva o ícone escolhido; backups antigos continuam aceitos, inclusive lançamentos de valor zero. A leitura do arquivo de restauração é limitada a 50 MB. Datas brasileiras dos parsers usam resolução estrita. Backups e exportações manuais usam o seletor de arquivos; os arquivos gerados não recebem criptografia pelo app.

O manifest não declara `android.permission.INTERNET`. O CI executa um teste instrumentado que verifica as permissões do app instalado, incluindo o resultado do merge de manifests. O backup automático do Android está desativado e `MainActivity` usa `FLAG_SECURE`. O banco local e o histórico de Chat não têm criptografia adicional nem bloqueio por PIN/biometria.

## Regras para evoluir o produto

1. Mantenha UI e ViewModels fora dos DAOs e da API LiteRT-LM. Regras de negócio e contratos ficam no domínio; detalhes de arquivo, Room e inferência ficam em dados.
2. Use `Money` para valores monetários. Preserve data, tipo, descrição e origem de cada lançamento ao importar.
3. Dê à pessoa uma etapa de revisão antes de confirmar categorias sugeridas. Exiba incerteza e fallback sem apresentá-los como categorização comprovada por IA.
4. Derive insights e respostas financeiras de dados locais identificáveis por período. Se faltar contexto, informe a limitação em vez de inventar valores.
5. Mantenha o processamento financeiro no dispositivo e verifique a ausência de `INTERNET` nos artefatos gerados.
6. Ao alterar o schema Room, exporte a nova versão, registre a migração e amplie `MigrationTest`. Ao acrescentar uma tela secundária, atualize `FinancesRoute` e `FinancesNavHost`.

### Aparência

As configurações permitem selecionar tema escuro, claro ou do aparelho, além de cinco paletas prontas. `ThemePreferences` persiste as escolhas em SharedPreferences e expõe StateFlows; `MainActivity` aplica as preferências ao `FinancesTheme`. O modo Aparelho acompanha `isSystemInDarkTheme()`. A preferência anterior de tema claro/escuro é preservada ao migrar para a seleção de três modos.
