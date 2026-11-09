package br.com.otavioesteves.finances.domain.model

import java.text.Normalizer
import java.util.Locale

/** Explicit payment labels shared by bank and card imports; refunds retain their own type. */
fun isCardPaymentDescription(description: String, includeBankLabels: Boolean = false): Boolean {
    val normalized = Normalizer.normalize(description.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(DIACRITICS, "").trim()
    return (CARD_PAYMENT.containsMatchIn(normalized) || (includeBankLabels && BANK_PAYMENT.containsMatchIn(normalized))) &&
        !REFUND.containsMatchIn(normalized)
}

private val DIACRITICS = Regex("\\p{M}+")
private val REFUND = Regex("\\b(?:estorno|estornad[oa]|devolucao|reembolso)\\b")
private val CARD_PAYMENT = Regex(
    "^(?:pagamento\\s+(?:d[ae]\\s+)?fatura|pag(?:amento)?\\s+(?:do\\s+)?cartao)\\b"
)
private val BANK_PAYMENT = Regex("^(?:obrigado pelo pagamento|pagto debito automatico|pgto\\.? debito conta|pagto antecipado pix|pag boleto bancario)\\b")
