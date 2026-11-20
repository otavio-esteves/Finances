package br.com.otavioesteves.finances.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import br.com.otavioesteves.finances.domain.model.ThemeMode
import br.com.otavioesteves.finances.domain.model.ThemePalette
import br.com.otavioesteves.finances.ui.theme.paletteAccent
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.settings.SettingsViewModel
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAiModelClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val themePreferences = (context.applicationContext as MainApplication).themePreferences
    val themeMode by themePreferences.mode.collectAsStateWithLifecycle()
    val themePalette by themePreferences.palette.collectAsStateWithLifecycle()
    val allowScreenshots by themePreferences.allowScreenshots.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    var showPalettePicker by rememberSaveable { mutableStateOf(false) }
    var protectedDestination by remember { mutableStateOf<android.net.Uri?>(null) }
    var showUnprotectedWarning by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    BackHandler(uiState.isLoading) { viewModel.cancelBackupOperation() }
    val scrollState = rememberScrollState()
    val contentResolver = context.applicationContext.contentResolver

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Tema") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ThemeMode.entries.forEach { option ->
                        AppearanceOption(option.label, option == themeMode, onClick = {
                            themePreferences.setMode(option)
                            showThemePicker = false
                        })
                    }
                    Text("Aparelho acompanha o tema definido no sistema.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showThemePicker = false }) { Text("Fechar") } }
        )
    }
    if (showPalettePicker) {
        AlertDialog(
            onDismissRequest = { showPalettePicker = false },
            title = { Text("Paleta de cores") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ThemePalette.entries.forEach { option ->
                        AppearanceOption(option.label, option == themePalette, paletteAccent(option, MaterialTheme.colorScheme.background.luminance() < 0.5f)) {
                            themePreferences.setPalette(option)
                            showPalettePicker = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPalettePicker = false }) { Text("Fechar") } }
        )
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.createBackup {
                contentResolver.openOutputStream(it, "wt")
                    ?: throw IllegalStateException("Não foi possível abrir o destino do backup.")
            }
        }
    }

    val protectedBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> protectedDestination = uri }

    protectedDestination?.let { destination ->
        BackupPasswordDialog(onDismiss = { protectedDestination = null }) { secret ->
            protectedDestination = null
            viewModel.createBackup(secret) {
                contentResolver.openOutputStream(destination, "wt")
                    ?: throw IllegalStateException("Destino indisponível")
            }
        }
    }
    if (showUnprotectedWarning) {
        AlertDialog(onDismissRequest = { showUnprotectedWarning = false }, title = { Text("JSON sem proteção") },
            text = { Text("Este arquivo contém seus dados financeiros legíveis. O provedor escolhido poderá acessá-los. Prefira o backup protegido por senha.") },
            confirmButton = { TextButton(onClick = {
                showUnprotectedWarning = false; createBackupLauncher.launch("finances_backup.json")
            }) { Text("Gerar JSON sem proteção") } },
            dismissButton = { TextButton(onClick = { showUnprotectedWarning = false }) { Text("Cancelar") } })
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.onRestoreRequest {
                contentResolver.openInputStream(it)
                    ?: throw IllegalStateException("Não foi possível abrir o backup.")
            }
        }
    }

    val feedbackMessage = listOfNotNull(uiState.successMessage, uiState.error).joinToString("\n")
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(feedbackMessage)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.showRestoreConfirmation) { if (!uiState.showRestoreConfirmation) restorePassword = "" }
    if (uiState.showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::onRestoreCancel,
            title = { Text("Restaurar Backup") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("A restauração substitui os dados atuais após verificar o arquivo. Esta ação não pode ser desfeita. Guarde uma cópia dos dados atuais antes de continuar.")
                    PasswordField(restorePassword, { restorePassword = it }, "Senha do backup (JSON legado: deixe vazio)")
                    Text("A senha não pode ser recuperada pelo Finances.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val secret = restorePassword.takeIf { it.isNotEmpty() }?.toCharArray()
                    restorePassword = ""; viewModel.onRestoreConfirm(secret)
                }) {
                    Text("Confirmar Restauração", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onRestoreCancel) {
                    Text("Cancelar")
                }
            }
        )
    }

    ImportHistoryDialogs(viewModel, showHistory) { showHistory = false }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configurações") },
                colors = financeTopAppBarColors(scrollState.value == 0),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text(uiState.operationLabel, modifier = Modifier.padding(16.dp))
                if (uiState.canCancelBackup) TextButton(onClick = viewModel::cancelBackupOperation) { Text("Cancelar") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = 20.dp, end = 20.dp, top = paddingValues.calculateTopPadding() + 20.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                NotificationSettingsCard()
                ImportManagementCard(viewModel) { showHistory = true }
                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "APARÊNCIA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp)
                        )
                        SettingsAction(
                            title = "Tema",
                            subtitle = themeMode.label,
                            icon = Icons.Filled.LightMode,
                            onClick = { showThemePicker = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp, end = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                        SettingsAction(
                            title = "Paleta de cores",
                            subtitle = themePalette.label,
                            icon = Icons.Filled.Palette,
                            onClick = { showPalettePicker = true }
                        )
                    }
                }
                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                    Text("PRIVACIDADE", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .toggleable(value = allowScreenshots, role = Role.Switch,
                                onValueChange = themePreferences::setAllowScreenshots)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Permitir capturas de tela", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (allowScreenshots) "Capturas e gravação de tela permitidas" else "Seus dados ficam protegidos nas capturas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = allowScreenshots, onCheckedChange = null)
                    }
                    }
                }
                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "SEUS DADOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp)
                        )
                        SettingsAction(
                            title = "Backup protegido por senha",
                            subtitle = "Cópia criptografada para restaurar em outro aparelho",
                            icon = Icons.Filled.FileUpload,
                            onClick = { protectedBackupLauncher.launch("finances_backup.fnb") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp, end = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                        SettingsAction(
                            title = "Avançado: JSON sem proteção",
                            subtitle = "Arquivo legível, sem senha",
                            icon = Icons.Filled.FileUpload,
                            onClick = { showUnprotectedWarning = true }
                        )
                        SettingsAction(
                            title = "Restaurar backup",
                            subtitle = "Recupere uma cópia anterior",
                            icon = Icons.Filled.FileDownload,
                            onClick = { restoreBackupLauncher.launch("*/*") }
                        )
                    }
                }

                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "INTELIGÊNCIA LOCAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp)
                        )
                        SettingsAction(
                            title = "Modelo de IA",
                            subtitle = "Gerencie a IA no aparelho",
                            icon = Icons.Filled.Memory,
                            onClick = onAiModelClick
                        )
                    }
                }

                Text(
                    text = "O arquivo de backup contém dados financeiros e conversas sem criptografia adicional. Guarde-o em um local seguro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(FinancesThemeTokens.colors.heroSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun AppearanceOption(
    label: String,
    selected: Boolean,
    swatch: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        if (swatch != null) Box(Modifier.size(24.dp).clip(CircleShape).background(swatch))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Password fields are deliberately not rememberSaveable or stored in a ViewModel. */
@Composable
private fun BackupPasswordDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Proteger backup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escolha uma senha longa e guarde-a em local seguro. Sem ela, não será possível recuperar este backup.")
                PasswordField(password, { password = it }, "Senha (mínimo 12 caracteres)")
                PasswordField(confirmation, { confirmation = it }, "Repita a senha")
                if (confirmation.isNotEmpty() && password != confirmation) Text("As senhas precisam ser iguais.")
            }
        },
        confirmButton = { TextButton(enabled = password.length in 12..1024 && password == confirmation,
            onClick = { val secret = password.toCharArray(); password = ""; confirmation = ""; onConfirm(secret) }) { Text("Gerar backup protegido") } },
        dismissButton = { TextButton(onClick = { password = ""; confirmation = ""; onDismiss() }) { Text("Cancelar") } })
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 1024) onChange(it) }, label = { Text(label) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
}
