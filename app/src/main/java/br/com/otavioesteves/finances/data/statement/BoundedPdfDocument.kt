package br.com.otavioesteves.finances.data.statement

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.RandomAccess
import com.tom_roush.pdfbox.io.RandomAccessBuffer
import com.tom_roush.pdfbox.io.ScratchFile
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.IOException

internal const val MAX_PDF_SCRATCH_BYTES = 32L * 1024 * 1024
internal const val PDF_RESOURCE_LIMIT_MESSAGE =
    "O PDF excede o limite de memória para análise local. Exporte uma versão menor ou mais simples."

internal class PdfResourceLimitException(cause: IOException) : IOException(PDF_RESOURCE_LIMIT_MESSAGE, cause)

/** Bound decoded buffers from the first object/xref stream, before the page tree can be inspected. */
internal fun <T> withBoundedPdfDocument(
    content: ByteArray,
    password: String,
    checkCancelled: () -> Unit,
    read: (PDDocument, () -> Unit) -> T
): T {
    val scratch = LimitedPdfScratchFile(checkCancelled)
    val source = RandomAccessBuffer(content)
    try {
        checkCancelled()
        val parser = PDFParser(source, password, scratch)
        parser.parse()
        return parser.pdDocument.use { document ->
            scratch.checkLimits()
            read(document, scratch::checkLimits).also { scratch.checkLimits() }
        }
    } catch (error: IOException) {
        // Lenient PDF recovery can replace the original buffer-limit error.
        scratch.checkLimits()
        throw error
    } finally {
        try { source.close() } finally { scratch.close() }
    }
}

private class LimitedPdfScratchFile(private val checkCancelled: () -> Unit) :
    ScratchFile(MemoryUsageSetting.setupMainMemoryOnly(MAX_PDF_SCRATCH_BYTES)) {
    private var limitFailure: IOException? = null

    fun checkLimits() {
        checkCancelled()
        limitFailure?.let { throw PdfResourceLimitException(it) }
    }

    private fun <T> checked(operation: () -> T): T {
        checkLimits()
        return try {
            operation()
        } catch (error: IOException) {
            // This pinned PDFBox version reports its global scratch-page budget here.
            if (error.message == "Maximum allowed scratch file memory exceeded.") {
                limitFailure = error
                throw PdfResourceLimitException(error)
            }
            throw error
        }
    }

    override fun createBuffer(): RandomAccess = checked {
        val buffer = super.createBuffer()
        object : RandomAccess by buffer {
            override fun write(value: Int) = checked { buffer.write(value) }
            override fun write(bytes: ByteArray) = checked { buffer.write(bytes) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) = checked {
                buffer.write(bytes, offset, length)
            }
        }
    }
}
