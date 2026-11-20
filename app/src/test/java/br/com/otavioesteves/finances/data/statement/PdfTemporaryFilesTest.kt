package br.com.otavioesteves.finances.data.statement

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PdfTemporaryFilesTest {
    @Test fun crashResiduesAreRemovedWithoutDeletingActivePdfsOrUnrelatedCache() {
        val cache = Files.createTempDirectory("finances-pdf-cleanup-").toFile()
        try {
            val live = PdfTemporaryFiles.create(cache).apply { writeText("active") }
            val legacy = File(cache, "pdf-ocr-123.pdf").apply { writeText("old") }
            val oldSession = File(cache, "finances-pdf-session-00000000-0000-0000-0000-000000000000").apply { mkdir() }
            val stale = File(oldSession, "pdf-ocr-456.pdf").apply { writeText("old") }
            val unrelated = File(oldSession, "keep.txt").apply { writeText("keep") }
            val outside = File(cache, "other.pdf").apply { writeText("keep") }
            PdfTemporaryFiles.cleanupPreviousSessions(cache)
            assertTrue(live.isFile)
            assertFalse(legacy.exists())
            assertFalse(stale.exists())
            assertTrue(unrelated.isFile)
            assertTrue(outside.isFile)
        } finally { cache.deleteRecursively() }
    }
}
