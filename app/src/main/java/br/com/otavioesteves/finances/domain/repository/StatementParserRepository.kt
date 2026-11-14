package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.StatementKind

interface StatementParserRepository {
    suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind = StatementKind.BANK_STATEMENT, password: String? = null, referenceYear: Int? = null): List<RawStatementEntry>
}
