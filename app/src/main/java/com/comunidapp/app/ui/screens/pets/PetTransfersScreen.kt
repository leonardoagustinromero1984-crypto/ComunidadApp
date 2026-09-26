package com.comunidapp.app.ui.screens.pets

import com.comunidapp.app.ui.theme.BrandBackground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.pets.PetCareTransferCopy
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.PetTransfersUiState
import com.comunidapp.app.viewmodel.PetTransfersViewModel

/** Acción confirmable sobre una transferencia. */
private enum class TransferAction { ACCEPT, REJECT, CANCEL }

/**
 * Operational care-transfer surface: PENDING only plus initiate when custodian.
 * Completed ACCEPTED/REJECTED/CANCELLED rows belong in VitaCora history.
 */
@Composable
fun PetTransfersScreen(
    onNavigateBack: () -> Unit,
    onOpenTransferDetail: (String) -> Unit = {},
    onAccepted: () -> Unit = {},
    viewModel: PetTransfersViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmAction by remember { mutableStateOf<Pair<TransferAction, String>?>(null) }
    var cancelReason by remember { mutableStateOf("") }

    LaunchedEffect(state.acceptedNavigateToMyPets) {
        if (state.acceptedNavigateToMyPets) {
            onAccepted()
        }
    }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }
    LaunchedEffect(state.isLoading, state.accessResolved, state.access, state.pendingTransfer, state.loadErrorMessage) {
        if (state.shouldLeaveUnauthorized()) {
            onNavigateBack()
        }
    }

    confirmAction?.let { (action, transferId) ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = {
                Text(
                    when (action) {
                        TransferAction.ACCEPT -> PetCareTransferCopy.acceptConfirmTitle(
                            state.petName.ifBlank { state.pendingTransfer?.petDisplayName.orEmpty() }
                        )
                        TransferAction.REJECT -> "Rechazar transferencia"
                        TransferAction.CANCEL -> "Cancelar transferencia"
                    }
                )
            },
            text = {
                Column {
                    Text(
                        when (action) {
                            TransferAction.ACCEPT -> {
                                val pet = state.petName.ifBlank {
                                    state.pendingTransfer?.petDisplayName.orEmpty()
                                }
                                PetCareTransferCopy.acceptConfirmBody(pet)
                            }
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = if (state.canInitiate) {
                    PetCareTransferCopy.SCREEN_TITLE
                } else {
                    PetCareTransferCopy.RECEIVER_TITLE
                },
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
                title = "No pudimos cargar las transferencias",
                message = state.loadErrorMessage.orEmpty(),
                contentModifier = Modifier.padding(padding),
                onRetry = viewModel::load
            )
            state.isEmpty && !state.canInitiate && state.canAccept -> EmptyState(
                title = PetCareTransferCopy.RECEIVER_TITLE,
                contentModifier = Modifier.padding(padding),
                message = "No hay una solicitud pendiente.",
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
                when (val pending = state.pendingTransfer) {
                    null -> Unit
                    else -> if (state.canAccept) {
                        IncomingCareTransferCard(
                            transfer = pending,
                            enabled = !state.isSubmitting,
                            onAccept = { viewModel.accept(pending.id.value) },
                            onReject = { viewModel.reject(pending.id.value) }
                        )
                    } else {
                        Text(
                            text = "Transferencia pendiente",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = PetCareTransferCopy.outgoingWaiting(
                                pending.targetDisplayName.orEmpty()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = PetCareTransferCopy.outgoingStillYours(
                                state.petName.ifBlank { pending.petDisplayName.orEmpty() }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (state.canCancel && !state.isSubmitting) {
                            LeoOutlinedButton(
                                text = PetCareTransferCopy.CANCEL_REQUEST,
                                onClick = { confirmAction = TransferAction.CANCEL to pending.id.value },
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
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
            ?: organizationId.trim().takeIf { it.isNotBlank() }
            ?: ""
        val isOrg = selectedPersonId == null && organizationId.isNotBlank()
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(PetCareTransferCopy.SCREEN_TITLE) },
            text = {
                Text(
                    PetCareTransferCopy.confirmInitiate(
                        targetName = destinationLabel,
                        petName = state.petName,
                        targetIsOrganization = isOrg
                    )
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

    Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = PetCareTransferCopy.SCREEN_TITLE,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = PetCareTransferCopy.describeTransfer(state.petName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = PetCareTransferCopy.SHARED_ACCESS_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Buscar persona u organización") },
                supportingText = { Text("Búsqueda de perfiles y organizaciones") },
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
                        organizationId = ""
                        viewModel.updateSearchQuery("")
                    }
                ) {
                    Text("${profile.displayName}${profile.username?.let { " (@$it)" }.orEmpty()}")
                }
            }
            state.targetHits.filter { it.kind.equals("ORGANIZATION", ignoreCase = true) }.forEach { hit ->
                TextButton(
                    onClick = {
                        selectedPersonId = null
                        selectedPersonLabel = hit.displayName
                        organizationId = hit.id
                        viewModel.updateSearchQuery("")
                    }
                ) {
                    Text(hit.displayName)
                }
            }
            selectedPersonLabel?.let { label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Destino seleccionado: $label",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            selectedPersonId = null
                            selectedPersonLabel = null
                            organizationId = ""
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) { Text("Quitar", maxLines = 1) }
                }
            }

            Text(
                text = PetCareTransferCopy.MEDIA_SECTION_TITLE,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = PetCareTransferCopy.MEDIA_SECTION_BODY,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.sharePersonalMedia,
                    onCheckedChange = viewModel::setSharePersonalMedia
                )
                Text(
                    text = PetCareTransferCopy.SHARE_PERSONAL_MEDIA_LABEL,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = PetCareTransferCopy.HISTORY_ALWAYS_TRAVELS,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            LeoPrimaryButton(
                text = PetCareTransferCopy.CONTINUE,
                onClick = { showConfirm = true },
                enabled = !state.isSubmitting &&
                    (selectedPersonId != null || organizationId.isNotBlank()),
                modifier = Modifier.padding(top = 12.dp)
            )
    }
}

@Composable
internal fun TransferCard(
    transfer: PetTransfer,
    canAccept: Boolean,
    canCancel: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            text = transferStatusLabel(transfer.status),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "De: ${transferActorLabel(transfer.fromPrincipal, transfer.sourceDisplayName)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Para: ${transferActorLabel(transfer.toPrincipal, transfer.targetDisplayName)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Solicitada: ${formatEpochDateTime(transfer.requestedAtEpochMs) ?: formatEpochDate(transfer.requestedAtEpochMs)} · " +
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
                    LeoPrimaryButton(
                        text = "Aceptar",
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    )
                    LeoOutlinedButton(
                        text = "Rechazar",
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (canCancel) {
                    LeoOutlinedButton(
                        text = "Cancelar",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        LeoHairline(modifier = Modifier.padding(top = 8.dp))
    }
}

internal fun transferActorLabel(holder: PetPrincipalHolder, displayName: String?): String {
    val name = displayName?.trim().orEmpty()
    if (name.isNotEmpty()) return name
    return holderLabel(holder)
}

private fun incomingAcceptCopy(state: PetTransfersUiState): String {
    val pending = state.pendingTransfer
    val source = pending?.sourceDisplayName?.trim().orEmpty()
    val pet = state.petName.ifBlank { pending?.petDisplayName.orEmpty() }
    val request = PetCareTransferCopy.incomingRequest(source, pet)
    val accept = when (pending?.toPrincipal) {
        is PetPrincipalHolder.Organization -> PetCareTransferCopy.incomingAcceptOrganization(
            pending.targetDisplayName.orEmpty(),
            pet
        )
        else -> PetCareTransferCopy.incomingAcceptPerson(pet)
    }
    return "$request $accept"
}

internal fun transferStatusLabel(status: PetTransferStatus): String = when (status) {
    PetTransferStatus.PENDING -> "Pendiente"
    PetTransferStatus.ACCEPTED -> "Aceptada"
    PetTransferStatus.REJECTED -> "Rechazada"
    PetTransferStatus.CANCELLED -> "Cancelada"
    PetTransferStatus.EXPIRED -> "Vencida"
}
