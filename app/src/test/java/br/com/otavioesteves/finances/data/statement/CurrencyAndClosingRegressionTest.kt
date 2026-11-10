package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CurrencyAndClosingRegressionTest {
    private val parser = PdfStatementParser { "" }
    private val cover = "PicPay\nFechamento: 29/01/2025\n\u000c\nTransações Nacionais\n"

    @Test fun explicitBrlDoesNotTurnMerchantNamesIntoCurrencyDeclarations() {
        for (merchant in listOf("TOP", "POSTO TOP", "SOS", "ALL", "CHF")) {
            val entries = parser.parseText(cover + "03/01 $merchant R$ 30,00", StatementKind.CARD_BILL)
            assertEquals(merchant, entries.single().description)
            assertEquals(3000L, entries.single().amount.cents)
        }
    }
    @Test fun foreignOnlyPositionedColumnsAreRejectedButConvertedValuesRemainSupported() {
        fun word(text: String, x: Float, y: Float, right: Float) = PdfWord(text,x,y,right,y+8)
        for (currency in listOf("USD", "EUR", "CHF", "US$")) {
            val words = listOf(word("Data",40f,50f,70f),word("Descrição",130f,50f,190f),
                word("Valor",370f,50f,400f),word(currency,430f,50f,460f),
                word("03/01",40f,80f,75f),word("Loja exterior",130f,80f,220f),word("30,00",370f,80f,400f))
            assertThrows(IllegalArgumentException::class.java) {
                val text = PdfLayoutReconstructor().reconstruct(words,612f)
                parser.parseText(cover+text,StatementKind.CARD_BILL)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfLayoutReconstructor().reconstruct(listOf(word("Data",40f,50f,70f),word("Descrição",130f,50f,190f),word("Valor(USD)",370f,50f,460f)),612f)
        }
        val entries = parser.parseText(cover+"data\tdescricao\tvalor\tvalor estrangeiro\n03/01\tLoja\t150,00\t30,00",StatementKind.CARD_BILL)
        assertEquals(15000L,entries.single().amount.cents)
    }
    @Test fun declaredCurrenciesAreValidatedInCsvAndOfx() = runBlocking {
        val csvOfx = CsvOfxStatementParser()
        for (currency in listOf("USD", "EUR", "CHF", "XYZ")) {
            for ((name,text) in listOf(
                "a.ofx" to "<CURDEF>$currency<STMTTRN><DTPOSTED>20260110<TRNAMT>-100.00<NAME>Compra</BANKTRANLIST>",
                "a.csv" to "Data;Descrição;Valor;Moeda\n10/01/2026;Compra;100.00;$currency")) {
                assertThrows(IllegalArgumentException::class.java) { runBlocking { csvOfx.parse(name,text.toByteArray()) } }
            }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            csvOfx.parse("a.csv","Data;Descrição;Valor USD\n10/01/2026;Compra;100.00".toByteArray())
        } }
        assertEquals(15000L,csvOfx.parse("a.ofx","<CURDEF>BRL<STMTTRN><DTPOSTED>20260110<TRNAMT>-150.00<NAME>Compra<ORIGCURRENCY><CURSYM>USD<CURRATE>5</ORIGCURRENCY></BANKTRANLIST>".toByteArray()).single().amount.cents)
        for (currency in listOf("BRL","R$")) {
            val entries=csvOfx.parse("a.csv","Data;Descrição;Valor;Moeda\n10/01/2026;Compra;100.00;$currency".toByteArray())
            assertEquals(10000L,entries.single().amount.cents)
        }
        assertEquals(10000L,csvOfx.parse("a.ofx","<CURDEF>BRL<STMTTRN><DTPOSTED>20260110<TRNAMT>-100.00<NAME>Compra</BANKTRANLIST>".toByteArray()).single().amount.cents)
    }
    @Test fun completeFutureDatesCannotBypassClosingButOlderInstallmentsRemainValid() {
        for (date in listOf("10/02/2025", "10 fev 2025")) {
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseText(cover+"02/01 Mercado 20,00\nParcelas a vencer\n$date Compra futura 900,00",StatementKind.CARD_BILL)
            }
        }
        assertEquals(1,parser.parseText(cover+"10/10/2024 Parcela antiga 20,00",StatementKind.CARD_BILL).size)
    }
}
