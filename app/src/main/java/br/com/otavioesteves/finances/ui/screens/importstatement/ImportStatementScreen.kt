package br.com.otavioesteves.finances.ui.screens.importstatement

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import br.com.otavioesteves.finances.ui.components.FinanceGlassIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.presentation.importstatement.ImportProcessingStage
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementViewModel
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import kotlinx.coroutines.delay
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
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            val fileName = queryDisplayName(context, selectedUri) ?: "arquivo financeiro"
            viewModel.onFileSelected(fileName, selectedKind, selectedYear) {
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
                    onSelectFileClick = { kind, year ->
                        selectedKind = kind
                        selectedYear = year
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
                    onSourceLabelChanged = viewModel::onSourceLabelChanged,
                    onExpectedTotalChanged = viewModel::onExpectedTotalChanged,
                    onDifferenceAcknowledged = viewModel::onDifferenceAcknowledged,
                    onFilterSelected = viewModel::onFilterSelected,
                    onSelectionModeChanged = viewModel::onSelectionModeChanged,
                    onEntrySelectionChanged = viewModel::onEntrySelectionChanged,
                    onSelectVisibleEntries = viewModel::onSelectVisibleEntries,
                    onBulkCategorySelected = viewModel::onBulkCategorySelected,
                    onEntryExcluded = viewModel::onEntryExcluded,
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
    onSelectFileClick: (StatementKind, Int?) -> Unit,
    onAddTransactionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var kind by rememberSaveable { mutableStateOf(StatementKind.BANK_STATEMENT) }
    var year by rememberSaveable { mutableStateOf("") }
    val yearValid = year.isBlank() || year.toIntOrNull() in 1..9999
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
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("Ano das compras sem ano (opcional, PDF)") },
                    supportingText = { Text("Preencha apenas se todas as compras sem ano forem do mesmo ano. Para compras de anos diferentes, use datas completas em CSV. O período explícito do PDF tem prioridade.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, isError = !yearValid, modifier = Modifier.fillMaxWidth()
                )
                PrimaryActionButton(text = "Selecionar arquivo", enabled = yearValid,
                    onClick = { onSelectFileClick(kind, year.toIntOrNull()) })
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
internal fun LoadingContent(
    state: ImportStatementUiState.Loading,
    modifier: Modifier = Modifier
) {
    var elapsedSeconds by remember(state.fileName) { mutableStateOf(0L) }
    LaunchedEffect(state.fileName) {
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            delay(1_000)
            elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1_000
        }
    }
    val title = when (state.stage) {
        ImportProcessingStage.READING -> "Abrindo arquivo…"
        ImportProcessingStage.EXTRACTING -> "Lendo transações…"
        ImportProcessingStage.CHECKING_OVERLAPS -> "Conferindo transações…"
        ImportProcessingStage.CATEGORIZING -> "Organizando categorias…"
        ImportProcessingStage.PREPARING_REVIEW -> "Preparando sua revisão…"
    }
    val progress = state.progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f, animationSpec = tween(450), label = "importProgress"
    )
    Column(
        modifier = modifier.fillMaxSize().navigationBarsPadding().padding(32.dp)
            .testTag("import_processing"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // This animation represents activity, including while a whole AI batch is pending.
        // The separate bar below represents only completed work, never elapsed time.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize().testTag("import_activity"),
                strokeWidth = 3.dp
            )
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (progress == null || state.processedEntries == 0) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("import_progress_indeterminate"))
        } else {
            LinearProgressIndicator(progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().testTag("import_progress_measured"))
        }
        if (state.stage == ImportProcessingStage.CATEGORIZING && state.totalEntries != null) {
            Text(
                "${state.processedEntries} de ${state.totalEntries} transações categorizadas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Text(
            if (elapsedSeconds < 15) "Seus dados ficam no aparelho."
            else "Ainda trabalhando. Você poderá revisar tudo ao concluir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            "Tempo decorrido: ${elapsedSeconds / 60}min ${elapsedSeconds % 60}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
