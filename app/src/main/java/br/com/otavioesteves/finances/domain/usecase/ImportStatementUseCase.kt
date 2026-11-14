package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository

class ImportStatementUseCase(
    private val repository: StatementParserRepository
) {
    suspend operator fun invoke(fileName: String, rawContent: ByteArray, kind: StatementKind = StatementKind.BANK_STATEMENT, password: String? = null, referenceYear: Int? = null): List<RawStatementEntry> {
        require(referenceYear == null || referenceYear in 1..9999) { "Informe um ano entre 1 e 9999." }
        return repository.parse(fileName, rawContent, kind, password, referenceYear)
    }
}
