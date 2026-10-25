package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PdfTableParserTest {
    private val parser = PdfStatementParser { "" }

    @Test
    fun explicitDebitCreditCellsPreserveEmptyColumnsAndExcludeBalances() {
        val entries = parser.parseText("Ano: 2026\nData\tHistórico\tDébito\tCrédito\tSaldo\n" +
            "10/01\tPix Farmácia\t20,00\t\t980,00\n11/01\tSalário\t0,00\t3.000,00\t3.980,00")
        assertEquals(listOf(2000L, 300000L), entries.map { it.amount.cents })
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.INCOME), entries.map { it.type })
    }

    @Test
    fun movementDateAndDocumentColumnAreExplicit() {
        val entries = parser.parseText("Data balancete\tData movimento\tHistórico\tDocumento\tValor\tSaldo\n" +
            "12/01/2026\t10/01/2026\tCompra\t12345\t20,00 D\t980,00")
        assertEquals(LocalDate.of(2026, 1, 10), entries.single().date)
        assertEquals("Compra 12345", entries.single().description)
        assertEquals(2000L, entries.single().amount.cents)
    }

    @Test
    fun typedValueColumnDefinesDirection() {
        val entries = parser.parseText("Data\tDescrição\tValor\tTipo\n10/01/2026\tPix recebido\t20,00\tC")
        assertEquals(TransactionType.INCOME, entries.single().type)
    }

    @Test
    fun contradictoryOrIncompleteColumnsRejectTheEntireBatch() {
        listOf(
            "10/01/2026\tCompra\t20,00\t30,00\t980,00",
            "10/01/2026\tCompra\t+20,00\t\t980,00",
            "10/01/2026\tCompra\t\t-20,00\t980,00",
            "10/01/2026\tCompra\t\t\t980,00"
        ).forEach { row ->
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseText("Data\tDescrição\tDébito\tCrédito\tSaldo\n$row")
            }
        }
    }

    @Test
    fun wrappedTableDescriptionAndAmountAreNotDropped() {
        val entries = parser.parseText("Ano: 2026\nData\tDescrição\tDébito\tCrédito\n" +
            "10/01\tPagamento\t\t\ncontinuado\n\t\t20,00\t")
        assertEquals("Pagamento continuado", entries.single().description)
        assertEquals(2000L, entries.single().amount.cents)
    }

    @Test
    fun geometryKeepsBlankDebitAndCreditCellsInsteadOfReadingColumnsVertically() {
        fun w(text: String, x: Float, y: Float, right: Float = x + 40f) = PdfWord(text, x, y, right, y + 14f)
        val layout = PdfLayoutReconstructor().reconstruct(listOf(
            w("Data", 10f, 10f), w("Descrição", 100f, 10f, 170f), w("Débito", 400f, 10f, 450f),
            w("Crédito", 520f, 10f, 580f), w("Saldo", 650f, 10f, 700f),
            w("10/01/2026", 10f, 50f, 85f), w("Farmácia", 100f, 50f, 180f), w("20,00", 410f, 50f, 450f), w("980,00", 650f, 50f, 700f),
            w("11/01/2026", 10f, 80f, 85f), w("Salário", 100f, 80f, 170f), w("3.000,00", 510f, 80f, 580f), w("3.980,00", 620f, 80f, 700f)
        ), 800f)
        val entries = parser.parseText(layout)
        assertEquals(listOf(2000L, 300000L), entries.map { it.amount.cents })
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.INCOME), entries.map { it.type })
    }

    @Test
    fun twoUnlabelledDatesCannotChooseTheWrongMovementDate() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseText("10/01/2026 11/01/2026 Compra -20,00")
        }
    }

    @Test
    fun strictOcrParsingRefusesUnmatchedMonetaryRows() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseText("10/01/2026 Compra -20,00\nData ilegível Farmácia -30,00", strictUnmatchedMoney = true)
        }
        assertEquals(1, parser.parseText("Fatura\n10/01/2026 Compra 20,00\nTotal da fatura 20,00",
            StatementKind.CARD_BILL, strictUnmatchedMoney = true).size)
    }
}
