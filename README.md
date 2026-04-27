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
O projeto segue princípios de Arquitetura Limpa para garantir testabilidade e manutenção:
- **Presentation:** Camada de UI e ViewModels, lidando com estados e eventos.
- **Domain:** Núcleo do negócio contendo Modelos (`Money`, `Transaction`, `Category`), Repositórios (interfaces) e Casos de Uso (`AddTransactionUseCase`, etc.).
- **Data:** Implementações dos repositórios, DAOs do Room, Entidades e Mappers.

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
- **Backup Seguro:** `android:allowBackup` está definido como `false`. Regras de extração de dados excluem explicitamente o banco de dados e arquivos de preferências de backups padrão do sistema.
- **Logs Limpos:** O código é validado para não imprimir dados sensíveis (valores ou descrições) no Logcat.

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
