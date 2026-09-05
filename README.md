# Finances

App financeiro focado em simplicidade, organização e privacidade, desenvolvido com as tecnologias mais modernas do ecossistema Android.

## Objetivo
Prover uma ferramenta leve e elegante para controle de finanças pessoais, operando de forma 100% local e priorizando a precisão dos dados e a segurança das informações do usuário.

## Stack Tecnológica
- **Linguagem:** Kotlin
- **Interface UI:** Jetpack Compose com Material Design 3
- **Arquitetura:** Clean Architecture (Domain, Data, Presentation)
- **Gerenciamento de Estado:** ViewModel + StateFlow
- **Persistência Local:** Room Database (com KSP)
- **Gestão de Dependências:** Gradle Version Catalog (libs.versions.toml)
- **Injeção de Dependências:** Manual via `AppContainer` e `ViewModelProvider.Factory`

## Arquitetura do Projeto
O projeto segue os princípios da **Clean Architecture** e **SOLID** para garantir testabilidade e baixo acoplamento.

Para detalhes técnicos, diagramas de fluxo e guias de implementação, consulte a [Documentação de Arquitetura](docs/ARCHITECTURE.md).

---

## Funcionalidades Atuais
- **Dashboard:** Visão geral do saldo mensal, total de receitas e total de despesas.
- **Resumo por Categoria:** Listagem das categorias com o montante gasto/recebido no mês selecionado.
- **Nova Transação:** Cadastro de transações com descrição, valor (parser robusto), tipo (receita/despesa), categoria, data e observações.
- **Gestão de Transações:** Histórico com edição e exclusão de transações existentes.
- **Exportação e Backup:** Exportação em CSV/JSON e backup/restauração local em JSON.
- **Persistência:** Todos os dados são salvos localmente e persistem entre reinicializações do app.
- **Design Minimalista:** Tema escuro padrão com alta legibilidade e hierarquia visual clara.

## Planejado (Backlog)
- Filtro dinâmico para navegar entre diferentes meses/anos.
- Relatórios detalhados e gráficos de gastos.

## Segurança e Privacidade
- **Armazenamento local:** O app não integra serviços de nuvem ou telemetria e não declara permissão de internet. Exportações e backups manuais usam o seletor de arquivos do Android; o destino escolhido pode ser um provedor de nuvem.
- **Backup automático:** `android:allowBackup="false"` e os arquivos `backup_rules.xml` e `data_extraction_rules.xml` configuram exclusões para backup e transferência de dados. Isso não impede exportações manuais nem constitui garantia contra extração em dispositivos comprometidos.
- **Captura de tela:** A `MainActivity` define `FLAG_SECURE`, solicitando ao Android proteção contra screenshots e exibição em telas não seguras; não é uma garantia absoluta de confidencialidade.
- **Logs:** Não foram encontradas chamadas de logging de dados financeiros no código de produção revisado. Não há teste automatizado que garanta a ausência de vazamentos em logs.

### Riscos Remanescentes e Melhorias Futuras
Embora o app siga boas práticas, segurança é uma jornada contínua. 

**Riscos Atuais:**
- **Acesso ao Dispositivo Desbloqueado:** Como não há PIN/Biometria interno, qualquer pessoa com o celular desbloqueado pode abrir o app.
- **Importação de backup:** A validação atual verifica a estrutura JSON e rejeita transações quando a lista de categorias está vazia; não valida integralmente versão, referências e limites do conteúdo. Fortalecer essa validação é trabalho futuro.
- **Falta de Criptografia no Repouso (At Rest):** O banco de dados SQLite está armazenado sem criptografia adicional (como SQLCipher), dependendo exclusivamente da sandbox do Android.

**Roadmap de Segurança:**
- [ ] **Autenticação Biométrica/PIN:** Adicionar uma camada de entrada para abrir o aplicativo.
- [ ] **SQLCipher:** Implementar criptografia transparente no banco de dados Room.
- [ ] **Ofuscação Avançada:** Configurar R8/ProGuard de forma agressiva para dificultar engenharia reversa.
- [x] **Backup e Restauração Local:** Implementado via exportação de arquivo JSON.
    - *Aviso:* O arquivo de backup não é criptografado. O usuário deve protegê-lo adequadamente (ex: movendo para um local seguro ou container criptografado).

## Qualidade e Testes
- **Precisão Financeira:** Uso de classe `Money` (long cents) para evitar erros de ponto flutuante (`Double`).
- **Testes de Arquitetura:** Testes que inspecionam imports do domínio, alguns padrões de instanciação de repositórios em ViewModels, a classe `Money` e referências ao package. Essas verificações textuais não comprovam todas as regras arquiteturais.
- **Testes Unitários:** Casos para `Money`, períodos mensais, parsers, formatação, exportação, casos de uso e alguns estados de ViewModels. Não há percentual de cobertura medido. Os testes JVM não validam a persistência Room em um dispositivo.

## Como Desenvolver

### Pré-requisitos
- JDK **17** completo (incluindo `javac`), preferencialmente Eclipse Temurin, com `JAVA_HOME` apontando para a instalação e `$JAVA_HOME/bin` no `PATH`.
- Android SDK com **Platform 36**, **Build Tools 35.0.0** e licenças aceitas. Configure `ANDROID_HOME` ou `sdk.dir` em `local.properties` (arquivo local, não versionado).
- Acesso à internet para baixar o wrapper e dependências na primeira execução.
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

O CI executa `test`, `lint` e `assembleDebug`, nessa ordem, com Temurin 17. O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`. Os testes em `app/src/androidTest` exigem dispositivo/emulador e não são executados por `test` nem pelo workflow atual.

---
*Este projeto é um MVP funcional em constante evolução.*
