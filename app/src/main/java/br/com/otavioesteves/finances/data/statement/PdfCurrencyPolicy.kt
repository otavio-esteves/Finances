package br.com.otavioesteves.finances.data.statement

/** Read declarations from the original document, before bank-specific cover/section removal. */
internal class PdfCurrencyPolicy(original: String) {
    private val requiresExplicitBrl: Boolean
    /** Remove only the actual metadata occurrences, including a value on the next line. */
    val text: String

    init {
        val lines = original.lines()
        val metadata = BooleanArray(lines.size)
        var needsBrl = false
        for (index in lines.indices) {
            if (metadata[index]) continue
            var combined = normalizePdfText(lines[index])
            var value = declaration(combined)
            var end = index
            // Reassemble only a known label/currency phrase; never cross an unrelated row.
            while (end - index < 8 && combined.length <= 256 &&
                (value?.let { it.isEmpty() || isCurrencyValue(it) } ?: isLabelPrefix(combined))) {
                val next = (end + 1 until lines.size).firstOrNull { lines[it].isNotBlank() } ?: break
                val joined = "$combined ${normalizePdfText(lines[next])}"
                val candidate = declaration(joined)
                if (candidate == null && !isLabelPrefix(joined)) break
                if (candidate != null && candidate.isNotEmpty() && !isCurrencyValue(candidate)) break
                combined = joined
                value = candidate
                end = next
            }
            val declared = value ?: continue
            require(declared.isNotEmpty()) {
                "A declaração de moeda do PDF está incompleta. Exporte novamente com a moeda e os valores identificados."
            }
            for (line in index..end) metadata[line] = true
            StatementCurrency.requireMajorUnit(declared)
            if (!BRL_DECLARATION.matches(declared)) needsBrl = true
        }
        requiresExplicitBrl = needsBrl
        text = lines.indices.joinToString("\n") { if (metadata[it]) "" else lines[it] }
    }

    fun requireBrlValue(raw: String, columnIsBrl: Boolean = false, rowCurrency: String? = null) {
        require(!requiresExplicitBrl || columnIsBrl || hasBrlMarker(raw) ||
            rowCurrency?.let { BRL_DECLARATION.matches(normalizePdfText(it)) } == true) {
            "O PDF declara moeda estrangeira ou não reconhecida sem valor inequivocamente convertido em reais. Nenhum lançamento foi importado."
        }
    }

    companion object {
        private const val LABEL = "(?:moeda(?: (?:do documento|da fatura|do extrato))?|currency)"
        private const val VALUES_LABEL = "(?:(?:todos os )?valores? (?:expressos? |apresentados? |informados? )?em|(?:all )?(?:amounts?|values?) (?:expressed )?in)"
        private val DECLARATION = Regex("^$LABEL\\s*[:=]\\s*(.*)$")
        private val VALUES_DECLARATION = Regex("^$VALUES_LABEL(?:\\s*[:=]\\s*|\\s+|$)(.*)$")
        private val EMPTY_LABEL = Regex("^$LABEL$")
        private val LABELS = listOf("moeda", "moeda do documento", "moeda da fatura", "moeda do extrato", "currency") +
            listOf("valor", "valores", "todos os valores").flatMap { noun ->
                listOf("", "expressos ", "expresso ", "apresentados ", "apresentado ", "informados ", "informado ")
                    .map { "$noun ${it}em" }
            } + listOf("amount", "amounts", "value", "values", "all amounts", "all values")
                .flatMap { listOf("$it in", "$it expressed in") }
        private val BRL_PERCENT_LEGEND = Regex("^valores? em (r\\$|brl|reais) (?:\\| )?% do total(?: parcelado)?$")
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
            BRL_PERCENT_LEGEND.matchEntire(normalized)?.let { return it.groupValues[1] }
            DECLARATION.matchEntire(normalized)?.let { return it.groupValues[1] }
            VALUES_DECLARATION.matchEntire(normalized)?.let { return it.groupValues[1] }
            if (EMPTY_LABEL.matches(normalized)) return ""
            return SPACED_DECLARATION.matchEntire(normalized)?.groupValues?.get(1)?.takeIf {
                isCurrencyValue(it)
            }
        }

        private fun isCurrencyValue(value: String): Boolean = BRL_DECLARATION.matches(value) || CURRENCY_VALUE.matches(value)

        private fun isLabelPrefix(value: String): Boolean = LABELS.any { it == value || it.startsWith("$value ") }
    }
}
