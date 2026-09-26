# Arquitetura do Finances

## Produto e fluxo principal

O Finances recebe **faturas de cartão e extratos bancários**, transforma seus lançamentos em transações categorizadas e ajuda a pessoa a entender os gastos. A Início apresenta os dados organizados; o **Chat, à direita da Início**, permite fazer perguntas sobre eles. Insights proativos e perguntas detalhadas por período são o próximo passo do produto.

```text
Arquivo → parser → lançamentos brutos → categorizador → revisão humana
                                                        ↓
                                              Room: transações confirmadas
                                                        ↓
                                     Início / Gráfico / Chat à direita
```

**Estado atual:** CSV/OFX, revisão de categorias, dashboard e Chat limitado ao resumo do mês corrente. **Alvo:** PDF de faturas, mais formatos de instituições, insights gerados a partir de números verificáveis e Chat capaz de consultar períodos e transações específicas. O código usa uma **barra inferior** na ordem Início, Chat, Gráfico e Config; não há `HorizontalPager` nem navegação por swipe.

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

`FinancesNavHost` abre `MainTabsScreen`. A barra inferior seleciona `DashboardScreen` (Início), `ChatScreen`, `CategoriesScreen` (Gráfico) ou `SettingsScreen` (Config). Importação, histórico, inclusão/edição de transações, detalhes de categoria e gerenciamento do modelo abrem como rotas secundárias do `NavHost`.

O Chat está **imediatamente à direita da Início na barra inferior**, conforme a ideia central do produto. Um gesto horizontal direto entre as telas pode ser estudado como melhoria de navegação, mas não faz parte da implementação atual.

## Fluxo de importação e categorização

1. `ImportStatementScreen` recebe o arquivo pelo seletor do Android e lê seus bytes. `ImportStatementViewModel` inicia o fluxo.
2. `ImportStatementUseCase` chama `StatementParserRepository`. `CsvOfxStatementParser` produz `RawStatementEntry` com descrição, valor, data e tipo. Ele reconhece `.ofx`/`.qfx` por extensão; os demais nomes são tentados como CSV. No CSV, são esperadas colunas de data, descrição e valor. PDF ainda não é interpretado.
3. `SynthesizeStatementUseCase` consulta as categorias conhecidas e chama `TransactionCategorizer`. `EngineAwareTransactionCategorizer` escolhe LiteRT-LM se o estado do modelo for `Ready`; caso contrário, usa `RuleBasedLocalAiRepository`. O adaptador LiteRT-LM também recorre às regras quando falha ou quando uma resposta tem baixa confiança.
4. A tela mostra as sugestões. A pessoa pode escolher uma categoria por lançamento; essa correção passa a ter `source = USER`. Só é possível confirmar quando todos têm categoria.
5. `ConfirmStatementImportUseCase` salva cada entrada como `Transaction(origin = IMPORTED)` e registra um `ImportedStatement` com nome do arquivo, quantidade e data da importação.

O fluxo atual guarda as transações confirmadas e os metadados, sem copiar o arquivo original para o armazenamento permanente do app. **Ainda não há deduplicação nem transação única envolvendo todo o lote**: se houver falha após alguns inserts, o caso de uso não faz rollback conjunto. Isso deve ser tratado antes de prometer importação idempotente de faturas.

## Visualização e insights

`DashboardViewModel` combina saldo, resumos por categoria e transações recentes para o período selecionado. `CategoryUsageChart` apresenta a composição por categoria; `CategoriesViewModel` e `TransactionsViewModel` permitem navegar por mês. Os dados são lidos dos repositórios Room por casos de uso como `GetMonthlyBalanceUseCase`, `GetCategorySummariesUseCase` e `GetTransactionsByMonthUseCase`.

O gráfico e os totais já fornecem um resumo visual. **Insights proativos em linguagem natural não existem ainda.** A implementação futura deve derivá-los das transações confirmadas, registrar o período e os números usados e permitir que a pessoa confira cada afirmação no histórico. Um insight não deve tratar uma categoria sugerida mas ainda não confirmada como fato.

## Chat à direita da Início

`ChatViewModel` usa `SendChatMessageUseCase` e `GetChatHistoryUseCase`. Ao enviar uma pergunta, o caso de uso monta `FinancialContext` com saldo e totais por categoria do **mês corrente**, salva a pergunta, chama `LocalAiRepository.sendMessage` e salva a resposta. `EngineAwareLocalAiRepository` escolhe LiteRT-LM se o modelo estiver `Ready`; caso contrário, responde pelo fallback por regras. O adaptador LiteRT-LM também usa esse fallback quando a inferência falha.

O Chat **não lê lançamentos individuais nem resolve períodos pedidos no texto**. Não há recuperação contextual por transação, streaming ou motor mantido em memória entre perguntas. Para responder “quanto gastei com mercado em fevereiro?” com precisão, o próximo contrato deve primeiro resolver o período, consultar as transações/categorias pertinentes no Room e só então montar a resposta. O Chat permanece **somente leitura**: respostas não alteram transações nem categorias.

## Motor de IA local

O domínio separa `TransactionCategorizer` (lote de lançamentos) de `LocalAiRepository` (resposta do Chat). `LocalAiEngine` expõe o estado do modelo; `ModelImporter` permite importá-lo ou removê-lo. `ImportedModelLocalAiEngine` copia um `.litertlm` escolhido via seletor do Android para `filesDir`, calcula SHA-256 e verifica capacidade do aparelho. A checagem `warmUp()` atual apenas confirma que o arquivo existe e tem tamanho mínimo; ela não carrega o runtime nem valida o conteúdo do bundle. A lista de hashes conhecidos está vazia, então nenhum modelo importado é marcado como verificado hoje.

`LiteRtTransactionCategorizer` e `LiteRtLocalAiRepository` fazem as chamadas ao LiteRT-LM. O primeiro processa lotes, tenta interpretar JSON e recorre às regras em caso de falha ou baixa confiança. O segundo recebe um contexto agregado e produz uma resposta sem streaming, recarregando o modelo a cada pergunta. A integração de inferência ainda precisa de teste de ponta a ponta e medição em aparelhos reais; os testes JVM dos roteadores não validam o runtime.

`DeviceCapabilityPolicy` usa limites provisórios de **6 GB de RAM, 2 GB livres e ABI `arm64-v8a`**. Assim, o aparelho intermediário de aproximadamente 4 GB previsto para medição não pode importar o modelo com a política atual. A revisão desses limites depende de um modelo menor e de medições reais; o fallback por regras mantém a importação utilizável nesse aparelho.

O projeto possui flavors `standalone` e `play`, atualmente equivalentes. A distribuição do modelo por Play Asset Delivery, sua resolução no flavor `play` e a preferência por um modelo importado são **decisões de produto ainda sem implementação**. O [plano do motor local](PLANO_MOTOR_IA_LOCAL.md) registra a sequência e os critérios de aceite.

## Persistência, privacidade e limites

Room está na versão **2**. `MIGRATION_1_2` acrescentou `origin` às transações, as tabelas de importações e de histórico de Chat. O schema fica em `app/schemas` e `MigrationTest` verifica a migração em teste instrumentado. Backups e exportações manuais usam o seletor de arquivos; os arquivos gerados não recebem criptografia pelo app.

O manifest não declara `android.permission.INTERNET`. O CI executa um teste instrumentado que verifica as permissões do app instalado, incluindo o resultado do merge de manifests. O backup automático do Android está desativado e `MainActivity` usa `FLAG_SECURE`. O banco local e o histórico de Chat não têm criptografia adicional nem bloqueio por PIN/biometria.

## Regras para evoluir o produto

1. Mantenha UI e ViewModels fora dos DAOs e da API LiteRT-LM. Regras de negócio e contratos ficam no domínio; detalhes de arquivo, Room e inferência ficam em dados.
2. Use `Money` para valores monetários. Preserve data, tipo, descrição e origem de cada lançamento ao importar.
3. Dê à pessoa uma etapa de revisão antes de confirmar categorias sugeridas. Exiba incerteza e fallback sem apresentá-los como categorização comprovada por IA.
4. Derive insights e respostas financeiras de dados locais identificáveis por período. Se faltar contexto, informe a limitação em vez de inventar valores.
5. Mantenha o processamento financeiro no dispositivo e verifique a ausência de `INTERNET` nos artefatos gerados.
6. Ao alterar o schema Room, exporte a nova versão, registre a migração e amplie `MigrationTest`. Ao acrescentar uma tela secundária, atualize `FinancesRoute` e `FinancesNavHost`.
