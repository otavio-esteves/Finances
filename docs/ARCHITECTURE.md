# Arquitetura do Projeto Finances

Este documento descreve a estrutura arquitetural, os princípios de design e os fluxos de dados do aplicativo Finances.

> **Nota de escopo (2026):** o projeto está migrando de um app de lançamento manual de transações para um app centrado em **importação de extratos bancários + síntese por IA local**, com uma tela inicial (gráfico de uso de capital por categoria) e uma segunda tela de chat com IA local, acessada por swipe. A navegação raiz por `HorizontalPager`, o gráfico da Home, a tela de Chat e a tela de Importar Extrato já estão implementadas; seções ainda marcadas como **(planejado)** cobrem apenas o motor de IA real e o parser de PDF (fase 2).

## Visão Geral
O projeto segue uma arquitetura inspirada em **Clean Architecture**, dividida em camadas para garantir separação de interesses, testabilidade e manutenibilidade. A migração de escopo adiciona duas responsabilidades novas ao domínio: **importar e interpretar extratos** e **sintetizar/conversar via IA local** — ambas mantendo o princípio de que nenhum dado financeiro do usuário sai do dispositivo.

---

## Camadas da Arquitetura

### 1. Camada de UI (User Interface)
- **Tecnologia:** Jetpack Compose.
- **Responsabilidade:** Renderizar o estado da tela e capturar interações do usuário.
- **Princípio:** Deve ser "burra" e puramente declarativa. Não contém lógica de negócio ou acesso direto a dados.
- **Navegação principal (implementado):** a raiz do app é um `HorizontalPager` de duas páginas — **Home** (índice 0, `DashboardScreen`) e **Chat** (índice 1, `ChatScreen`) — navegáveis por swipe, hospedado por `HomeChatPager` e montado na rota `Dashboard` do `FinancesNavHost`. As demais telas (Transações, Categorias, Importar Extrato, Configurações) são acessadas a partir da Home como navegação secundária (rotas empilhadas no mesmo `NavHost`, sobre a rota do pager), não como páginas do pager.

### 2. Camada de Presentation (ViewModel)
- **Tecnologia:** `ViewModel` do Android, `StateFlow`.
- **Responsabilidade:** Gerenciar o estado da UI (**UDF - Unidirectional Data Flow**). Recebe eventos da UI e interage com os *Use Cases* e interfaces de repositórios do domínio injetadas por construtor.
- **Comunicação:** Expõe `uiState` via `StateFlow`. `DashboardViewModel`, `CategoriesViewModel` e `TransactionsViewModel` expõem o período selecionado e um método para trocá-lo (`onMonthSelected`/`CategoriesEvent.OnMonthChanged`), navegável pelo componente `MonthPeriodSelector` (mês anterior/próximo) presente na Home, em Categorias e no Histórico.
- **Novos ViewModels (implementado):** `ChatViewModel` (histórico via `GetChatHistoryUseCase`, rascunho e envio via `SendChatMessageUseCase`; sem streaming de resposta ainda) e `ImportStatementViewModel` (estado da tela via `ImportStatementUiState` — `Idle`/`Loading`/`ReviewingSuggestions`/`Success`/`Error`; orquestra `ImportStatementUseCase`, `SynthesizeStatementUseCase` e `ConfirmStatementImportUseCase`, e permite sobrescrever a categoria sugerida por entrada antes de confirmar). `DashboardViewModel` já expõe `categorySummaries` (via `GetCategorySummariesUseCase`) para o gráfico da Home.

### 3. Camada de Domain (Domínio)
- **Componentes:** *Models*, *Use Cases*, *Repository Interfaces*.
- **Responsabilidade:** Contém as regras de negócio puras. É o núcleo do app.
- **Restrição:** **Não deve depender de bibliotecas Android**. Deve ser código Kotlin puro. Isso vale também para as novas interfaces de importação e IA — o domínio não conhece o parser de PDF nem o motor de inferência concretos, apenas seus contratos.
- **Models:** uso obrigatório da classe `Money` (centavos em `Long`) para evitar erros de precisão numérica.
- **Novos modelos (implementado):**
  - `ImportedStatement`: metadados de um extrato importado (nome do arquivo, período, quantidade de transações, data de importação).
  - `RawStatementEntry`: uma linha bruta extraída do extrato (descrição, valor, data), antes da categorização.
  - `CategorySuggestion`: sugestão de categoria feita pela IA local para uma `RawStatementEntry`.
  - `ChatMessage`: mensagem de uma conversa com a IA local (papel, conteúdo, timestamp).
  - `TransactionOrigin`: enum (`MANUAL` / `IMPORTED`) que marca a proveniência de uma `Transaction`.
- **Novas interfaces de repositório (implementado):**
  - `StatementParserRepository`: contrato para extrair `RawStatementEntry` de um arquivo (implementado por `CsvOfxStatementParser` na camada de Data; PDF é fase 2, ainda não implementada).
  - `LocalAiRepository`: contrato para categorização (`suggestCategories(entries, knownCategories): List<CategorySuggestion>`) e chat (`sendMessage(message, context): ChatMessage`), isolando o domínio do motor de IA escolhido.
  - `ChatRepository`: persistência do histórico de conversas (`getMessages(): Flow<List<ChatMessage>>`, `addMessage(message)`).
  - `StatementImportRepository`: persistência do histórico de importações (`getImports(): Flow<List<ImportedStatement>>`, `addImport(statementImport)`).
- **Use Cases (implementado):** `ImportStatementUseCase` (lê o arquivo via `StatementParserRepository`), `SynthesizeStatementUseCase` (sugere categorias via `LocalAiRepository`), `ConfirmStatementImportUseCase` (persiste as sugestões confirmadas como `Transaction(origin = IMPORTED)` via `AddTransactionUseCase` e registra o import via `StatementImportRepository`), `SendChatMessageUseCase` (monta o `FinancialContext`, persiste a mensagem do usuário e a resposta via `ChatRepository`, e retorna a resposta), `GetChatHistoryUseCase`. `GetCategorySummariesUseCase`, já existente, é reaproveitado como fonte de dados do gráfico da Home, consumido por `DashboardViewModel`/`CategoryUsageChart`.

### 4. Camada de Data (Dados)
- **Componentes:** *Room Database*, *DAOs*, *Repository Implementations*, *Mappers*.
- **Tecnologia:** Room.
- **Responsabilidade:** Persistência local e mapeamento de dados externos/locais para o domínio.
- **Novos componentes (implementado):**
  - `CsvOfxStatementParser`: implementação de `StatementParserRepository` para CSV/OFX (fase 1). `PdfStatementParser` (fase 2, extração de texto/tabelas) ainda **planejado**.
  - `RuleBasedLocalAiRepository`: implementação provisória de `LocalAiRepository` por correspondência de palavra-chave, até a escolha do motor de IA real (ver [Decisão de Arquitetura: Motor de IA Local](#decisão-de-arquitetura-motor-de-ia-local)). É a única camada que conhecerá a biblioteca/SDK de IA concreta quando essa decisão for tomada.
  - `RoomChatRepository` e `RoomStatementImportRepository`: implementações Room de `ChatRepository`/`StatementImportRepository`.
  - Entidades Room adicionais: `StatementImportEntity` (histórico de importações) e `ChatMessageEntity` (histórico de conversas), além do campo `origin` (`MANUAL`/`IMPORTED`) em `TransactionEntity`, já mapeado de ponta a ponta até o modelo de domínio `Transaction`.

---

## Decisão de Arquitetura: Motor de IA Local

**Status: em aberto.** Duas opções candidatas foram avaliadas para a camada `LocalAiRepository`:

1. **Gemini Nano via AICore / ML Kit GenAI APIs** — API oficial do Android para IA generativa on-device. Não aumenta o tamanho do APK e é mantida pelo Google, mas só está disponível em dispositivos compatíveis (ex: Pixel 8+ e similares), exigindo uma estratégia de fallback (ex: categorização manual, chat desabilitado com aviso) nos demais aparelhos.
2. **MediaPipe LLM Inference API + modelo embarcado** (ex: Gemma 2B/3 1B em formato `.task`/gguf) — funciona na maioria dos Androids modernos e dá controle total sobre modelo e prompt, sem depender de serviço do Google em runtime. Custa tamanho de app/download inicial (centenas de MB) e desempenho/bateria piores que uma solução nativa do SO.

**Regra inegociável, independente da escolha:** o motor de IA deve processar tudo **on-device**, sem chamadas de rede em runtime — nem para categorização de extratos, nem para o chat. Isso mantém a app sem a permissão `INTERNET` como hoje. A escolha final, uma vez tomada, deve ser registrada aqui com a justificativa e o plano de fallback para dispositivos incompatíveis.

---

## Fluxo de Dados

### Exemplo 1: Importação de Extrato e Síntese (implementado)

`ImportStatementScreen` (seleção de arquivo via `ActivityResultContracts.GetContent`)
  → `ImportStatementViewModel`
  → `ImportStatementUseCase(fileName, bytes)` → `StatementParserRepository.parse(...)` → lista de `RawStatementEntry`
  → `SynthesizeStatementUseCase(entries)` → `LocalAiRepository.suggestCategories(entries, knownCategories)` → lista de `CategorySuggestion`
  → usuário revisa as sugestões na UI (`ImportStatementUiState.ReviewingSuggestions`) e pode sobrescrever a categoria de qualquer entrada antes de confirmar
  → `ConfirmStatementImportUseCase(fileName, confirmedSuggestions)` → persiste cada sugestão como `Transaction(origin = IMPORTED)` via `AddTransactionUseCase`, e registra o import via `StatementImportRepository`

O extrato original não é retido após a importação; apenas as transações resultantes (com origem `IMPORTED`) e os metadados em `StatementImportEntity` persistem. A cadeia de casos de uso já existe e está coberta por testes; falta apenas a tela que a aciona.

### Exemplo 2: Dashboard com Gráfico por Categoria (implementado)

A UI (`DashboardScreen`) coleta `DashboardViewModel.uiState`, que combina:
- `GetMonthlyBalanceUseCase(period)` → saldo, receitas e despesas do mês.
- `GetTransactionsByMonthUseCase(period)` → totais de receitas e despesas do mês.
- `GetCategorySummariesUseCase(period)` → composição por categoria, renderizada por `CategoryUsageChart` (barra de composição horizontal + legenda, sem dependência de lib externa de gráficos) na Home.

### Exemplo 3: Chat com IA Local (implementado)

`ChatScreen` (segunda página do `HomeChatPager`, à direita da Home)
  → `ChatViewModel`
  → `SendChatMessageUseCase(message)`:
    1. monta o `FinancialContext` a partir dos dados já persistidos (via `GetMonthlyBalanceUseCase`/`GetCategorySummariesUseCase`), nunca enviado para fora do dispositivo;
    2. persiste a mensagem do usuário via `ChatRepository.addMessage`;
    3. chama `LocalAiRepository.sendMessage(message, context)` e persiste a resposta via `ChatRepository.addMessage`;
    4. retorna a resposta.
  → `GetChatHistoryUseCase` expõe o histórico persistido (`ChatMessageEntity`), coletado por `ChatViewModel.uiState` e renderizado pela `ChatScreen`. A resposta é gerada de forma síncrona (sem streaming) pelo stand-in atual de `LocalAiRepository`.

### Exemplo 4: Listagem de Transações (existente)

A UI coleta `TransactionsViewModel.uiState`. O ViewModel observa o período selecionado e combina os fluxos de transações e categorias:

`TransactionsViewModel`
  → `GetTransactionsByMonthUseCase(period)`
  → `TransactionsRepository.getTransactions(period)`
  → `RoomTransactionsRepository`
  → `TransactionDao.getTransactionsByDateRange(startDate, endDate)`
  → `Room Database`

O repositório converte entidades em modelos de domínio via mappers. O ViewModel combina esses dados com `CategoriesRepository.getCategories()` e expõe o estado à UI. A coleta do fluxo de transações usa `SharingStarted.WhileSubscribed(5_000)`.

---

## Regras e Convenções Inegociáveis

1. **Separação de Camadas:** a UI nunca acessa o DAO. O Domínio nunca conhece o Banco de Dados, o parser de extrato concreto ou o motor de IA concreto — apenas suas interfaces (`StatementParserRepository`, `LocalAiRepository`).
2. **Tratamento de Moeda:** nunca use `Double` ou `Float` para valores monetários. Use sempre a classe `Money`.
3. **Eventos Únicos:** eventos de navegação ou mensagens rápidas (Toast/Snackbar) devem ser tratados como eventos únicos (ex: via `LaunchedEffect`), não como estado persistente.
4. **Imutabilidade:** os estados da UI e modelos de domínio devem ser preferencialmente classes de dados imutáveis (`data class` com `val`).
5. **IA e dados 100% on-device:** nenhuma informação financeira do usuário (extratos, transações, mensagens de chat) pode ser enviada pela rede. Qualquer implementação de `LocalAiRepository` deve rodar inteiramente no dispositivo; a ausência da permissão `INTERNET` no `AndroidManifest.xml` é um invariante do projeto, não apenas uma configuração atual.

---

A implementação atual usa campos de estado para sucesso/erro e `LaunchedEffect` para reagir a eles. A tela de configurações limpa as mensagens após exibição; isso não comprova entrega única em todas as telas, como orienta a regra acima.

## Como Adicionar uma Nova Feature

1. **Domínio:**
   - Crie o modelo se necessário.
   - Defina o contrato do repositório.
   - Crie o(s) Use Case(s).
2. **Dados:**
   - Crie/Atualize as `Entity` do Room.
   - Adicione os métodos no `DAO`.
   - Implemente o repositório na camada *Data*.
   - Registre no `AppContainer`.
3. **Apresentação:**
   - Crie o `UiState` e o `ViewModel`.
   - Registre o `ViewModel` no `AppViewModelProvider`.
4. **UI:**
   - Implemente os componentes Compose e a tela.
   - Se a tela pertence ao fluxo secundário (não Home/Chat), adicione a rota em `FinancesRoute` e configure no `FinancesNavHost`. Se afetar a Home ou o Chat, ajuste a página correspondente do `HorizontalPager` raiz.

---

## Como Criar uma Nova Migration do Room

O banco está na versão **2**. A migração `MIGRATION_1_2` (em `AppDatabase.kt`) já adicionou:
- `StatementImportEntity` (histórico de importações de extrato).
- `ChatMessageEntity` (histórico de conversas com a IA local).
- A coluna `origin` (`MANUAL` / `IMPORTED`) em `TransactionEntity`.

`MigrationTest.kt` cobre tanto a criação da v1 quanto a migração v1 → v2 (via `MigrationTestHelper.runMigrationsAndValidate`). O schema exportado em `app/schemas` é incluído nos assets de `androidTest`, e o CI executa o teste em um emulador.

Passo a passo geral para a **próxima** migration (ex: v2 → v3):

1. **Exportar Schema:** certifique-se de que o schema atual está versionado na pasta `app/schemas`.
2. **Atualizar Banco:** altere a versão em `AppDatabase.kt` (ex: `version = 3`).
3. **Criar Migration:** siga o padrão de `MIGRATION_1_2` em `AppDatabase.kt` — um objeto `Migration(oldVersion, newVersion)` com os `execSQL` necessários (colunas novas, novas tabelas etc.).
4. **Registrar:** adicione a nova migration ao `.addMigrations(...)` no builder do banco.
5. **Testar:** adicione um caso de teste em `MigrationTest.kt` usando o `MigrationTestHelper`, cobrindo a nova transição de versão.

---

## Injeção de Dependências
Utilizamos **Manual Dependency Injection** através do `AppContainer` inicializado na classe `MainApplication`. Isso mantém o projeto simples, sem o overhead de bibliotecas como Dagger/Hilt, mas permitindo fácil substituição de implementações para testes: cada teste de use case/ViewModel define suas próprias implementações fake das interfaces de repositório (`FakeTransactionsRepository`, `FakeCategoriesRepository`, `FakeLocalAiRepository`, `FakeChatRepository`, `FakeStatementImportRepository` etc.) como classes privadas no próprio arquivo de teste — não há repositórios fake compartilhados em `data/repository`. `ChatViewModelTest` e `ImportStatementViewModelTest` já seguem esse padrão.
