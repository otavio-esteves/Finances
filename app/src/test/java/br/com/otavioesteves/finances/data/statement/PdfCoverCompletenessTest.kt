package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import org.junit.Assert.*
import org.junit.Test

/** A reconciled amount cannot prove completeness: a lost zero row leaves every total unchanged. */
class PdfCoverCompletenessTest {
    private val parser = PdfStatementParser { "" }

    @Test fun datedCoverRowsCannotDisappearBehindMatchingAccountTotalsOrBeneficiaryHeaders() {
        val totals = """
            Extrato de conta
            Período: 01/11/2026 a 30/11/2026
            %s
            Entradas: R$ 100,00
            Saldo inicial: R$ 10,00 Saldo final: R$ 90,00
            Saídas: R$ -20,00
            DETALHE DOS MOVIMENTOS
            data\tdescricao\tdocumento\tvalor\tsaldo
            01/11/2026\tCrédito\t111\tR$ 100,00\tR$ 110,00
            02/11/2026\tCompra\t222\tR$ -20,00\tR$ 90,00
        """.trimIndent().replace("\\t", "\t")
        val beneficiary = "Extrato da conta\n%s\ndata\tdescricao\tfavorecido\tidentificador\tvalor\tsaldo\n" +
            "02/11/2026\tCompra\tLoja Exemplo\t222\tR$ -20,00\tR$ 90,00"
        for (layout in listOf(totals, beneficiary)) {
            assertTrue(parser.parseText(layout.format("")).isNotEmpty())
            assertTrue(parser.parseText(layout.format("1.234,56")).isNotEmpty())
            for (row in listOf("01/11/2026 Movimento não conferido R$ 0,00",
                "01/11/2026\nMovimento fragmentado R$ 0,00", "01 / 11 / 2026 Movimento R$ 0,00")) {
                assertThrows(row, IllegalArgumentException::class.java) { parser.parseText(layout.format(row)) }
            }
        }
    }

    @Test fun zeroRowsBeforeRecognizedCardSectionsMustNotBeDropped() {
        val layouts = listOf(
            "Nubank\nFatura EMISSÃO E ENVIO 31 JAN 2025\n%s\nTRANSAÇÕES DE 31 DEZ A 31 JAN\nPessoa Fictícia R$ 10,00\n02 JAN Compra R$ 10,00",
            "Banco Inter\nFatura\n%s\nDespesas da fatura\n02 de jan. 2025 Compra - R$ 10,00",
            "Banco do Brasil\nFatura\n%s\nDetalhamento da sua fatura\n02/01/2025 Compra R$ 10,00",
            "Caixa\nFatura\n%s\nDemonstrativo\n02/01/2025 Compra R$ 10,00",
            "Santander\nFatura\n%s\nDetalhamento da fatura\n02/01/2025 Compra R$ 10,00",
            "Bradesco\nFatura\n%s\nLançamentos\n02/01/2025 Compra R$ 10,00"
        )
        for (layout in layouts) {
            assertEquals(layout, 1, parser.parseText(layout.format(""), StatementKind.CARD_BILL).size)
            for (row in listOf("01/01/2025 Movimento R$ 0,00", "01 de jan. 2025 Movimento - R$ 0,00")) {
                assertThrows(layout, IllegalArgumentException::class.java) {
                    parser.parseText(layout.format(row), StatementKind.CARD_BILL)
                }
            }
        }
    }

    @Test fun anEarlierPicpayPageCannotBeAssumedToContainOnlyCoverInformation() {
        val body = "\n\u000c\nTransações Nacionais\n02/01 Compra R$ 10,00"
        val cover = "PicPay\nFechamento: 29/01/2025\n"
        assertEquals(1, parser.parseText(cover + body, StatementKind.CARD_BILL).size)
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseText(cover + "01/01/2025 Movimento R$ 0,00" + body, StatementKind.CARD_BILL)
        }
        // An explicitly identified future section remains distinguishable from current transactions.
        assertEquals(1, parser.parseText(cover + "Próximas faturas\n01/02/2025 Oferta R$ 0,00" + body,
            StatementKind.CARD_BILL).size)
    }
}
