package br.com.otavioesteves.finances.utils

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedDocumentReaderTest {
    private class TrackingStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test
    fun acceptsExactLimitAndClosesStream() {
        val input = TrackingStream("12345".toByteArray())
        assertEquals("12345", readBoundedDocument(input, 5, "large").toString(Charsets.UTF_8))
        assertTrue(input.closed)
    }

    @Test
    fun rejectsOversizeAfterOneExtraByteAndClosesStream() {
        val input = TrackingStream(ByteArray(1000))
        val error = assertThrows(IllegalArgumentException::class.java) { readBoundedDocument(input, 5, "large") }
        assertEquals("large", error.message)
        assertEquals(994, input.available())
        assertTrue(input.closed)
    }

    @Test
    fun backupReaderPreservesUtf8() {
        assertEquals("{\"descrição\":\"ação\"}", readBackupDocument(ByteArrayInputStream("{\"descrição\":\"ação\"}".toByteArray(Charsets.UTF_8))))
    }

    @Test
    fun backupReaderRejectsInvalidOrTruncatedUtf8AndClosesTheStream() {
        for (invalid in listOf(byteArrayOf(0xe9.toByte()), byteArrayOf(0xc3.toByte()), byteArrayOf(0xff.toByte()))) {
            val input = TrackingStream("{\"description\":\"Caf".toByteArray() + invalid + "\"}".toByteArray())
            val error = assertThrows(IllegalArgumentException::class.java) { readBackupDocument(input) }
            assertTrue(error.message.orEmpty().contains("UTF-8"))
            assertTrue(input.closed)
        }
    }

    @Test
    fun backupReaderPreservesAnExplicitReplacementCharacter() {
        val json = "{\"description\":\"Café �\"}"
        assertEquals(json, readBackupDocument(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))))
    }
}
