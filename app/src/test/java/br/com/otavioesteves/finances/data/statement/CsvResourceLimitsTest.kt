package br.com.otavioesteves.finances.data.statement

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.utils.ExportFormat
import br.com.otavioesteves.finances.utils.TransactionExporter
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.time.LocalDate

class CsvResourceLimitsTest {
    private val parser = CsvOfxStatementParser()

    @Test fun millionsOfBlankLinesFitInA128MiBHeapAndPreserveMultilineFields() {
        val classpath = listOf(
            CsvMemoryProbe::class.java, CsvOfxStatementParser::class.java, Unit::class.java,
            CoroutineScope::class.java, KSerializer::class.java, Json::class.java
        ).map { File(requireNotNull(it.protectionDomain?.codeSource?.location).toURI()).absolutePath }
            .distinct().joinToString(File.pathSeparator)
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(java, "-Xmx128m", "-cp", classpath, CsvMemoryProbe::class.java.name)
            .redirectErrorStream(true).start()
        try {
            assertTrue("CSV parsing exceeded 60 seconds", process.waitFor(60, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, process.exitValue())
            assertTrue(output, output.contains("CSV_OK count=2"))
        } finally { process.destroyForcibly() }
    }

    @Test fun acceptsFiftyThousandTransactionsAndRejectsTheNextInsteadOfReturningATruncatedBatch() = runBlocking {
        val csv = "Data;Descrição;Valor\n" + "01/11/2026;Compra;-10,00\n".repeat(50_000)
        assertEquals(50_000, parser.parse("a.csv", csv.toByteArray()).size)
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { parser.parse("a.csv", (csv + "02/11/2026;Outra;-1,00").toByteArray()) }
        }
        assertTrue(error.message.orEmpty().contains("50.000"))
    }

    @Test fun boundsColumnsInBothHeaderAndDataRowsIncludingEmptyFields() = runBlocking {
        val header = (listOf("Data", "Descrição", "Valor") + (1..61).map { "Extra $it" }).joinToString(";")
        val row = (listOf("01/11/2026", "Compra", "-10,00") + List(61) { "" }).joinToString(";")
        assertEquals(1, parser.parse("a.csv", "$header\n$row".toByteArray()).size)
        for (csv in listOf("$header;Extra 62\n$row;", "$header\n$row;")) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("a.csv", csv.toByteArray()) }
            }
            assertTrue(error.message.orEmpty().contains("64 colunas"))
        }
    }

    @Test fun boundsFieldsWhileReadingAndMeasuresUtf8BytesIncludingIgnoredColumns() = runBlocking {
        val header = "Data;Descrição;Valor;Extra\n01/11/2026;Compra;-10,00;"
        assertEquals(1, parser.parse("a.csv", (header + "a".repeat(131_072)).toByteArray()).size)
        for (field in listOf("a".repeat(131_073), "é".repeat(65_537), "\"" + "a".repeat(131_073))) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { parser.parse("a.csv", (header + field).toByteArray()) }
            }
            assertTrue(error.message.orEmpty().contains("extenso"))
        }
        // A rejected file does not retain parser state or affect the next import.
        assertEquals(1, parser.parse("a.csv", (header + "ok").toByteArray()).size)
    }

    @Test fun nativeExportsAtTheStorageLimitStillRoundTripWithAProtectiveApostrophe() = runBlocking {
        val description = "=" + "a".repeat(65_535)
        val transaction = Transaction(1, description, Money(100), 1,
            LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
        val csv = TransactionExporter().export(listOf(transaction), emptyMap(), ExportFormat.CSV)
        assertEquals(description, parser.parse("a.csv", csv.toByteArray()).single().description)
    }

    @Test fun aCancelledImportDoesNotStartParsingCsv() = runBlocking {
        var observedCancellation = false
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineContext.cancel()
            try {
                parser.parse("a.csv", "Data;Descrição;Valor\n01/11/2026;Compra;-10,00".toByteArray())
            } catch (_: CancellationException) { observedCancellation = true }
        }
        task.join()
        assertTrue(observedCancellation)
    }
}

/** Runs separately so this regression fails on memory growth even on a large development machine. */
internal object CsvMemoryProbe {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val content = buildString(7_600_000) {
            repeat(2_500_000) { append('\n') }
            append("Data;Descrição;Valor\n01/11/2026;\"Loja\n\nFilial\";-10,00\n")
            repeat(2_500_000) { append('\n') }
            append("02/11/2026;Outra;-20,00\n")
            repeat(2_500_000) { append('\n') }
        }.toByteArray()
        check(content.size < 10 * 1024 * 1024)
        val entries = CsvOfxStatementParser().parse("a.csv", content)
        check(entries.map { it.description } == listOf("Loja\n\nFilial", "Outra"))
        check(entries.map { it.amount.cents } == listOf(1_000L, 2_000L))
        println("CSV_OK count=${entries.size} bytes=${content.size}")
    }
}
