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
import androidx.compose.material3.FilterChip
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
import com.comunidapp.app.domain.pets.PetAuthorization
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.PetAuthorizationDisplayStatus
import com.comunidapp.app.viewmodel.PetAuthorizationsViewModel

/**
 * LeoVer M08 Etapa 5 — autorizaciones delegadas: persona destinataria,
 * capacidades permitidas (allowlist) y vigencia. Sin transferencia, fallecimiento,
 * archivo ni gestión de responsables implícitos.
 */
@Composable
fun PetAuthorizationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PetAuthorizationsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var revokeTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    revokeTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { revokeTargetId = null },
            title = { Text("Revocar autorización") },
            text = { Text("¿Seguro que querés revocar esta autorización?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        revokeTargetId = null
                        viewModel.revoke(targetId)
                    }
                ) {
                    Text("Revocar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTargetId = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Autorizaciones",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(
                contentModifier = Modifier.padding(padding),
                contentDescription = "Cargando autorizaciones"
            )
            state.loadErrorMessage != null -> ErrorState(
                message = state.loadErrorMessage.orEmpty(),
                contentModifier = Modifier.padding(padding),
                onRetry = viewModel::load
            )
            state.isEmpty && !state.canManage -> EmptyState(
                title = "Sin autorizaciones",
                contentModifier = Modifier.padding(padding),
                message = "Todavía no hay personas autorizadas para esta mascota.",
                actionLabel = "Actualizar",
                onAction = viewModel::load
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .semantics { contentDescription = "Autorizaciones de la mascota" }
            ) {
                if (state.mutationsLocked) {
                    Text(
                        text = "La mascota no está activa: la gestión de autorizaciones está deshabilitada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = "Autorizaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.authorizations.isEmpty()) {
                    Text(
                        text = "Sin autorizaciones registradas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                state.authorizations.forEach { authorization ->
                    AuthorizationCard(
                        authorization = authorization,
                        displayStatus = viewModel.displayStatusOf(authorization),
                        canRevoke = state.canManage &&
                            !state.mutationsLocked &&
                            !state.isSubmitting &&
                            viewModel.displayStatusOf(authorization) ==
                            PetAuthorizationDisplayStatus.ACTIVE,
                        onRevoke = { revokeTargetId = authorization.id.value }
                    )
                }

                if (state.canManage && !state.mutationsLocked) {
                    Spacer(modifier = Modifier.height(24.dp))
                    GrantAuthorizationSection(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun GrantAuthorizationSection(viewModel: PetAuthorizationsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var selectedPersonLabel by remember { mutableStateOf<String?>(null) }
    var selectedCapabilities by remember { mutableStateOf<Set<PetCapability>>(emptySet()) }
    var validUntilText by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Otorgar autorización") },
            text = {
                Text(
                    "¿Confirmás autorizar a ${selectedPersonLabel.orEmpty()} con: " +
                        selectedCapabilities.joinToString {
                            PetAuthorizationsViewModel.capabilityLabel(it)
                        } + "?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        viewModel.grant(
                            personId = selectedPersonId.orEmpty(),
                            capabilities = selectedCapabilities,
                            validUntilEpochMs = validUntilText.trim()
                                .takeIf { it.isNotBlank() }
                                ?.let { parseDateToEpochMs(it) }
                        )
                        selectedPersonId = null
                        selectedPersonLabel = null
                        selectedCapabilities = emptySet()
                        validUntilText = ""
                    }
                ) { Text("Confirmar") }
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
                text = "Nueva autorización",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Buscar persona") },
                supportingText = { Text("Búsqueda controlada de perfiles públicos") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("pet_auth_person_search"),
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
                        text = "Persona seleccionada: $label",
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

            Text(
                text = "Capacidades a otorgar",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "No incluyen transferir, marcar fallecimiento, archivar ni gestionar responsables.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PetAuthorizationsViewModel.GRANTABLE_CAPABILITIES.forEach { capability ->
                FilterChip(
                    selected = capability in selectedCapabilities,
                    onClick = {
                        selectedCapabilities = if (capability in selectedCapabilities) {
                            selectedCapabilities - capability
                        } else {
                            selectedCapabilities + capability
                        }
                    },
                    label = { Text(PetAuthorizationsViewModel.capabilityLabel(capability)) },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            OutlinedTextField(
                value = validUntilText,
                onValueChange = {
                    validUntilText = it
                    dateError = false
                },
                label = { Text("Válida hasta (AAAA-MM-DD, opcional)") },
                isError = dateError,
                supportingText = {
                    if (dateError) Text("Fecha inválida. Usá el formato AAAA-MM-DD.")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    if (validUntilText.isNotBlank() && parseDateToEpochMs(validUntilText) == null) {
                        dateError = true
                        return@Button
                    }
                    showConfirm = true
                },
                enabled = !state.isSubmitting &&
                    selectedPersonId != null &&
                    selectedCapabilities.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Otorgar autorización")
            }
        }
    }
}

@Composable
private fun AuthorizationCard(
    authorization: PetAuthorization,
    displayStatus: PetAuthorizationDisplayStatus,
    canRevoke: Boolean,
    onRevoke: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Persona: ${authorization.granteeUserId}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = authorization.capabilities.joinToString {
                    PetAuthorizationsViewModel.capabilityLabel(it)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = buildString {
                    append(authorizationStatusLabel(displayStatus))
                    append(" · Desde: ${formatEpochDate(authorization.validFromEpochMs)}")
                    authorization.validToEpochMs?.let {
                        append(" · Hasta: ${formatEpochDate(it)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canRevoke) {
                OutlinedButton(
                    onClick = onRevoke,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Revocar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun authorizationStatusLabel(status: PetAuthorizationDisplayStatus): String =
    when (status) {
        PetAuthorizationDisplayStatus.ACTIVE -> "Activa"
        PetAuthorizationDisplayStatus.EXPIRED -> "Vencida"
        PetAuthorizationDisplayStatus.REVOKED -> "Revocada"
        PetAuthorizationDisplayStatus.INACTIVE -> "Inactiva"
    }
