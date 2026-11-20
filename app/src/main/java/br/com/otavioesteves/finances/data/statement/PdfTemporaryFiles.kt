package br.com.otavioesteves.finances.data.statement

import java.io.File
import java.util.UUID

/** Only our own previous-process PDF files are removed; live-session files and other cache are preserved. */
internal object PdfTemporaryFiles {
    private val session = "finances-pdf-session-${UUID.randomUUID()}"
    private val sessionPattern = Regex("finances-pdf-session-[a-f0-9-]{36}")
    private val pdfPattern = Regex("pdf-ocr-.*\\.pdf")

    fun create(cache: File): File {
        val directory = File(cache, session)
        check(directory.isDirectory || directory.mkdirs()) { "Não foi possível preparar o reconhecimento do PDF." }
        return File.createTempFile("pdf-ocr-", ".pdf", directory)
    }

    fun cleanupPreviousSessions(cache: File) {
        cache.listFiles()?.forEach { file ->
            when {
                file.isFile && pdfPattern.matches(file.name) -> file.delete() // Legacy staging location.
                file.isDirectory && file.name != session && sessionPattern.matches(file.name) -> {
                    file.listFiles()?.filter { it.isFile && pdfPattern.matches(it.name) }?.forEach { it.delete() }
                    file.delete() // Only succeeds for an empty directory; never recursively erase unknown files.
                }
            }
        }
    }
}
