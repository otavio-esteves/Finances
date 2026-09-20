# Plano de Implementação: Motor de IA Local

> **Status:** aprovado em 2026-09-18. Premissas da seção 0 verificadas contra
> o código (duas corrigidas — ver anotações). As cinco perguntas da seção 9
> foram respondidas e estão registradas ali. Fase 1 (contrato de domínio),
> Fase 2a (provisionamento via import manual) e a parte não bloqueada da Fase
> 2b (flavors + CI + teste do invariante de rede) já foram implementadas
> nesta mesma revisão — ver os status em cada fase na seção 5. **A parte
> restante da Fase 2b (asset pack real, dependência do Play Core, resolução
> de fonte por flavor) está genuinamente bloqueada** por insumos que só o
> usuário tem: conta no Play Console, keystore de assinatura, e o bundle real
> do modelo — ver o status da Fase 2b.
> **Escopo:** substituir o stand-in `RuleBasedLocalAiRepository` por um motor
> de inferência on-device real, preservando o invariante de que nenhum dado
> financeiro sai do aparelho — e sem declarar permissão de internet.

---

## 0. Premissas verificadas

Este plano foi escrito a partir do `README.md`. Os itens abaixo foram
confirmados lendo o código em 2026-09-18. Duas precisaram de correção — o
resto do documento já reflete o estado real.

| # | Premissa | Resultado |
|---|---|---|
| P1 | Existe uma interface de domínio para IA e `RuleBasedLocalAiRepository` é sua única implementação. | ✅ Confirmada. `LocalAiRepository` era a única interface; `RuleBasedLocalAiRepository`, a única implementação. |
| P2 | `SynthesizeStatementUseCase` é o único ponto que chama a categorização por IA. | ✅ Confirmada. |
| P3 | `ChatViewModel` fala com a IA através da mesma interface, não de uma segunda. | ✅ Confirmada. |
| P4 | As chamadas de IA hoje são síncronas ou `suspend` simples, sem streaming. | ✅ Confirmada. |
| P5 | `ChatRepository` já persiste histórico, e o chat não recebe hoje nenhum contexto das transações. | ⚠️ **Parcialmente errada.** O histórico persiste, mas o chat **já recebe contexto** hoje: `SendChatMessageUseCase` monta um `FinancialContext` (saldo + resumo por categoria do mês corrente) e passa para `sendMessage`. Não há RAG nem granularidade por transação — mas não é "zero contexto". A Fase 5 (seção 5) é uma **expansão** desse contexto, não a introdução dele. |
| P6 | O manifest não declara `android.permission.INTERNET`, nem diretamente nem por merge de alguma dependência. | ✅ Confirmada por inspeção do manifest e da lista de dependências (nenhuma delas — Room, Compose, navigation, serialization, desugar — adiciona permissão de rede). O merge real de um build de release não foi executado (ambiente de verificação sem toolchain completa de build); confirmar com um build real antes de fechar a Fase 2b. |
| P7 | A categorização atual devolve uma categoria por transação, sem score de confiança. | ❌ **Errada.** `CategorySuggestion` já tinha `confidence: Float` (validado em 0..1), populado pelo `RuleBasedLocalAiRepository` (0.8 / 0.3 / 0). O que era de fato novo na seção 3 é `source: SuggestionSource` (AI/RULE/USER) — isso sim foi adicionado na Fase 1. `transactionRef`/`RawTransaction` da seção 3 original não foram adotados; ver nota na seção 3. |

Como P1 e P2 se confirmaram, a Fase 1 não cresceu como o plano previa que
cresceria se elas falhassem.

---

## 1. Decisão técnica

**Motor:** LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`).

A API MediaPipe LLM Inference, citada como candidata no README, entrou em modo
manutenção — o Google direciona novos projetos ao LiteRT-LM, que é a camada de
orquestração sobre o LiteRT e a mesma infraestrutura que roda o Gemini Nano no
Chrome e no Pixel Watch. A tabela "Decisão em Aberto" do README deve ser
atualizada para refletir isso (Fase 7).

**Modelo padrão:** Gemma 4 E2B int4, formato `.litertlm`, licença Apache 2.0.

**Descartado por ora:** ML Kit GenAI / Gemini Nano. A lista de aparelhos
compatíveis ainda é restrita e o SDK `genai-prompt` está em beta. Fica
registrado como caminho futuro — a Structured Output API anunciada é
interessante para a categorização —, mas não como fase deste plano.

### 1.1 Duas tarefas, não uma

A separação abaixo é a decisão arquitetural central do plano:

| | Categorização | Chat |
|---|---|---|
| Quando roda | Batch, na importação | Interativo, usuário esperando |
| Entrada | Lote de transações cruas | Pergunta + contexto recuperado |
| Saída | JSON estruturado | Texto em streaming |
| Tolerância a latência | Alta (com progresso visível) | Baixa |
| Modelo | Pode ser o maior | Pode precisar ser menor |

Motivo: em benchmark público recente, gemma-4-e4b-q4 teve a melhor qualidade
(94% de acerto, menor índice de correção) com ~34 s de latência média por
requisição; o e2b-q4 ficou em ~20 s; o LFM2-1.2B em ~2 s. São números de outro
hardware e outro prompt — servem como ordem de grandeza, não como medição
nossa. Mas bastam para justificar que o chat e a categorização **não precisam
compartilhar o mesmo modelo**, e que a escolha do modelo do chat só se fecha
depois da Fase 4.

### 1.2 Candidatos

| Modelo | Papel |
|---|---|
| **Gemma 4 E2B int4** | Padrão. Apache 2.0, ~5 GB RAM em 4 bits, 128K contexto, function calling e JSON estruturado nativos, 140+ idiomas. |
| **Qwen3 1.7B / Qwen3.5 0.8B** | Alternativa para categorização em batch. Apache 2.0, boa em classificação e extração, footprint bem menor. |
| **LFM2.5-1.2B** | Candidato ao chat se o E2B se mostrar lento demais no aparelho-alvo. |
| **Gemma 3n E2B** | Geração anterior, ainda suportada. Fallback testado. |
| **Phi-4-mini 3.8B** | MIT, forte em raciocínio, português mais fraco. Não é padrão. |

---

## 2. Entrega do modelo — decidido

**Decisão: Play Asset Delivery (install-time) como caminho principal, import
manual via SAF como caminho secundário. O app continua sem declarar
`android.permission.INTERNET`.**

### 2.1 O impasse

O README promete que o app não declara permissão de internet, e trata isso como
garantia estrutural de privacidade — não como promessa de código. Mas o modelo
pesa centenas de MB e o limite do módulo base no Google Play é de **200 MB de
download comprimido**; a própria documentação do Google diz que o modelo é
grande demais para ir empacotado num APK. Alguma coisa tem que ceder.

### 2.2 Play Asset Delivery resolve sem ceder nada

Um asset pack individual pode ter até **1,5 GB**, com teto cumulativo de **4 GB**
para o módulo base mais os asset packs install-time. Em modo *install-time*, o
pacote vem junto da instalação: quando o app abre pela primeira vez, o modelo já
está no disco.

Quem faz o download é a Play Store, não o app. Consequências:

- **O manifest continua sem `INTERNET`.** A garantia segue sendo estrutural — o
  processo não tem como abrir um socket, e isso é verificável por qualquer
  pessoa que leia o manifest. Este é o argumento mais forte do produto e o plano
  não o troca por conveniência.
- **Zero fricção.** Sem tela de onboarding de download, sem seletor de arquivo,
  sem estado "baixando" no caminho feliz. Instalou, funciona.
- **Integridade vem de graça.** A Play assina e verifica o pacote. A verificação
  manual de SHA-256 deixa de ser necessária *neste caminho* (continua sendo no
  caminho 2.3).

### 2.3 Import manual via SAF — sideload e modelos à gosto

O caminho PAD só existe para quem instalou pela Play. Isso deixa dois casos
descobertos, e ambos são atendidos pelo mesmo mecanismo:

1. **Sideload e debug.** O CI hoje publica `app-debug.apk` direto do GitHub.
   Nesse caminho não há asset pack.
2. **Usuário que quer outro modelo.** Trocar o Gemma 4 E2B por um Qwen3 1.7B
   menor, ou por qualquer outro bundle `.litertlm` que ele tenha.

O app abre a página do modelo via `Intent.ACTION_VIEW` — o navegador faz o
download, não nós — e o usuário seleciona o arquivo pelo seletor de arquivos do
Android, exatamente como já acontece na importação de extrato. É o mesmo fluxo
que o Google AI Edge Gallery passou a suportar para importar bundles.

Um modelo importado assim **tem precedência sobre o modelo do asset pack**. Isso
é o que entrega "modelos à gosto" sem nenhuma linha de código de rede.

### 2.4 Por que não declarar `INTERNET`

Foi considerado e recusado. Declarar a permissão simplificaria a Fase 2 e
permitiria um seletor de modelos com download embutido — mais bonito. O custo é
que a garantia central do produto deixa de ser verificável de fora e vira "confie
no nosso código". Num app cuja tese inteira é privacidade de dados bancários,
essa troca não vale a economia de trabalho. E o par PAD + SAF entrega o mesmo
resultado funcional, inclusive a escolha de modelo pelo usuário.

Registro da alternativa, caso a decisão mude: seria a permissão `INTERNET` mais
uma `NetworkSecurityConfig` restringindo o tráfego aos domínios de distribuição
do modelo, com o README reescrito para descrever a garantia em termos de código
auditável em vez de ausência de permissão. Não é o caminho deste plano.

### 2.5 Consequências de build

- O artefato de produção passa a ser **AAB**, não APK. O asset pack exige App
  Bundle; PAD é exclusivo do Google Play. **Decidido:** o app vai para a Play
  Store (ver seção 9), então este caminho segue como escrito.
- **Dois fluxos de build.** Recomendação: um *product flavor* `play` (com o asset
  pack) e um `standalone` (sem ele, dependendo só do import via SAF). O código do
  provisionamento é o mesmo; muda só a fonte inicial do bundle.
- **Apps acima de 1 GB têm exigências adicionais de target de API** na Play.
  Conferir antes de fechar o tamanho do asset pack.
- Install-time **não é condicional por RAM**: um aparelho incapaz baixa o pacote
  e não usa. Se isso incomodar, a saída é entrega *fast-follow* ou *on-demand*
  após a checagem de capacidade — ao custo de reintroduzir o estado "baixando" e,
  no caso do on-demand, de depender da API do Play Core em runtime. Decisão
  adiada para depois da Fase 2, com dado real de quantos usuários caem no
  `Unsupported`.

---

## 3. Contrato

Camada de domínio, sem qualquer referência a LiteRT-LM.

> **Nota de execução (Fase 1, 2026-09-18):** o código implementado reaproveita
> os tipos de domínio já existentes (`RawStatementEntry`, `Category`,
> `CategorySuggestion`) em vez de introduzir `RawTransaction`/`TransactionRef`
> como um segundo `CategorySuggestion`. Motivo: `CategorySuggestion.entry` e
> `.suggestedCategory` já são consumidos diretamente pelo `ImportStatementViewModel`,
> `ImportStatementScreen` e `ConfirmStatementImportUseCase`; migrar esses call
> sites para uma referência leve (`TransactionRef`) é um refactor de UI real,
> não um contrato de domínio isolado, e não tinha por que acontecer numa fase
> cujo aceite é "app se comporta exatamente como antes". Fica registrado como
> trabalho da Fase 3, quando o `LiteRtTransactionCategorizer` de fato existir e
> todos os call sites forem tocados com teste de ponta a ponta junto. O campo
> `confidence` também não era novo — já existia (ver P7). O único campo
> realmente adicionado ao modelo existente foi `source: SuggestionSource`.
>
> `FinancialChat`/`ChatChunk` (streaming) também não foram introduzidos na Fase
> 1: nada os consome antes da Fase 5, e adicionar uma interface sem
> implementação nem chamador é dívida, não contrato. Ficam para quando o RAG
> for de fato conectado.

```kotlin
// domain/ai/LocalAiEngine.kt

sealed interface AiEngineState {
    data object NotProvisioned : AiEngineState          // modelo ausente
    data class Provisioning(val progress: Float) : AiEngineState
    data class Ready(val model: InstalledModel) : AiEngineState
    data class Unsupported(val reason: UnsupportedReason) : AiEngineState
    data class Failed(val cause: AiError) : AiEngineState
}

data class InstalledModel(
    val id: String,
    val displayName: String,
    val source: ModelSource,
    val verified: Boolean       // hash bate com um bundle conhecido
)

/** De onde veio o bundle. Ver seção 2. */
enum class ModelSource { ASSET_PACK, USER_IMPORTED }

enum class UnsupportedReason { INSUFFICIENT_RAM, UNSUPPORTED_ABI, NO_STORAGE }

sealed interface AiError {
    data object ModelNotLoaded : AiError
    data object OutOfMemory : AiError
    data object Timeout : AiError
    data class MalformedOutput(val raw: String) : AiError   // JSON inválido
    data class Unknown(val throwable: Throwable) : AiError
}

interface LocalAiEngine {
    val state: StateFlow<AiEngineState>
    suspend fun warmUp(): Result<Unit>
    suspend fun release()
}
```

**Implementado como especificado** em `domain/ai/LocalAiEngine.kt`, com
`data/ai/NotProvisionedLocalAiEngine.kt` como a implementação permanentemente
`NotProvisioned` até a Fase 3, exposta via `AppContainer.localAiEngine`.

```kotlin
// domain/ai/TransactionCategorizer.kt (assinatura real, ver nota acima)

interface TransactionCategorizer {
    suspend fun categorize(
        entries: List<RawStatementEntry>,
        categories: List<Category>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<CategorySuggestion>>
}
```

```kotlin
// domain/model/CategorySuggestion.kt (existente, com o campo novo)

enum class SuggestionSource { AI, RULE, USER }

data class CategorySuggestion(
    val entry: RawStatementEntry,
    val suggestedCategory: Category?,
    val confidence: Float,
    val source: SuggestionSource = SuggestionSource.RULE
)
```

```kotlin
// domain/ai/FinancialChat.kt — AINDA NÃO IMPLEMENTADO, ver nota acima. Fica
// registrado aqui como o contrato-alvo da Fase 5.

interface FinancialChat {
    fun ask(
        question: String,
        history: List<ChatMessage>
    ): Flow<ChatChunk>               // streaming token a token
}

sealed interface ChatChunk {
    data class Token(val text: String) : ChatChunk
    data class Done(val fullText: String) : ChatChunk
    data class Error(val error: AiError) : ChatChunk
}
```

**Notas de design:**

- `confidence` é o que permite a UI destacar o que precisa de revisão humana na
  tela de importação. Sem isso o fallback fica cego.
- `source` permite mostrar ao usuário o que veio de IA e o que veio de regra —
  honestidade de interface, e útil para depurar. `ImportStatementViewModel.onCategorySelected`
  já marca `SuggestionSource.USER` quando o usuário escolhe manualmente.
- Separar `TransactionCategorizer` de `FinancialChat` é o que torna possível
  usar modelos diferentes para cada um sem refatoração posterior.
- `Result<T>` em vez de exceção: erro de inferência é esperado, não excepcional.
  `SynthesizeStatementUseCase` desembrulha com `getOrThrow()` na fronteira do
  caso de uso, preservando o comportamento atual (falha de importação continua
  virando exceção capturada pela ViewModel).
- `ModelSource` e `verified` existem para a UI poder ser honesta: um modelo que
  o usuário importou por conta própria não tem a mesma garantia de qualidade que
  o que vem no asset pack, e a tela de configurações deve dizer isso.

---

## 4. Estratégia de modelo

**Armazenamento:** o bundle do asset pack é lido do caminho que o Play Core
expõe, sem cópia — duplicar 1 GB em `filesDir` é desperdício. Modelo importado
pelo usuário é copiado para `filesDir` e a URI de origem é descartada. Em ambos
os casos, conferir que `backup_rules.xml` e `data_extraction_rules.xml` excluem
o caminho; se não, incluir.

**Resolução de qual modelo usar**, nesta ordem:

1. Modelo importado pelo usuário, se houver (`USER_IMPORTED`)
2. Modelo do asset pack, se o flavor for `play` e o pacote estiver presente
3. Nenhum → `NotProvisioned` → fallback por regras

**Integridade:**

- `ASSET_PACK`: assinado e verificado pela Play. Sem verificação adicional.
- `USER_IMPORTED`: SHA-256 calculado na importação e comparado contra uma lista
  de hashes conhecidos embutida no app (os bundles que testamos, alimentada
  pelos modelos avaliados na Fase 4). Bate → `verified = true`. Não bate →
  **não é rejeitado**, mas entra como `verified = false`, com aviso explícito
  de que é um modelo não testado e de que a qualidade da categorização não é
  garantida. Rejeitar seria impedir o "modelos à gosto"; aceitar em silêncio
  seria desonesto.
- Em ambos os casos, o `warmUp()` é o teste real: bundle corrompido ou de
  formato incompatível falha ali e vira `Failed`.

**Detecção de capacidade**, antes de tentar carregar:

1. `ActivityManager.MemoryInfo.totalMem` e `isLowRamDevice`
2. ABI suportada (`Build.SUPPORTED_ABIS`)
3. Espaço livre em disco, com margem
4. Resultado de um `warmUp()` real — o teste definitivo

Falhou qualquer um → `Unsupported` → fallback por regras, sem prometer o que o
aparelho não entrega e sem oferecer o import manual (que também não
funcionaria).

**Fallback: decidido como permanente** (seção 9). `RuleBasedLocalAiRepository`
**não é código descartável** — já implementa `TransactionCategorizer` em
definitivo (Fase 1) e continuará sendo usado quando o estado for
`NotProvisioned`, `Unsupported` ou `Failed`, e também por transação individual
quando a IA devolver `confidence` abaixo de um limiar. Aparelhos incompatíveis
continuam funcionando, com qualidade de categorização menor, para sempre — não
é um estado transitório a ser removido depois. Para o chat não há fallback por
regras equivalente a partir da Fase 5: com o motor indisponível, a tela de chat
informa isso honestamente em vez de simular uma resposta. Isso não contradiz o
que existe hoje (Fase 1) — `LocalAiRepository.sendMessage` segue com as
respostas por regra até a Fase 5 trocar o contrato de chat para o real
`FinancialChat` com RAG.

---

## 5. Fases

Cada fase é entregável e testável sozinha. Nenhuma delas deixa o app quebrado.

### Fase 0 — Baseline e instrumentação
Medir o que existe antes de mudar: tempo da categorização por regras num
extrato real, tamanho do APK, uso de memória na importação.
**Aceite:** números registrados no plano; script ou teste que os reproduz.
**Status:** não iniciada — o ambiente usado para a revisão deste plano só tem
o JRE do OpenJDK instalado (sem `javac`), então nenhum build Gradle roda nele.
Precisa ser feita numa máquina com toolchain completa antes de prosseguir para
a Fase 2.

### Fase 1 — Contrato, sem motor
Introduzir as interfaces da seção 3 que já têm consumidor (`TransactionCategorizer`,
`LocalAiEngine`); `FinancialChat` fica para a Fase 5 (ver nota da seção 3).
`RuleBasedLocalAiRepository` passa a implementar `TransactionCategorizer` com
`source = RULE`, mantendo `confidence` graduado como já calculava.
`AppContainer` passa a injetar `SynthesizeStatementUseCase` pelo novo contrato.
Estado `NotProvisioned` permanente via `NotProvisionedLocalAiEngine`.
**Aceite:** app se comporta exatamente como antes; testes existentes passam;
nenhuma dependência nova no Gradle.
**Status: implementada em 2026-09-18.** Arquivos novos:
`domain/ai/LocalAiEngine.kt`, `domain/ai/TransactionCategorizer.kt`,
`data/ai/NotProvisionedLocalAiEngine.kt`, `domain/model/SuggestionSource.kt`.
Alterados: `CategorySuggestion` (+`source`), `LocalAiRepository` (perdeu
`suggestCategories`, ficou só com `sendMessage`), `RuleBasedLocalAiRepository`
(implementa as duas interfaces agora), `SynthesizeStatementUseCase` (recebe
`TransactionCategorizer`), `AppContainer` (expõe `transactionCategorizer` e
`localAiEngine`), `ImportStatementViewModel` (marca `SuggestionSource.USER` na
seleção manual). Testes atualizados nos quatro arquivos que tinham fakes de
`LocalAiRepository` para categorização. **Não verificado por build real** —
mesma limitação de toolchain da Fase 0; rodar `./gradlew test` numa máquina com
JDK completo antes de prosseguir.

### Fase 2a — Provisionamento via import manual
O caminho secundário vem primeiro, porque não depende de build de AAB nem de
conta na Play e portanto é testável no mesmo dia. Tela de gerenciamento de
modelo em configurações, `Intent.ACTION_VIEW` para a página do bundle, seleção
via SAF, cópia para `filesDir`, cálculo de SHA-256, detecção de capacidade,
estados na UI. Ainda sem inferência.
**Aceite:** usuário importa um `.litertlm` e vê o estado virar `Ready`; consegue
remover e reimportar; bundle de outro formato falha no `warmUp` e vira `Failed`
com mensagem clara; modelo fora da lista de hashes conhecidos entra como
`verified = false` com o aviso visível; aparelho incapaz nunca chega a oferecer
o import.
**Status: implementada em 2026-09-18.** Arquivos novos:
`domain/ai/ModelImporter.kt`, `domain/ai/DeviceCapabilityPolicy.kt` (lógica
pura de capacidade — RAM, ABI, armazenamento — com teste JVM),
`data/ai/AndroidDeviceCapabilityGate.kt` (coleta os sinais do aparelho e
delega ao policy), `data/ai/ImportedModelLocalAiEngine.kt` (implementa
`LocalAiEngine` + `ModelImporter`; substitui e remove o
`NotProvisionedLocalAiEngine` da Fase 1), `presentation/aimodel/AiModelViewModel.kt`,
`ui/screens/aimodel/AiModelScreen.kt`, `utils/DocumentUriUtils.kt` (helper de
nome de arquivo extraído de `ImportStatementScreen` para reuso). Nova rota
`FinancesRoute.AiModel`, acessível por um botão em Configurações.

Desvios e pendências conscientes:
- **`warmUp()` não é um teste de inferência real** — é um "isso parece um
  bundle" (arquivo existe e tem pelo menos 10 MB). A validação de formato de
  verdade só existe quando o LiteRT-LM real for integrado na Fase 3; até lá,
  um arquivo grande o bastante mas com conteúdo arbitrário passaria. Aceitável
  para esta fase porque não há motor real para enganar ainda.
- **`KNOWN_MODEL_HASHES` está vazio.** Nenhum modelo foi testado ainda —
  qualquer import legítimo hoje entra como `verified = false` por definição,
  até a Fase 4 alimentar essa lista com os hashes dos modelos medidos.
- **Thresholds de capacidade (6 GB RAM, 2 GB de armazenamento livre,
  `arm64-v8a` obrigatório) são heurísticas de primeira passada**, não medidos
  em aparelho real — mesma ressalva da seção 4 e do risco já listado na
  seção 8.
- **`MODEL_DOWNLOAD_PAGE_URL` em `AiModelScreen.kt` está vazia de propósito.**
  Não há uma URL de download oficial confirmada para preencher com confiança;
  o botão correspondente fica oculto até alguém preencher essa constante. O
  caminho de import por SAF funciona independentemente disso.
- **Não verificado por build real** — mesma limitação de toolchain das fases
  anteriores (seção 8). Este é o maior risco acumulado: três fases de código
  Android novo (Fase 1 + Fase 2a) sem nunca ter rodado `./gradlew build` ou
  `./gradlew test`. Rodar isso numa máquina com JDK completo é o próximo passo
  obrigatório antes de prosseguir para a Fase 2b.

### Fase 2b — Asset pack e flavors
Migrar o build de produção para AAB, criar o asset pack install-time com o Gemma
4 E2B, criar os flavors `play` e `standalone`, e ligar a resolução de fonte da
seção 4. O CI continua produzindo `app-debug.apk` no flavor `standalone`.
**Decidido (seção 9): o app vai para a Play Store**, então esta fase segue como
planejada, sem condicional.
**Aceite:** build `play` gera AAB com o asset pack dentro do limite; instalação
por internal testing track deixa o app `Ready` na primeira abertura, sem nenhuma
tela de download; build `standalone` continua funcionando só com o import manual;
manifest de ambos segue sem `INTERNET` — isso vira um teste automatizado.

**Status: parcialmente implementada em 2026-09-18.** O que dava para fazer sem
insumos externos:
- `flavorDimensions`/`productFlavors` (`standalone`, `play`) em `app/build.gradle.kts`
  — hoje idênticos em comportamento, é só a divisão de build.
- CI (`android-ci.yml`) e `README.md` atualizados para os nomes de tarefa com
  flavor (`assembleStandaloneDebug` etc.) — sem isso, `./gradlew assembleDebug`
  ainda funcionaria (é uma tarefa agregadora), mas o caminho do APK e os nomes
  de tarefa citados na documentação teriam ficado errados.
- `androidTest/.../NetworkPermissionInvariantTest.kt` — o "teste automatizado"
  do aceite acima. Não dá para inspecionar o manifest *merged* num teste JVM
  (isso é uma etapa de build, não de runtime); em vez disso, o teste lê as
  permissões do pacote já instalado via `PackageManager` no aparelho/emulador
  — testa o resultado real do merge, incluindo o de qualquer dependência
  transitiva, e roda no `connectedStandaloneDebugAndroidTest` que o CI já
  executa.

**Genuinamente bloqueado, não meramente adiado** — precisa de insumos que só
o usuário tem, e não deveria ser adivinhado:
- **Conta no Google Play Console** com o app registrado (necessária para
  Play Asset Delivery existir de fato — ver seção 9, pergunta 2).
- **Keystore de assinatura de release** — hoje o projeto não assina builds de
  release; um AAB para o Play Console precisa disso.
- **O bundle real do modelo** (Gemma 4 E2B `.litertlm`) para popular o asset
  pack — um arquivo de centenas de MB a ~1,5 GB não deveria ir para o
  histórico do git.
- A partir desses três, o trabalho real: criar o módulo `:model_pack` com o
  plugin `com.android.asset-pack`, adicionar a dependência do Play Core
  (`com.google.android.play:feature-delivery` ou equivalente vigente na época),
  e implementar a resolução de fonte da seção 4 (`USER_IMPORTED` > `ASSET_PACK`
  > nenhum) como uma nova implementação de `LocalAiEngine` usada só no flavor
  `play`. Nenhuma dessas peças foi escrita — eu poderia ter fabricado algo que
  parecesse plausível a partir de memória de treinamento sobre a API do Play
  Core, mas sem conseguir compilar nem ter um app real no Play Console para
  testar contra, o risco de produzir algo silenciosamente errado (e só
  descoberto na hora de publicar) é alto demais para valer a pena.

### Fase 3 — Categorização com LiteRT-LM
Implementar `LiteRtTransactionCategorizer`. Prompt em pt-BR, saída JSON
estruturada, parsing defensivo, retry único em `MalformedOutput`, chunking do
lote, fallback por regras transação a transação abaixo do limiar de confiança.
Este é o ponto natural para revisitar a nota da seção 3 sobre `TransactionRef`
se o volume de transações por lote justificar não carregar `RawStatementEntry`
inteiro em memória para o motor.
**Aceite:** extrato de amostra categorizado com qualidade mensuravelmente
melhor que as regras; JSON malformado não derruba a importação; cancelar a
importação no meio libera a memória do motor.

**Status: implementada em 2026-09-18, com uma ressalva importante.** Ao
contrário das fases anteriores, esta foi escrita usando uma API real mas
pouco familiar (`com.google.ai.edge.litertlm`), verificada por pesquisa (docs
oficiais do Google AI Edge + `maven-metadata.xml` real em `dl.google.com` para
a versão, `0.17.1` — pinada em vez de `latest.release`, ver risco na seção 8),
não por compilação. Arquivos novos:
`data/ai/LiteRtTransactionCategorizer.kt` (toda a superfície da biblioteca
fica isolada aqui — ver risco da seção 8), `data/ai/EngineAwareTransactionCategorizer.kt`
(escolhe IA vs. regra por chamada, lendo o estado atual do motor — testado em
JVM sem nenhuma dependência do LiteRT-LM, `EngineAwareTransactionCategorizerTest.kt`).
`InstalledModel` (seção 3) ganhou um campo `path` para o categorizador saber
de onde carregar o bundle.

**Onde a incerteza está concentrada** (documentado também no comentário de
classe do arquivo, para quem for revisar no Android Studio):
- A chamada `conversation.sendMessage(prompt).toString()` é o ponto mais
  arriscado do arquivo. A documentação oficial mostra `println(conversation.sendMessage(...))`
  diretamente, o que sugere fortemente que o retorno tem um `toString()` com o
  texto da resposta, mas isso não foi confirmado contra a classe `Message`
  real. Se o parsing de JSON falhar sistematicamente mesmo com bundle e prompt
  corretos, esta é a primeira linha a conferir — talvez `Message` tenha uma
  propriedade `.text` ou `.content` que seja o acessor certo.
- Não usei o `responseSchema`/`enableStructuredOutput` (saída JSON garantida
  por decodificação restrita) que a biblioteca aparentemente suporta, porque
  não consegui confirmar a forma exata da API. Fiz por instrução de prompt +
  parsing defensivo + retry, que é exatamente o plano B que a seção 3 já
  prevê. Migrar para `responseSchema` mais tarde é uma otimização de
  qualidade, não um bloqueador.
- `engine.initialize()` e `engine.createConversation(config)` foram tratados
  como possivelmente `suspend`; como tudo já roda dentro de
  `withContext(Dispatchers.IO)`, o código funciona de qualquer jeito — mas se
  não forem `suspend`, o Android Studio pode acusar isso como aviso, não erro.
- Não integrei `Backend.GPU()`/`Backend.NPU(...)` (aceleração de hardware) —
  fica de fora até a Fase 4 medir se é necessário.

**Ainda não verificado por build real** — mesma limitação de todas as fases
anteriores. Esta é a fase onde isso mais importa: peço explicitamente que
você rode `./gradlew testStandaloneDebugUnitTest` (cobre o router e a lógica
pura) e depois compile o app de verdade no Android Studio antes de confiar em
`LiteRtTransactionCategorizer` — os testes JVM não tocam a biblioteca real.

### Fase 4 — Medição e escolha do modelo do chat
**Decidido (seção 9): medir nos dois extremos de hardware** — um aparelho
topo de linha recente e um intermediário de ~4 GB de RAM — antes de fechar o
modelo do chat. Rodar o Gemma 4 E2B nos dois e medir latência de prefill e
decode com prompts do tamanho que o chat vai usar. Comparar com Qwen3 1.7B e
LFM2.5-1.2B se o E2B não couber no orçamento de latência no aparelho
intermediário.
**Aceite:** decisão do modelo do chat registrada com os números que a
sustentam, para os dois aparelhos. Esta fase pode reverter a escolha da seção
1 — é o objetivo dela. Os hashes dos modelos testados aqui alimentam a lista
de bundles conhecidos da seção 4.

### Fase 5 — Chat com RAG
Introduzir o contrato `FinancialChat`/`ChatChunk` da seção 3 (ainda não
implementado — ver nota) e conectar `ChatViewModel`/`SendChatMessageUseCase` a
ele no lugar do `LocalAiRepository.sendMessage` atual. Expandir o
`FinancialContext` de hoje (saldo + resumo por categoria do mês corrente, ver
P5) para recuperação de verdade: filtro por período e categoria sobre as
transações já categorizadas no Room antes de qualquer coisa mais sofisticada
— provavelmente basta —, montagem de contexto com orçamento de tokens,
streaming via `Flow<ChatChunk>`, cancelamento ao sair da tela.
**Decidido (seção 9): chat continua somente leitura** — não escreve nem
corrige categorias. O contrato acima não precisa de comandos de escrita.
**Aceite:** "quanto gastei com mercado em fevereiro?" responde corretamente a
partir dos dados reais (hoje só responde sobre o mês corrente); primeiro token
em tempo aceitável; girar a tela no meio da resposta não vaza o motor nem
duplica a requisição.

**Status: versão reduzida implementada em 2026-09-19, sem substituir esta
fase.** `data/ai/LiteRtLocalAiRepository.kt` (+ `EngineAwareLocalAiRepository.kt`)
faz o chat chamar o motor LiteRT-LM já importado, testado num S21 físico
(import do modelo confirmado; resposta de ponta a ponta ainda não confirmada
na sessão que escreveu isto). O que falta desta fase continua em aberto:
contrato `FinancialChat`/`ChatChunk`, streaming, RAG de verdade (a versão
atual só usa saldo + resumo por categoria do `FinancialContext` de hoje, sem
retrieval por transação), engine "morno" entre mensagens (hoje recarrega o
bundle inteiro a cada mensagem — inaceitável em latência), e a medição da
Fase 4 (que ainda não rodou).

### Fase 6 — Degradação, ciclo de vida e limpeza
Liberar o motor em background, reagir a `onTrimMemory`, timeout global,
descarte seguro do extrato original após a importação (já está no roadmap de
segurança), revisão do R8 com as regras que o LiteRT-LM exigir.
**Aceite:** app não é morto por OOM num aparelho de 4 GB; extrato original não
persiste depois do fluxo; build de release funciona com minificação.

### Fase 7 — Documentação
Atualizar `README.md` (a tabela "Decisão em Aberto" sai; o estado atual, o
backlog e a seção de privacidade entram atualizados) e `docs/ARCHITECTURE.md`
com a escolha final e o plano de fallback — que o próprio README já promete que
ficariam registrados lá. Documentar também a mudança de APK para AAB e como
instalar um modelo alternativo.

---

## 6. Plano de testes

**JVM, com fake:** um `FakeLocalAiEngine` que devolve saídas canônicas,
incluindo JSON malformado, resposta vazia, timeout e OOM. Todo caso de uso e
ViewModel testado contra ele. O motor real nunca aparece em teste JVM.

**Determinístico, sem modelo:** o parser da saída JSON e o mapeamento
sugestão → categoria são lógica pura e devem ter cobertura alta, com corpus de
saídas reais capturadas do modelo durante a Fase 3.

**Golden set de categorização:** decidido (seção 9) começar com **extratos
reais próprios, anonimizados** — a fonte mais confiável para medir acerto,
ainda que limitada a um banco/formato até haver mais fontes. Guardar fora do
repositório versionado (dado sensível, mesmo anonimizado). É isso que prova
que a IA supera as regras — e o que impede uma regressão silenciosa na troca
de modelo.

**`androidTest` com emulador:** carga do bundle, `warmUp`, uma inferência
curta, liberação de memória. Lento e pesado; manter mínimo e separado do CI
rápido. O CI hoje roda `connectedStandaloneDebugAndroidTest` na API 35 (já
inclui o `NetworkPermissionInvariantTest` da Fase 2b) — avaliar se o
bundle do modelo cabe nesse job ou se essa suíte precisa de um workflow
próprio, manual.

**Teste do invariante de rede:** um teste que faz parse do `AndroidManifest.xml`
final (merged, de ambos os flavors) e falha se `android.permission.INTERNET`
aparecer. Barato, e é o que impede a garantia central do produto de ser perdida
por um merge de manifest de alguma dependência transitiva — que é exatamente
como isso costuma acontecer.

**O que não dá para testar:** qualidade do chat em linguagem natural. Aceitar
avaliação manual com um roteiro fixo de perguntas.

---

## 7. Impacto em segurança e privacidade

**Resolve:**
- O risco "motor de IA ainda não escolhido" do README sai da lista.
- A dependência de hardware específico é mitigada pelo fallback por regras
  (agora permanente por decisão, não só transitório).

**Mantém:**
- Manifest sem `INTERNET`, agora com teste automatizado que garante isso (seção
  6). A garantia continua estrutural, não documental.

**Cria:**
- **Superfície nova:** um binário nativo de inferência e um arquivo de modelo
  vindo de fora do APK.
- **Modelo importado pelo usuário:** um bundle arbitrário do sistema de arquivos
  passa a ser carregado por um runtime nativo. O `verified = false` comunica o
  risco, mas não o elimina. Mitigação realista: o `warmUp` isola falhas de
  formato, e o modelo não tem acesso a nada além do texto do prompt — que já é
  minimizado pelo item seguinte.
- **Conteúdo sensível no prompt:** o extrato bruto pode conter nome do titular
  e número de conta/agência. O prompt de categorização deve receber **só**
  descrição, valor e data — nunca o cabeçalho do extrato. Isso vira um teste.
- **Vazamento por log:** o SDK de inferência pode logar prompts em debug.
  Verificar e silenciar em release. O README já admite não ter teste automatizado
  contra vazamento em log; este é um bom momento para criar um.
- **Histórico de chat:** passa a conter perguntas sobre finanças em texto livre,
  persistidas sem criptografia. Reforça a prioridade do SQLCipher no roadmap.

---

## 8. Riscos e decisões em aberto

| Risco | Alternativa |
|---|---|
| Latência do chat inaceitável mesmo com E2B, no aparelho intermediário medido na Fase 4 | Trocar por LFM2.5-1.2B ou Qwen3.5-0.8B (Fase 4 existe para isso) |
| Modelo não respeita o JSON pedido de forma confiável | Usar function calling nativo do Gemma 4 em vez de prompt+parse; em último caso, gramática restrita |
| Bundle `.litertlm` do Gemma 4 E2B indisponível ou gated | Gemma 3n E2B, que tem bundle estável há mais tempo |
| Licença do modelo impede redistribuição no asset pack | Gemma 4 é Apache 2.0, então não deve impedir — mas conferir os termos antes de empacotar, e ter o import manual como plano B já pronto |
| Asset pack estoura o limite ou o app passa de 1 GB | Modelo menor no pack (Qwen3 1.7B) com o E2B via import opcional |
| Instalação de 1 GB afasta usuários | Medir a taxa de conclusão de instalação; se for ruim, migrar o pack para *fast-follow* ou *on-demand* |
| API do LiteRT-LM instável entre versões | Pinar versão exata; toda a superfície fica atrás de uma classe só |
| Play Core necessário para ler o asset pack contradiz "sem dependência de serviço Google em runtime" | Para install-time o caminho é de arquivo comum; confirmar na Fase 2b se dá para ler sem a biblioteca |
| CI não aguenta o modelo no `androidTest` | Workflow manual separado, fora do caminho do PR |
| Ambiente de desenvolvimento sem toolchain de build completa (só JRE) | Fases 0 e 1 precisam ser validadas (`./gradlew test`/`build`) numa máquina com JDK completo antes de prosseguir para a Fase 2 |

---

## 9. Decisões (2026-09-18)

Estas cinco perguntas não davam para responder lendo o código; foram
decididas com o usuário nesta revisão.

1. **Aparelho-alvo real:** cobrir os dois extremos desde já — um topo de linha
   recente e um intermediário de ~4 GB de RAM. A Fase 4 mede nos dois antes de
   fechar o modelo do chat.
2. **Distribuição:** sim, Google Play Store. A Fase 2b (AAB + Play Asset
   Delivery, flavors `play`/`standalone`) segue confirmada, sem condicional.
3. **Fallback por regras:** permanente. `RuleBasedLocalAiRepository` é parte
   definitiva da arquitetura, não código a ser removido depois — aparelhos
   incompatíveis continuam funcionando, com qualidade menor, para sempre.
4. **Golden set:** extratos reais próprios, anonimizados, guardados fora do
   repositório versionado.
5. **Chat escreve?** Não — somente leitura, como hoje. O contrato `FinancialChat`
   da Fase 5 não precisa de comandos de escrita.
