package br.com.otavioesteves.finances.domain.model

import java.time.LocalDate

data class RawStatementEntry(
    val description: String,
    val amount: Money,
    val date: LocalDate,
    val type: TransactionType,
    val extractedByOcr: Boolean = false,
    /** Pre-decoding text (historically first-line only), for old OFX imports and memories. */
    val legacyOfxDescription: String? = null,
    /** Direction produced by the older PDF parser, for duplicate and memory compatibility only. */
    val legacyInputType: TransactionType? = null,
    /** Direction used by the previous source-scoped PDF fingerprints, never for categorization. */
    val legacyScopedInputType: TransactionType? = null,
    /** Decoded first-line text saved before multiline OFX fields were supported. */
    val legacyDecodedOfxDescription: String? = null,
    /** Description saved before PDF continuations following the amount were preserved. */
    val legacyPdfDescription: String? = null
) {
    init {
        require(description.isNotBlank()) { "description must not be blank" }
        requirePersistableText(description, "descrição")
        require(!amount.isNegative()) { "amount must not be negative" }
        requireSupportedTransactionDate(date)
        require(legacyOfxDescription == null || legacyOfxDescription.isNotBlank())
        require(legacyDecodedOfxDescription == null || legacyDecodedOfxDescription.isNotBlank())
        require(legacyPdfDescription == null || legacyPdfDescription.isNotBlank())
    }
}
