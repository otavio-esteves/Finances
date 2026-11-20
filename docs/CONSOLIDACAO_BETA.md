# Consolidação para beta — rodada de 19–20/11/2026

## Estado inicial e preservação

Base local: commit `943b682`. O trabalho começou com alterações não commitadas em README, documentação de PDF, validação de moeda/unidades, fronteiras de tabelas e seus testes. Essas alterações foram preservadas; não foram tratadas como entregas novas desta missão. A implementação e a validação foram concluídas sem commit, push ou publicação; commit e push foram autorizados posteriormente pelo usuário em 20/11/2026. Publicação de release permanece fora dessa autorização.

Foram lidos README, arquitetura, importação PDF, guia de amostras, planos de IA e otimização, código e testes dos fluxos envolvidos e o workflow Android. Documentos históricos de otimização/IA ainda descrevem a tela de Chat removida; a navegação atual é Início, Gráficos, Transações e Categorias.

Arquitetura mantida: domínio/casos de uso, repositórios Room, ViewModels/StateFlow, Compose/Material 3 e injeção manual. Banco Room **10**, schemas 1–10 exportados; JSON de backup **5**, com restauração de versões 1–5. `standalone` e `play` têm hoje o mesmo comportamento, sem asset pack. Valores continuam em `Long` de centavos.

## Linha de base executada antes das alterações desta missão

Ambiente: JDK do Android Studio, SDK 36, emulador dedicado API 35/x86_64. Nenhum teste foi instalado em aparelho pessoal.

| Verificação | Resultado inicial |
|---|---|
| `testStandaloneDebugUnitTest --rerun` | 489 aprovados, zero falhas/ignorados |
| `lintStandaloneDebug` | Zero erros, 40 avisos e uma sugestão |
| `assembleStandaloneDebug` | Aprovado |
| `assembleStandaloneRelease` | Aprovado |
| `-PisolatedDeviceValidation=true connectedStandaloneDebugAndroidTest` | 167 casos: 162 aprovados, cinco opt-ins ignorados, zero falhas |
| Corpus privado | Quatro amostras cadastradas: uma aceita e três recusadas. Falhas estruturais identificadas em capa com saldos combinados, subtotal de portador e legenda de reais/percentual. Resultados e conteúdo ficam exclusivamente em `samples/private/`, ignorado pelo Git. |

Os opt-ins iniciais exigem modelo real, corpus privado ou ativação de medição/captura. Não comprovam qualidade de LLM nem desempenho em aparelhos físicos. O runner imprime uma contagem intermediária maior para testes ignorados; os números acima vêm do XML JUnit, com 167 casos efetivos.

## Diagnóstico e critérios desta rodada

### P0 — Integridade

Já existem leitura limitada/cancelável, parsers estritos, revisão de OCR, hashes versionados com multiplicidade, avisos de sobreposição, memória explícita, geração transitória contra restaurações concorrentes e escrita atômica. Não serão recriados.

Lacunas a fechar: unidades declaradas desconhecidas ainda podem seguir a interpretação genérica em reais; declarações de moeda fragmentadas dentro do próprio rótulo precisam de tratamento conservador; a síntese não verifica integralmente a cardinalidade/proveniência devolvida pelo categorizador; o resumo da revisão omite o total de transferências. Cada correção receberá casos sintéticos, incluindo recusas e controles válidos. Critério: conservar quantidades, datas, valores, multiplicidade e origem; nunca liberar um resultado parcial ou reinterpretar uma unidade não suportada.

### P1 — Categorização

Regras, memória e LiteRT-LM já existem, mas não há corpus público sintético com avaliação comparável e métricas automatizadas. Implementar avaliação de regras, memória e fluxo combinado, mais execução opt-in do modelo quando houver hardware/bundle. Separar medidas de qualidade, cobertura, revisão e erros de alta confiança; pontuações não serão apresentadas como probabilidades calibradas. Melhorias de vocabulário serão orientadas pelos erros medidos.

### P2 — Segurança

O app não declara INTERNET; backup Android é negado e captura de tela é bloqueada por padrão. O JSON manual e o Room não têm criptografia adicional. Implementar backup portátil protegido, usando biblioteca madura de criptografia autenticada em fluxo, derivação de senha e autenticação completa antes de qualquer restauração. Preservar JSON legado. Avaliar bloqueio opcional com credencial oficial do aparelho, distinguindo-o de criptografia do banco. Senhas e conteúdo financeiro não entram em logs.

### P3 — Experiência

Preservar a identidade visual. Priorizar totais completos na revisão, confirmações acessíveis, backup protegido como caminho claro, senha transitória, erro compreensível e cancelamento sem sucesso falso. Cobrir caminhos críticos com Compose.

### P4 — Desempenho e beta

Medir categorização em lote e processamento antes de otimizar. Há normalizações repetidas no categorizador que serão avaliadas com a mesma carga. Não reduzir os requisitos de IA. Validar ambos os flavors, migrações, permissões, builds e restauração; registrar hardware/modelos ausentes e checklist físico em `BETA_READINESS.md`.

## Registro incremental

As seções abaixo registram implementações e verificações incrementais. Os números finais comparáveis estão em “Validação final”; contagens incrementais não devem ser somadas. A existência deste documento não significa aprovação para beta.

### P0 implementado e verificado

- Cabeçalhos CSV só aceitam unidades maiores reconhecidas; unidades desconhecidas, escalas e moedas incompatíveis recusam o lote. O caminho de exportação nativa em centavos permanece distinto.
- Declarações de moeda com rótulo quebrado entre linhas são reconstituídas conservadoramente; declaração sem valor não desaparece. A legenda específica de reais/percentual não transforma os valores da fatura em percentuais.
- Capas com dois saldos são isoladas apenas no layout identificado que exige reconciliação de entradas e saídas. Faturas identificadas conciliam subtotais do portador e pagamentos; PicPay concilia subtotais e total geral, descontando estornos e excluindo pagamentos. Totais de fatura não são presumidos iguais às despesas.
- O contexto de fatura passa a ser explícito nas transformações de layout: banco e cabeçalho genérico não podem inverter o sinal de um extrato de conta.
- Síntese exige cardinalidade, ordem, origem, datas, descrição e centavos preservados pelo categorizador. Mudança de tipo exige memória explícita; categorias precisam pertencer ao catálogo atual.
- Revisão mostra contagens e total de transferências/pagamentos separados de despesas e receitas/estornos.

Validação incremental: **500 testes JVM aprovados** após as correções P0; **19 testes Android direcionados aprovados**, incluindo PDF real gerado, revisão Compose, atomicidade, restauração inválida e concorrência. O corpus privado foi executado novamente: **cinco cenários aprovados** (Caixa, Mercado Pago, Nubank e duas faturas PicPay), sem copiar dados financeiros para arquivos versionados. BB e Inter têm regressões sintéticas existentes, mas documentos privados dessas famílias não estão disponíveis. A amostra Caixa usou OCR; aprovação de uma amostra não demonstra cobertura de todos os layouts.

### P1 implementado e verificado

Benchmark automatizado de 58 casos públicos sintéticos, correções de treinamento separadas, regras/memória/fluxo combinado, métricas por categoria, cobertura, revisão, erros de alta confiança, latência em lote e heap observado. A avaliação preserva números de parcelas e verifica invariantes financeiros. Relatório e metodologia em `CATEGORIZACAO_BENCHMARK.md`.

Vocabulário ampliado a partir dos erros observados. Respostas do modelo com índices repetidos/conflitantes/fora do lote usam fallback; pontuações inválidas não são artificialmente elevadas para 1. Cancelamento continua propagado. **502 testes JVM aprovados**. No mesmo corpus, regras passaram de 60,34% para 94,83%; memória + regras + síntese, de 65,52% para 100%. Resultados sintéticos, sem garantia de generalização. Modelo real não executado: hardware/ABI e bundle ausentes; nenhuma métrica foi inventada.

### P2 implementado e verificado

Backup `.fnb` portátil por senha, com Tink Streaming AEAD (AES-256-GCM/HKDF-SHA256), PBKDF2-HMAC-SHA256/600000, salt aleatório e cabeçalho autenticado/versionado. O JSON interno segue na versão 5; leitores legados 1–5 preservados. Senhas/arrays são transitórios, erros de JSON/provedor são sanitizados e o EOF autenticado precede qualquer chamada de restauração. Não há buffering do documento cifrado inteiro nem temporário de backup; listas de registros ainda são materializadas. Formato, dependência justificada, fallback API 24–25 e limites em `BACKUP_SEGURANCA.md`.

Proteções cobertas: senha errada/ausente, versões/KDF incompatíveis, adulteração no salt e último segmento, truncamento/bytes extras, JSON inválido autenticado, cancelamento, senhas Unicode, salinização aleatória e restauração por instâncias novas. Android verifica a preservação exata do banco sentinela/totais e restauração entre dois Room independentes. Limpeza de PDFs temporários de processos anteriores foi adicionada, mantendo sessão ativa e outros caches.

A opção sem proteção permanece avançada e identificada; `.fnb` é o caminho principal. Captura, backup Android, ausência de rede e gates de IA foram preservados. Bloqueio oficial de interface foi avaliado e adiado pelo risco de ciclo de vida/SAF/diálogos; não há PIN próprio nem criptografia adicional de Room. Justificativa e ação futura estão documentadas.

### P3 implementado e verificado

Revisão separa transferências, informa origem das sugestões, esclarece PIX/resgates entre contas e mantém conferência/correção explícita. Rótulos de reconhecimento OCR, duplicidade e diferença agora são controles únicos clicáveis, com área mínima de 48 dp. Backup tem confirmação transitória de senha, tentativa novamente após senha incorreta, erro sanitizado, estados úteis de operação e cancelamento antes da escrita atômica. Exportações pedem truncamento ao provedor. Histórico volta a ser consultado após restaurar; falha persistente continua visível, e recuperação real não exige recriar o ViewModel.

Validação incremental P2/P3: **512 testes JVM aprovados**, antes dos novos ensaios de métricas/desempenho; **23 testes Android direcionados aprovados**, incluindo backup protegido no Room e Compose, controle acessível com fonte 150%/largura 320 dp, migração completa 1→10 e ausência de INTERNET. O ensaio Android completo do backup protegido levou cerca de 69 segundos neste AVD sob carga compartilhada, abrangendo criação, várias falhas e restauração correta; não representa latência de uma única operação nem de aparelho físico. O fator de derivação não foi reduzido.

### P4 implementado e medido

Regras preparam catálogo/vocabulário uma vez por lote e reutilizam regexes de normalização. A revisão guarda apenas lista incluída e totais por snapshot imutável, preservando a aritmética central; a variante que piorou a primeira leitura foi descartada. Valores/contagens após edição/troca de tipo/exclusão são verificados em regressão. Nenhum requisito de IA foi reduzido e não houve schema novo.

Medições executadas e artefatos sintéticos estão em `PERFORMANCE_BETA.md`/`benchmarks/`. Qualidade de categorização ficou idêntica após a otimização; tempos de regras e releitura do resumo melhoraram nos ensaios JVM, com limitações explícitas de carga/JIT/hardware. Não há extrapolação para FPS nem memória de modelo real. A CI agora inclui os dois flavors e verificação das permissões/proteções dos APKs debug/release.

### Fechamento adicional de P0: completude além dos totais

Uma regressão sintética comprovou que reconciliação de valores, sozinha, não detecta lançamento **zerado** descartado antes do cabeçalho da tabela. O corte de capas foi endurecido: linhas que podem ser lançamentos datados antes da seção reconhecida recusam o lote, inclusive datas fragmentadas/abreviadas. A proteção abrange os recortes de extrato, tabela com favorecido, Nubank, PicPay e seções de cartões das demais famílias. Seções futuras explicitamente identificadas no layout PicPay continuam separadas; quantias brasileiras com milhares não são confundidas com datas.

Os três testes novos falharam antes da correção por aceitação indevida do lote e passaram após ela, junto das regressões relacionadas. Foi incluído também PDF sintético gerado no Android para conferir a mesma proteção após extração real de palavras. O primeiro comando de validação ampla foi cancelado para incluir essa correção; seus resultados intermediários de 515 JVM por flavor não são a aprovação final. A validação ampla foi reiniciada com o código corrigido.

## Validação final

Código final validado com o Gradle Wrapper 8.13, JBR do Android Studio e SDK instalado localmente. Os testes instrumentados usaram somente o pacote `.validation` no AVD API 35/x86_64, preservando a instalação normal e dados pessoais. Não houve alteração do schema nem de arquivos privados versionados.

| Verificação | Antes da missão | Resultado final |
|---|---|---|
| JVM standalone | 489 aprovados | **518 aprovados**, zero falhas/erros/ignorados |
| JVM play | Não executado na linha de base | **518 aprovados**, zero falhas/erros/ignorados |
| Android standalone | 167 casos: 162 aprovados, cinco ignorados | **175 casos: 169 aprovados, seis ignorados**, zero falhas/erros |
| Android play | Não executado na linha de base | **175 casos: 169 aprovados, seis ignorados**, zero falhas/erros |
| Lint de cada flavor | standalone: zero erros, 40 avisos, uma sugestão | Ambos: **zero erros, 41 avisos, uma sugestão** |
| Debug/release otimizado | standalone aprovado | **Ambos os flavors aprovados**, release sem assinatura |
| Corpus privado disponível | Uma aprovação e três recusas | **Cinco cenários aprovados**, incluindo amostra adicional PicPay |
| Benchmark opt-in Android + PDFs gerados | Ausente | **Cinco testes aprovados em 4,532 s**, três modos locais medidos |
| Privacidade dos APKs finais | Inspeção parcial em fonte/testes | **Quatro APKs normais aprovados**, Manifest e exclusões XML empacotadas verificados |

São 29 novos casos JVM e oito novos casos Android em relação à linha de base; a repetição nos dois flavors não representa cenários distintos. As contagens vêm dos XMLs JUnit, pois o progresso do runner conta novamente callbacks de testes ignorados. Os seis opt-ins são: benchmark de categorização, dois testes com modelo real, corpus privado, captura visual e medição de frames da navegação. Benchmark local e corpus privado foram executados separadamente; os demais não foram anunciados como aprovados. Nenhum modelo real foi executado.

Comando amplo aprovado em **41 min 41 s** (262 tarefas, 180 executadas), incluindo os quatro builds e APKs de teste:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew --max-workers=2 -PisolatedDeviceValidation=true \
  testStandaloneDebugUnitTest testPlayDebugUnitTest lintStandaloneDebug lintPlayDebug \
  assembleStandaloneDebug assembleStandaloneDebugAndroidTest \
  assemblePlayDebug assemblePlayDebugAndroidTest \
  assembleStandaloneRelease assemblePlayRelease --console=plain
```

Depois dos builds e liberação do daemon, suíte Android completa aprovada em **19 min 40 s**:

```bash
JAVA_HOME=/opt/android-studio/jbr ANDROID_SERIAL=emulator-5554 ./gradlew --max-workers=2 \
  -PisolatedDeviceValidation=true connectedStandaloneDebugAndroidTest connectedPlayDebugAndroidTest --console=plain
```

O caminho do JDK é específico desta máquina, não uma configuração compartilhada obrigatória. Evidências locais: `app/build/test-results/test{Standalone,Play}DebugUnitTest/`, `app/build/outputs/androidTest-results/connected/debug/flavors/{standalone,play}/` e `app/build/reports/lint-results-{standalone,play}Debug.xml`. Resumo sem dados privados: `benchmarks/validation-summary.json`.

A passagem final sem a propriedade de isolamento executou novamente os dois JVM (`--rerun`) e os dois lints com os resultados da tabela. O comando completo falhou após **9 min 38 s**, em `packagePlayDebug`, sem detalhar a causa interna do empacotador. A repetição apenas dos builds, com um worker e stacktrace, terminou aprovada em **1 min 1 s**, sem mudança de código. Não se atribui essa falha a disco, memória ou concorrência sem evidência; a causa ficou não estabelecida. Os quatro APKs resultantes passaram na inspeção de permissões e das exclusões reais de backup, inclusive recursos otimizados pelo R8:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew --max-workers=1 \
  assemblePlayDebug assembleStandaloneDebug assembleStandaloneRelease assemblePlayRelease --console=plain --stacktrace
python3 scripts/verify_android_privacy.py
```

Os APKs finais têm o identificador normal `br.com.otavioesteves.finances`. Não foram instalados sobre a aplicação pessoal. O AVD dedicado foi encerrado após os testes. Hashes SHA-256, tamanhos, comandos concluídos e tentativa malsucedida estão no resumo verificável; a falha não foi omitida nem contada como build aprovado.

O lint não foi artificialmente zerado: os três avisos `TrustAllX509TrustManager` pertencem à pilha PDF/PKI preexistente; não há cliente HTTP/TLS nem INTERNET no app. O aviso adicional é a versão disponível do `bcprov` que agora está explicitamente declarado, antes transitivo. A atualização ampla dessa pilha precisa de validação própria; detalhes em `BACKUP_SEGURANCA.md`.

## Medições e decisão de beta

No corpus sintético de 58 casos, regras passaram de **60,34% para 94,83%** e o fluxo de memória/regras/síntese de **65,52% para 100%**. O vocabulário foi ajustado nesse corpus: esses resultados não demonstram generalização. A memória isolada tem cobertura limitada e não deve ser comparada como um classificador completo. Métricas por categoria, precisão/recall/F1, correções, abstenções e erros de alta confiança estão nos relatórios; pontuações não são probabilidades calibradas.

A otimização posterior preservou todos os resultados financeiros e de classificação comparados. Na JVM, o ensaio de regras passou de 2,263 para 0,392 ms/transação amortizado; a releitura do resumo de 6000 lançamentos, de 4,213 para 0,838 ms de mediana. No Android emulado, regras/fluxo combinado mediram 1,863/1,922 ms/transação e PSS observado de 207,98/242,49 MiB para o processo completo de testes. JIT, aquecimento, amostragem e carga compartilhada limitam a interpretação. Não há medição de inferência LiteRT-LM nem promessa de desempenho físico. Metodologia e artefatos: `CATEGORIZACAO_BENCHMARK.md`, `PERFORMANCE_BETA.md` e `benchmarks/`.

**Ainda não aprovado para beta fechado.** Não foram observadas falhas críticas de integridade nos cenários executados. Restam como gates de distribuição: backup por SAF entre aparelhos físicos (incluindo API 24–25), uso completo com TalkBack/fontes grandes, desempenho/cancelamento com bases e PDFs grandes em aparelho intermediário e assinatura dos releases. Inferência real exige hardware/bundle compatíveis; regras continuam independentes dela. BB/Inter não têm documentos privados disponíveis nesta máquina. O bloqueio interno opcional foi avaliado e adiado; Room não recebeu criptografia adicional. A matriz `BETA_READINESS.md` relaciona cada evidência, risco e ação pendente.

Foram mantidos os limites de PDF/OCR, requisitos provisórios de IA e testes de carga existentes. Uma tentativa Android concorrente com R8 excedeu o prazo de inicialização do ActivityManager; foi registrada como falha do ensaio, repetida após liberar o compilador e não entrou nas métricas. Não houve aumento de timeouts para mascará-la. Os resultados desta rodada são de execução local, anterior ao versionamento autorizado; não dependem de aprovação da CI remota. O resumo JSON preserva o HEAD e o estado não commitado observados durante a validação, como registro histórico. Nenhuma publicação de release foi realizada.

## Arquivos centrais da implementação

| Área | Arquivos principais |
|---|---|
| Importação e moeda | `data/statement/StatementCurrency.kt`, `CsvOfxStatementParser.kt`, `PdfCurrencyPolicy.kt`, `PdfStatementDocument.kt`, `BankCardPdfText.kt`, `PdfStatementParser.kt` |
| Proveniência e integridade | `domain/usecase/SynthesizeStatementUseCase.kt`; escrita atômica existente preservada |
| Categorização | `data/ai/RuleBasedLocalAiRepository.kt`, `LocalTransactionCategorization.kt` |
| Backup portátil | `domain/repository/BackupEncryption.kt`, `data/backup/PasswordBackupEncryption.kt`, `domain/usecase/BackupUseCases.kt`, `di/AppContainer.kt` |
| Operação/privacidade | `presentation/settings/SettingsViewModel.kt`, `data/statement/PdfTemporaryFiles.kt`, `MainApplication.kt` |
| Revisão e interface | `presentation/importstatement/ImportStatementUiState.kt`, `ui/screens/importstatement/ImportReviewContent.kt`, `ImportReviewChecks.kt`, `ImportReviewEntry.kt`, `ReviewAcknowledgment.kt`, `ui/screens/settings/SettingsScreen.kt`, `ui/screens/transactions/TransactionsScreen.kt` |
| Regressões JVM | `BetaImportIntegrityTest`, `PdfCoverCompletenessTest`, `ProtectedBackupTest`, `PdfTemporaryFilesTest`, testes de categorização/ViewModel/estado da revisão |
| Regressões Android | `BetaImportPdfIntegrationTest`, `FinancialDataIntegrityTest`, `BetaBackupFlowTest`, `ReviewAccessibilityTest`, `MigrationTest` |
| Avaliação reproduzível | `src/sharedTest/.../CategorizationEvaluation.kt`, `CategorizationBenchmarkTest`, `ImportReviewSummaryBenchmarkTest`, `CategorizationDeviceBenchmarkTest`, `samples/categorization/corpus.json` |
| Builds/CI | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/android-ci.yml`, `scripts/verify_android_privacy.py` |

Os caminhos de código na tabela são relativos a `app/src/main/java/br/com/otavioesteves/finances`, salvo testes/source sets indicados. Alterações preexistentes de README, PDF/moeda e seus contratos de regressão continuam preservadas. Nenhum schema Room, banco pessoal, documento privado ou chave de assinatura foi substituído.
