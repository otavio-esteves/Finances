package br.com.otavioesteves.finances.utils

import java.io.ByteArrayOutputStream
import java.io.InputStream
import br.com.otavioesteves.finances.domain.model.BackupInputStream

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

/** Compatibility helper for callers that need text; the UI uses the streaming restore use case. */
fun readBackupDocument(input: InputStream): String {
    return BackupInputStream(input).reader(Charsets.UTF_8).use { it.readText() }
}
