package br.com.otavioesteves.finances.ui.screens.importstatement

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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementViewModel
import br.com.otavioesteves.finances.ui.components.AmountText
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens
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
                title = { Text("Importar transações") },
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
                    onSelectFileClick = { kind ->
                        selectedKind = kind
                        filePickerLauncher.launch("*/*")
                    }
                )

                is ImportStatementUiState.Loading -> LoadingContent()
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Comece pelo seu arquivo",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(FinancesThemeTokens.colors.heroSurface)
                        .border(1.dp, FinancesThemeTokens.colors.border.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "Fatura ou extrato",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Escolha o tipo de arquivo. Em CSV de fatura, valores positivos são compras e negativos são estornos. Confira todos os lançamentos antes de importar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                PrimaryActionButton(
                    text = "Escolher extrato",
                    onClick = { onSelectFileClick(StatementKind.BANK_STATEMENT) }
                )
                PrimaryActionButton(
                    text = "Escolher fatura",
                    onClick = { onSelectFileClick(StatementKind.CARD_BILL) }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = "Organizando suas transações...",
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
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = FinancesThemeTokens.colors.income,
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
            Text(
                text = "${state.suggestions.size} lançamentos · Despesas: ${MoneyFormatter.format(expenses)} · Receitas: ${MoneyFormatter.format(incomes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    Checkbox(checked = state.overlapAcknowledged, onCheckedChange = onOverlapAcknowledged)
                    Text("Revisei os possíveis lançamentos repetidos")
                }
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
    modifier: Modifier = Modifier
) {
    var showEditor by remember { mutableStateOf(false) }
    if (showEditor) EntryEditor(suggestion, onEntryEdited, onDismiss = { showEditor = false })
    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { showEditor = true }) { Text("Corrigir dados") }
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
                TextButton(onClick = { typeExpanded = true }) {
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
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                onTypeSelected(type)
                                typeExpanded = false
                            })
                        }
                }
            }

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = suggestion.suggestedCategory
                        ?.takeIf { it.type.name == suggestion.entry.type.name }
                        ?.name ?: "Selecione uma categoria",
                    onValueChange = {},
                    readOnly = true,
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
