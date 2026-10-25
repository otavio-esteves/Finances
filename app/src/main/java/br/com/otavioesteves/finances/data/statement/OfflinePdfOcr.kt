package br.com.otavioesteves.finances.data.statement

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CancellationException
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

/** Bundled Portuguese OCR; no services, network requests, or first-run model downloads. */
internal class OfflinePdfOcr(context: Context, private val checkCancelled: () -> Unit) : Closeable {
    private val tess: TessBaseAPI
    private var cancellation: CancellationException? = null

    init {
        val dataRoot = File(context.filesDir, "ocr")
        val model = File(dataRoot, "tessdata/por.traineddata")
        synchronized(MODEL_LOCK) {
            model.parentFile!!.mkdirs()
            if (!model.isFile || sha256(model) != MODEL_SHA256) {
                val candidate = File(model.parentFile, "por.tmp")
                try {
                    context.assets.open("ocr/por.traineddata").use { input -> candidate.outputStream().use { input.copyTo(it) } }
                    check(sha256(candidate) == MODEL_SHA256) { "Modelo de OCR inválido." }
                    check(candidate.renameTo(model)) { "Não foi possível preparar o OCR local." }
                } finally { candidate.delete() }
            }
        }
        var engine: TessBaseAPI? = null
        tess = TessBaseAPI {
            try { checkCancelled() } catch (cancelled: CancellationException) {
                cancellation = cancelled
                engine?.stop()
            }
        }
        engine = tess
        if (!tess.init(dataRoot.absolutePath, "por", TessBaseAPI.OEM_LSTM_ONLY)) {
            tess.recycle()
            error("Não foi possível iniciar o OCR local.")
        }
        tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        tess.setVariable("preserve_interword_spaces", "1")
    }

    fun recognize(bitmap: Bitmap): List<PdfWord> {
        checkCancelled()
        tess.setImage(bitmap)
        try {
            tess.getHOCRText(0) // Unlike getUTF8Text(), this recognition entry point supports stop().
            cancellation?.let { throw it }
            checkCancelled()
            val iterator = tess.resultIterator ?: return emptyList()
            val words = mutableListOf<PdfWord>()
            try {
                iterator.begin()
                do {
                    checkCancelled()
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        val box = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        val confidence = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        require(!FINANCIAL_TOKEN.containsMatchIn(text) || confidence >= 80f) {
                            "O OCR encontrou data ou valor pouco legível. Use uma digitalização mais nítida ou o PDF original."
                        }
                        words += PdfWord(text, box[0].toFloat(), box[1].toFloat(), box[2].toFloat(), box[3].toFloat(), confidence)
                        require(words.size <= 100_000) { "Página digitalizada muito extensa." }
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            } finally { iterator.delete() }
            return words
        } finally { tess.clear() }
    }

    override fun close() { tess.recycle() }

    companion object {
        private val MODEL_LOCK = Any()
        private const val MODEL_SHA256 = "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb"
        private val FINANCIAL_TOKEN = Regex("\\d[\\d.]*,\\d{2}|\\d{1,2}[./-]\\d{1,2}")
        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
