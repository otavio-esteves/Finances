package br.com.otavioesteves.finances.data.statement

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Decode the declared OFX encoding before parsing descriptions or calculating fingerprints. */
internal fun decodeStatementText(content: ByteArray, ofx: Boolean): String {
    val utf8Bom = content.take(3) == listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
    val utf16Bom = content.take(2).let { it == listOf(0xff.toByte(), 0xfe.toByte()) || it == listOf(0xfe.toByte(), 0xff.toByte()) }
    val charset = when {
        utf16Bom -> Charsets.UTF_16
        utf8Bom -> Charsets.UTF_8
        !ofx -> Charsets.UTF_8
        else -> {
            // OFX SGML headers and XML encoding declarations use ASCII characters.
            val prefix = content.take(4096).toByteArray().toString(Charsets.ISO_8859_1)
            val header = prefix.substringBefore('<')
            val xmlEncoding = Regex("(?i)^\\s*<\\?xml\\b[^>]*\\bencoding\\s*=\\s*['\"]([^'\"]+)['\"]").find(prefix)?.groupValues?.get(1)
            fun field(name: String): String? {
                val values = Regex("(?im)^$name:[ \\t]*([^\\r\\n]+)").findAll(header)
                    .map { it.groupValues[1].trim().uppercase() }.toList()
                require(values.size <= 1) { "Cabeçalho OFX com declaração repetida de $name." }
                return values.singleOrNull()
            }
            val encoding = field("ENCODING")
            val codePage = field("CHARSET")
            when {
                xmlEncoding != null -> supportedCharset(xmlEncoding)
                encoding == "UNICODE" || encoding == "UTF-8" || encoding == "UTF8" -> {
                    require(codePage == null || codePage == "NONE" || supportedCharset(codePage) == Charsets.UTF_8) {
                        "Cabeçalho OFX com codificações contraditórias."
                    }
                    Charsets.UTF_8
                }
                codePage != null && codePage != "NONE" -> supportedCharset(codePage)
                encoding == "USASCII" || encoding == "ASCII" -> Charsets.US_ASCII
                encoding == null || encoding == "NONE" -> Charsets.UTF_8
                else -> supportedCharset(encoding)
            }
        }
    }
    return try {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(content)).toString().removePrefix("\uFEFF")
    } catch (error: CharacterCodingException) {
        throw IllegalArgumentException("O arquivo contém texto inválido para a codificação ${charset.name()}. Exporte-o novamente com a codificação correta.", error)
    }
}

private fun supportedCharset(name: String): Charset = try {
    Charset.forName(when (name.uppercase()) {
        "1252", "CP1252" -> "windows-1252"
        "8859-1", "8859_1", "ISO8859-1" -> "ISO-8859-1"
        "65001" -> "UTF-8"
        else -> name
    })
} catch (error: IllegalArgumentException) {
    throw IllegalArgumentException("Codificação do arquivo não suportada: $name.", error)
}
