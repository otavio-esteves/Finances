package br.com.otavioesteves.finances.ui.screens.addtransaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import br.com.otavioesteves.finances.ui.theme.categoryAccent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import br.com.otavioesteves.finances.ui.components.FinanceGlassIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.addtransaction.AddTransactionViewModel
import java.time.Instant
import java.time.ZoneOffset
import br.com.otavioesteves.finances.utils.DateFormatter
import br.com.otavioesteves.finances.ui.components.transactionIconOption
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.SectionTitle
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.ui.components.TransactionIconOption
import br.com.otavioesteves.finances.ui.components.transactionIcon
import br.com.otavioesteves.finances.ui.components.transactionIconOptions
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddTransactionViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditMode = viewModel.isEditMode
    val scrollState = rememberScrollState()

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showIconChoices by rememberSaveable { mutableStateOf(false) }
    if (uiState.showCategoryCreation) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateCategory,
            title = { Text("Nova categoria") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(when (uiState.type) {
                        TransactionType.EXPENSE -> "Categoria de despesa"
                        TransactionType.INCOME -> "Categoria de receita"
                        TransactionType.TRANSFER -> "Categoria de transferência"
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Nome da categoria") }, singleLine = true,
                        enabled = !uiState.isCreatingCategory, isError = uiState.categoryCreationError != null,
                        supportingText = uiState.categoryCreationError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.createCategory(name) }, enabled = !uiState.isCreatingCategory) {
                    Text(if (uiState.isCreatingCategory) "Salvando…" else "Criar")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreateCategory, enabled = !uiState.isCreatingCategory) { Text("Cancelar") }
            }
        )
    }
    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1..9999
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        viewModel.onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }, enabled = picker.selectedDateMillis != null) { Text("Aplicar data") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = picker) }
    }

    LaunchedEffect(uiState.isSaveSuccessful) {
        if (uiState.isSaveSuccessful) {
            onBackClick()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteCancel,
            title = { Text("Excluir transação") },
            text = { Text("Tem certeza que deseja excluir esta transação?") },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirm) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteCancel) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                colors = financeTopAppBarColors(scrollState.value == 0),
                title = { Text(if (isEditMode) "Editar transação" else "Novo lançamento") },
                navigationIcon = {
                    FinanceGlassIconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    if (isEditMode && !uiState.isLoading && !uiState.isSaving && !uiState.isSaveSuccessful) {
                        FinanceGlassIconButton(onClick = viewModel::onDeleteRequest) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Excluir",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = paddingValues.calculateTopPadding() + 20.dp,
                        bottom = 20.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Description
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = { Text("Descrição") },
                        isError = uiState.descriptionError != null,
                        supportingText = uiState.descriptionError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )

                    // Amount
                    OutlinedTextField(
                        value = uiState.amount,
                        onValueChange = viewModel::onAmountChange,
                        label = { Text("Valor") },
                        placeholder = { Text("0,00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.amountError != null,
                        supportingText = uiState.amountError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )

                    FormSelectionField(
                        label = "Data",
                        value = DateFormatter.format(uiState.date),
                        icon = Icons.Filled.CalendarToday,
                        enabled = !uiState.isSaving,
                        onClick = { showDatePicker = true }
                    )

                    // Type
                    Column {
                        SectionTitle(title = "Tipo")
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TypeChoice("Despesa", TransactionType.EXPENSE, uiState.type == TransactionType.EXPENSE,
                                { viewModel.onTypeChange(TransactionType.EXPENSE) }, Modifier.fillMaxWidth())
                            TypeChoice("Receita", TransactionType.INCOME, uiState.type == TransactionType.INCOME,
                                { viewModel.onTypeChange(TransactionType.INCOME) }, Modifier.fillMaxWidth())
                            TypeChoice("Transferência", TransactionType.TRANSFER, uiState.type == TransactionType.TRANSFER,
                                { viewModel.onTypeChange(TransactionType.TRANSFER) }, Modifier.fillMaxWidth())
                        }
                    }

                    // Category
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedCategory?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Categoria") },
                            leadingIcon = uiState.selectedCategory?.let { category -> {
                                Icon(transactionIcon(category.name, uiState.type), contentDescription = null,
                                    tint = categoryAccent(category.id))
                            } },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            isError = uiState.categoryError != null,
                            supportingText = uiState.categoryError?.let { { Text(it) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = MaterialTheme.shapes.small
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            uiState.categories.filter { it.type.name == uiState.type.name }.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    leadingIcon = {
                                        Icon(transactionIcon(category.name, uiState.type), contentDescription = null,
                                            tint = categoryAccent(category.id))
                                    },
                                    onClick = {
                                        viewModel.onCategoryChange(category)
                                        expanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Nova categoria", color = MaterialTheme.colorScheme.primary) },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    expanded = false
                                    viewModel.showCreateCategory()
                                }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormSelectionField(
                            label = "Ícone",
                            value = transactionIconOption(uiState.iconKey)?.label ?: "Automático",
                            icon = transactionIcon(uiState.selectedCategory?.name, uiState.type,
                                uiState.description, uiState.iconKey),
                            expanded = showIconChoices,
                            enabled = !uiState.isSaving,
                            onClick = { showIconChoices = !showIconChoices }
                        )
                        if (showIconChoices) {
                        IconChoice(
                            option = null,
                            selected = uiState.iconKey == null,
                            categoryName = uiState.selectedCategory?.name,
                            description = uiState.description,
                            type = uiState.type,
                            onClick = { viewModel.onIconChange(null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        transactionIconOptions.chunked(3).forEach { options ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                options.forEach { option ->
                                    IconChoice(
                                        option = option,
                                        selected = uiState.iconKey == option.key,
                                        categoryName = uiState.selectedCategory?.name,
                                        description = uiState.description,
                                        type = uiState.type,
                                        onClick = { viewModel.onIconChange(option.key) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - options.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = uiState.sourceLabel,
                        onValueChange = viewModel::onSourceLabelChange,
                        label = { Text("Conta ou cartão (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = viewModel::onNotesChange,
                        label = { Text("Observação (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = MaterialTheme.shapes.small
                    )
                }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryActionButton(
                        text = if (uiState.isSaving) "Salvando..." else "Salvar",
                        onClick = viewModel::saveTransaction,
                        enabled = !uiState.isSaving && !uiState.isSaveSuccessful
                    )
                    PrimaryActionButton(
                        text = "Cancelar",
                        onClick = onBackClick,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FormSelectionField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    expanded: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IconChoice(
    option: TransactionIconOption?,
    selected: Boolean,
    categoryName: String?,
    description: String,
    type: TransactionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val background = accent.copy(alpha = if (selected) 0.18f else 0.08f)
    val label = option?.label ?: "Automático"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = option?.image ?: transactionIcon(categoryName, type, description),
            contentDescription = label,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(background)
                .padding(9.dp)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TypeChoice(
    label: String,
    type: TransactionType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .background(if (selected) FinancesThemeTokens.colors.heroSurface else Color.Transparent, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (selected) 0.7f else 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (type) {
                TransactionType.INCOME -> Icons.Filled.SouthWest
                TransactionType.EXPENSE -> Icons.Filled.NorthEast
                TransactionType.TRANSFER -> Icons.Filled.SwapHoriz
            },
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
