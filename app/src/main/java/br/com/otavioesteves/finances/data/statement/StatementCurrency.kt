package br.com.otavioesteves.finances.data.statement

import java.util.Currency
import java.util.Locale
import java.text.Normalizer

/** Currency declarations must never silently become BRL amounts. */
internal object StatementCurrency {
    private val foreignCodes = Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() - "BRL"
    private val tokens = Regex("[A-Za-z]{3}")
    private val diacritics = Regex("\\p{M}+")
    private val scaledUnits = Regex(
        "(?<!\\p{L})(?:centavos?|cents?|minor[\\s_-]+units?|mil|milhares|milhoes|thousands?|millions?|percent(?:age)?|por cento)(?!\\p{L})|%"
    )
    private val majorCsvHeader = Regex(
        "(?:(?:transaction )?(?:valor|amount|value)(?: (?:da transacao|do lancamento|total|liquido|bruto|pago|paid))?)" +
            "(?: (?:em |in )?(?:reais|real brasileiro|brl|r\\$)| ?\\((?:reais|real brasileiro|brl|r\\$)\\))?"
    )
    private val foreignNames = Regex(
        "(?<!\\p{L})(?:dolar(?:es)?|dollars?|euros?|libras?|pounds?|sterling|ienes?|yens?|" +
            "yuans?|renminbi|francos?|francs?|pesos?|rupias?|rupees?|rublos?|rubles?|won)(?!\\p{L})"
    )
    fun hasForeignCurrency(label: String): Boolean {
        val withoutBrl = label.replace(Regex("(?i)(?<![A-Za-z])R\\$"), "BRL")
        val normalized = Normalizer.normalize(withoutBrl, Normalizer.Form.NFD)
            .replace(diacritics, "").lowercase(Locale.ROOT)
        return foreignNames.containsMatchIn(normalized) || tokens.findAll(withoutBrl).any {
            it.value.uppercase(Locale.ROOT) in foreignCodes &&
                (it.range.first == 0 || !withoutBrl[it.range.first - 1].isLetter()) &&
                (it.range.last == withoutBrl.lastIndex || !withoutBrl[it.range.last + 1].isLetter())
        } || withoutBrl.any { Character.getType(it) == Character.CURRENCY_SYMBOL.toInt() }
    }
    fun requireBrl(value: String) {
        require(value.trim().uppercase(Locale.ROOT) in setOf("BRL", "R$")) {
            "Moeda estrangeira ou não reconhecida sem conversão inequívoca em reais."
        }
    }

    /** Major-unit parsing must not silently rescale cents, thousands or percentages. */
    fun requireMajorUnit(label: String) {
        val normalized = Normalizer.normalize(label, Normalizer.Form.NFD).replace(diacritics, "").lowercase(Locale.ROOT)
        require(!scaledUnits.containsMatchIn(normalized)) {
            "Unidade monetária não suportada. Use valores em reais; centavos só são aceitos no CSV completo exportado pelo Finances."
        }
    }

    fun requireCsvMajorUnit(label: String) {
        requireMajorUnit(label)
        val normalized = Normalizer.normalize(label, Normalizer.Form.NFD).replace(diacritics, "")
            .lowercase(Locale.ROOT).replace(Regex("[\\s\\p{Z}_]+"), " ").trim()
        require(majorCsvHeader.matches(normalized)) {
            "Cabeçalho monetário não reconhecido. Use uma coluna Valor em reais (BRL), sem outra unidade ou escala."
        }
    }
}
