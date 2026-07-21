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
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.PetResponsibilitiesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * LeoVer M08 Etapa 5 — responsables de la mascota: principal (persona u
 * organización), co-responsables y custodias temporales.
 */
@Composable
fun PetResponsibilitiesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PetResponsibilitiesViewModel = viewModel()
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
            title = { Text("Revocar vínculo") },
            text = { Text("¿Seguro que querés revocar este vínculo de responsabilidad?") },
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
                title = "Responsables",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(
                contentModifier = Modifier.padding(padding),
                contentDescription = "Cargando responsables"
            )
            state.loadErrorMessage != null -> ErrorState(
                message = state.loadErrorMessage.orEmpty(),
                contentModifier = Modifier.padding(padding),
                onRetry = viewModel::load
            )
            state.isEmpty && !state.canManage -> EmptyState(
                title = "Sin responsables",
                contentModifier = Modifier.padding(padding),
                message = "Todavía no hay responsables registrados para esta mascota.",
                actionLabel = "Actualizar",
                onAction = viewModel::load
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .semantics { contentDescription = "Responsables de la mascota" }
            ) {
                if (state.mutationsLocked) {
                    Text(
                        text = "La mascota no está activa: la gestión de responsables está deshabilitada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = "Responsable principal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                when (val principal = state.principal) {
                    null -> Text(
                        text = "Sin responsable principal activo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    else -> ResponsibilityCard(
                        responsibility = principal,
                        canRevoke = false,
                        onRevoke = {}
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Co-responsables",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.coResponsibles.isEmpty()) {
                    Text(
                        text = "Sin co-responsables activos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                state.coResponsibles.forEach { item ->
                    ResponsibilityCard(
                        responsibility = item,
                        canRevoke = state.canManage && !state.mutationsLocked && !state.isSubmitting,
                        onRevoke = { revokeTargetId = item.id.value }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Custodias temporales",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.custodians.isEmpty()) {
                    Text(
                        text = "Sin custodias temporales activas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                state.custodians.forEach { item ->
                    ResponsibilityCard(
                        responsibility = item,
                        canRevoke = state.canManage && !state.mutationsLocked && !state.isSubmitting,
                        onRevoke = { revokeTargetId = item.id.value }
                    )
                }

                if (state.inactiveLinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Historial de vínculos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.inactiveLinks.forEach { item ->
                        ResponsibilityCard(
                            responsibility = item,
                            canRevoke = false,
                            onRevoke = {}
                        )
                    }
                }

                if (state.canManage && !state.mutationsLocked) {
                    Spacer(modifier = Modifier.height(24.dp))
                    AddResponsibilitySection(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun AddResponsibilitySection(viewModel: PetResponsibilitiesViewModel) {
    val state by viewModel.uiState.collectAsState()
    var roleIsCustody by remember { mutableStateOf(false) }
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var selectedPersonLabel by remember { mutableStateOf<String?>(null) }
    var organizationId by remember { mutableStateOf("") }
    var endsAtText by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        val destinationLabel = selectedPersonLabel
            ?: organizationId.trim().takeIf { it.isNotBlank() }?.let { "Organización $it" }
            ?: ""
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (roleIsCustody) "Asignar custodia temporal" else "Agregar co-responsable") },
            text = { Text("¿Confirmás el vínculo para $destinationLabel?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        val orgId = organizationId.trim().takeIf { it.isNotBlank() }
                        if (roleIsCustody) {
                            viewModel.addTemporaryCustodian(
                                personId = selectedPersonId,
                                organizationId = if (selectedPersonId == null) orgId else null,
                                endsAtEpochMs = parseDateToEpochMs(endsAtText)
                            )
                        } else {
                            viewModel.addCoResponsible(
                                personId = selectedPersonId,
                                organizationId = if (selectedPersonId == null) orgId else null
                            )
                        }
                        selectedPersonId = null
                        selectedPersonLabel = null
                        organizationId = ""
                        endsAtText = ""
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
                text = "Agregar vínculo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !roleIsCustody,
                    onClick = { roleIsCustody = false },
                    label = { Text("Co-responsable") }
                )
                FilterChip(
                    selected = roleIsCustody,
                    onClick = { roleIsCustody = true },
                    label = { Text("Custodia temporal") }
                )
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Buscar persona") },
                supportingText = { Text("Búsqueda controlada de perfiles públicos") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("pet_resp_person_search"),
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

            OutlinedTextField(
                value = organizationId,
                onValueChange = { organizationId = it },
                label = { Text("O ID de organización (opcional)") },
                enabled = selectedPersonId == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true
            )

            if (roleIsCustody) {
                OutlinedTextField(
                    value = endsAtText,
                    onValueChange = {
                        endsAtText = it
                        dateError = false
                    },
                    label = { Text("Fin de custodia (AAAA-MM-DD)") },
                    isError = dateError,
                    supportingText = {
                        if (dateError) Text("Fecha inválida. Usá el formato AAAA-MM-DD.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    if (roleIsCustody && parseDateToEpochMs(endsAtText) == null) {
                        dateError = true
                        return@Button
                    }
                    showConfirm = true
                },
                enabled = !state.isSubmitting &&
                    (selectedPersonId != null || organizationId.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(if (roleIsCustody) "Asignar custodia" else "Agregar co-responsable")
            }
        }
    }
}

@Composable
private fun ResponsibilityCard(
    responsibility: PetResponsibility,
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
                text = holderLabel(responsibility.holder),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${roleLabel(responsibility.role)} · ${linkStatusLabel(responsibility.status)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = buildString {
                    append("Desde: ${formatEpochDate(responsibility.validFromEpochMs)}")
                    responsibility.validToEpochMs?.let {
                        append(" · Hasta: ${formatEpochDate(it)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canRevoke && responsibility.role != PetResponsibilityRole.PRINCIPAL) {
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

internal fun holderLabel(holder: PetPrincipalHolder): String = when (holder) {
    is PetPrincipalHolder.Person -> "Persona: ${holder.userId}"
    is PetPrincipalHolder.Organization -> "Organización: ${holder.organizationId.value}"
}

private fun roleLabel(role: PetResponsibilityRole): String = when (role) {
    PetResponsibilityRole.PRINCIPAL -> "Principal"
    PetResponsibilityRole.CO_RESPONSIBLE -> "Co-responsable"
    PetResponsibilityRole.TEMPORARY_CUSTODIAN -> "Custodia temporal"
}

internal fun linkStatusLabel(status: PetLinkStatus): String = when (status) {
    PetLinkStatus.ACTIVE -> "Activo"
    PetLinkStatus.PENDING_ACCEPTANCE -> "Pendiente de aceptación"
    PetLinkStatus.REVOKED -> "Revocado"
    PetLinkStatus.EXPIRED -> "Vencido"
    PetLinkStatus.SUPERSEDED -> "Reemplazado"
}

internal fun formatEpochDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

internal fun parseDateToEpochMs(raw: String): Long? =
    runCatching {
        LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
