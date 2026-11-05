package br.com.otavioesteves.finances.data.ai

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/** Stages a model without touching the installed file, retaining a free-space reserve. */
internal fun writeModelCandidate(
    candidate: File,
    openStream: () -> InputStream,
    availableBytes: () -> Long,
    reservedBytes: Long,
    checkCancelled: () -> Unit
): String {
    require(reservedBytes >= 0)
    fun remainingSpace(): Long = (availableBytes().coerceAtLeast(0) - reservedBytes).coerceAtLeast(0)
    val spaceMessage = "Espaço insuficiente para copiar este modelo e manter espaço livre no aparelho. Libere espaço e tente novamente."
    try {
        checkCancelled()
        check(!candidate.exists() || candidate.delete()) { "Não foi possível limpar a importação anterior do modelo." }
        // The current model already occupies disk space and remains installed throughout staging.
        // This per-import budget permits large bundles when the device has room, without a fixed model-size cap.
        val maxBytes = remainingSpace()
        require(maxBytes > 0) { spaceMessage }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        openStream().use { input ->
            candidate.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkCancelled()
                    val remaining = maxBytes - total
                    // Read one byte beyond the budget to distinguish an exact fit from an oversized bundle.
                    val readLimit = if (remaining < buffer.size) (remaining + 1).toInt() else buffer.size
                    var count = input.read(buffer, 0, readLimit)
                    checkCancelled()
                    if (count < 0) break
                    if (count == 0) {
                        // A provider that returns zero must not spin forever without consuming its byte budget.
                        val single = input.read()
                        checkCancelled()
                        if (single < 0) break
                        buffer[0] = single.toByte()
                        count = 1
                    }
                    require(count <= remaining && count <= remainingSpace()) { spaceMessage }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    total += count
                }
            }
        }
        checkCancelled()
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    } catch (failure: Throwable) {
        candidate.delete()
        throw failure
    }
}
