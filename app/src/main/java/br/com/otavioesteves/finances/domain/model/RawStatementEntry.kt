package br.com.otavioesteves.finances.domain.model

import java.time.LocalDate

data class RawStatementEntry(
    val description: String,
    val amount: Money,
    val date: LocalDate,
    val type: TransactionType,
    val extractedByOcr: Boolean = false,
    /** Pre-decoding text, only to recognize OFX imports and memories saved by older versions. */
    val legacyOfxDescription: String? = null,
    /** Direction produced by the older PDF parser, for duplicate and memory compatibility only. */
    val legacyInputType: TransactionType? = null,
    /** Direction used by the previous source-scoped PDF fingerprints, never for categorization. */
    val legacyScopedInputType: TransactionType? = null
) {
    init {
        require(description.isNotBlank()) { "description must not be blank" }
        requirePersistableText(description, "descrição")
        require(!amount.isNegative()) { "amount must not be negative" }
        requireSupportedTransactionDate(date)
        require(legacyOfxDescription == null || legacyOfxDescription.isNotBlank())
    }
}
