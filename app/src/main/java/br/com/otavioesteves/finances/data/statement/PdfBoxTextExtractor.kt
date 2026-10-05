package br.com.otavioesteves.finances.data.statement

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException

fun interface PdfTextExtractor {
    fun extract(content: ByteArray): String
}

class PdfBoxTextExtractor(private val context: Context) : PdfTextExtractor {
    override fun extract(content: ByteArray): String {
        PDFBoxResourceLoader.init(context)
        try {
            PDDocument.load(content).use { document ->
                require(!document.isEncrypted) { "PDF protegido por senha não é aceito." }
                require(document.numberOfPages <= MAX_PAGES) { "PDF com mais de $MAX_PAGES páginas não é aceito." }
                return PDFTextStripper().apply { sortByPosition = true }.getText(document)
            }
        } catch (error: IOException) {
            throw IllegalArgumentException("Não foi possível ler o PDF. Verifique se o arquivo está íntegro e sem senha.", error)
        }
    }

    private companion object {
        const val MAX_PAGES = 100
    }
}
