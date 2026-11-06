package br.com.otavioesteves.finances.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.presentation.settings.SettingsViewModel
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.utils.DateFormatter

@Composable
internal fun NotificationSettingsCard() {
    val context = LocalContext.current
    val reminders = (context.applicationContext as MainApplication).invoiceReminders
    val enabled by reminders.enabled.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(reminders.canNotify()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = reminders.canNotify()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        reminders.setEnabled(granted)
        allowed = reminders.canNotify()
    }
    FinanceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NOTIFICAÇÕES", style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lembretes de faturas", Modifier.weight(1f))
                Switch(checked = enabled && allowed, onCheckedChange = { checked ->
                    if (!checked) reminders.setEnabled(false)
                    else if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        reminders.markPermissionAsked()
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        reminders.setEnabled(true)
                        allowed = reminders.canNotify()
                    }
                })
            }
            Text("Último dia do mês e dia 05, às 9h: Já faturou esse mês?",
                style = MaterialTheme.typography.bodySmall)
            if (!allowed) {
                Text("As notificações precisam estar permitidas no Android.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val settings = if (Build.VERSION.SDK_INT >= 26) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    }
                    context.startActivity(settings)
                }) { Text("Permitir no Android") }
            }
        }
    }
}

@Composable
internal fun ImportManagementCard(viewModel: SettingsViewModel, onHistoryClick: () -> Unit) {
    var showMemoryConfirmation by rememberSaveable { mutableStateOf(false) }
    FinanceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onHistoryClick) { Text("Histórico de importações") }
            Text("MEMÓRIA DE CATEGORIAS", style = MaterialTheme.typography.labelSmall)
            Text("Suas correções confirmadas são lembradas para a mesma descrição e tipo de lançamento.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showMemoryConfirmation = true }) { Text("Apagar memória das correções") }
        }
    }
    if (showMemoryConfirmation) AlertDialog(
        onDismissRequest = { showMemoryConfirmation = false },
        title = { Text("Apagar memória?") },
        text = { Text("As transações ficam salvas. Novas importações voltarão a usar as regras e a IA para sugerir categorias.") },
        confirmButton = { TextButton(onClick = { showMemoryConfirmation = false; viewModel.clearCategoryMemory() }) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = { showMemoryConfirmation = false }) { Text("Cancelar") } }
    )
}

@Composable
internal fun ImportHistoryDialogs(viewModel: SettingsViewModel, showHistory: Boolean, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val imports by viewModel.imports.collectAsStateWithLifecycle()
    if (showHistory && state.pendingUndo == null) AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Importações") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (imports.isEmpty()) Text("Nenhuma importação salva.")
                imports.forEach { item ->
                    Column {
                        Text(item.fileName, style = MaterialTheme.typography.titleSmall)
                        Text("${DateFormatter.format(item.importedAt.toLocalDate())} · ${item.transactionCount} lançamentos",
                            style = MaterialTheme.typography.bodySmall)
                        item.sourceLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (item.canUndo) TextButton(onClick = { viewModel.requestUndo(item) }) { Text("Desfazer importação") }
                        else Text("Importação antiga: desfazer indisponível", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
    state.pendingUndo?.let { item ->
        AlertDialog(onDismissRequest = viewModel::cancelUndo,
            title = { Text("Desfazer importação?") },
            text = { Text("${item.fileName}: todos os lançamentos ainda vinculados serão removidos, inclusive os editados. Outros lotes e lançamentos manuais serão preservados. As correções memorizadas continuam disponíveis. Você poderá importar o arquivo novamente.") },
            confirmButton = { TextButton(onClick = viewModel::confirmUndo) { Text("Desfazer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelUndo) { Text("Cancelar") } })
    }
}
