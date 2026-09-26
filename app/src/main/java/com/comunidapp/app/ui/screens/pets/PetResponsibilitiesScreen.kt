package com.comunidapp.app.ui.screens.pets

import com.comunidapp.app.ui.theme.BrandBackground

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
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
            title = { Text("Quitar responsable") },
            text = { Text("¿Quitar a esta persona como responsable? La mascota y su VitaCora no se eliminan.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        revokeTargetId = null
                        viewModel.revoke(targetId)
                    }
                ) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTargetId = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                Text(
                    text = state.petName.takeIf { it.isNotBlank() }?.let {
                        "Personas responsables de $it. Todas ven la misma mascota y la misma VitaCora."
                    } ?: "Personas responsables de esta mascota. Todas ven la misma mascota y la misma VitaCora.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (state.mutationsLocked) {
                    Text(
                        text = "La mascota no está activa: no se puede modificar responsables.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = "Responsable original",
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
                        displayName = state.displayNames[holderKey(principal.holder)],
                        username = state.displayUsernames[holderKey(principal.holder)],
                        canRevoke = false,
                        onRevoke = {}
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Otros responsables",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.coResponsibles.isEmpty() && state.custodians.isEmpty() &&
                    state.pendingInvites.isEmpty()
                ) {
                    Text(
                        text = "Todavía no invitaste a otra persona responsable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                (state.pendingInvites + state.coResponsibles + state.custodians).forEach { item ->
                    ResponsibilityCard(
                        responsibility = item,
                        displayName = state.displayNames[holderKey(item.holder)],
                        username = state.displayUsernames[holderKey(item.holder)],
                        canRevoke = state.canManage && !state.mutationsLocked && !state.isSubmitting,
                        onRevoke = { revokeTargetId = item.id.value }
                    )
                }

                if (state.canLeave) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LeoOutlinedButton(
                        text = "Dejar de ser responsable / Salir de esta mascota",
                        onClick = viewModel::leaveThisPet,
                        enabled = !state.isSubmitting
                    )
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
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var selectedPersonLabel by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Enviar invitación") },
            text = {
                Text(
                    "¿Invitar a ${selectedPersonLabel.orEmpty()} como responsable de esta mascota? " +
                        "Al aceptar verá la misma mascota y la misma VitaCora en su perfil personal."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        val personId = selectedPersonId
                        if (personId != null) {
                            viewModel.inviteResponsible(personId)
                        }
                        selectedPersonId = null
                        selectedPersonLabel = null
                    }
                ) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "+ Agregar responsable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Buscar persona") },
                supportingText = { Text("Nombre o @usuario de LeoVer") },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPersonId = profile.id
                            selectedPersonLabel = buildString {
                                append(profile.displayName)
                                profile.username?.takeIf { it.isNotBlank() }?.let { append(" @$it") }
                            }
                            viewModel.updateSearchQuery("")
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatar = profile.avatarUrl ?: profile.avatarPath
                        if (avatar.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            AsyncImage(
                                model = avatar,
                                contentDescription = profile.displayName,
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        profile.username?.takeIf { it.isNotBlank() }?.let { username ->
                            Text(
                                text = "@$username",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                        text = "Seleccionada: $label",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            selectedPersonId = null
                            selectedPersonLabel = null
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) { Text("Quitar", maxLines = 1) }
                }
            }

            LeoPrimaryButton(
                text = "Enviar invitación",
                onClick = { showConfirm = true },
                enabled = !state.isSubmitting && selectedPersonId != null,
                modifier = Modifier.padding(top = 12.dp)
            )
    }
}

@Composable
private fun ResponsibilityCard(
    responsibility: PetResponsibility,
    displayName: String? = null,
    username: String? = null,
    canRevoke: Boolean,
    onRevoke: () -> Unit
) {
    val name = displayName?.takeIf { it.isNotBlank() }
        ?: friendlyHolderLabel(responsibility.holder)
    val handle = username?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
    LeoListRow(
        title = name,
        subtitle = handle?.let { "@$it" }
            ?: "${roleLabel(responsibility)} · ${linkStatusLabel(responsibility.status)}",
        leading = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        trailing = {
            if (canRevoke && responsibility.role != PetResponsibilityRole.PRINCIPAL) {
                TextButton(
                    onClick = onRevoke,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = "Quitar",
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                }
            }
        }
    )
}

internal fun holderKey(holder: PetPrincipalHolder): String = when (holder) {
    is PetPrincipalHolder.Person -> "p:${holder.userId}"
    is PetPrincipalHolder.Organization -> "o:${holder.organizationId.value}"
}

/** Nunca muestra UUID/ID crudo como etiqueta principal. */
internal fun friendlyHolderLabel(holder: PetPrincipalHolder): String = when (holder) {
    is PetPrincipalHolder.Person -> "No pudimos cargar esta persona"
    is PetPrincipalHolder.Organization -> "Organización"
}

@Deprecated("Usar friendlyHolderLabel + displayNames resueltos", ReplaceWith("friendlyHolderLabel(holder)"))
internal fun holderLabel(holder: PetPrincipalHolder): String = friendlyHolderLabel(holder)

private fun roleLabel(responsibility: PetResponsibility): String {
    return when (responsibility.role) {
        PetResponsibilityRole.PRINCIPAL -> "Responsable original"
        PetResponsibilityRole.CO_RESPONSIBLE -> "Responsable"
        PetResponsibilityRole.TEMPORARY_CUSTODIAN -> "Responsable"
    }
}

internal fun linkStatusLabel(status: PetLinkStatus): String = when (status) {
    PetLinkStatus.ACTIVE -> "Activo"
    PetLinkStatus.PENDING_ACCEPTANCE -> "Pendiente de aceptación"
    PetLinkStatus.REVOKED -> "Revocado"
    PetLinkStatus.EXPIRED -> "Vencido"
    PetLinkStatus.SUPERSEDED -> "Reemplazado"
}

internal fun validSinceLabel(fromEpochMs: Long, toEpochMs: Long?): String? {
    val from = formatEpochDate(fromEpochMs) ?: return toEpochMs?.let { to ->
        formatEpochDate(to)?.let { "Hasta: $it" }
    }
    return buildString {
        append("Desde: $from")
        toEpochMs?.let { formatEpochDate(it)?.let { to -> append(" · Hasta: $to") } }
    }
}

internal fun formatEpochDate(epochMs: Long): String? {
    if (epochMs <= 0L) return null
    val formatted = Instant.ofEpochMilli(epochMs)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
    return formatted.takeIf { it != "1970-01-01" }
}

/** Real timestamptz events (transfers): local date + hour. */
internal fun formatEpochDateTime(epochMs: Long): String? {
    if (epochMs <= 0L) return null
    return com.comunidapp.app.ui.util.formatMemoryDateTime(epochMs).takeIf { it.isNotBlank() }
}

internal fun parseDateToEpochMs(raw: String): Long? =
    runCatching {
        LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
