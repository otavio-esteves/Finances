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
        val canonicalFingerprint = fingerprint(fingerprintSuggestions, canonical = true)
        val legacyFingerprint = fingerprint(fingerprintSuggestions, canonical = false)
        val parserLegacyFingerprints = if (fingerprintSuggestions.any {
            it.originalEntry.legacyOfxDescription != null || it.originalEntry.legacyInputType != null
        }) {
            listOf(fingerprint(fingerprintSuggestions, canonical = true, legacyInput = true),
                fingerprint(fingerprintSuggestions, canonical = false, legacyInput = true))
        } else emptyList()
        val memories = confirmedSuggestions.groupBy {
            br.com.otavioesteves.finances.domain.model.categoryMemoryKey(it.originalEntry.description) to it.originalType
        }.filterValues { group ->
            group.any { it.rememberCorrection } && group.map { it.suggestedCategory?.id }.distinct().size == 1
        }.map { (key, group) -> br.com.otavioesteves.finances.domain.model.CategoryMemory(
            key.first, key.second, requireNotNull(group.first().suggestedCategory).id
        ) }
        statementImportRepository.confirmReviewedImport(statementImport, transactions, canonicalFingerprint, legacyFingerprint, memories, parserLegacyFingerprints)
        return statementImport
    }

    private fun fingerprint(suggestions: List<CategorySuggestion>, canonical: Boolean, legacyInput: Boolean = false): String {
        fun description(suggestion: CategorySuggestion): String =
            (if (legacyInput) suggestion.originalEntry.legacyOfxDescription else null) ?: suggestion.originalEntry.description
        fun inputType(suggestion: CategorySuggestion) =
            (if (legacyInput) suggestion.originalEntry.legacyInputType else null) ?: suggestion.originalType
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update((if (canonical) "finances-statement-v2" else "finances-statement-v1").toByteArray(Charsets.UTF_8))
        // Sort without distinct(): two identical purchases must remain two entries.
        val ordered = if (canonical) suggestions.sortedWith(
            compareBy({ it.originalEntry.date }, { inputType(it).name }, { it.originalEntry.amount.cents }, { description(it) })
        ) else suggestions
        ordered.forEach { suggestion ->
            val entry = suggestion.originalEntry
            listOf(entry.date.toString(), inputType(suggestion).name, entry.amount.cents.toString(), description(suggestion))
                .forEach { field ->
                    val bytes = field.toByteArray(Charsets.UTF_8)
                    digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                    digest.update(bytes)
                }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
