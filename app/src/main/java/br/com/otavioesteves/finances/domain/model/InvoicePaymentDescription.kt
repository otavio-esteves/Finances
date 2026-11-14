package br.com.otavioesteves.finances.domain.model

import java.text.Normalizer
import java.util.Locale

/** A payment settles the card; a purchase refund must retain its own financial direction. */
fun isInvoicePaymentDescription(description: String, allowDatedPayment: Boolean = false): Boolean {
    val normalized = Normalizer.normalize(description, Normalizer.Form.NFD)
        .replace(DIACRITICS, "").lowercase(Locale.ROOT).trim().replace(WHITESPACE, " ")
    if (REFUND.containsMatchIn(normalized)) return false
    return INVOICE_PAYMENT.containsMatchIn(normalized) ||
        (allowDatedPayment && DATED_PAYMENT.containsMatchIn(normalized))
}

private val DIACRITICS = Regex("\\p{M}+")
private val WHITESPACE = Regex("[\\s\\p{Z}]+")
private val REFUND = Regex("\\b(?:estornos?|estornad[ao]s?|devolucao|devolucoes|reembolsos?|cancelamentos?|cancelad[ao]s?)\\b")
private val INVOICE_PAYMENT = Regex("\\b(?:pagamento\\s+(?:(?:da|de)\\s+)?fatura|pag(?:amento)?\\s+(?:(?:do|de)\\s+)?cartao)\\b")
private val DATED_PAYMENT = Regex("^pagamento\\s+em\\s+\\d{1,2}\\s+[a-z]{3}\\b")
