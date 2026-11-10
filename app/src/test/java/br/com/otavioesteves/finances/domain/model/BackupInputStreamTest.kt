package br.com.otavioesteves.finances.domain.model

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class BackupInputStreamTest {
    @Test fun utf8SequencesCanCrossEveryReadBoundary() {
        val bytes = "{\"descrição\":\"aé€😀�\\\"\\\\\"}".toByteArray()
        val output = BackupInputStream(ByteArrayInputStream(bytes)).use { input ->
            val copied = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1)
            while (input.read(buffer) >= 0) copied.write(buffer)
            copied.toByteArray()
        }
        assertArrayEquals(bytes, output)
    }

    @Test fun invalidUtf8AndTruncatedSequencesAreRejected() {
        for (bytes in listOf(
            byteArrayOf(0x80.toByte()), byteArrayOf(0xc2.toByte()), byteArrayOf(0xff.toByte()),
            byteArrayOf(0xc0.toByte(), 0xaf.toByte()), byteArrayOf(0xe0.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte())
        )) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                BackupInputStream(ByteArrayInputStream(bytes)).use { input -> while (input.read() >= 0) Unit }
            }
            assertTrue(error.message.orEmpty().contains("UTF-8"))
        }
    }

    @Test fun oversizedJsonTokenIsRejectedBeforeTheWholeDocumentIsConsumed() {
        val source = ByteArrayInputStream(("\"" + "a".repeat(3 * 1024 * 1024) + "\"").toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            BackupInputStream(source).use { it.copyTo(java.io.OutputStream.nullOutputStream()) }
        }
        assertTrue(source.available() > 2 * 1024 * 1024)
    }

    @Test fun zeroLengthProviderReadDoesNotSpin() {
        val source = object : ByteArrayInputStream("{}".toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = 0
        }
        assertEquals("{}", BackupInputStream(source).use { it.readBytes().toString(Charsets.UTF_8) })
    }
}
