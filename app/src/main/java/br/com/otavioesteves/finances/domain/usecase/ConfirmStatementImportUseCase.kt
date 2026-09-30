package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Persists a reviewed statement as one atomic batch. The fingerprint uses the
 * extracted entries, so category corrections do not change duplicate detection.
 */
class ConfirmStatementImportUseCase(
    private val statementImportRepository: StatementImportRepository,
    private val dateProvider: DateProvider
) {
    suspend operator fun invoke(fileName: String, confirmedSuggestions: List<CategorySuggestion>): ImportedStatement {
        require(confirmedSuggestions.isNotEmpty()) { "O extrato não contém transações." }
        val transactions = confirmedSuggestions.map { suggestion ->
            val category = requireNotNull(suggestion.suggestedCategory) {
                "Sugestão sem categoria não pode ser confirmada: ${suggestion.entry.description}"
            }
            require(category.type == CategoryType.valueOf(suggestion.entry.type.name)) {
                "Categoria incompatível com o tipo da transação: ${suggestion.entry.description}"
            }
            Transaction(
                id = 0,
                description = suggestion.entry.description,
                amount = suggestion.entry.amount,
                categoryId = category.id,
                date = suggestion.entry.date,
                type = suggestion.entry.type,
                origin = TransactionOrigin.IMPORTED
            )
        }

        val statementImport = ImportedStatement(
            id = 0,
            fileName = fileName,
            importedAt = dateProvider.getCurrentDateTime(),
            transactionCount = confirmedSuggestions.size
        )
        statementImportRepository.confirmImport(
            statementImport,
            transactions,
            fingerprint(confirmedSuggestions)
        )
        return statementImport
    }

    private fun fingerprint(suggestions: List<CategorySuggestion>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("finances-statement-v1".toByteArray(Charsets.UTF_8))
        suggestions.forEach { suggestion ->
            val entry = suggestion.entry
            listOf(entry.date.toString(), entry.type.name, entry.amount.cents.toString(), entry.description)
                .forEach { field ->
                    val bytes = field.toByteArray(Charsets.UTF_8)
                    digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                    digest.update(bytes)
                }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
