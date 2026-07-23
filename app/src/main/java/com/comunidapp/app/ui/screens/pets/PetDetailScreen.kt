package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetClinicalRecord
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.ageDisplay
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.util.formatDisplayDate
import com.comunidapp.app.ui.util.formatRelativeTime
import com.comunidapp.app.viewmodel.PetDetailViewModel

@Composable
fun PetDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {},
    onDeleteSuccess: () -> Unit = {},
    onNavigateToResponsibilities: (String) -> Unit = {},
    onNavigateToAuthorizations: (String) -> Unit = {},
    onNavigateToTransfers: (String) -> Unit = {},
    onNavigateToStatusHistory: (String) -> Unit = {},
    viewModel: PetDetailViewModel = viewModel()
) {
    val pet by viewModel.pet.collectAsState()
    val isPetLoading by viewModel.isPetLoading.collectAsState()
    val petLoadError by viewModel.petLoadError.collectAsState()
    val canManage by viewModel.canManage.collectAsState()
    val canViewGovernance by viewModel.canViewGovernance.collectAsState()
    val canMarkDeceased by viewModel.canMarkDeceased.collectAsState()
    val canRestore by viewModel.canRestore.collectAsState()
    val canViewHistory by viewModel.canViewHistory.collectAsState()
    val clinicalRecords by viewModel.clinicalRecords.collectAsState()
    val clinicalTitle by viewModel.clinicalTitle.collectAsState()
    val clinicalNote by viewModel.clinicalNote.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()
    val lifecycleSuccess by viewModel.lifecycleSuccess.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeceasedDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var deceasedReason by remember { mutableStateOf("") }

    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            viewModel.clearDeleteSuccess()
            onDeleteSuccess()
        }
    }

    LaunchedEffect(lifecycleSuccess) {
        if (lifecycleSuccess) {
            viewModel.clearLifecycleSuccess()
            showDeceasedDialog = false
            showRestoreDialog = false
            deceasedReason = ""
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Archivar mascota") },
            text = { Text("¿Archivar esta mascota? Podés restaurarla después si tenés permiso.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePet()
                    }
                ) {
                    Text("Archivar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeceasedDialog) {
        MarkPetDeceasedDialog(
            reason = deceasedReason,
            onReasonChange = { deceasedReason = it },
            isSubmitting = isSubmitting,
            onConfirm = { viewModel.markPetDeceased(deceasedReason) },
            onDismiss = {
                if (!isSubmitting) {
                    showDeceasedDialog = false
                    deceasedReason = ""
                }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showRestoreDialog = false },
            title = { Text("Restaurar mascota") },
            text = { Text("¿Volver a activar esta mascota archivada?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.restorePet() },
                    enabled = !isSubmitting
                ) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = pet?.name ?: "Mascota",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        val data = pet
        when {
            isPetLoading && data == null -> {
                LoadingState(contentModifier = Modifier.padding(padding))
            }
            data == null && !petLoadError.isNullOrBlank() -> {
                ErrorState(
                    message = petLoadError.orEmpty(),
                    contentModifier = Modifier.padding(padding),
                    title = "No se pudo abrir la mascota",
                    onRetry = viewModel::loadPet
                )
            }
            data == null -> {
                EmptyState(
                    title = "Mascota no disponible",
                    contentModifier = Modifier.padding(padding),
                    message = "No encontramos esta mascota o ya no tenés acceso.",
                    actionLabel = "Reintentar",
                    onAction = viewModel::loadPet
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                PetImage(
                    imageUrl = data.photoUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    cornerRadius = 12.dp,
                    contentDescription = data.name
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = data.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    PetLifecycleStatusBadge(status = data.status)
                }
                Text(
                    text = "${data.species.toDisplayName()} · ${data.sex.toDisplayName()} · ${data.ageDisplay()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tamaño: ${data.size.toDisplayName()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = data.description, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                PetHealthSection(pet = data)
                if (canViewGovernance) {
                    Spacer(modifier = Modifier.height(16.dp))
                    PetGovernanceSection(
                        mutationsEnabled = data.status == "ACTIVE",
                        onOpenResponsibilities = { onNavigateToResponsibilities(data.id) },
                        onOpenAuthorizations = { onNavigateToAuthorizations(data.id) },
                        onOpenTransfers = { onNavigateToTransfers(data.id) }
                    )
                }
                if (canViewHistory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onNavigateToStatusHistory(data.id) }) {
                        Text("Ver historial de estado")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                ClinicalRecordsSection(
                    records = clinicalRecords,
                    title = clinicalTitle,
                    note = clinicalNote,
                    canManage = canManage && data.status == "ACTIVE",
                    onTitleChange = viewModel::updateClinicalTitle,
                    onNoteChange = viewModel::updateClinicalNote,
                    onAdd = viewModel::addClinicalNote
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                if (canMarkDeceased) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showDeceasedDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting
                    ) {
                        Text("Marcar como fallecida")
                    }
                }
                if (canRestore) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting
                    ) {
                        Text("Restaurar mascota")
                    }
                }

                if (canManage) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToEdit(data.id) },
                            modifier = Modifier.weight(1f),
                            enabled = data.status == "ACTIVE" && !isSubmitting
                        ) {
                            Text("Editar")
                        }
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = data.status == "ACTIVE" && !isSubmitting
                        ) {
                            Text("Archivar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarkPetDeceasedDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marcar como fallecida") },
        text = {
            Column {
                Text(
                    text = "Esta acción es irreversible. La mascota pasará a estado fallecida " +
                        "y se cancelarán transferencias pendientes."
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Motivo (opcional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = !isSubmitting,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSubmitting
            ) {
                Text("Confirmar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
internal fun PetLifecycleStatusBadge(status: String) {
    val label = petStatusLabel(status)
    if (status.equals("ACTIVE", ignoreCase = true)) return
    val color = when (status.uppercase()) {
        "ARCHIVED" -> MaterialTheme.colorScheme.surfaceVariant
        "DECEASED" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Text(
        text = label,
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

/** M08 Etapa 5 — accesos a responsables, autorizaciones y transferencias. */
@Composable
private fun PetGovernanceSection(
    mutationsEnabled: Boolean,
    onOpenResponsibilities: () -> Unit,
    onOpenAuthorizations: () -> Unit,
    onOpenTransfers: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Responsables y permisos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!mutationsEnabled) {
                Text(
                    text = "La gestión está bloqueada para este estado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            TextButton(
                onClick = onOpenResponsibilities,
                enabled = mutationsEnabled
            ) {
                Text("Responsables y custodias")
            }
            TextButton(
                onClick = onOpenAuthorizations,
                enabled = mutationsEnabled
            ) {
                Text("Personas autorizadas")
            }
            TextButton(
                onClick = onOpenTransfers,
                enabled = mutationsEnabled
            ) {
                Text("Transferencias de responsabilidad")
            }
        }
    }
}

@Composable
private fun ClinicalRecordsSection(
    records: List<PetClinicalRecord>,
    title: String,
    note: String,
    canManage: Boolean,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Historial clínico",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (records.isEmpty()) {
                Text(
                    text = "Sin registros clínicos todavía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                records.forEach { record ->
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = record.notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${record.authorName} · ${record.recordedAt?.let(::formatRelativeTime).orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canManage) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Título") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Nota clínica") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    minLines = 2
                )
                Button(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Agregar nota")
                }
            }
        }
    }
}

@Composable
private fun PetHealthSection(pet: Pet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Salud y cuidados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            pet.sterilized?.let {
                Text(
                    text = "Castración: ${when (it) {
                        SterilizationStatus.YES -> "Sí"
                        SterilizationStatus.NO -> "No"
                        SterilizationStatus.UNKNOWN -> "No especificado"
                    }}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            pet.microchipId?.let {
                Text(
                    text = "Microchip: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.lastVetVisit?.let {
                Text(
                    text = "Última consulta: ${formatDisplayDate(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.vaccinations
                .filter { it.name.isNotBlank() || it.date.isNotBlank() }
                .forEach { vac ->
                    val label = vac.name.ifBlank { "Vacuna" }
                    val applied = vac.date.takeIf { it.isNotBlank() }?.let(::formatDisplayDate) ?: "—"
                    val next = vac.nextDueDate?.takeIf { d -> d.isNotBlank() }
                        ?.let { " · Próx: ${formatDisplayDate(it)}" }
                        .orEmpty()
                    Text(
                        text = "💉 $label: $applied$next",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            pet.lastDeworming?.takeIf { it.isNotBlank() }?.let {
                val product = pet.dewormingProduct?.takeIf { p -> p.isNotBlank() }?.let { p -> " ($p)" }.orEmpty()
                Text(
                    text = "🪱 Desparasitación: ${formatDisplayDate(it)}$product",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            pet.lastFleaTreatment?.takeIf { it.isNotBlank() }?.let {
                val product = pet.fleaTreatmentProduct?.takeIf { p -> p.isNotBlank() }?.let { p -> " ($p)" }.orEmpty()
                Text(
                    text = "🐾 Antiparasitarios: ${formatDisplayDate(it)}$product",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.healthNotes?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Notas: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            val hasHealthData = pet.vaccinations.any { it.name.isNotBlank() || it.date.isNotBlank() } ||
                !pet.lastDeworming.isNullOrBlank() ||
                !pet.lastFleaTreatment.isNullOrBlank() ||
                pet.sterilized != null ||
                !pet.microchipId.isNullOrBlank() ||
                !pet.lastVetVisit.isNullOrBlank() ||
                !pet.healthNotes.isNullOrBlank()
            if (!hasHealthData) {
                Text(
                    text = "Sin datos de salud registrados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            val reminders = pet.reminders.filter { it.title.isNotBlank() || it.date.isNotBlank() }
            if (reminders.isNotEmpty()) {
                Text(
                    text = "Recordatorios",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                reminders.forEach { reminder ->
                    val title = reminder.title.ifBlank { "Recordatorio" }
                    val whenLabel = reminder.date.takeIf { it.isNotBlank() } ?: "—"
                    Text(
                        text = "⏰ $title — $whenLabel",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
