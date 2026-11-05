package br.com.otavioesteves.finances.ui.screens.importstatement

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import br.com.otavioesteves.finances.ui.components.FinanceGlassIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.math.BigDecimal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.presentation.importstatement.ImportProcessingStage
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementViewModel
import br.com.otavioesteves.finances.ui.components.AmountText
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.utils.DateFormatter
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.queryDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStatementScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportStatementViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onAddTransactionClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = uiState is ImportStatementUiState.Loading) { viewModel.onStartOver() }
    val context = LocalContext.current
    val contentResolver = context.applicationContext.contentResolver
    var isAtTop by remember { mutableStateOf(true) }
    var selectedKind by rememberSaveable { mutableStateOf(StatementKind.BANK_STATEMENT) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            val fileName = queryDisplayName(context, selectedUri) ?: "arquivo financeiro"
            viewModel.onFileSelected(fileName, selectedKind) {
                contentResolver.openInputStream(selectedUri)
                    ?: throw IllegalArgumentException("Não foi possível abrir o arquivo selecionado.")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                colors = financeTopAppBarColors(isAtTop),
                title = { if (uiState !is ImportStatementUiState.Loading) Text("Importar transações") },
                navigationIcon = {
                    FinanceGlassIconButton(onClick = {
                        if (uiState is ImportStatementUiState.Loading) viewModel.onStartOver() else onBackClick()
                    }) {
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
                    onAddTransactionClick = onAddTransactionClick,
                    onSelectFileClick = { kind ->
                        selectedKind = kind
                        filePickerLauncher.launch("*/*")
                    }
                )

                is ImportStatementUiState.Loading -> LoadingContent(state)
                is ImportStatementUiState.NeedsPassword -> PasswordContent(state, viewModel::onPasswordSubmitted, viewModel::onStartOver)

                is ImportStatementUiState.ReviewingSuggestions -> ReviewContent(
                    state = state,
                    onCategorySelected = viewModel::onCategorySelected,
                    onTypeSelected = viewModel::onTypeSelected,
                    onOverlapAcknowledged = viewModel::onOverlapAcknowledged,
                    onOcrAcknowledged = viewModel::onOcrAcknowledged,
                    onEntryEdited = viewModel::onEntryEdited,
                    onConfirmClick = viewModel::onConfirmImport,
                    onCancelClick = viewModel::onStartOver,
                    onScrollTopChange = { isAtTop = it }
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
    onSelectFileClick: (StatementKind) -> Unit,
    onAddTransactionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var kind by rememberSaveable { mutableStateOf(StatementKind.BANK_STATEMENT) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Importar de um arquivo", style = MaterialTheme.typography.titleLarge)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatementKindChoice("Extrato bancário", "Entradas e saídas da conta",
                        Icons.Filled.AccountBalance, kind == StatementKind.BANK_STATEMENT,
                        onClick = { kind = StatementKind.BANK_STATEMENT })
                    StatementKindChoice("Fatura de cartão", "Compras e estornos do cartão",
                        Icons.Filled.CreditCard, kind == StatementKind.CARD_BILL,
                        onClick = { kind = StatementKind.CARD_BILL })
                }
                Text("PDF, CSV ou OFX · até 10 MB", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryActionButton(text = "Selecionar arquivo", onClick = { onSelectFileClick(kind) })
            }
        }
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Lançamento individual", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onAddTransactionClick, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Adicionar lançamento", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatementKindChoice(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun LoadingContent(
    state: ImportStatementUiState.Loading,
    modifier: Modifier = Modifier
) {
    val title = when (state.stage) {
        ImportProcessingStage.READING, ImportProcessingStage.EXTRACTING -> "Importando…"
        ImportProcessingStage.CHECKING_OVERLAPS, ImportProcessingStage.CATEGORIZING,
        ImportProcessingStage.PREPARING_REVIEW -> "Processando…"
    }
    Column(
        modifier = modifier.fillMaxSize().navigationBarsPadding().padding(32.dp)
            .testTag("import_processing"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        val progress = state.progress
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("import_progress_indeterminate"))
        } else {
            LinearProgressIndicator(progress = { progress },
                modifier = Modifier.fillMaxWidth().testTag("import_progress_measured"))
        }
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
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = if (transactionCount == 1) {
                "1 transação importada com sucesso."
            } else {
                "$transactionCount transações importadas com sucesso."
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
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
    onTypeSelected: (Int, TransactionType) -> Unit,
    onOverlapAcknowledged: (Boolean) -> Unit,
    onOcrAcknowledged: (Boolean) -> Unit,
    onEntryEdited: (Int, String, String, String) -> String?,
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit,
    onScrollTopChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    LaunchedEffect(isAtTop) { onScrollTopChange(isAtTop) }
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Confira os lançamentos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = state.fileName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val expenses = state.suggestions.filter { it.entry.type == TransactionType.EXPENSE }
                .fold(Money.Zero) { total, suggestion -> total + suggestion.entry.amount }
            val incomes = state.suggestions.filter { it.entry.type == TransactionType.INCOME }
                .fold(Money.Zero) { total, suggestion -> total + suggestion.entry.amount }
            Text("${state.suggestions.size} lançamentos para conferir", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Despesas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(MoneyFormatter.format(expenses), isPositive = false, style = MaterialTheme.typography.titleSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text("Receitas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(MoneyFormatter.format(incomes), isPositive = true, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(state.suggestions) { index: Int, suggestion: CategorySuggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    availableCategories = state.availableCategories,
                    enabled = !state.isConfirming,
                    onCategorySelected = { category -> onCategorySelected(index, category) },
                    onEntryEdited = { description, amount, date -> onEntryEdited(index, description, amount, date) },
                    onTypeSelected = { type -> onTypeSelected(index, type) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.requiresOcrReview) {
                Text("Este PDF passou por reconhecimento de imagem. Confira datas, valores e descrições; use Corrigir dados em qualquer lançamento.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.ocrAcknowledged, onCheckedChange = onOcrAcknowledged, enabled = !state.isConfirming)
                    Text("Conferi os dados reconhecidos", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.potentialOverlapCount > 0) {
                Text(
                    "${state.potentialOverlapCount} lançamento(s) parecem já existir no histórico. Confira antes de continuar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.overlapAcknowledged, onCheckedChange = onOverlapAcknowledged, enabled = !state.isConfirming)
                    Text("Revisei os possíveis lançamentos repetidos")
                }
            }
            if (state.isConfirming || state.isCheckingOverlaps) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(if (state.isConfirming) "Salvando seus lançamentos…" else "Conferindo possíveis repetições…",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            PrimaryActionButton(
                text = if (state.isConfirming) "Confirmando..." else "Confirmar Importação",
                onClick = onConfirmClick,
                enabled = state.canConfirm
            )
            TextButton(onClick = onCancelClick, enabled = !state.isConfirming) {
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
    onTypeSelected: (TransactionType) -> Unit,
    onEntryEdited: (String, String, String) -> String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showEditor by remember { mutableStateOf(false) }
    if (showEditor) EntryEditor(suggestion, onEntryEdited, onDismiss = { showEditor = false })
    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { showEditor = true }, enabled = enabled) { Text("Corrigir dados") }
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

            var typeExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { typeExpanded = true }, enabled = enabled) {
                    Text(
                        when (suggestion.entry.type) {
                            TransactionType.EXPENSE -> "Tipo: Despesa"
                            TransactionType.INCOME -> "Tipo: Receita"
                            TransactionType.TRANSFER -> "Tipo: Pagamento de fatura / transferência"
                        }
                    )
                }
                DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    listOf(
                        TransactionType.EXPENSE to "Despesa",
                        TransactionType.INCOME to "Receita",
                        TransactionType.TRANSFER to "Pagamento de fatura / transferência"
                    ).filter { (type, _) -> availableCategories.any { it.type.name == type.name } }
                        .forEach { (type, label) ->
                            DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = {
                                onTypeSelected(type)
                                typeExpanded = false
                            })
                        }
                }
            }

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (enabled) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = suggestion.suggestedCategory
                        ?.takeIf { it.type.name == suggestion.entry.type.name }
                        ?.name ?: "Selecione uma categoria",
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = { Text("Categoria") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    isError = suggestion.suggestedCategory?.type?.name != suggestion.entry.type.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = MaterialTheme.shapes.small
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableCategories.filter { it.type.name == suggestion.entry.type.name }.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            enabled = enabled,
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

@Composable
private fun PasswordContent(state: ImportStatementUiState.NeedsPassword, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember(state) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PDF protegido", style = MaterialTheme.typography.titleLarge)
        Text(state.fileName, style = MaterialTheme.typography.bodySmall)
        Text("Informe a senha fornecida pelo banco. Ela é usada apenas nesta leitura e não fica salva.")
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha do PDF") },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, isError = state.error != null, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryActionButton(text = "Abrir PDF", enabled = password.isNotEmpty(), onClick = {
            val entered = password
            password = ""
            onSubmit(entered)
        })
        TextButton(onClick = onCancel) { Text("Cancelar") }
    }
}

@Composable
private fun EntryEditor(suggestion: CategorySuggestion, onSave: (String, String, String) -> String?, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf(suggestion.entry.description) }
    var amount by remember { mutableStateOf(BigDecimal.valueOf(suggestion.entry.amount.cents, 2).toPlainString().replace('.', ',')) }
    var date by remember { mutableStateOf(DateFormatter.format(suggestion.entry.date)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Corrigir lançamento") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descrição") })
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Valor sem sinal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Data (dd/MM/aaaa)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            error = onSave(description, amount, date)
            if (error == null) onDismiss()
        }) { Text("Aplicar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
