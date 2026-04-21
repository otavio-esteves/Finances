# Finances

App financeiro focado em simplicidade e organização.

## Estado do Projeto
- Package padronizado para `br.com.otavioesteves.finances`
- Arquitetura limpa com `domain/model`
- Gerenciamento de estado na UI via `CategoriesUiState`
- Tipagem de dinheiro isolada (não utiliza `Double`)
- Dependências gerenciadas via Version Catalog
- Visual escuro minimalista

## Como rodar
```bash
./gradlew assembleDebug
```

## Como testar
```bash
./gradlew test
```
