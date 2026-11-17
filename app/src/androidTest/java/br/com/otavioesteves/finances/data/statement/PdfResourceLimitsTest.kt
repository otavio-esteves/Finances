package br.com.otavioesteves.finances.data.statement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

@RunWith(AndroidJUnit4::class)
class PdfResourceLimitsTest {
    @Test fun oversizedCompressedCatalogIsRejectedBeforeOpeningThePageTreeAndTheNextFileStillWorks() {
        val extractor = PdfBoxTextExtractor(InstrumentationRegistry.getInstrumentation().targetContext)
        val oversized = compressedPdf(expandedMiB = 256, compressedCatalog = true)
        assertTrue(oversized.size < 300_000)
        val error = assertThrows(IllegalArgumentException::class.java) { extractor.extract(oversized) }
        assertEquals(PDF_RESOURCE_LIMIT_MESSAGE, error.message)
        assertTrue(error.cause is PdfResourceLimitException)
        val valid = compressedPdf(expandedMiB = 1, compressedCatalog = true)
        withBoundedPdfDocument(valid, "", {}) { document, check ->
            check()
            assertEquals(1, document.numberOfPages)
        }
    }

    @Test fun oversizedPageStreamIsRejectedDuringExtractionEvenWhenThePageTreeWasLoaded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oversized = compressedPdf(expandedMiB = 256, compressedCatalog = false)
        withBoundedPdfDocument(oversized, "", {}) { document, _ -> assertEquals(1, document.numberOfPages) }
        val error = assertThrows(IllegalArgumentException::class.java) {
            PdfBoxTextExtractor(context).extract(oversized)
        }
        assertEquals(PDF_RESOURCE_LIMIT_MESSAGE, error.message)
    }

    @Test fun aCaughtDecoderLimitCannotBeHiddenByLenientRecoveryOrALaterSuccessfulCallback() {
        val content = compressedPdf(expandedMiB = 256, compressedCatalog = false)
        assertThrows(PdfResourceLimitException::class.java) {
            withBoundedPdfDocument(content, "", {}) { document, _ ->
                // PDF libraries may swallow IOExceptions and continue with partial data.
                runCatching {
                    document.getPage(0).contents.use { input ->
                        val buffer = ByteArray(8192)
                        while (input.read(buffer) >= 0) { /* discard decoded bytes */ }
                    }
                }
                "partial result"
            }
        }
    }

    @Test fun cancellationDuringDecodedWritesIsPropagatedAndDoesNotPoisonTheNextRead() {
        val content = compressedPdf(expandedMiB = 4, compressedCatalog = true)
        var checks = 0
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            withBoundedPdfDocument(content, "", {
                if (++checks == 20) throw kotlinx.coroutines.CancellationException("cancelled")
            }) { _, _ -> fail("Cancelled PDF must not reach the reader") }
        }
        withBoundedPdfDocument(content, "", {}) { document, _ -> assertEquals(1, document.numberOfPages) }
    }
}

/** A valid one-page PDF whose catalog or page stream expands from a tiny Deflate stream. */
internal fun compressedPdf(expandedMiB: Int, compressedCatalog: Boolean): ByteArray {
    val compressed = ByteArrayOutputStream().use { output ->
        DeflaterOutputStream(output).use { encoder ->
            if (compressedCatalog) encoder.write("1 0 << /Type /Catalog /Pages 2 0 R >>\n".toByteArray())
            val spaces = ByteArray(8192) { ' '.code.toByte() }
            repeat(expandedMiB * 128) { encoder.write(spaces) }
        }
        output.toByteArray()
    }
    val output = ByteArrayOutputStream()
    fun write(text: String) { output.write(text.toByteArray(Charsets.US_ASCII)) }
    val offsets = mutableMapOf<Int, Int>()
    fun obj(number: Int, body: () -> Unit) {
        offsets[number] = output.size()
        write("$number 0 obj\n"); body(); write("\nendobj\n")
    }
    write("%PDF-1.5\n")
    if (!compressedCatalog) obj(1) { write("<< /Type /Catalog /Pages 2 0 R >>") }
    obj(2) { write("<< /Type /Pages /Kids [3 0 R] /Count 1 >>") }
    obj(3) { write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100]${if (compressedCatalog) "" else " /Contents 4 0 R"} >>") }
    obj(4) {
        write("<< ${if (compressedCatalog) "/Type /ObjStm /N 1 /First 4 " else ""}/Length ${compressed.size} /Filter /FlateDecode >>\nstream\n")
        output.write(compressed); write("\nendstream")
    }
    offsets[5] = output.size()
    val xref = ByteArrayOutputStream()
    for (number in 0..5) {
        val type = if (number == 0) 0 else if (number == 1 && compressedCatalog) 2 else 1
        val offset = if (type == 2) 4 else offsets[number] ?: 0
        val generation = if (number == 0) 65_535 else 0
        xref.write(type)
        for (shift in listOf(24, 16, 8, 0)) xref.write(offset ushr shift and 255)
        xref.write(generation ushr 8); xref.write(generation and 255)
    }
    obj(5) {
        write("<< /Type /XRef /Size 6 /Root 1 0 R /W [1 4 2] /Length ${xref.size()} >>\nstream\n")
        output.write(xref.toByteArray()); write("\nendstream")
    }
    write("startxref\n${offsets.getValue(5)}\n%%EOF\n")
    return output.toByteArray()
}
