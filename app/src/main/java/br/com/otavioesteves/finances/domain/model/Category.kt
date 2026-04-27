package br.com.otavioesteves.finances.domain.model

data class Category(
    val id: Long,
    val name: String,
    val type: CategoryType
) {
    init {
        require(id >= 0) { "id must be positive or zero" }
        require(name.isNotBlank()) { "name must not be blank" }
    }
}
