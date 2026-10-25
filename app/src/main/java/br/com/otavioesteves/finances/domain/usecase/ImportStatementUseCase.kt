package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository

class ImportStatementUseCase(
    private val repository: StatementParserRepository
) {
    suspend operator fun invoke(fileName: String, rawContent: ByteArray, kind: StatementKind = StatementKind.BANK_STATEMENT, password: String? = null): List<RawStatementEntry> {
        return repository.parse(fileName, rawContent, kind, password)
    }
}
