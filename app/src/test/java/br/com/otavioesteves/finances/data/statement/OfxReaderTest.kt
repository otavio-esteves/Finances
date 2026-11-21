package br.com.otavioesteves.finances.data.statement

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine

class OfxReaderTest {
    private val parser = CsvOfxStatementParser()
    private val valid = "<STMTTRN><DTPOSTED>20261101<TRNAMT>-1.00<MEMO>Compra</STMTTRN>"

    @Test fun refusesEntireBatchWithOfxCorrectionsInXmlOrSgml() {
        val original = valid.replace("</STMTTRN>", "<FITID>original</STMTTRN>")
        val corrected = valid.replace("-1.00", "-1.25")
        for (correction in listOf(
            "<CORRECTFITID>original</CORRECTFITID><CORRECTACTION>REPLACE</CORRECTACTION>",
            "<CORRECTFITID>original<CORRECTACTION>DELETE",
            "<CORRECTFITID>original",
            "<CORRECTACTION/>"
        )) {
            val text = "<OFX><BANKTRANLIST>$original" +
                corrected.replace("</STMTTRN>", "$correction</STMTTRN>") + "</BANKTRANLIST></OFX>"
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("banco.ofx", text.toByteArray()) }
            }
            assertTrue(error.message.orEmpty().contains("correções de lançamentos"))
            assertTrue(error.message.orEmpty().contains("Nenhum lançamento foi importado"))
        }
    }

    @Test fun rejectsRepeatedAmountAndDateEvenWhenValuesAreEqualOrTheFirstRecordWasValid() {
        for ((field, first, second) in listOf(
            Triple("TRNAMT", "-1.00", "-999.00"), Triple("TRNAMT", "-1.00", "-1.00"),
            Triple("DTPOSTED", "20261101", "20261110"), Triple("DTPOSTED", "20261101", "20261101")
        )) for (closed in listOf(false, true)) {
            val repeated = "<$field>$first${if (closed) "</$field>" else ""}<$field>$second"
            val other = if (field == "TRNAMT") "<DTPOSTED>20261101" else "<TRNAMT>-1.00"
            val error = assertThrows(IllegalArgumentException::class.java) { runBlocking {
                parser.parse("a.ofx", (valid + "<STMTTRN>$other$repeated<MEMO>Ambígua</STMTTRN>").toByteArray())
            } }
            assertTrue(error.message.orEmpty().contains("$field repetido"))
            assertTrue(error.message.orEmpty().contains("Nenhum lançamento"))
        }
    }

    @Test fun doesNotTreatAnEncodedTagInTheDescriptionAsAnotherFinancialField() = runBlocking {
        val entry = parser.parse("a.ofx", valid.replace("Compra", "Texto &lt;TRNAMT&gt;-999.00").toByteArray()).single()
        assertEquals("Texto <TRNAMT>-999.00", entry.description)
        assertEquals(100L, entry.amount.cents)
    }

    @Test fun acceptsIndependentRecordsWithSgmlLeavesAndExplicitAggregateClosures() = runBlocking {
        for (closedLeaves in listOf(false, true)) {
            fun leaf(name: String, value: String) = "<$name>$value${if (closedLeaves) "</$name>" else ""}"
            val text = "<OFX><BANKTRANLIST><STMTTRN>" + leaf("DTPOSTED", "20261101") +
                leaf("TRNAMT", "-1.00") + leaf("MEMO", "Primeira") + "</STMTTRN><STMTTRN>" +
                leaf("DTPOSTED", "20261102") + leaf("TRNAMT", "2.00") + leaf("MEMO", "Segunda") +
                "</STMTTRN></BANKTRANLIST></OFX>"
            val entries = parser.parse("a.ofx", text.toByteArray())
            assertEquals(listOf("Primeira", "Segunda"), entries.map { it.description })
            assertEquals(listOf(100L, 200L), entries.map { it.amount.cents })
            assertEquals(listOf(1, 2), entries.map { it.date.dayOfMonth })
        }
    }

    @Test fun requiresAmountAndDateOnEachRecordWithoutInheritingPreviousValues() {
        for (missing in listOf("<DTPOSTED>20261102", "<TRNAMT>-2.00")) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                parser.parse("a.ofx", (valid + "<STMTTRN>$missing<MEMO>Incompleta</STMTTRN>").toByteArray())
            } }
        }
    }

    @Test fun emptyXmlElementsCannotUseTextOutsideTheElementAsAnAmountDateOrCurrency() {
        for (text in listOf(
            "<STMTTRN><DTPOSTED>20261101<TRNAMT/>-1.00<MEMO>Compra</STMTTRN>",
            "<STMTTRN><DTPOSTED/>20261101<TRNAMT>-1.00<MEMO>Compra</STMTTRN>",
            "<CURDEF/>BRL$valid"
        )) assertThrows(IllegalArgumentException::class.java) { runBlocking { parser.parse("a.ofx", text.toByteArray()) } }
    }

    @Test fun rejectsIncompleteNestedAndUnmatchedOriginalCurrencyBlocks() {
        for (tail in listOf(
            "<ORIGCURRENCY><CURSYM>USD", "</ORIGCURRENCY>",
            "<ORIGCURRENCY><ORIGCURRENCY></ORIGCURRENCY></ORIGCURRENCY>",
            "<ORIGCURRENCY>$valid", "<ORIGCURRENCY></BANKTRANLIST>", "<ORIGCURRENCY/>"
        )) {
            val error = assertThrows(IllegalArgumentException::class.java) { runBlocking {
                parser.parse("a.ofx", (valid + tail).toByteArray())
            } }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("ORIGCURRENCY"))
        }
    }

    @Test fun foreignOriginalCurrencyDoesNotOverrideTheBilledBrlAmountOrHideLaterDeclarations() = runBlocking {
        val original = "<ORIGCURRENCY><CURSYM>USD<CURRATE>5.00</ORIGCURRENCY>"
        val text = "<CURDEF>BRL" + valid.replace("</STMTTRN>", "$original</STMTTRN>")
        assertEquals(100L, parser.parse("a.ofx", text.toByteArray()).single().amount.cents)
        for (extra in listOf("<CURDEF>USD", "<CURSYM>EUR")) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                parser.parse("a.ofx", (text + extra).toByteArray())
            } }
        }
    }

    @Test fun commentsAndProcessingInstructionsCannotAddTransactionsOrDuplicateAmounts() = runBlocking {
        val text = "<?xml version=\"1.0\"?><?OFX OFXHEADER=\"200\"?>" +
            "<!-- $valid -->" + valid.replace("<TRNAMT>", "<!-- <TRNAMT>-999.00 --><?skip?><TRNAMT>")
        assertEquals(100L, parser.parse("a.ofx", text.toByteArray()).single().amount.cents)
    }

    @Test fun cancellationIsCheckedInsideLargeFieldsCommentsInstructionsAndTagAttributes() = runBlocking {
        val longText = "x".repeat(500_000)
        for (text in listOf(
            "<STMTTRN><MEMO>$longText", "<!-- $longText -->$valid",
            "<?info $longText?>$valid", "<OFX label=\"$longText\">$valid"
        )) {
            var checks = 0
            assertThrows(CancellationException::class.java) {
                ofxTransactions(text) { if (++checks == 20) throw CancellationException("cancelled") }.toList()
            }
            assertEquals(20, checks)
        }
        assertEquals(1, parser.parse("a.ofx", valid.toByteArray()).size)
    }

    @Test fun malformedTagCommentOrInstructionCannotReturnTheValidPrefixAsAPartialBatch() {
        for (tail in listOf("<STMTTRN", "<TRNAMT<STMTTRN>", "<!-- unfinished", "<?unfinished")) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                parser.parse("a.ofx", (valid + tail).toByteArray())
            } }
        }
    }

    @Test fun publicParserObservesJobCancellationWhileReadingAMultilineField() = runBlocking {
        val job = Job()
        var checks = 0
        val context = object : CoroutineContext by job {
            override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? {
                if (key == Job && ++checks == 20) job.cancel()
                return job[key]
            }
        }
        var outcome: Result<*>? = null
        val text = "<STMTTRN><DTPOSTED>20261101<TRNAMT>-1.00<MEMO>" + "Compra\n".repeat(100_000)
        val parse = suspend { parser.parse("a.ofx", text.toByteArray()) }
        parse.startCoroutine(Continuation(context) { outcome = it })
        assertTrue(outcome?.exceptionOrNull() is CancellationException)
        assertTrue(checks >= 20)
        assertEquals(1, parser.parse("a.ofx", valid.toByteArray()).size)
    }
}
