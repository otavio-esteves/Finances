package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository

/**
 * Persists a statement import once the user has confirmed the AI-suggested
 * categories: each entry becomes an imported transaction, and the import
 * itself is recorded for history.
 */
class ConfirmStatementImportUseCase(
    private val addTransactionUseCase: AddTransactionUseCase,
    private val statementImportRepository: StatementImportRepository,
    private val dateProvider: DateProvider
) {
    suspend operator fun invoke(fileName: String, confirmedSuggestions: List<CategorySuggestion>): ImportedStatement {
        confirmedSuggestions.forEach { suggestion ->
            val category = requireNotNull(suggestion.suggestedCategory) {
                "Sugestão sem categoria não pode ser confirmada: ${suggestion.entry.description}"
            }
            addTransactionUseCase(
                Transaction(
                    id = 0,
                    description = suggestion.entry.description,
                    amount = suggestion.entry.amount,
                    categoryId = category.id,
                    date = suggestion.entry.date,
                    type = suggestion.entry.type,
                    origin = TransactionOrigin.IMPORTED
                )
            )
        }

        val statementImport = ImportedStatement(
            id = 0,
            fileName = fileName,
            importedAt = dateProvider.getCurrentDateTime(),
            transactionCount = confirmedSuggestions.size
        )
        statementImportRepository.addImport(statementImport)
        return statementImport
    }
}
