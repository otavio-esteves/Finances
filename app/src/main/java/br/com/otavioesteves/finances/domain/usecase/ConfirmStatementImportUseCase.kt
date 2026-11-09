package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.model.statementSourceKey
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
    suspend operator fun invoke(
        fileName: String,
        confirmedSuggestions: List<CategorySuggestion>,
        sourceLabel: String? = null,
        fingerprintSuggestions: List<CategorySuggestion> = confirmedSuggestions
    ): ImportedStatement {
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
                origin = TransactionOrigin.IMPORTED,
                categoryMemoryDescriptionKey = br.com.otavioesteves.finances.domain.model.categoryMemoryKey(suggestion.originalEntry.description),
                categoryMemoryInputType = suggestion.originalType
            )
        }
        br.com.otavioesteves.finances.domain.model.checkedMagnitudeTotal(transactions.asSequence().map { it.amount.cents })

        val statementImport = ImportedStatement(
            id = 0,
            fileName = fileName,
            importedAt = dateProvider.getCurrentDateTime(),
            transactionCount = confirmedSuggestions.size,
            sourceLabel = sourceLabel?.trim()?.takeIf { it.isNotEmpty() },
            canUndo = true
        )
        // Excluding rows changes what is saved, but the claim still identifies the entire source batch.
        val canonicalFingerprint = fingerprint(fingerprintSuggestions, version = 3, sourceLabel = sourceLabel)
        val legacyFingerprints = listOf(2, 1).map { fingerprint(fingerprintSuggestions, version = it) }
        val memories = confirmedSuggestions.groupBy {
            br.com.otavioesteves.finances.domain.model.categoryMemoryKey(it.originalEntry.description) to it.originalType
        }.filterValues { group ->
            group.any { it.rememberCorrection } && group.map { it.suggestedCategory?.id }.distinct().size == 1
        }.map { (key, group) -> br.com.otavioesteves.finances.domain.model.CategoryMemory(
            key.first, key.second, requireNotNull(group.first().suggestedCategory).id
        ) }
        statementImportRepository.confirmReviewedImport(statementImport, transactions, canonicalFingerprint, legacyFingerprints, memories)
        return statementImport
    }

    private fun fingerprint(suggestions: List<CategorySuggestion>, version: Int, sourceLabel: String? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("finances-statement-v$version".toByteArray(Charsets.UTF_8))
        fun updateField(field: String) {
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        if (version >= 3) updateField(statementSourceKey(sourceLabel))
        // Sort without distinct(): two identical purchases must remain two entries.
        val ordered = if (version >= 2) suggestions.sortedWith(
            compareBy({ it.originalEntry.date }, { it.originalType.name }, { it.originalEntry.amount.cents }, { it.originalEntry.description })
        ) else suggestions
        ordered.forEach { suggestion ->
            val entry = suggestion.originalEntry
            listOf(entry.date.toString(), suggestion.originalType.name, entry.amount.cents.toString(), entry.description)
                .forEach(::updateField)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
