package br.com.otavioesteves.finances.domain.model

import java.time.LocalDate

const val AMOUNT_TOTAL_LIMIT_MESSAGE = "O total das movimentações excede o limite suportado. Reduza os valores ou exclua lançamentos antes de continuar."

/** A gross total within Long also bounds every subset, chart total and signed balance. */
fun checkedMagnitudeTotal(amountCents: Sequence<Long>): Money {
    var total = 0L
    for (amount in amountCents) {
        require(amount >= 0) { "O valor da transação não pode ser negativo." }
        require(amount <= Long.MAX_VALUE - total) { AMOUNT_TOTAL_LIMIT_MESSAGE }
        total += amount
    }
    return Money.fromCents(total)
}

fun requireSupportedTransactionDate(date: LocalDate): LocalDate = date.also {
    require(it.year in 1..9999) { "A data da transação deve ter um ano entre 1 e 9999." }
}
