package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.data.statement.CsvOfxStatementParser
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ImportResourceLimitsTest {
    @Test fun fiftyThousandRowsAndThousandsOfCandidatesFitIn128MiBIncludingDifferentLegacyAliases() {
        runProbe("overlaps", 60, "OVERLAPS_OK")
    }

    @Test fun repeatedUnclosedCurrencyBlocksAreRejectedWithoutAnUninterruptibleRegexSearch() {
        runProbe("ofx", 15, "OFX_OK")
    }

    @Test fun aliasesPreserveCandidateOrderWithoutRepeatingNormalizedDescriptionsOrHistoricalIds() = runBlocking<Unit> {
        val date = LocalDate.of(2026, 11, 1)
        val entry = RawStatementEntry(" Loja  A ", Money(100), date, TransactionType.EXPENSE,
            legacyOfxDescription = "loja a", legacyDecodedOfxDescription = "Outra", legacyPdfDescription = "Antiga")
        val current = Transaction(1, "loja a", entry.amount, 1, date, entry.type)
        val decoded = current.copy(id = 2, description = "outra")
        val old = current.copy(id = 3, description = "antiga")
        val repo = StoredTransactions(listOf(old, decoded, current, current))
        val matches = FindPotentialOverlapsUseCase(repo).findMatches(listOf(entry)).getValue(0)
        assertEquals(listOf(current, decoded, old), matches)
        assertEquals(current, matches[0])
        assertEquals(old, matches[2])
        assertThrows(IndexOutOfBoundsException::class.java) { matches[-1] }
        assertThrows(IndexOutOfBoundsException::class.java) { matches[3] }
    }

    private fun runProbe(mode: String, timeout: Long, expected: String) {
        val classpath = listOf(ImportMemoryProbe::class.java, CsvOfxStatementParser::class.java,
            Unit::class.java, CoroutineScope::class.java)
            .map { File(requireNotNull(it.protectionDomain?.codeSource?.location).toURI()).absolutePath }
            .distinct().joinToString(File.pathSeparator)
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(java, "-Xmx128m", "-cp", classpath, ImportMemoryProbe::class.java.name, mode)
            .redirectErrorStream(true).start()
        try {
            assertTrue("$mode exceeded $timeout seconds", process.waitFor(timeout, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, process.exitValue())
            assertTrue(output, output.contains(expected))
        } finally { process.destroyForcibly() }
    }
}

/** The heap cap catches the original quadratic allocation on any development machine. */
internal object ImportMemoryProbe {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        when (args.single()) {
            "overlaps" -> {
                for (aliases in 0..2) checkOverlaps(aliases)
                println("OVERLAPS_OK rows=50000 candidates=2000")
            }
            "ofx" -> {
                val content = ("<OFX>" + "<ORIGCURRENCY>".repeat(64_000) +
                    "<STMTTRN><DTPOSTED>20261101<TRNAMT>-1.00<MEMO>Compra</STMTTRN></OFX>").toByteArray()
                check(content.size < 10 * 1024 * 1024)
                val error = runCatching { CsvOfxStatementParser().parse("a.ofx", content) }.exceptionOrNull()
                check(error is IllegalArgumentException && error.message.orEmpty().contains("ORIGCURRENCY"))
                println("OFX_OK bytes=${content.size}")
            }
            else -> error("Unknown probe")
        }
    }

    private suspend fun checkOverlaps(aliases: Int) {
        val parser = CsvOfxStatementParser()
        val content = ("Data,Descricao,Valor\n" + "2026-11-01,Compra,-1.00\n".repeat(50_000)).toByteArray()
        val entries = parser.parse("a.csv", content).mapIndexed { index, entry ->
            when (aliases) {
                0 -> entry
                1 -> entry.copy(legacyPdfDescription = "Não encontrada $index")
                else -> entry.copy(legacyPdfDescription = "Antiga")
            }
        }
        val first = entries.first()
        val stored = List(2000) { index ->
            Transaction(index + 1L, if (aliases == 2 && index >= 1000) "Antiga" else first.description,
                first.amount, 1, first.date, first.type)
        }
        val matches = FindPotentialOverlapsUseCase(StoredTransactions(stored)).findMatches(entries)
        check(matches.size == 50_000)
        check(matches.values.all { it.size == 2000 && it.first().id == 1L && it.last().id == 2000L })
        for (index in listOf(0, 25_000, 49_999)) check(matches.getValue(index) == stored)
    }
}

private class StoredTransactions(private val rows: List<Transaction>) : TransactionsRepository {
    override fun hasTransactions() = flowOf(rows.isNotEmpty())
    override fun getTransaction(id: Long) = flowOf(rows.firstOrNull { it.id == id })
    override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> =
        flowOf(rows.filter { it.date.year == period.year && it.date.monthValue == period.month })
    override fun getMonthlyBalance(period: MonthPeriod) = flowOf(Money.Zero)
    override suspend fun addTransaction(transaction: Transaction) = error("Read only")
    override suspend fun removeTransaction(transactionId: Long) = error("Read only")
    override suspend fun updateTransaction(transaction: Transaction) = error("Read only")
}
