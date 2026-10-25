package br.com.otavioesteves.finances.data.statement

import kotlin.math.abs

internal data class PdfWord(
    val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float,
    val confidence: Float = 100f
) {
    val centerY get() = (top + bottom) / 2f
    val height get() = (bottom - top).coerceAtLeast(1f)
}

/** Rebuild rows from word coordinates, preserving empty debit/credit cells with tabs. */
internal class PdfLayoutReconstructor {
    private data class Column(val name: String, val left: Float, val right: Float)
    private var columns = emptyList<Column>()

    fun reconstruct(words: List<PdfWord>, width: Float): String {
        require(width > 0 && words.size <= 100_000) { "Página PDF excede o limite de análise." }
        val rows = mutableListOf<MutableList<PdfWord>>()
        words.filter { it.text.isNotBlank() }.sortedWith(compareBy<PdfWord> { it.centerY }.thenBy { it.left }).forEach { word ->
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().centerY - word.centerY) <= minOf(row.first().height, word.height) * 0.55f) row += word
            else rows += mutableListOf(word)
        }
        return rows.joinToString("\n") { row ->
            val ordered = row.sortedBy { it.left }
            val text = ordered.joinToString(" ") { it.text }
            val normalized = normalizePdfText(text)
            if (METADATA.containsMatchIn(normalized)) return@joinToString text
            if (DATE_HEADER.containsMatchIn(normalized) && DESCRIPTION.containsMatchIn(normalized) && VALUE.containsMatchIn(normalized)) {
                val header = mutableListOf<Column>()
                ordered.forEach { word ->
                    val label = normalizePdfText(word.text).substringBefore('(').trim(':', '.', '(', ')')
                    val kind = when {
                        label in listOf("data", "dt", "dia") -> "data"
                        label in listOf("descricao", "historico", "estabelecimento") -> "descricao"
                        label in listOf("lancamento", "lancamentos") && header.none { it.name == "descricao" } -> "descricao"
                        label in listOf("documento", "doc", "nr", "numero") -> "documento"
                        label == "hora" -> "hora"
                        label == "valor" -> "valor"
                        label == "debito" -> "debito"
                        label == "credito" -> "credito"
                        label == "saldo" -> "saldo"
                        label in listOf("tipo", "d/c", "c/d") -> "tipo"
                        else -> null
                    }
                    if (kind != null) header += Column(kind, word.left / width, word.right / width)
                    else if (header.isNotEmpty()) {
                        val previous = header.removeAt(header.lastIndex)
                        val name = if (previous.name.startsWith("data") && label in listOf("movimento", "mov", "balancete"))
                            "data $label" else previous.name
                        header += previous.copy(name = name, right = word.right / width)
                    }
                }
                val description = header.firstOrNull { it.name == "descricao" }
                val firstMoney = header.firstOrNull { it.name in MONEY_COLUMNS }
                if (description != null && firstMoney != null && firstMoney.left - description.right > 0.025f) {
                    columns = header
                    return@joinToString header.joinToString("\t") { it.name }
                }
            }
            if (columns.isEmpty() || (!PdfDateResolver.DATE_PREFIX.containsMatchIn(normalized) && !MONEY.containsMatchIn(text))) text
            else {
                // Non-transaction headings/footers keep their original text.
                if (!PdfDateResolver.DATE_PREFIX.containsMatchIn(normalized) && ordered.first().left / width < columns.first().left - 0.03f) return@joinToString text
                val cells = Array(columns.size) { mutableListOf<String>() }
                ordered.forEach { word ->
                    val x = word.left / width
                    val index = if (MONEY.matches(word.text)) {
                        columns.indices.filter { columns[it].name in MONEY_COLUMNS }
                            .minByOrNull { abs(word.right / width - columns[it].right) } ?: 0
                    } else {
                        columns.indices.lastOrNull { x >= columns[it].left - 0.02f } ?: 0
                    }
                    cells[index] += word.text
                }
                cells.joinToString("\t") { it.joinToString(" ") }
            }
        }
    }

    companion object {
        private val DESCRIPTION = Regex("\\b(?:descricao|historico|estabelecimento|lancamentos?)\\b")
        private val DATE_HEADER = Regex("^(?:data|dt\\.?|dia)\\s+")
        private val METADATA = Regex("^(?:ano|fatura|periodo|referencia|emissao|vencimento|saldo|total|valor total|pagamento minimo|limite)\\b")
        private val VALUE = Regex("\\b(?:valor|debito|credito)\\b")
        private val MONEY = Regex("(?:[+-]?R\\$\\s*)?[+(\\-]?\\d[\\d.]*,\\d{2}[)+\\-]?(?:[CD]|CR|DR)?", RegexOption.IGNORE_CASE)
        private val MONEY_COLUMNS = setOf("valor", "debito", "credito", "saldo")
    }
}
