package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.*
import org.junit.Test

class StatementPaymentTest {
    @Test fun genericBankPaymentLabelsRequireCardContext() {
        for (label in listOf("PAG BOLETO BANCARIO", "PAGTO DEBITO AUTOMATICO", "PGTO DEBITO CONTA 0000", "OBRIGADO PELO PAGAMENTO")) {
            assertFalse(label, isCardPaymentDescription(label))
            assertTrue(label, isCardPaymentDescription(label, includeBankLabels = true))
        }
    }

    @Test fun cardPaymentRefundsRemainRefundsEvenWithBankLabels() {
        for (label in listOf("PAGTO DEBITO AUTOMATICO ESTORNADO", "Estorno PAG BOLETO BANCARIO")) {
            assertFalse(label, isCardPaymentDescription(label, includeBankLabels = true))
        }
    }
}
