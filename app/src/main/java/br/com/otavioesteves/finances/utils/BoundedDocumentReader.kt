package br.com.otavioesteves.finances.utils

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.CharacterCodingException

/** Reads at most [maxBytes], closes the stream even when the document is rejected. */
fun readBoundedDocument(input: InputStream, maxBytes: Int, tooLargeMessage: String): ByteArray {
    require(maxBytes > 0)
    return input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            // Read only one byte beyond the limit to determine whether the file fits.
            val count = stream.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes.toLong() - total + 1).toInt())
            if (count < 0) break
            require(count <= maxBytes - total) { tooLargeMessage }
            total += count
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

const val MAX_BACKUP_BYTES = 50 * 1024 * 1024

fun readBackupDocument(input: InputStream): String {
    val bytes = readBoundedDocument(input, MAX_BACKUP_BYTES, "Backup muito grande. O limite é 50 MB.")
    return try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (error: CharacterCodingException) {
        throw IllegalArgumentException("O backup contém texto UTF-8 inválido. Exporte o arquivo novamente.", error)
    }
}
