package br.com.otavioesteves.finances.ui.screens.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.transactions.TransactionsUiState
import br.com.otavioesteves.finances.presentation.transactions.TransactionsViewModel
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.MonthPeriodSelector
import br.com.otavioesteves.finances.ui.components.TransactionListRow
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.utils.ExportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isAtTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }

    fun exportTo(uri: Uri, format: ExportFormat) {
        scope.launch {
            try {
                val data = withContext(Dispatchers.Default) { viewModel.getExportData(format) }
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Não foi possível abrir o destino do arquivo.")
                    stream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.message ?: "Não foi possível exportar as transações.")
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { exportTo(it, ExportFormat.CSV) }
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportTo(it, ExportFormat.JSON) }
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
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                colors = financeTopAppBarColors(isAtTop),
                title = { Text("Histórico") },
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
            onPreviousMonth = { viewModel.onMonthSelected(uiState.monthPeriod.previousMonth()) },
            onNextMonth = { viewModel.onMonthSelected(uiState.monthPeriod.nextMonth()) },
            onDeleteTransaction = viewModel::onDeleteRequest,
            onTransactionClick = onTransactionClick,
            listState = listState,
            scaffoldPadding = paddingValues,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TransactionsContent(
    state: TransactionsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scaffoldPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(
            start = scaffoldPadding.calculateLeftPadding(layoutDirection) + 20.dp,
            end = scaffoldPadding.calculateRightPadding(layoutDirection) + 20.dp,
            top = scaffoldPadding.calculateTopPadding(),
            bottom = bottomPadding
        )
    ) {
        item(key = "month_selector") {
            MonthPeriodSelector(
                monthPeriod = state.monthPeriod,
                onPreviousClick = onPreviousMonth,
                onNextClick = onNextMonth,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)
            )
        }
        when {
            state.isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.transactions.isEmpty() -> item(key = "empty") {
                EmptyState(message = "Nenhuma transação encontrada para este mês.")
            }
            else -> items(
                items = state.transactions,
                key = { it.transaction.id },
                contentType = { "transaction" }
            ) { item ->
                val shape = MaterialTheme.shapes.large
                TransactionListRow(
                    transaction = item.transaction,
                    categoryName = item.category?.name,
                    formattedAmount = item.formattedAmount,
                    formattedDate = item.formattedDate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f), shape)
                        .clickable { onTransactionClick(item.transaction) }
                        .padding(horizontal = 14.dp),
                    trailing = {
                        IconButton(onClick = { onDeleteTransaction(item.transaction) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Excluir transação",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}
