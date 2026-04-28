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

## Camadas do Projeto

## Funcionalidades Atuais
- **Dashboard:** Visão geral do saldo mensal, total de receitas e total de despesas.
- **Resumo por Categoria:** Listagem das categorias com o montante gasto/recebido no mês selecionado.
- **Nova Transação:** Cadastro de transações com descrição, valor (parser robusto), tipo (receita/despesa), categoria, data e observações.
- **Persistência:** Todos os dados são salvos localmente e persistem entre reinicializações do app.
- **Design Minimalista:** Tema escuro padrão com alta legibilidade e hierarquia visual clara.

## Planejado (Backlog)
- Edição e exclusão de transações existentes.
- Filtro dinâmico para navegar entre diferentes meses/anos.
- Relatórios detalhados e gráficos de gastos.
- Exportação de dados (CSV/JSON).

## Segurança e Privacidade
- **Zero Cloud:** Os dados nunca saem do dispositivo. Não há integração com serviços de nuvem ou telemetria.
- **Backup Desativado:** `android:allowBackup` está definido como `false`. Isso impede que os dados financeiros sejam movidos para o backup automático do Google, garantindo que o controle permaneça no dispositivo físico.
- **Prevenção de Captura de Tela:** O app utiliza `FLAG_SECURE` em sua Activity principal, impedindo capturas de tela (screenshots) e gravações de tela por outros aplicativos ou pelo próprio sistema, protegendo a visibilidade dos seus saldos e transações.
- **Logs Limpos:** O código foi auditado para garantir que dados sensíveis (valores, descrições ou categorias) não sejam registrados no Logcat.

### Riscos Remanescentes e Melhorias Futuras
Embora o app siga boas práticas, segurança é uma jornada contínua. 

**Riscos Atuais:**
- **Acesso ao Dispositivo Desbloqueado:** Como não há PIN/Biometria interno, qualquer pessoa com o celular desbloqueado pode abrir o app.
- **Falta de Criptografia no Repouso (At Rest):** O banco de dados SQLite está armazenado sem criptografia adicional (como SQLCipher), dependendo exclusivamente da sandbox do Android.

**Roadmap de Segurança:**
- [ ] **Autenticação Biométrica/PIN:** Adicionar uma camada de entrada para abrir o aplicativo.
- [ ] **SQLCipher:** Implementar criptografia transparente no banco de dados Room.
- [ ] **Ofuscação Avançada:** Configurar R8/ProGuard de forma agressiva para dificultar engenharia reversa.
- [x] **Backup e Restauração Local:** Implementado via exportação de arquivo JSON.
    - *Aviso:* O arquivo de backup não é criptografado. O usuário deve protegê-lo adequadamente (ex: movendo para um local seguro ou container criptografado).

## Qualidade e Testes
- **Precisão Financeira:** Uso de classe `Money` (long cents) para evitar erros de ponto flutuante (`Double`).
- **Testes de Arquitetura:** Suite automática que garante o desacoplamento das camadas e proíbe dependências proibidas (ex: Domain dependendo do Android SDK).
- **Testes Unitários:** Cobertura de lógica de parsers, formatação e validação de ViewModels.

## Como Desenvolver

### Pré-requisitos
- JDK 11 ou superior instalado.
- Variável `JAVA_HOME` configurada.

### Comandos Principais
- **Gerar APK de Debug:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Executar Testes Unitários:**
  ```bash
  ./gradlew test
  ```

---
*Este projeto é um MVP funcional em constante evolução.*
