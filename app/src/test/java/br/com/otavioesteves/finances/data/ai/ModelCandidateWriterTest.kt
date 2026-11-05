package br.com.otavioesteves.finances.data.ai

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelCandidateWriterTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun exactBudgetCopiesAllBytesAndHashesTheCandidate() {
        val bytes = ByteArray(70_000) { (it % 251).toByte() }
        val candidate = folder.root.resolve("candidate")
        val input = TrackingInput(bytes)
        val hash = writeModelCandidate(candidate, { input }, { bytes.size + 100L }, 100) { }

        assertArrayEquals(bytes, candidate.readBytes())
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }, hash)
        assertTrue(input.closed)
    }

    @Test
    fun exceedingBudgetRemovesCandidateAndRetainsInstalledModel() {
        val installed = folder.newFile("current").apply { writeText("installed model") }
        val candidate = folder.root.resolve("candidate")
        val input = TrackingInput(ByteArray(70_001))
        assertThrows(IllegalArgumentException::class.java) {
            writeModelCandidate(candidate, { input }, { 70_100 }, 100) { }
        }
        assertEquals("installed model", installed.readText())
        assertFalse(candidate.exists())
        assertTrue(input.closed)
        assertEquals(70_001, input.bytesRead)
    }

    @Test
    fun refusesBeforeOpeningTheSourceWhenThereIsNoRoomBeyondTheReserve() {
        var opened = false
        val candidate = folder.root.resolve("candidate")
        assertThrows(IllegalArgumentException::class.java) {
            writeModelCandidate(candidate, { opened = true; ByteArrayInputStream(byteArrayOf(1)) }, { 100 }, 100) { }
        }
        assertFalse(opened)
        assertFalse(candidate.exists())
    }

    @Test
    fun concurrentStorageConsumptionStopsBeforeWritingIntoTheReserve() {
        val candidate = folder.root.resolve("candidate")
        var freeBytes = 1_000_000L
        val input = TrackingInput(ByteArray(70_000), afterRead = { count -> freeBytes = 100 + count - 1L })
        assertThrows(IllegalArgumentException::class.java) {
            writeModelCandidate(candidate, { input }, { freeBytes }, 100) { }
        }
        assertTrue(input.closed)
        assertFalse(candidate.exists())
    }

    @Test
    fun cancellationDuringCopyClosesSourceAndRemovesOnlyCandidate() = runTest {
        val installed = folder.newFile("current").apply { writeText("installed model") }
        val candidate = folder.root.resolve("candidate")
        lateinit var input: TrackingInput
        val copying = async {
            val context = currentCoroutineContext()
            var reads = 0
            input = TrackingInput(ByteArray(200_000), afterRead = {
                if (++reads == 2) context.cancel()
            })
            writeModelCandidate(candidate, { input }, { 1_000_000 }, 100) { context.ensureActive() }
        }
        try {
            copying.await()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertTrue(input.closed)
        assertFalse(candidate.exists())
        assertEquals("installed model", installed.readText())
        assertTrue(input.bytesRead < 200_000)
    }

    @Test
    fun sourceFailureDoesNotLeavePartialCandidateOrDeleteInstalledModel() {
        val installed = folder.newFile("current").apply { writeText("installed model") }
        val candidate = folder.root.resolve("candidate")
        val input = TrackingInput(ByteArray(70_000), afterRead = { throw IOException("Provider disconnected") })
        assertThrows(IOException::class.java) {
            writeModelCandidate(candidate, { input }, { Long.MAX_VALUE }, 100) { }
        }
        assertTrue(input.closed)
        assertFalse(candidate.exists())
        assertEquals("installed model", installed.readText())
    }

    @Test
    fun budgetUsesLongRatherThanAnIntSizedLimit() {
        val candidate = folder.root.resolve("candidate")
        val bytes = byteArrayOf(1, 2, 3)
        writeModelCandidate(candidate, { ByteArrayInputStream(bytes) }, { Int.MAX_VALUE.toLong() * 4 }, 100) { }
        assertArrayEquals(bytes, candidate.readBytes())
    }

    @Test
    fun removesCandidateLeftByAnInterruptedProcessBeforeCalculatingBudget() {
        val candidate = folder.newFile("candidate").apply { writeText("partial previous import") }
        var checked = false
        writeModelCandidate(candidate, { ByteArrayInputStream(byteArrayOf(7)) }, {
            if (!checked) { assertFalse(candidate.exists()); checked = true }
            1000
        }, 100) { }
        assertArrayEquals(byteArrayOf(7), candidate.readBytes())
    }

    @Test(timeout = 5_000)
    fun zeroLengthBufferedReadsStillConsumeBudgetAndCannotSpinForever() {
        val candidate = folder.root.resolve("candidate")
        var reads = 0
        var closed = false
        val input = object : InputStream() {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = 0
            override fun read(): Int { reads++; return 7 }
            override fun close() { closed = true }
        }
        assertThrows(IllegalArgumentException::class.java) {
            writeModelCandidate(candidate, { input }, { 110 }, 100) { }
        }
        assertEquals(11, reads)
        assertTrue(closed)
        assertFalse(candidate.exists())
    }

    private class TrackingInput(bytes: ByteArray, private val afterRead: (Int) -> Unit = {}) : ByteArrayInputStream(bytes) {
        var closed = false
        var bytesRead = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) { bytesRead += count; afterRead(count) }
            return count
        }
        override fun close() { closed = true; super.close() }
    }
}
