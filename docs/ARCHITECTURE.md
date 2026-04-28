# Arquitetura do Projeto Finances

Este documento descreve a estrutura arquitetural, os princípios de design e os fluxos de dados do aplicativo Finances.

## Visão Geral
O projeto segue uma arquitetura inspirada em **Clean Architecture**, dividida em camadas para garantir separação de interesses, testabilidade e manutenibilidade.

---

## Camadas da Arquitetura

### 1. Camada de UI (User Interface)
- **Tecnologia:** Jetpack Compose.
- **Responsabilidade:** Renderizar o estado da tela e capturar interações do usuário.
- **Princípio:** Deve ser "burra" e puramente declarativa. Não contém lógica de negócio ou acesso direto a dados.

### 2. Camada de Presentation (ViewModel)
- **Tecnologia:** `ViewModel` do Android, `StateFlow`.
- **Responsabilidade:** Gerenciar o estado da UI (**UDF - Unidirectional Data Flow**). Recebe eventos da UI e interage com os *Use Cases*.
- **Comunicação:** Expõe um único `StateFlow` contendo o `UiState`.

### 3. Camada de Domain (Domínio)
- **Componentes:** *Models*, *Use Cases*, *Repository Interfaces*.
- **Responsabilidade:** Contém as regras de negócio puras. É o núcleo do app.
- **Restrição:** **Não deve depender de bibliotecas Android**. Deve ser código Kotlin puro.
- **Models:** Uso obrigatório da classe `Money` (centavos em `Long`) para evitar erros de precisão numérica.

### 4. Camada de Data (Dados)
- **Componentes:** *Room Database*, *DAOs*, *Repository Implementations*, *Mappers*.
- **Tecnologia:** Room.
- **Responsabilidade:** Persistência local e mapeamento de dados externos/locais para o domínio.

---

## Fluxo de Dados (Exemplo: Listagem)

`UI (Compose)` 
  → aciona → `ViewModel.loadData()` 
  → invoca → `GetTransactionsUseCase()` 
  → chama → `TransactionsRepository.getTransactions()` 
  → requisita → `TransactionDao.getTransactions()` 
  → consulta → `Room Database`

O dado retorna mapeado via `Mappers` até o `ViewModel`, que atualiza o `UiState` via `Flow`.

---

## Regras e Convenções Inegociáveis

1. **Separação de Camadas:** A UI nunca acessa o DAO. O Domínio nunca conhece o Banco de Dados.
2. **Tratamento de Moeda:** Nunca use `Double` ou `Float` para valores monetários. Use sempre a classe `Money`.
3. **Eventos Únicos:** Eventos de navegação ou mensagens rápidas (Toast/Snackbar) devem ser tratados como eventos únicos (ex: via `LaunchedEffect`), não como estado persistente.
4. **Imutabilidade:** Os estados da UI e modelos de domínio devem ser preferencialmente classes de dados imutáveis (`data class` com `val`).

---

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
   - Adicione a rota em `FinancesRoute` e configure no `FinancesNavHost`.

---

## Como Criar uma Nova Migration do Room

1. **Exportar Schema:** Certifique-se de que o schema atual está versionado na pasta `app/schemas`.
2. **Atualizar Banco:** Altere a versão em `AppDatabase.kt` (ex: `version = 2`).
3. **Criar Migration:**
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(database: SupportSQLiteDatabase) {
           database.execSQL("ALTER TABLE transactions ADD COLUMN notes TEXT")
       }
   }
   ```
4. **Registrar:** Adicione `.addMigrations(MIGRATION_1_2)` no builder do banco.
5. **Testar:** Adicione um caso de teste em `MigrationTest.kt` usando o `MigrationTestHelper`.

---

## Injeção de Dependências
Utilizamos **Manual Dependency Injection** através do `AppContainer` inicializado na classe `MainApplication`. Isso mantém o projeto simples, sem o overhead de bibliotecas como Dagger/Hilt, mas permitindo fácil substituição de implementações para testes.
