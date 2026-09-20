package br.com.otavioesteves.finances.domain.model

/** Where a [CategorySuggestion] came from — lets the UI be honest about what's AI, rule, or human. */
enum class SuggestionSource { AI, RULE, USER }
