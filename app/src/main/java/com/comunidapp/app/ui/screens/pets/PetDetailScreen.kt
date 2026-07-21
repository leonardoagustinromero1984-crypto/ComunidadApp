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
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.ageDisplay
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
    viewModel: PetDetailViewModel = viewModel()
) {
    val pet by viewModel.pet.collectAsState()
    val canManage by viewModel.canManage.collectAsState()
    val canViewGovernance by viewModel.canViewGovernance.collectAsState()
    val clinicalRecords by viewModel.clinicalRecords.collectAsState()
    val clinicalTitle by viewModel.clinicalTitle.collectAsState()
    val clinicalNote by viewModel.clinicalNote.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            viewModel.clearDeleteSuccess()
            onDeleteSuccess()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar mascota") },
            text = { Text("¿Estás seguro? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePet()
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
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
        when (val data = pet) {
            null -> LoadingState(Modifier.padding(padding))
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
                Text(
                    text = data.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
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
                        onOpenResponsibilities = { onNavigateToResponsibilities(data.id) },
                        onOpenAuthorizations = { onNavigateToAuthorizations(data.id) },
                        onOpenTransfers = { onNavigateToTransfers(data.id) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                ClinicalRecordsSection(
                    records = clinicalRecords,
                    title = clinicalTitle,
                    note = clinicalNote,
                    canManage = canManage,
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

                if (canManage) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToEdit(data.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Editar")
                        }
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/** M08 Etapa 5 — accesos a responsables, autorizaciones y transferencias. */
@Composable
private fun PetGovernanceSection(
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
            TextButton(onClick = onOpenResponsibilities) {
                Text("Responsables y custodias")
            }
            TextButton(onClick = onOpenAuthorizations) {
                Text("Personas autorizadas")
            }
            TextButton(onClick = onOpenTransfers) {
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
            pet.vaccinations.forEach { vac ->
                val next = vac.nextDueDate?.takeIf { d -> d.isNotBlank() }?.let { " · Próx: ${formatDisplayDate(it)}" }.orEmpty()
                Text(
                    text = "💉 ${vac.name}: ${formatDisplayDate(vac.date)}$next",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            pet.lastDeworming?.let {
                val product = pet.dewormingProduct?.let { p -> " ($p)" }.orEmpty()
                Text(
                    text = "🪱 Desparasitación: ${formatDisplayDate(it)}$product",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            pet.lastFleaTreatment?.let {
                val product = pet.fleaTreatmentProduct?.let { p -> " ($p)" }.orEmpty()
                Text(
                    text = "🐾 Antiparasitarios: ${formatDisplayDate(it)}$product",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.healthNotes?.let {
                Text(
                    text = "Notas: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (pet.vaccinations.isEmpty() && pet.lastDeworming == null && pet.lastFleaTreatment == null &&
                pet.sterilized == null && pet.microchipId == null && pet.lastVetVisit == null && pet.healthNotes == null
            ) {
                Text(
                    text = "Sin datos de salud registrados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (pet.reminders.isNotEmpty()) {
                Text(
                    text = "Recordatorios",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                pet.reminders.forEach { reminder ->
                    Text(
                        text = "⏰ ${reminder.title} — ${reminder.date}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
