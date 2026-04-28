package br.com.otavioesteves.finances.ui.screens.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.transactions.TransactionItem
import br.com.otavioesteves.finances.presentation.transactions.TransactionsUiState
import br.com.otavioesteves.finances.presentation.transactions.TransactionsViewModel
import br.com.otavioesteves.finances.ui.components.AmountText
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.utils.DateFormatter
import br.com.otavioesteves.finances.utils.ExportFormat
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.formatMonthPeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onBackClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            val data = viewModel.getExportData(ExportFormat.CSV)
            context.contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(data.toByteArray())
            }
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val data = viewModel.getExportData(ExportFormat.JSON)
            context.contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(data.toByteArray())
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorDismiss()
        }
    }

    if (uiState.transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteCancel,
            title = { Text("Excluir transação") },
            text = { Text("Tem certeza que deseja excluir esta transação? Esta ação não pode ser desfeita.") },
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Histórico")
                        Text(
                            text = formatMonthPeriod(uiState.monthPeriod),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Exportar CSV") },
                            onClick = {
                                showMenu = false
                                csvLauncher.launch("finances_${uiState.monthPeriod.year}_${uiState.monthPeriod.month}.csv")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar JSON") },
                            onClick = {
                                showMenu = false
                                jsonLauncher.launch("finances_${uiState.monthPeriod.year}_${uiState.monthPeriod.month}.json")
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        TransactionsContent(
            state = uiState,
            onDeleteTransaction = viewModel::onDeleteRequest,
            onTransactionClick = onTransactionClick,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun TransactionsContent(
    state: TransactionsUiState,
    onDeleteTransaction: (Transaction) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.isLoading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }
        state.transactions.isEmpty() -> {
            EmptyState(
                message = "Nenhuma transação encontrada para este mês.",
                modifier = modifier.fillMaxSize()
            )
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize()
            ) {
                items(state.transactions, key = { it.transaction.id }) { item ->
                    TransactionRow(
                        item = item,
                        onDelete = { onDeleteTransaction(item.transaction) },
                        modifier = Modifier.clickable { onTransactionClick(item.transaction) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    item: TransactionItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transaction = item.transaction
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${item.category?.name ?: "Sem categoria"} • ${DateFormatter.format(transaction.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        AmountText(
            amount = MoneyFormatter.format(transaction.amount),
            isPositive = transaction.type == TransactionType.INCOME,
            style = MaterialTheme.typography.bodyLarge
        )

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Excluir transação",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
