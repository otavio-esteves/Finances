package br.com.otavioesteves.finances.ui.screens.importstatement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementViewModel
import br.com.otavioesteves.finances.ui.components.AmountText
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
import br.com.otavioesteves.finances.utils.DateFormatter
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.queryDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStatementScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportStatementViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            val fileName = queryDisplayName(context, selectedUri) ?: "extrato"
            val bytes = context.contentResolver.openInputStream(selectedUri)?.use { it.readBytes() }
            if (bytes != null) {
                viewModel.onFileSelected(fileName, bytes)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar Extrato") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ImportStatementUiState.Idle -> IdleContent(
                    onSelectFileClick = { filePickerLauncher.launch("*/*") }
                )

                is ImportStatementUiState.Loading -> LoadingContent()

                is ImportStatementUiState.ReviewingSuggestions -> ReviewContent(
                    state = state,
                    onCategorySelected = viewModel::onCategorySelected,
                    onConfirmClick = viewModel::onConfirmImport,
                    onCancelClick = viewModel::onStartOver
                )

                is ImportStatementUiState.Success -> SuccessContent(
                    transactionCount = state.transactionCount,
                    onDoneClick = onBackClick
                )

                is ImportStatementUiState.Error -> EmptyState(
                    message = state.message,
                    modifier = Modifier.fillMaxSize(),
                    onActionClick = viewModel::onStartOver,
                    actionLabel = "Tentar novamente"
                )
            }
        }
    }
}

@Composable
private fun IdleContent(
    onSelectFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Importe um extrato bancário (CSV ou OFX) para que a IA local sugira categorias para cada transação. Nada é enviado para fora do aparelho.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryActionButton(
            text = "Selecionar Arquivo",
            onClick = onSelectFileClick
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = "Lendo extrato e sugerindo categorias...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun SuccessContent(
    transactionCount: Int,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (transactionCount == 1) {
                "1 transação importada com sucesso."
            } else {
                "$transactionCount transações importadas com sucesso."
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        PrimaryActionButton(
            text = "Concluir",
            onClick = onDoneClick,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewContent(
    state: ImportStatementUiState.ReviewingSuggestions,
    onCategorySelected: (Int, Category) -> Unit,
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = state.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${state.suggestions.size} transações encontradas. Revise as categorias sugeridas antes de confirmar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(state.suggestions) { index: Int, suggestion: CategorySuggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    availableCategories = state.availableCategories,
                    onCategorySelected = { category -> onCategorySelected(index, category) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryActionButton(
                text = if (state.isConfirming) "Confirmando..." else "Confirmar Importação",
                onClick = onConfirmClick,
                enabled = state.canConfirm
            )
            TextButton(onClick = onCancelClick) {
                Text("Cancelar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionRow(
    suggestion: CategorySuggestion,
    availableCategories: List<Category>,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.entry.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = DateFormatter.format(suggestion.entry.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AmountText(
                    amount = MoneyFormatter.format(suggestion.entry.amount),
                    isPositive = suggestion.entry.type == TransactionType.INCOME,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = suggestion.suggestedCategory?.name ?: "Selecione uma categoria",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoria") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    isError = suggestion.suggestedCategory == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = MaterialTheme.shapes.small
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onCategorySelected(category)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
