package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.*
import org.junit.Test

class StatementPaymentTest {
    @Test fun genericBankPaymentLabelsRequireCardContext() {
        for (label in listOf("Pagamento da fatura", "PAGAMENTO DE FATURA", "Pagamento fatura", "PAG BOLETO BANCARIO", "PAGTO DEBITO AUTOMATICO", "PGTO DEBITO CONTA 0000", "OBRIGADO PELO PAGAMENTO")) {
            assertFalse(label, isCardPaymentDescription(label))
            assertTrue(label, isCardPaymentDescription(label, includeBankLabels = true))
        }
    }

    @Test fun utilityInvoicesDoNotIdentifyCardPaymentsEvenInCardContext() {
        for (label in listOf("Pagamento de fatura de energia", "PAGAMENTO FATURA CLARO", "Pagamento da fatura de água")) {
            assertFalse(label, isCardPaymentDescription(label))
            assertFalse(label, isCardPaymentDescription(label, includeBankLabels = true))
        }
        for (label in listOf("Pagamento do cartão", "Pag cartão", "Pagamento da fatura do cartão Nubank")) {
            assertTrue(label, isCardPaymentDescription(label))
        }
    }

    @Test fun cardPaymentRefundsRemainRefundsEvenWithBankLabels() {
        for (label in listOf("PAGTO DEBITO AUTOMATICO ESTORNADO", "Estorno PAG BOLETO BANCARIO")) {
            assertFalse(label, isCardPaymentDescription(label, includeBankLabels = true))
        }
    }
}
