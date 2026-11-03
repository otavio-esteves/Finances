package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.StatementPasswordRequiredException
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.IOException
import java.io.Writer
import kotlin.math.min
import kotlin.math.sqrt

data class PdfTextExtraction(val text: String, val usedOcr: Boolean = false)

fun interface PdfTextExtractor {
    fun extract(content: ByteArray): String
    fun extractWithMetadata(content: ByteArray, password: String? = null, checkCancelled: () -> Unit = {}): PdfTextExtraction =
        PdfTextExtraction(extract(content))
}

class PdfBoxTextExtractor(context: Context) : PdfTextExtractor {
    private val appContext = context.applicationContext
    override fun extract(content: ByteArray): String = extractWithMetadata(content, null) {}.text

    override fun extractWithMetadata(content: ByteArray, password: String?, checkCancelled: () -> Unit): PdfTextExtraction {
        initializeResources()
        var stagedFile: File? = null
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var ocr: OfflinePdfOcr? = null
        var scannedPages = 0
        try {
            PDDocument.load(content, password.orEmpty()).use { document ->
                require(document.numberOfPages <= MAX_PAGES) { "PDF com mais de $MAX_PAGES páginas não é aceito." }
                val output = BoundedTextWriter(PdfStatementParser.MAX_TEXT_CHARS)
                val layout = PdfLayoutReconstructor()
                for (pageIndex in 0 until document.numberOfPages) {
                    checkCancelled()
                    val words = mutableListOf<PdfWord>()
                    val stripper = object : PDFTextStripper() {
                        override fun writeString(text: String, positions: MutableList<TextPosition>) {
                            var token = StringBuilder()
                            var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
                            fun finish() {
                                if (token.isNotBlank()) words += PdfWord(token.toString(), left, top, right, bottom)
                                token = StringBuilder()
                            }
                            positions.forEach { position ->
                                val value = position.unicode
                                if (value.isBlank()) finish()
                                else {
                                    if (token.isNotEmpty() && position.xDirAdj - right > position.heightDir * 0.35f) finish()
                                    if (token.isEmpty()) { left = position.xDirAdj; top = position.yDirAdj - position.heightDir; bottom = position.yDirAdj }
                                    token.append(value)
                                    right = position.xDirAdj + position.widthDirAdj
                                    top = minOf(top, position.yDirAdj - position.heightDir)
                                    bottom = maxOf(bottom, position.yDirAdj)
                                }
                            }
                            finish()
                            require(words.size <= 100_000) { "Página PDF excede o limite de análise." }
                        }
                    }.apply { sortByPosition = true; startPage = pageIndex + 1; endPage = pageIndex + 1 }
                    stripper.writeText(document, BoundedTextWriter(PdfStatementParser.MAX_TEXT_CHARS))
                    val page = document.getPage(pageIndex)
                    val imageFraction = PdfImageCoverage(page).fraction()
                    var width = page.cropBox.width
                    var wasScanned = false
                    val pageWords = if (imageFraction >= 0.20f || words.isEmpty()) {
                        if (renderer == null) {
                            stagedFile = File.createTempFile("pdf-ocr-", ".pdf", appContext.cacheDir).also {
                                if (document.isEncrypted) {
                                    document.isAllSecurityToBeRemoved = true
                                    document.save(it)
                                } else it.writeBytes(content)
                            }
                            descriptor = ParcelFileDescriptor.open(stagedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            renderer = PdfRenderer(requireNotNull(descriptor))
                        }
                        requireNotNull(renderer).openPage(pageIndex).use { rasterPage ->
                            val scale = min(3200f / maxOf(rasterPage.width, rasterPage.height),
                                sqrt(MAX_RENDER_PIXELS.toFloat() / (rasterPage.width.toFloat() * rasterPage.height)))
                            val bitmap = Bitmap.createBitmap((rasterPage.width * scale).toInt().coerceAtLeast(1),
                                (rasterPage.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                rasterPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                width = bitmap.width.toFloat()
                                if (isBlank(bitmap, checkCancelled)) emptyList()
                                else {
                                    require(++scannedPages <= MAX_OCR_PAGES) { "O PDF excede o limite de $MAX_OCR_PAGES páginas digitalizadas por arquivo." }
                                    wasScanned = true
                                    if (ocr == null) ocr = OfflinePdfOcr(appContext, checkCancelled)
                                    requireNotNull(ocr).recognize(bitmap)
                                }
                            } finally { bitmap.recycle() }
                        }
                    } else words
                    checkCancelled()
                    if (wasScanned) require(pageWords.isNotEmpty()) {
                        "Não foi possível reconhecer a página ${pageIndex + 1}. Use uma digitalização mais nítida."
                    }
                    output.write(layout.reconstruct(pageWords, width))
                    output.write("\n\u000c\n")
                }
                layout.finish()
                return PdfTextExtraction(output.toString(), scannedPages > 0)
            }
        } catch (error: InvalidPasswordException) {
            throw StatementPasswordRequiredException(!password.isNullOrEmpty())
        } catch (error: IOException) {
            throw IllegalArgumentException("Não foi possível ler o PDF. Verifique se o arquivo está íntegro.", error)
        } finally {
            try { ocr?.close() } finally {
                try { renderer?.close() } finally { try { descriptor?.close() } finally { stagedFile?.delete() } }
            }
        }
    }

    private fun initializeResources() {
        if (resourcesInitialized) return
        synchronized(RESOURCE_LOCK) {
            if (!resourcesInitialized) { PDFBoxResourceLoader.init(appContext); resourcesInitialized = true }
        }
    }

    private fun isBlank(bitmap: Bitmap, checkCancelled: () -> Unit): Boolean {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            if (y % 64 == 0) checkCancelled()
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (row.any { it and 0x00ffffff != 0x00ffffff }) return false
        }
        return true
    }

    private companion object {
        const val MAX_PAGES = 100
        const val MAX_OCR_PAGES = 30
        const val MAX_RENDER_PIXELS = 6_000_000
        val RESOURCE_LOCK = Any()
        @Volatile var resourcesInitialized = false
    }
}

internal class BoundedTextWriter(private val limit: Int) : Writer() {
    private val content = StringBuilder()
    override fun write(buffer: CharArray, offset: Int, length: Int) {
        require(length <= limit - content.length) { "O texto do PDF excede o limite de análise local." }
        content.append(buffer, offset, length)
    }
    override fun flush() = Unit
    override fun close() = Unit
    override fun toString(): String = content.toString()
}
