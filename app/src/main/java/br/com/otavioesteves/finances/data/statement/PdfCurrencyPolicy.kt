package br.com.otavioesteves.finances.data.statement

/** Read declarations from the original document, before bank-specific cover/section removal. */
internal class PdfCurrencyPolicy(original: String) {
    private val requiresExplicitBrl = original.lineSequence().mapNotNull(::declaration)
        .any { !BRL_DECLARATION.matches(it) }

    fun isDeclaration(line: String): Boolean = declaration(line) != null

    fun requireBrlValue(raw: String, columnIsBrl: Boolean = false, rowCurrency: String? = null) {
        require(!requiresExplicitBrl || columnIsBrl || hasBrlMarker(raw) ||
            rowCurrency?.let { BRL_DECLARATION.matches(normalizePdfText(it)) } == true) {
            "O PDF declara moeda estrangeira ou não reconhecida sem valor inequivocamente convertido em reais. Nenhum lançamento foi importado."
        }
    }

    companion object {
        private const val LABEL = "(?:moeda(?: (?:do documento|da fatura|do extrato))?|currency)"
        private val DECLARATION = Regex("^$LABEL\\s*[:=]\\s*(.+)$")
        private val SPACED_DECLARATION = Regex("^$LABEL\\s+(.+)$")
        private val BRL_DECLARATION = Regex("(?:brl|r\\$|reais|real(?: brasileiro)?)(?:\\s*\\((?:brl|r\\$|reais|real brasileiro)\\))?")
        private val CURRENCY_VALUE = Regex("[a-z]{3}|us\\$|\\p{Sc}|(?:us )?dollars?|dolar(?:es)?(?: americanos?)?|euros?|libras?|ienes?")
        private val BRL_MARKER = Regex("(?<!\\p{L})r\\$|\\bbrl\\b|\\breais\\b|\\breal(?: brasileiro)?\\b")
        private val BRL_COLUMN = Regex("(valor|debito|credito|saldo)(?:\\s+(?:em\\s+)?)?\\(?\\s*(?:r\\$|brl|reais|real(?: brasileiro)?)\\s*\\)?")
        private val BRL_VALUE_HEADER = Regex("\\bvalor\\s*(?:em\\s+)?\\(?\\s*(?:r\\$|brl\\b|reais\\b|real\\b)")

        fun hasBrlMarker(value: String): Boolean = BRL_MARKER.containsMatchIn(normalizePdfText(value))

        fun hasBrlValueColumn(header: String): Boolean = BRL_VALUE_HEADER.containsMatchIn(normalizePdfText(header))

        /** Canonical names retain explicit BRL provenance separately from their geometry. */
        fun columnName(value: String): String {
            val normalized = normalizePdfText(value)
            return when (normalized) {
                "currency", "moeda" -> "moeda"
                else -> BRL_COLUMN.matchEntire(normalized)?.groupValues?.get(1) ?: normalized
            }
        }

        private fun declaration(line: String): String? {
            val cells = line.split('\t').map(::normalizePdfText)
            if (cells.any { it.startsWith("data") } && cells.any { it in setOf("descricao", "historico", "estabelecimento", "lancamento") }) return null
            val normalized = normalizePdfText(line)
            DECLARATION.matchEntire(normalized)?.let { return it.groupValues[1] }
            return SPACED_DECLARATION.matchEntire(normalized)?.groupValues?.get(1)?.takeIf {
                BRL_DECLARATION.matches(it) || CURRENCY_VALUE.matches(it)
            }
        }
    }
}
