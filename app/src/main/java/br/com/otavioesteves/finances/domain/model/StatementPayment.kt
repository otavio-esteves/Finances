package br.com.otavioesteves.finances.domain.model

import java.text.Normalizer
import java.util.Locale

/** Explicit payment labels shared by bank and card imports; refunds retain their own type. */
fun isCardPaymentDescription(description: String, includeBankLabels: Boolean = false, includeDatedPayments: Boolean = false): Boolean {
    val normalized = Normalizer.normalize(description.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(DIACRITICS, "").trim().replace(WHITESPACE, " ")
    return (CARD_PAYMENT.containsMatchIn(normalized) ||
        (includeBankLabels && (INVOICE_PAYMENT.matches(normalized) || BANK_PAYMENT.containsMatchIn(normalized))) ||
        (includeDatedPayments && DATED_PAYMENT.containsMatchIn(normalized))) &&
        !REFUND.containsMatchIn(normalized)
}

private val DIACRITICS = Regex("\\p{M}+")
private val REFUND = Regex("\\b(?:estornos?|estornad[ao]s?|devolucao|devolucoes|reembolsos?|cancelamentos?|cancelad[ao]s?)\\b")
private val CARD_PAYMENT = Regex(
    "^(?:pagamento\\s+(?:d[ae]\\s+)?fatura\\s+(?:d[oe]\\s+)?cartao|pag(?:amento)?\\s+(?:(?:do|de)\\s+)?cartao)\\b"
)
// A utility invoice is also a "fatura". Only an exact generic label in a card
// document can imply a card payment without explicitly mentioning the card.
private val INVOICE_PAYMENT = Regex("pagamento\\s+(?:d[ae]\\s+)?fatura")
private val BANK_PAYMENT = Regex("^(?:obrigado pelo pagamento|pagto debito automatico|pgto\\.? debito conta|pagto antecipado pix|pag boleto bancario)\\b")

private val WHITESPACE = Regex("[\\s\\p{Z}]+")
private val DATED_PAYMENT = Regex("^pagamento\\s+em\\s+\\d{1,2}\\s+[a-z]{3}\\b")
