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
    private var descriptionPrefix = ""

    fun finish() {
        require(descriptionPrefix.isBlank()) { "Descrição sem lançamento na última página do PDF." }
    }

    fun reconstruct(words: List<PdfWord>, width: Float): String {
        require(width > 0 && words.size <= 100_000) { "Página PDF excede o limite de análise." }
        val rows = mutableListOf<MutableList<PdfWord>>()
        words.filter { it.text.isNotBlank() }.sortedWith(compareBy<PdfWord> { it.centerY }.thenBy { it.left }).forEach { word ->
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().centerY - word.centerY) <= maxOf(
                    minOf(row.first().height, word.height) * 0.55f, maxOf(row.first().height, word.height) * 0.25f
                )) row += word
            else rows += mutableListOf(word)
        }
        splitCardColumns(rows, width)?.let { return it }
        positionedStatement(rows, width)?.let { return it }
        return rows.joinToString("\n") { row ->
            val ordered = row.sortedBy { it.left }
            val text = ordered.joinToString(" ") { it.text }
            val normalized = normalizePdfText(text)
            if (METADATA.containsMatchIn(normalized)) return@joinToString text
            if (DATE_HEADER.containsMatchIn(normalized) && DESCRIPTION.containsMatchIn(normalized) && VALUE.containsMatchIn(normalized)) {
                val header = mutableListOf<Column>()
                ordered.forEachIndexed { wordIndex, word ->
                    val label = normalizePdfText(word.text).substringBefore('(').trim(':', '.', '(', ')')
                    val kind = when {
                        label in listOf("data", "dt", "dia") -> "data"
                        label in listOf("descricao", "historico", "estabelecimento", "transacoes") -> "descricao"
                        label in listOf("lancamento", "lancamentos") && header.none { it.name == "descricao" } -> "descricao"
                        label in listOf("documento", "doc", "nr", "numero") -> "documento"
                        label == "hora" -> "hora"
                        label == "valor" -> if (StatementCurrency.hasForeignCurrency(word.text)) "valor estrangeiro" else "valor"
                        label == "r$" && header.lastOrNull()?.name !in MONEY_COLUMNS -> "valor"
                        (label == "us$" || StatementCurrency.hasForeignCurrency(label)) && (header.lastOrNull()?.name != "valor" ||
                            ordered.getOrNull(wordIndex - 1)?.text?.lowercase() == "r$") -> "valor estrangeiro"
                        label in listOf("credito/debito", "debito/credito") -> "valor"
                        label == "moeda" -> "moeda"
                        label == "debito" -> "debito"
                        label == "credito" -> "credito"
                        label == "saldo" -> "saldo"
                        label in listOf("tipo", "d/c", "c/d") -> "tipo"
                        else -> null
                    }
                    if (kind != null) header += Column(kind, word.left / width, word.right / width)
                    else if (header.isNotEmpty()) {
                        val previous = header.removeAt(header.lastIndex)
                        val name = when {
                            previous.name.startsWith("data") && label in listOf("movimento", "mov", "balancete") -> "data $label"
                            previous.name == "valor" && (label == "us$" || StatementCurrency.hasForeignCurrency(label)) -> "valor estrangeiro"
                            else -> previous.name
                        }
                        header += previous.copy(name = name, right = word.right / width)
                    }
                }
                require(header.none { it.name == "valor estrangeiro" } || header.any { it.name in MONEY_COLUMNS && it.name != "valor estrangeiro" }) {
                    "Tabela em moeda estrangeira sem coluna de conversão em reais."
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

    /** Two newspaper-style purchase columns must be read independently, including a continuation above the header. */
    private fun splitCardColumns(rows: List<List<PdfWord>>, width: Float): String? {
        if (rows.none { row -> normalizePdfText(row.sortedBy { it.left }.joinToString(" ") { it.text }).let {
                it.startsWith("data ") && DESCRIPTION.containsMatchIn(it) && VALUE.containsMatchIn(it)
            } }) return null
        val dates = rows.flatten().filter { SHORT_DATE.matches(it.text) }
        val left = dates.filter { it.left < width * 0.15f }
        val right = dates.filter { it.left in width * 0.45f..width * 0.60f }
        val repeatedHeader = rows.any { row ->
            row.count { normalizePdfText(it.text) == "data" } == 2 &&
                row.count { normalizePdfText(it.text) in listOf("descricao", "transacoes", "estabelecimento") } == 2
        }
        if (left.isEmpty() || right.isEmpty() || (!repeatedHeader && (left.size < 2 || right.size < 2))) return null
        val boundary = right.minOf { it.left } - right.minOf { it.height } * 0.5f
        val top = dates.minOf { it.top }
        val metadata = rows.filter { row -> row.all { it.bottom < top } }
            .joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
        val body = rows.filter { row -> row.any { it.bottom >= top } }
        return metadata + "\n" + listOf(false, true).joinToString("\n") { rightColumn ->
            body.map { row -> row.filter { (it.left >= boundary) == rightColumn }.sortedBy { it.left } }
                .filter { it.isNotEmpty() }.joinToString("\n") { row -> row.joinToString(" ") { it.text } }
        }
    }

    /** These statement tables center the date on a multi-line description; group the whole visual block first. */
    private fun positionedStatement(rows: List<List<PdfWord>>, width: Float): String? {
        val headerIndex = rows.indexOfFirst { row ->
            val text = normalizePdfText(row.sortedBy { it.left }.joinToString(" ") { it.text })
            (text.startsWith("data descricao") && "id da operacao" in text && "saldo" in text) ||
                (text.startsWith("lancamentos ") && "historico/complemento" in text && "saldo" in text)
        }
        if (headerIndex < 0) return null
        val headerWords = rows[headerIndex].sortedBy { it.left }
        val header = headerWords.mapNotNull { word ->
            val name = when (normalizePdfText(word.text)) {
                "data", "lancamentos" -> "data"
                "descricao", "historico/complemento" -> "descricao"
                "id", "nr", "nr.", "nr.doc", "nr.doc." -> "documento"
                "favorecido" -> "favorecido"
                "cpf/cnpj" -> "identificador"
                "valor" -> "valor"
                "saldo" -> "saldo"
                else -> null
            }
            name?.let { Column(it, word.left / width, word.right / width) }
        }
        require(header.count { it.name == "data" } == 1 && header.count { it.name == "descricao" } == 1 &&
            header.count { it.name == "valor" } == 1 && header.count { it.name == "saldo" } == 1) {
            "Colunas do extrato PDF não foram identificadas."
        }
        val descriptionIndex = header.indexOfFirst { it.name == "descricao" }
        val blocks = mutableListOf<MutableList<PdfWord>>()
        var previous: List<PdfWord>? = null
        for (row in rows.drop(headerIndex + 1)) {
            val text = normalizePdfText(row.sortedBy { it.left }.joinToString(" ") { it.text })
            if (PAGE_NUMBER.matches(text)) continue
            if (text.startsWith("data de geracao:") || text.startsWith("alo caixa:")) break
            val last = previous
            val gap = if (last == null) Float.MAX_VALUE else row.minOf { it.top } - last.maxOf { it.bottom }
            val close = last != null && gap <= minOf(last.minOf { it.height }, row.minOf { it.height }) * 2.1f
            // Even densely printed neighbouring transactions remain separate blocks.
            val dated = row.any { it.left / width < header[descriptionIndex].left && FULL_DATE.matches(it.text) }
            val previousDated = blocks.lastOrNull()?.any { FULL_DATE.matches(it.text) } == true
            if (close && !(dated && previousDated)) blocks.last() += row.sortedBy { it.left }
            else blocks += row.sortedBy { it.left }.toMutableList()
            previous = row
        }
        val output = mutableListOf<String>()
        output += rows.take(headerIndex).joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
        output += header.joinToString("\t") { it.name }
        for (block in blocks) {
            val cells = Array(header.size) { mutableListOf<String>() }
            val monetary = block.filter { MONEY.matches(it.text) }
            block.forEach { word ->
                if (word.text == "R$") return@forEach
                val numeric = if (MONEY.matches(word.text)) word else if (word.text in listOf("C", "D", "+", "-"))
                    monetary.filter { abs(it.centerY - word.centerY) < word.height &&
                        minOf(abs(it.right - word.left), abs(word.right - it.left)) < word.height * 2 }
                        .minByOrNull { abs(it.right - word.left) } else null
                val index = if (numeric != null) header.indices.filter { header[it].name in MONEY_COLUMNS }
                    .minBy { abs(numeric.right / width - header[it].right) }
                else header.indices.lastOrNull { word.left / width >= header[it].left - 0.015f } ?: 0
                cells[index] += word.text
            }
            val dated = cells[0].any { FULL_DATE.matches(it) }
            if (!dated) {
                require(cells.indices.all { it == descriptionIndex || cells[it].isEmpty() }) {
                    "Linha sem data ou colunas ambíguas no extrato PDF."
                }
                descriptionPrefix = (descriptionPrefix + " " + cells[descriptionIndex].joinToString(" ")).trim()
                continue
            }
            if (descriptionPrefix.isNotEmpty()) {
                cells[descriptionIndex].add(0, descriptionPrefix)
                descriptionPrefix = ""
            }
            output += cells.joinToString("\t") { it.joinToString(" ") }
        }
        return output.joinToString("\n")
    }

    companion object {
        private val DESCRIPTION = Regex("\\b(?:descricao|historico|estabelecimento|transacoes|lancamentos?)\\b")
        private val DATE_HEADER = Regex("^(?:data|dt\\.?|dia)\\s+")
        private val METADATA = Regex("^(?:ano|fatura|periodo|referencia|emissao|vencimento|saldo|total|valor total|pagamento minimo|limite)\\b")
        private val VALUE = Regex("\\b(?:valor|debito|credito)\\b|r\\$")
        private val MONEY = Regex("(?:[+-]?R\\$\\s*)?[+(\\-]?\\d[\\d.]*,\\d{2}[)+\\-]?(?:[CD]|CR|DR)?", RegexOption.IGNORE_CASE)
        private val MONEY_COLUMNS = setOf("valor", "debito", "credito", "saldo", "valor estrangeiro")
        private val SHORT_DATE = Regex("\\d{2}[/.]\\d{2}")
        private val FULL_DATE = Regex("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}")
        private val PAGE_NUMBER = Regex("(?:pagina\\s+)?\\d+\\s*(?:/|de)\\s*\\d+")
    }
}
