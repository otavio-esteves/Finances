package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.RawStatementEntry

interface StatementParserRepository {
    suspend fun parse(fileName: String, rawContent: ByteArray): List<RawStatementEntry>
}
