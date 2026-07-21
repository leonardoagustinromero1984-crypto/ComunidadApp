package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.PetTransfersViewModel

/** Acción confirmable sobre una transferencia. */
private enum class TransferAction { ACCEPT, REJECT, CANCEL }

/**
 * LeoVer M08 Etapa 5 — transferencias del responsable principal: pendiente,
 * historial y flujo iniciar/aceptar/rechazar/cancelar por capacidades backend.
 */
@Composable
fun PetTransfersScreen(
    onNavigateBack: () -> Unit,
    onOpenTransferDetail: (String) -> Unit = {},
    viewModel: PetTransfersViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmAction by remember { mutableStateOf<Pair<TransferAction, String>?>(null) }
    var cancelReason by remember { mutableStateOf("") }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    confirmAction?.let { (action, transferId) ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = {
                Text(
                    when (action) {
                        TransferAction.ACCEPT -> "Aceptar transferencia"
                        TransferAction.REJECT -> "Rechazar transferencia"
                        TransferAction.CANCEL -> "Cancelar transferencia"
                    }
                )
            },
            text = {
                Column {
                    Text(
                        when (action) {
                            TransferAction.ACCEPT ->
                                "Vas a convertirte (vos o tu organización) en responsable " +
                                    "principal de la mascota. ¿Confirmás?"
                            TransferAction.REJECT ->
                                "¿Seguro que querés rechazar esta transferencia?"
                            TransferAction.CANCEL ->
                                "¿Seguro que querés cancelar esta transferencia pendiente?"
                        }
                    )
                    if (action == TransferAction.CANCEL) {
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
                            TransferAction.ACCEPT -> viewModel.accept(transferId)
                            TransferAction.REJECT -> viewModel.reject(transferId)
                            TransferAction.CANCEL -> {
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
                title = "Transferencias",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(
                contentModifier = Modifier.padding(padding),
                contentDescription = "Cargando transferencias"
            )
            state.loadErrorMessage != null -> ErrorState(
                message = state.loadErrorMessage.orEmpty(),
                contentModifier = Modifier.padding(padding),
                onRetry = viewModel::load
            )
            state.isEmpty && !state.canInitiate -> EmptyState(
                title = "Sin transferencias",
                contentModifier = Modifier.padding(padding),
                message = "No hay transferencias registradas para esta mascota.",
                actionLabel = "Actualizar",
                onAction = viewModel::load
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .semantics { contentDescription = "Transferencias de la mascota" }
            ) {
                Text(
                    text = "Transferencia pendiente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                when (val pending = state.pendingTransfer) {
                    null -> Text(
                        text = "No hay transferencia pendiente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    else -> TransferCard(
                        transfer = pending,
                        canAccept = state.canAccept && !state.isSubmitting,
                        canCancel = state.canCancel && !state.isSubmitting,
                        onAccept = { confirmAction = TransferAction.ACCEPT to pending.id.value },
                        onReject = { confirmAction = TransferAction.REJECT to pending.id.value },
                        onCancel = { confirmAction = TransferAction.CANCEL to pending.id.value },
                        onOpenDetail = { onOpenTransferDetail(pending.id.value) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Historial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.history.isEmpty()) {
                    Text(
                        text = "Sin transferencias anteriores.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                state.history.forEach { transfer ->
                    // Terminal states: visible, never editable.
                    TransferCard(
                        transfer = transfer,
                        canAccept = false,
                        canCancel = false,
                        onAccept = {},
                        onReject = {},
                        onCancel = {},
                        onOpenDetail = { onOpenTransferDetail(transfer.id.value) }
                    )
                }

                if (state.canInitiate && !state.mutationsLocked && state.pendingTransfer == null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    InitiateTransferSection(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun InitiateTransferSection(viewModel: PetTransfersViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var selectedPersonLabel by remember { mutableStateOf<String?>(null) }
    var organizationId by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        val destinationLabel = selectedPersonLabel
            ?: organizationId.trim().takeIf { it.isNotBlank() }?.let { "Organización $it" }
            ?: ""
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Iniciar transferencia") },
            text = {
                Text(
                    "Vas a transferir la responsabilidad principal a $destinationLabel. " +
                        "Si acepta, dejás de ser responsable principal. ¿Confirmás?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        viewModel.initiate(
                            toPersonId = selectedPersonId,
                            toOrganizationId = if (selectedPersonId == null) {
                                organizationId.trim().takeIf { it.isNotBlank() }
                            } else {
                                null
                            }
                        )
                        selectedPersonId = null
                        selectedPersonLabel = null
                        organizationId = ""
                    }
                ) { Text("Transferir") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Iniciar transferencia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Transferís la responsabilidad principal a otra persona u organización. " +
                    "Requiere aceptación del destino.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Buscar persona destino") },
                supportingText = { Text("Búsqueda controlada de perfiles públicos") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("pet_transfer_person_search"),
                singleLine = true
            )
            if (state.isSearching) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            state.searchResults.forEach { profile ->
                TextButton(
                    onClick = {
                        selectedPersonId = profile.id
                        selectedPersonLabel = profile.displayName
                        viewModel.updateSearchQuery("")
                    }
                ) {
                    Text("${profile.displayName}${profile.username?.let { " (@$it)" }.orEmpty()}")
                }
            }
            selectedPersonLabel?.let { label ->
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Destino seleccionado: $label",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = {
                            selectedPersonId = null
                            selectedPersonLabel = null
                        }
                    ) { Text("Quitar") }
                }
            }

            OutlinedTextField(
                value = organizationId,
                onValueChange = { organizationId = it },
                label = { Text("O ID de organización destino (opcional)") },
                enabled = selectedPersonId == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true
            )

            Button(
                onClick = { showConfirm = true },
                enabled = !state.isSubmitting &&
                    (selectedPersonId != null || organizationId.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Iniciar transferencia")
            }
        }
    }
}

@Composable
internal fun TransferCard(
    transfer: PetTransfer,
    canAccept: Boolean,
    canCancel: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onOpenDetail: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = transferStatusLabel(transfer.status),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "De: ${holderLabel(transfer.fromPrincipal)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Para: ${holderLabel(transfer.toPrincipal)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Solicitada: ${formatEpochDate(transfer.requestedAtEpochMs)} · " +
                    "Vence: ${formatEpochDate(transfer.expiresAtEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (transfer.status == PetTransferStatus.PENDING && (canAccept || canCancel)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canAccept) {
                        Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                            Text("Aceptar")
                        }
                        OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                            Text("Rechazar")
                        }
                    }
                    if (canCancel) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            onOpenDetail?.let {
                TextButton(onClick = it, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Ver detalle")
                }
            }
        }
    }
}

internal fun transferStatusLabel(status: PetTransferStatus): String = when (status) {
    PetTransferStatus.PENDING -> "Pendiente"
    PetTransferStatus.ACCEPTED -> "Aceptada"
    PetTransferStatus.REJECTED -> "Rechazada"
    PetTransferStatus.CANCELLED -> "Cancelada"
    PetTransferStatus.EXPIRED -> "Vencida"
}
