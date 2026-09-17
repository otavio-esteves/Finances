package br.com.otavioesteves.finances.domain.model

import java.time.LocalDateTime

data class ImportedStatement(
    val id: Long,
    val fileName: String,
    val importedAt: LocalDateTime,
    val transactionCount: Int
) {
    init {
        require(id >= 0) { "id must be positive or zero" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
        require(transactionCount >= 0) { "transactionCount must be positive or zero" }
    }
}
