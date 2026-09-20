package br.com.otavioesteves.finances.ui.screens.aimodel

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.UnsupportedReason
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.aimodel.AiModelViewModel
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
import br.com.otavioesteves.finances.utils.queryDisplayName

/**
 * Página oficial de download do bundle padrão (Gemma 4 E2B `.litertlm`).
 * Vazio de propósito: preencher com a URL real antes do lançamento (ver
 * docs/PLANO_MOTOR_IA_LOCAL.md, seção 2.3). Enquanto vazia, o botão de
 * atalho para a página fica oculto — importar continua funcionando via SAF.
 */
private const val MODEL_DOWNLOAD_PAGE_URL = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiModelViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val name = queryDisplayName(context, selectedUri) ?: "Modelo importado"
            viewModel.onModelSelected(name) {
                context.contentResolver.openInputStream(selectedUri)
                    ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado")
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modelo de IA") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val engineState = uiState.engineState) {
                is AiEngineState.Unsupported -> UnsupportedContent(engineState.reason)

                is AiEngineState.NotProvisioned -> NotProvisionedContent(
                    isImporting = uiState.isImporting,
                    onDownloadPageClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MODEL_DOWNLOAD_PAGE_URL)))
                    },
                    onSelectFileClick = { filePickerLauncher.launch("*/*") }
                )

                is AiEngineState.Ready -> ReadyContent(
                    displayName = engineState.model.displayName,
                    verified = engineState.model.verified,
                    onRemoveClick = viewModel::onRemoveModel
                )

                is AiEngineState.Failed -> FailedContent(
                    onRetryClick = { filePickerLauncher.launch("*/*") }
                )

                is AiEngineState.Provisioning -> LoadingContent()
            }
        }
    }
}

@Composable
private fun UnsupportedContent(reason: UnsupportedReason, modifier: Modifier = Modifier) {
    val message = when (reason) {
        UnsupportedReason.INSUFFICIENT_RAM ->
            "Este aparelho não tem memória suficiente para rodar o modelo de IA local. " +
                "A categorização automática continuará funcionando por regras."
        UnsupportedReason.UNSUPPORTED_ABI ->
            "Este aparelho não tem suporte ao motor de IA local. " +
                "A categorização automática continuará funcionando por regras."
        UnsupportedReason.NO_STORAGE ->
            "Não há espaço de armazenamento suficiente para instalar o modelo de IA local. " +
                "Libere espaço e reabra esta tela, ou continue usando a categorização por regras."
    }

    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun NotProvisionedContent(
    isImporting: Boolean,
    onDownloadPageClick: () -> Unit,
    onSelectFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Nenhum modelo de IA instalado. Enquanto isso, a categorização de transações " +
                "usa regras simples por palavra-chave.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isImporting) {
            LoadingContent()
        } else {
            if (MODEL_DOWNLOAD_PAGE_URL.isNotBlank()) {
                PrimaryActionButton(
                    text = "Baixar página do modelo",
                    onClick = onDownloadPageClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            PrimaryActionButton(
                text = "Selecionar arquivo do modelo (.litertlm)",
                onClick = onSelectFileClick
            )
        }
    }
}

@Composable
private fun ReadyContent(
    displayName: String,
    verified: Boolean,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (verified) {
                        "Modelo verificado."
                    } else {
                        "Modelo não verificado — não foi testado pela equipe do app. " +
                            "A qualidade da categorização não é garantida."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (verified) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }

        PrimaryActionButton(
            text = "Remover modelo",
            onClick = onRemoveClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun FailedContent(onRetryClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Não foi possível carregar o modelo importado. O arquivo pode estar corrompido " +
                "ou não ser um bundle válido.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        PrimaryActionButton(
            text = "Selecionar outro arquivo",
            onClick = onRetryClick
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = "Importando modelo…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
