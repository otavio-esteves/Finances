package br.com.otavioesteves.finances.data.statement

import java.util.Locale

internal data class OfxTransactionFields(
    var amount: String? = null,
    var date: String? = null,
    var memo: String? = null,
    var name: String? = null
)

/** One forward scan supports XML and SGML leaves without building a second document or block list. */
internal fun ofxTransactions(text: String, checkCancelled: () -> Unit): Sequence<OfxTransactionFields> = sequence {
    var current: OfxTransactionFields? = null
    var originalCurrency = false
    for (tag in ofxTags(text, checkCancelled)) {
        checkCancelled()
        when (tag.name) {
            "ORIGCURRENCY" -> {
                require(!tag.selfClosing && originalCurrency == tag.closing) {
                    "Bloco ORIGCURRENCY inválido ou incompleto no OFX. Nenhum lançamento foi importado."
                }
                originalCurrency = !tag.closing
            }
            "STMTTRN" -> {
                require(!originalCurrency) { "Bloco ORIGCURRENCY incompleto no OFX." }
                require(!tag.selfClosing && (!tag.closing || current != null)) { "Transação OFX incompleta." }
                current?.let { yield(it) }
                current = if (tag.closing) null else OfxTransactionFields()
            }
            "BANKTRANLIST" -> if (tag.closing) {
                require(!originalCurrency) { "Bloco ORIGCURRENCY incompleto no OFX." }
                current?.let { yield(it) }
                current = null
            }
            "CURDEF", "CURSYM" -> if (!tag.closing && !originalCurrency) {
                StatementCurrency.requireBrl(tag.value(text))
            }
            "TRNAMT", "DTPOSTED", "MEMO", "NAME" -> if (!tag.closing) {
                current?.let { transaction ->
                    require(!originalCurrency) { "Campo de transação dentro de ORIGCURRENCY no OFX." }
                    when (tag.name) {
                        "TRNAMT" -> {
                            require(transaction.amount == null) { "Transação OFX com TRNAMT repetido. Nenhum lançamento foi importado." }
                            transaction.amount = tag.value(text)
                        }
                        "DTPOSTED" -> {
                            require(transaction.date == null) { "Transação OFX com DTPOSTED repetido. Nenhum lançamento foi importado." }
                            transaction.date = tag.value(text)
                        }
                        "MEMO" -> if (transaction.memo == null) transaction.memo = tag.value(text)
                        "NAME" -> if (transaction.name == null) transaction.name = tag.value(text)
                    }
                }
            }
        }
    }
    checkCancelled()
    require(!originalCurrency) { "Bloco ORIGCURRENCY incompleto no OFX. Nenhum lançamento foi importado." }
    current?.let { yield(it) }
}

private data class OfxTag(
    val name: String,
    val closing: Boolean,
    val selfClosing: Boolean,
    val valueStart: Int,
    val valueEnd: Int
) {
    fun value(text: String): String = if (selfClosing) "" else text.substring(valueStart, valueEnd)
}

private fun ofxTags(text: String, checkCancelled: () -> Unit): Sequence<OfxTag> = sequence {
    var index = 0
    var nextCheck = 0
    fun advance() {
        index++
        if (index >= nextCheck) {
            checkCancelled()
            nextCheck = index + 4096
        }
    }
    fun skipUntil(ending: String) {
        while (index < text.length && !text.startsWith(ending, index)) advance()
        require(index < text.length) { "Comentário ou declaração OFX incompleta." }
        repeat(ending.length) { advance() }
    }
    checkCancelled()
    while (index < text.length) {
        if (text[index] != '<') { advance(); continue }
        when {
            text.startsWith("<!--", index) -> { repeat(4) { advance() }; skipUntil("-->"); continue }
            text.startsWith("<?", index) -> { repeat(2) { advance() }; skipUntil("?>"); continue }
            text.startsWith("<!", index) -> throw IllegalArgumentException("Declarações DTD/CDATA não são aceitas no OFX.")
        }
        advance()
        val start = index
        var quote: Char? = null
        while (index < text.length && (text[index] != '>' || quote != null)) {
            val char = text[index]
            require(char != '<') { "Tag OFX inválida ou incompleta." }
            if (quote != null) { if (char == quote) quote = null }
            else if (char == '\'' || char == '"') quote = char
            advance()
        }
        require(index < text.length) { "Tag OFX incompleta." }
        val raw = text.substring(start, index).trim()
        val closing = raw.startsWith('/')
        val name = raw.removePrefix("/").takeWhile { !it.isWhitespace() && it != '/' }.uppercase(Locale.ROOT)
        require(name.isNotEmpty()) { "Tag OFX inválida." }
        advance()
        val valueStart = index
        while (index < text.length && text[index] != '<') advance()
        yield(OfxTag(name, closing, raw.endsWith('/'), valueStart, index))
    }
    checkCancelled()
}
