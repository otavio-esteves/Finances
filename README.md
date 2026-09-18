# Finances

App financeiro focado em simplicidade, organização e privacidade, desenvolvido com as tecnologias mais modernas do ecossistema Android.

## Objetivo
Prover uma ferramenta leve e elegante para controle de finanças pessoais, operando de forma 100% local. O usuário importa seus extratos bancários, uma IA local sintetiza e categoriza as transações, e o app apresenta um resumo visual dos gastos por categoria — com um assistente de IA local para tirar dúvidas sobre os próprios dados, sem que nenhuma informação financeira saia do aparelho.

> **Mudança de escopo (2026):** o projeto está migrando de um app de lançamento manual de transações para um app centrado em **importação de extratos + síntese por IA local**. Este README descreve a visão-alvo do produto; a seção [Estado Atual da Implementação](#estado-atual-da-implementação) explica o que já existe e o que ainda está em construção.

## Stack Tecnológica
- **Linguagem:** Kotlin
- **Interface UI:** Jetpack Compose com Material Design 3
- **Arquitetura:** Clean Architecture (Domain, Data, Presentation)
- **Gerenciamento de Estado:** ViewModel + StateFlow
- **Persistência Local:** Room Database (com KSP)
- **Gestão de Dependências:** Gradle Version Catalog (libs.versions.toml)
- **Injeção de Dependências:** Manual via `AppContainer` e `ViewModelProvider.Factory`
- **IA Local (on-device):** motor ainda **em decisão** — ver [Decisão em Aberto: Motor de IA](#decisão-em-aberto-motor-de-ia-local)
- **Importação de Extratos:** parsers locais para CSV/OFX (fase 1) e PDF (fase 2)

## Arquitetura do Projeto
O projeto segue os princípios da **Clean Architecture** e **SOLID** para garantir testabilidade e baixo acoplamento.

```text
UI → Presentation → Domain ← Data
```

Para detalhes técnicos, diagramas de fluxo e guias de implementação, consulte a [Documentação de Arquitetura](docs/ARCHITECTURE.md).

---

## Visão do Produto

O fluxo principal do app passa a ser:

1. **Importar extrato** — o usuário seleciona um arquivo de extrato bancário (CSV, OFX ou PDF) via seletor de arquivos do Android.
2. **Sintetizar com IA local** — um modelo de IA rodando inteiramente no dispositivo interpreta as transações do extrato, sugere categorias e monta um resumo do período.
3. **Visualizar no Dashboard (tela inicial)** — a Home mostra um **gráfico de uso de capital por categoria** para o mês selecionado, além do saldo, receitas e despesas.
4. **Tirar dúvidas no Chat (tela à direita)** — deslizando a Home para a esquerda, o usuário acessa uma tela de **chat com a IA local**, podendo perguntar sobre os próprios gastos (ex: "quanto gastei com mercado em fevereiro?"), sem qualquer chamada de rede.

A navegação principal entre essas duas telas é feita por **swipe horizontal** (Home ⇄ Chat), como um pager de duas páginas. As demais telas (transações, categorias, importação, configurações, backup) são acessadas a partir da Home como navegação secundária.

## Funcionalidades-Alvo
- **Importação de Extratos:** leitura de arquivos CSV/OFX (estruturados) e PDF (extração de texto/tabelas) exportados por bancos.
- **Síntese por IA Local:** categorização automática das transações importadas e geração de um resumo do período, executados 100% on-device.
- **Dashboard com Gráfico por Categoria:** tela inicial com visualização gráfica do uso de capital por categoria no mês selecionado, além do saldo mensal.
- **Chat com IA Local:** segunda tela (acessível por swipe à direita da Home) para tirar dúvidas sobre as finanças pessoais, respondida por um modelo rodando localmente.
- **Entrada Manual de Transações:** mantida como complemento ao fluxo de importação, para lançamentos avulsos ou correções que não vêm de um extrato.
- **Gestão de Transações e Categorias:** histórico com edição/exclusão, resumo por categoria.
- **Exportação e Backup:** exportação em CSV/JSON e backup/restauração local em JSON.
- **Persistência:** todos os dados (transações, extratos importados, histórico de chat) são salvos localmente e persistem entre reinicializações.
- **Design Minimalista:** tema escuro padrão com alta legibilidade e hierarquia visual clara.

## Decisão em Aberto: Motor de IA Local

Ainda não foi escolhido o motor de inferência on-device. As duas opções candidatas:

| Opção | Prós | Contras |
|---|---|---|
| **Gemini Nano (AICore / ML Kit GenAI)** | API oficial do Android, sem aumento de tamanho do APK, mantida pelo Google | Só funciona em aparelhos compatíveis (ex: Pixel 8+); exige estratégia de fallback nos demais |
| **MediaPipe LLM Inference + modelo embarcado (ex: Gemma pequeno, gguf/tflite)** | Funciona na maioria dos Androids modernos, controle total do modelo/prompt, sem dependência de serviço Google em runtime | Modelo pesa centenas de MB (APK maior ou download inicial); mais lento e consome mais bateria |

Qualquer que seja a escolha, a regra é inegociável: **a IA deve rodar inteiramente no dispositivo, sem chamadas de rede**, preservando o princípio 100% local do app. A escolha final e o plano de fallback para dispositivos incompatíveis ficam registrados em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Estado Atual da Implementação

A base de código atual é o MVP anterior (lançamento manual de transações), agora com a camada de domínio/dados da migração já implementada por baixo da UI existente:

- **Já existe:** Clean Architecture, classe `Money` (centavos em `Long`), Room (banco na versão 2, com migração testada), resumo por categoria, CRUD de transações manuais, exportação CSV/JSON e backup/restauração local. Parser de extrato CSV/OFX, síntese de categorias (implementação provisória por palavra-chave), campo `origin` (`manual`/`imported`) em `Transaction`, e persistência de histórico de chat e de importações — todos com casos de uso e testes. Navegação raiz por `HorizontalPager` (Home ⇄ Chat), gráfico de uso de capital por categoria na Home (`CategoryUsageChart`, barra de composição + legenda), tela de Chat funcional (`ChatScreen`/`ChatViewModel`), tela de Importar Extrato (`ImportStatementScreen`/`ImportStatementViewModel`, com seleção de arquivo, revisão/edição das categorias sugeridas e confirmação) e filtro de mês/ano (`MonthPeriodSelector` na Home, Categorias e Histórico) já implementados e ligados ao `AppContainer`.
- **Em construção:** escolha do motor de IA local real (hoje há apenas um stand-in por regras), parser de PDF (fase 2).

## Planejado (Backlog)
- [x] Parser de extrato CSV/OFX (fase 1).
- [ ] Parser de extrato PDF com extração de texto/tabelas (fase 2).
- [ ] Escolha e integração do motor de IA local real (Gemini Nano ou MediaPipe LLM Inference) — hoje há um stand-in provisório por regras (`RuleBasedLocalAiRepository`).
- [x] Categorização automática das transações importadas (via stand-in atual; casos de uso e persistência prontos, independem do motor final).
- [x] Tela de Importar Extrato (UI que aciona `ImportStatementUseCase`/`SynthesizeStatementUseCase`/`ConfirmStatementImportUseCase`).
- [x] Gráfico de uso de capital por categoria na Home.
- [x] Tela de Chat com IA local e navegação por swipe (HorizontalPager Home ⇄ Chat).
- [x] Histórico de conversas do chat persistido localmente (`ChatRepository`/`GetChatHistoryUseCase`), exibido na tela de Chat.
- [x] Filtro dinâmico para navegar entre diferentes meses/anos (`MonthPeriodSelector` na Home, Categorias e Histórico).
- [ ] Relatórios detalhados adicionais.

## Segurança e Privacidade
- **Armazenamento e IA 100% locais:** o app não integra serviços de nuvem ou telemetria e não declara permissão de internet. Isso vale tanto para os dados financeiros quanto para o motor de IA (síntese de extratos e chat) — nenhum dado sai do aparelho. Exportações e backups manuais usam o seletor de arquivos do Android; o destino escolhido pode ser um provedor de nuvem, por decisão explícita do usuário.
- **Backup automático:** `android:allowBackup="false"` e os arquivos `backup_rules.xml` e `data_extraction_rules.xml` configuram exclusões para backup e transferência de dados. Isso não impede exportações manuais nem constitui garantia contra extração em dispositivos comprometidos.
- **Captura de tela:** a `MainActivity` define `FLAG_SECURE`, solicitando ao Android proteção contra screenshots e exibição em telas não seguras; não é uma garantia absoluta de confidencialidade.
- **Logs:** não foram encontradas chamadas de logging de dados financeiros no código de produção revisado. Não há teste automatizado que garanta a ausência de vazamentos em logs.
- **Extratos importados:** arquivos de extrato (CSV/OFX/PDF) podem conter dados sensíveis adicionais (nome do titular, número de conta/agência). O parsing e a síntese por IA devem tratar esse conteúdo com o mesmo cuidado dado às transações, e o arquivo original não deve ser retido além do necessário para a importação.

### Riscos Remanescentes e Melhorias Futuras
Embora o app siga boas práticas, segurança é uma jornada contínua.

**Riscos Atuais:**
- **Acesso ao Dispositivo Desbloqueado:** como não há PIN/Biometria interno, qualquer pessoa com o celular desbloqueado pode abrir o app.
- **Importação de backup:** a validação atual verifica a estrutura JSON e rejeita transações quando a lista de categorias está vazia; não valida integralmente versão, referências e limites do conteúdo. Fortalecer essa validação é trabalho futuro.
- **Falta de Criptografia no Repouso (At Rest):** o banco de dados SQLite está armazenado sem criptografia adicional (como SQLCipher), dependendo exclusivamente da sandbox do Android.
- **Motor de IA ainda não escolhido:** sem a definição final, o risco de dependência de hardware específico (caso de IA nativa do SO) ou de aumento de superfície de ataque/tamanho de app (caso de modelo embarcado) ainda não foi mitigado.
- **Extração de PDF:** parsers de PDF costumam ser um vetor de bugs/crash com arquivos malformados; exige tratamento defensivo e testes com extratos reais de múltiplos bancos.

**Roadmap de Segurança:**
- [ ] **Autenticação Biométrica/PIN:** adicionar uma camada de entrada para abrir o aplicativo.
- [ ] **SQLCipher:** implementar criptografia transparente no banco de dados Room.
- [ ] **Ofuscação Avançada:** configurar R8/ProGuard de forma agressiva para dificultar engenharia reversa.
- [ ] **Descarte seguro do extrato original:** garantir que o arquivo importado não seja copiado/retido além do ciclo de importação.
- [x] **Backup e Restauração Local:** implementado via exportação de arquivo JSON.
    - *Aviso:* o arquivo de backup não é criptografado. O usuário deve protegê-lo adequadamente (ex: movendo para um local seguro ou container criptografado).

## Qualidade e Testes
- **Precisão Financeira:** uso de classe `Money` (long cents) para evitar erros de ponto flutuante (`Double`).
- **Testes de Arquitetura:** testes que inspecionam imports do domínio, alguns padrões de instanciação de repositórios em ViewModels, a classe `Money` e referências ao package. Essas verificações textuais não comprovam todas as regras arquiteturais.
- **Testes Unitários:** casos para `Money`, períodos mensais, parsers, formatação, exportação, casos de uso e alguns estados de ViewModels. Não há percentual de cobertura medido. Os testes JVM não validam a persistência Room em um dispositivo.
- **Testes de IA/Parsers (planejado):** novos parsers de extrato e a camada de IA local exigirão testes com arquivos de amostra (CSV/OFX/PDF reais e sintéticos) e testes determinísticos para a lógica de categorização, isolando o modelo de IA por trás de uma interface testável (fake/stub).

## Como Desenvolver

### Pré-requisitos
- JDK **17** completo (incluindo `javac`), preferencialmente Eclipse Temurin, com `JAVA_HOME` apontando para a instalação e `$JAVA_HOME/bin` no `PATH`.
- Android SDK com **Platform 36**, **Build Tools 35.0.0** e licenças aceitas. Configure `ANDROID_HOME` ou `sdk.dir` em `local.properties` (arquivo local, não versionado).
- Acesso à internet para baixar o wrapper e dependências na primeira execução (a IA local em runtime, quando implementada, não requer rede).
- Execute os comandos na raiz do repositório. Use o wrapper incluído; não é necessário instalar Gradle separadamente.

O projeto usa AGP **8.13.2**, Gradle **8.13**, Kotlin **2.0.21**, KSP **2.0.21-1.0.28** e Room **2.6.1**. O [AGP 8.13 exige JDK 17 e Gradle 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes). Os alvos de bytecode Java/Kotlin continuam em **11**; isso é independente do JDK que executa o build. Android Studio é opcional para os comandos abaixo; na IDE, selecione também JDK 17 como Gradle JDK.

A versão do KSP acompanha Kotlin 2.0.21. A combinação atual com Gradle/AGP está além da faixa de suporte pleno publicada para o [plugin Kotlin 2.0.21](https://kotlinlang.org/docs/gradle-configure-project.html); a validação local dos comandos abaixo não amplia essa garantia oficial. Uma atualização coordenada de Kotlin/KSP fica para trabalho separado.

### Comandos Principais
- **Gerar APK de Debug:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Executar Testes Unitários:**
  ```bash
  ./gradlew test
  ```
- **Executar Android Lint:**
  ```bash
  ./gradlew lint
  ```

O CI executa `test`, `lint`, `assembleDebug` e `connectedDebugAndroidTest`, nessa ordem, com Temurin 17. O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`. Os testes em `app/src/androidTest` exigem um emulador Android e são executados no CI com API 35.

---
*Este projeto é um MVP funcional em constante evolução, atualmente em migração de escopo para importação de extratos + IA local.*
