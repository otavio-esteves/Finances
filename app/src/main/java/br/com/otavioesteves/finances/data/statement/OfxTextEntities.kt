package br.com.otavioesteves.finances.data.statement

/** Decode text once, without an XML parser, DTDs, or external entity resolution. */
internal fun decodeOfxTextEntities(value: String): String = OFX_ENTITY.replace(value) { match ->
    when (val entity = match.groupValues[1]) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        else -> {
            require(entity.startsWith('#')) { "Entidade de texto OFX não suportada: &${entity};" }
            require(NUMERIC_ENTITY.matches(entity)) { "Referência de caractere inválida no OFX: &${entity};" }
            val hex = entity.startsWith("#x", ignoreCase = true)
            val number = entity.drop(if (hex) 2 else 1).toIntOrNull(if (hex) 16 else 10)
            require(number != null && (number in listOf(9, 10, 13) || number in 0x20..0xD7FF ||
                number in 0xE000..0xFFFD || number in 0x10000..0x10FFFF)) {
                "Referência de caractere inválida no OFX: &${entity};"
            }
            String(Character.toChars(number))
        }
    }
}

// Bare ampersands in older SGML files remain literal; declared entities are never expanded.
private val OFX_ENTITY = Regex("&(#(?:[xX])?[^;&\\s]*|[A-Za-z][A-Za-z0-9]*);")
private val NUMERIC_ENTITY = Regex("#(?:[0-9]+|[xX][0-9a-fA-F]+)")
