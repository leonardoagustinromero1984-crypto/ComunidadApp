package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.PetTransfersViewModel

/** Acción confirmable en el detalle. */
private enum class DetailAction { ACCEPT, REJECT, CANCEL }

/**
 * LeoVer M08 Etapa 5 — detalle de una transferencia. Comparte el
 * [PetTransfersViewModel] de la mascota (misma fuente de estado); los estados
 * terminales se muestran sin acciones.
 */
@Composable
fun PetTransferDetailScreen(
    transferId: String,
    onNavigateBack: () -> Unit,
    viewModel: PetTransfersViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmAction by remember { mutableStateOf<DetailAction?>(null) }
    var cancelReason by remember { mutableStateOf("") }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = {
                Text(
                    when (action) {
                        DetailAction.ACCEPT -> "Aceptar transferencia"
                        DetailAction.REJECT -> "Rechazar transferencia"
                        DetailAction.CANCEL -> "Cancelar transferencia"
                    }
                )
            },
            text = {
                Column {
                    Text(
                        when (action) {
                            DetailAction.ACCEPT ->
                                "Vas a convertirte (vos o tu organización) en responsable " +
                                    "principal de la mascota. ¿Confirmás?"
                            DetailAction.REJECT ->
                                "¿Seguro que querés rechazar esta transferencia?"
                            DetailAction.CANCEL ->
                                "¿Seguro que querés cancelar esta transferencia pendiente?"
                        }
                    )
                    if (action == DetailAction.CANCEL) {
                        OutlinedTextField(
                            value = cancelReason,
                            onValueChange = { cancelReason = it },
                            label = { Text("Motivo (opcional)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAction = null
                        when (action) {
                            DetailAction.ACCEPT -> viewModel.accept(transferId)
                            DetailAction.REJECT -> viewModel.reject(transferId)
                            DetailAction.CANCEL -> {
                                viewModel.cancel(transferId, cancelReason)
                                cancelReason = ""
                            }
                        }
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmAction = null
                        cancelReason = ""
                    }
                ) { Text("Volver") }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Detalle de transferencia",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(
                contentModifier = Modifier.padding(padding),
                contentDescription = "Cargando transferencia"
            )
            state.loadErrorMessage != null -> ErrorState(
                message = state.loadErrorMessage.orEmpty(),
                contentModifier = Modifier.padding(padding),
                onRetry = viewModel::load
            )
            else -> {
                val transfer = state.transferById(transferId)
                if (transfer == null) {
                    EmptyState(
                        title = "Transferencia no encontrada",
                        contentModifier = Modifier.padding(padding),
                        message = "La transferencia no existe o no tenés acceso.",
                        actionLabel = "Actualizar",
                        onAction = viewModel::load
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .semantics { contentDescription = "Detalle de transferencia" }
                    ) {
                        val isPending = transfer.status == PetTransferStatus.PENDING
                        TransferCard(
                            transfer = transfer,
                            canAccept = isPending && state.canAccept && !state.isSubmitting,
                            canCancel = isPending && state.canCancel && !state.isSubmitting,
                            onAccept = { confirmAction = DetailAction.ACCEPT },
                            onReject = { confirmAction = DetailAction.REJECT },
                            onCancel = { confirmAction = DetailAction.CANCEL },
                            onOpenDetail = null
                        )
                        if (!isPending) {
                            Text(
                                text = "Esta transferencia está en un estado final y no admite cambios.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
