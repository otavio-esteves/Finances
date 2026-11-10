package br.com.otavioesteves.finances.data.statement

import java.util.Currency
import java.util.Locale

/** Currency declarations must never silently become BRL amounts. */
internal object StatementCurrency {
    private val foreignCodes = Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() - "BRL"
    private val tokens = Regex("[A-Za-z]{3}")
    fun hasForeignCurrency(label: String): Boolean {
        val withoutBrl = label.replace(Regex("(?i)(?<![A-Za-z])R\\$"), "BRL")
        return tokens.findAll(withoutBrl).any {
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
}
