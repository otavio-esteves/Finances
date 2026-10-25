package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository

/** Routes supported file types without interpreting binary PDF bytes as CSV. */
class FinancialStatementParser(
    private val csvOfxParser: StatementParserRepository,
    private val pdfParser: StatementParserRepository
) : StatementParserRepository {
    override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind, password: String?): List<RawStatementEntry> {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "csv", "ofx", "qfx" -> csvOfxParser.parse(fileName, rawContent, kind, password)
            "pdf" -> pdfParser.parse(fileName, rawContent, kind, password)
            else -> throw IllegalArgumentException("Formato não aceito. Escolha um arquivo CSV, OFX ou PDF.")
        }
    }
}
