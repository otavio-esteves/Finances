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
    private data class Column(val name: String, val left: Float, val right: Float, val label: String = name) {
        val isBrl get() = PdfCurrencyPolicy.hasBrlMarker(label)
        val isForeign get() = StatementCurrency.hasForeignCurrency(label) ||
            PARENTHESIZED_CODE.findAll(normalizePdfText(label)).any { it.groupValues[1] != "brl" }

        fun headerLabel(): String = if (isBrl && name in MONEY_COLUMNS) "$name (BRL)" else name
    }
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
                val header = readColumns(ordered, width)
                val description = header.firstOrNull { it.name == "descricao" }
                val firstMoney = header.firstOrNull { it.name in MONEY_COLUMNS }
                if (description != null && firstMoney != null && firstMoney.left - description.right > 0.025f) {
                    columns = header
                    return@joinToString header.joinToString("\t") { it.headerLabel() }
                }
            }
            if (columns.isEmpty() || (!PdfDateResolver.DATE_PREFIX.containsMatchIn(normalized) && !MONEY.containsMatchIn(text))) text
            else {
                // Non-transaction headings/footers keep their original text.
                if (!PdfDateResolver.DATE_PREFIX.containsMatchIn(normalized) && ordered.first().left / width < columns.first().left - 0.03f) return@joinToString text
                val cells = Array(columns.size) { mutableListOf<String>() }
                val monetary = ordered.filter { MONEY.matches(normalizeAmountToken(it.text)) }
                ordered.forEach { word ->
                    val x = word.left / width
                    val index = monetaryColumn(word, monetary, columns, width)
                        ?: columns.indices.lastOrNull { x >= columns[it].left - 0.02f } ?: 0
                    cells[index] += word.text
                }
                cells.joinToString("\t") { it.joinToString(" ") }
            }
        }
    }

    /** Keep the complete label until currency validation, including qualifiers split into PDF words. */
    private fun readColumns(words: List<PdfWord>, width: Float, centered: Boolean = false): List<Column> {
        require(words.size <= MAX_HEADER_WORDS) { "O cabeçalho do PDF excede o limite de análise local." }
        val header = mutableListOf<Column>()
        for (word in words) {
            require(word.text.length <= MAX_HEADER_LABEL_CHARS) { "O cabeçalho do PDF contém um campo muito extenso." }
            val normalized = normalizePdfText(word.text)
            val label = normalized.substringBefore('(').trim().trim(':', '.', '(', ')').trim()
            val previous = header.lastOrNull()
            val parenthesized = '(' in normalized || ')' in normalized ||
                (previous != null && previous.label.lastIndexOf('(') > previous.label.lastIndexOf(')'))
            val kind = when {
                label in listOf("data", "dt", "dia") || (centered && label == "lancamentos" && header.isEmpty()) -> "data"
                label in listOf("descricao", "historico", "estabelecimento", "transacoes") ||
                    (centered && label == "historico/complemento") -> "descricao"
                label in listOf("lancamento", "lancamentos") && header.none { it.name == "descricao" } -> "descricao"
                label in listOf("documento", "doc", "nr", "numero") || (centered && label in listOf("nr.doc", "id")) -> "documento"
                label == "hora" || (centered && label == "favorecido") -> label
                centered && label == "cpf/cnpj" -> "identificador"
                label == "valor" || label in listOf("credito/debito", "debito/credito") -> "valor"
                label == "r$" && previous?.name !in MONEY_COLUMNS -> "valor"
                label in listOf("moeda", "currency") -> "moeda"
                label in listOf("debito", "credito", "saldo") -> label
                label in listOf("tipo", "d/c", "c/d") -> "tipo"
                // A standalone foreign value can follow a BRL column. Parentheses qualify the same column.
                StatementCurrency.hasForeignCurrency(normalized) && !parenthesized &&
                    (previous?.name !in MONEY_COLUMNS || (previous?.name == "valor" && previous.isBrl)) -> "valor estrangeiro"
                else -> null
            }
            if (kind != null) {
                header += Column(kind, word.left / width, word.right / width, word.text)
            } else if (previous != null) {
                require(previous.label.length + 1 + word.text.length <= MAX_HEADER_LABEL_CHARS) {
                    "O cabeçalho do PDF contém um campo muito extenso."
                }
                header[header.lastIndex] = previous.copy(
                    name = when {
                        previous.name.startsWith("data") && label in listOf("movimento", "mov", "balancete") -> "data $label"
                        previous.name == "valor" && label == "estrangeiro" -> "valor estrangeiro"
                        else -> previous.name
                    },
                    right = word.right / width,
                    label = "${previous.label} ${word.text}"
                )
            }
        }
        val columns = header.map { column ->
            if (column.name !in MONEY_COLUMNS) return@map column
            StatementCurrency.requireMajorUnit(column.label)
            require(!column.isForeign || !column.isBrl) { "Coluna monetária com moedas conflitantes no PDF." }
            require(!column.isForeign || column.name in listOf("valor", "valor estrangeiro")) {
                "Coluna de débito, crédito ou saldo em moeda estrangeira sem conversão em reais."
            }
            if (column.isForeign) column.copy(name = "valor estrangeiro") else column
        }
        val foreignUnits = columns.any { it.name == "valor estrangeiro" || (it.name == "moeda" && it.isForeign) }
        val explicitBrl = columns.any { it.name == "valor" && it.isBrl } ||
            listOf("debito", "credito").all { name -> columns.any { it.name == name && it.isBrl } }
        require(!foreignUnits || explicitBrl) {
            "Tabela em moeda estrangeira sem coluna inequívoca de conversão em reais."
        }
        return columns
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
        val header = readColumns(headerWords, width, centered = true)
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
        output += header.joinToString("\t") { it.headerLabel() }
        for (block in blocks) {
            val cells = Array(header.size) { mutableListOf<String>() }
            val monetary = block.filter { MONEY.matches(normalizeAmountToken(it.text)) }
            block.forEach { word ->
                val index = monetaryColumn(word, monetary, header, width)
                    ?: header.indices.lastOrNull { word.left / width >= header[it].left - 0.015f } ?: 0
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

    /** A separate sign follows its adjacent amount, even when the header starts farther right. */
    private fun monetaryColumn(word: PdfWord, monetary: List<PdfWord>, header: List<Column>, width: Float): Int? {
        val token = normalizeAmountToken(word.text)
        val numeric = if (MONEY.matches(token)) word else {
            if (token !in AMOUNT_MARKERS && !CURRENCY_MARKER.matches(token)) return null
            val geometricColumn = header.lastOrNull { word.left / width >= it.left - 0.02f }
            if (geometricColumn?.name == "tipo") return null
            fun distance(amount: PdfWord) = maxOf(amount.left - word.right, word.left - amount.right, 0f)
            val adjacent = monetary.filter {
                abs(it.centerY - word.centerY) < maxOf(it.height, word.height) &&
                    distance(it) <= maxOf(it.height, word.height) * 2 &&
                    // C/D before a value can be the end of a merchant's name. In a
                    // dedicated type column they are kept above; otherwise they are suffixes.
                    (token !in SUFFIX_MARKERS || word.left >= it.right - 0.5f) &&
                    (token != "(" || word.right <= it.left + 0.5f)
            }.sortedBy(::distance)
            if (adjacent.size > 1) require(distance(adjacent[1]) - distance(adjacent[0]) > 0.5f) {
                "Sinal monetário entre valores ambíguos no PDF. Nenhum lançamento foi importado."
            }
            adjacent.firstOrNull() ?: return null
        }
        return header.indices.filter { header[it].name in MONEY_COLUMNS }
            .minByOrNull { abs(numeric.right / width - header[it].right) }
    }

    private fun normalizeAmountToken(text: String) = normalizePdfText(text).replace('\u2212', '-').replace('\u2013', '-')

    companion object {
        private const val MAX_HEADER_WORDS = 256
        private const val MAX_HEADER_LABEL_CHARS = 1024
        private val PARENTHESIZED_CODE = Regex("\\(\\s*([a-z]{3})\\s*\\)")
        private val DESCRIPTION = Regex("\\b(?:descricao|historico|estabelecimento|transacoes|lancamentos?)\\b")
        private val DATE_HEADER = Regex("^(?:data|dt\\.?|dia)\\s+")
        private val METADATA = Regex("^(?:ano|fatura|periodo|referencia|emissao|vencimento|saldo|total|valor total|pagamento minimo|limite)\\b")
        private val VALUE = Regex("\\b(?:valor|debito|credito)\\b|r\\$")
        private val MONEY = Regex("(?:[+-]?R\\$\\s*)?[+(\\-]?\\d[\\d.]*,\\d{2}[)+\\-]?(?:[CD]|CR|DR)?", RegexOption.IGNORE_CASE)
        private val MONEY_COLUMNS = setOf("valor", "debito", "credito", "saldo", "valor estrangeiro")
        private val AMOUNT_MARKERS = setOf("+", "-", "(", ")", "c", "d", "cr", "dr", "credito", "debito", "r$")
        private val SUFFIX_MARKERS = setOf(")", "c", "d", "cr", "dr", "credito", "debito")
        private val CURRENCY_MARKER = Regex("(?:[+\\-(]?r\\$|r\\$[+\\-(]?)")
        private val SHORT_DATE = Regex("\\d{2}[/.]\\d{2}")
        private val FULL_DATE = Regex("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}")
        private val PAGE_NUMBER = Regex("(?:pagina\\s+)?\\d+\\s*(?:/|de)\\s*\\d+")
    }
}
