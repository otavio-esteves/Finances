package br.com.otavioesteves.finances.domain.model

import java.io.InputStream

/** Validates UTF-8 and bounds individual JSON strings without limiting the number of records. */
class BackupInputStream(
    private val source: InputStream,
    private val checkCancelled: () -> Unit = {}
) : InputStream() {
    private var remainingUtf8Bytes = 0
    private var minimumContinuation = 0x80
    private var maximumContinuation = 0xbf
    private var inString = false
    private var escaped = false
    private var stringBytes = 0

    override fun read(): Int {
        checkCancelled()
        val value = source.read()
        checkCancelled()
        if (value < 0) finish() else validate(value)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkCancelled()
        if (length == 0) return 0
        var count = source.read(buffer, offset, length)
        checkCancelled()
        if (count == 0) {
            val value = source.read()
            checkCancelled()
            if (value < 0) count = -1 else { buffer[offset] = value.toByte(); count = 1 }
        }
        if (count < 0) finish()
        else for (index in offset until offset + count) validate(buffer[index].toInt() and 0xff)
        return count
    }

    private fun finish() {
        require(remainingUtf8Bytes == 0) { INVALID_UTF8 }
    }

    private fun validate(value: Int) {
        if (remainingUtf8Bytes > 0) {
            require(value in minimumContinuation..maximumContinuation) { INVALID_UTF8 }
            remainingUtf8Bytes--
            minimumContinuation = 0x80
            maximumContinuation = 0xbf
        } else {
            when (value) {
                in 0..0x7f -> Unit
                in 0xc2..0xdf -> remainingUtf8Bytes = 1
                in 0xe0..0xef -> {
                    remainingUtf8Bytes = 2
                    minimumContinuation = if (value == 0xe0) 0xa0 else 0x80
                    maximumContinuation = if (value == 0xed) 0x9f else 0xbf
                }
                in 0xf0..0xf4 -> {
                    remainingUtf8Bytes = 3
                    minimumContinuation = if (value == 0xf0) 0x90 else 0x80
                    maximumContinuation = if (value == 0xf4) 0x8f else 0xbf
                }
                else -> throw IllegalArgumentException(INVALID_UTF8)
            }
        }
        if (!inString) {
            if (value == '"'.code) { inString = true; stringBytes = 0 }
        } else {
            // An ASCII control character may take six bytes as a JSON Unicode escape.
            require(++stringBytes <= MAX_PERSISTED_TEXT_BYTES * 6 + 1) {
                "O backup contém um campo de texto muito extenso. Reduza o texto antes de restaurar."
            }
            when {
                escaped -> escaped = false
                value == '\\'.code -> escaped = true
                value == '"'.code -> inString = false
            }
        }
    }

    override fun close() = source.close()

    private companion object {
        const val INVALID_UTF8 = "O backup contém texto UTF-8 inválido. Exporte o arquivo novamente."
    }
}
