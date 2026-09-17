package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository

class ImportStatementUseCase(
    private val repository: StatementParserRepository
) {
    suspend operator fun invoke(fileName: String, rawContent: ByteArray): List<RawStatementEntry> {
        return repository.parse(fileName, rawContent)
    }
}
