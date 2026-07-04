package com.comunidapp.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.PetHealthCatalog
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.ui.util.formatDisplayDate

@Composable
fun PetHealthFormSection(
    species: PetSpecies,
    sterilized: SterilizationStatus?,
    microchipId: String,
    lastVetVisit: String,
    vaccinations: List<VaccinationRecord>,
    pendingVaccineName: String,
    pendingVaccineDate: String,
    pendingVaccineNextDate: String,
    dewormingProduct: String,
    lastDeworming: String,
    fleaTreatmentProduct: String,
    lastFleaTreatment: String,
    healthNotes: String,
    enabled: Boolean,
    onSterilizedChange: (SterilizationStatus) -> Unit,
    onMicrochipChange: (String) -> Unit,
    onLastVetVisitChange: (String) -> Unit,
    onPendingVaccineNameChange: (String) -> Unit,
    onPendingVaccineDateChange: (String) -> Unit,
    onPendingVaccineNextDateChange: (String) -> Unit,
    onAddVaccination: () -> Unit,
    onRemoveVaccination: (Int) -> Unit,
    onDewormingProductChange: (String) -> Unit,
    onLastDewormingChange: (String) -> Unit,
    onFleaProductChange: (String) -> Unit,
    onLastFleaTreatmentChange: (String) -> Unit,
    onHealthNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Registro de salud",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Completá lo que sepas. Podés actualizarlo cuando vayas al veterinario.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Text(
            text = "Castración / esterilización",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SterilizationStatus.entries.forEach { status ->
                FilterChip(
                    selected = sterilized == status,
                    onClick = { onSterilizedChange(status) },
                    enabled = enabled,
                    label = { Text(status.toDisplayName(), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = microchipId,
            onValueChange = onMicrochipChange,
            label = { Text("Microchip / identificación") },
            placeholder = { Text("Nº de chip o identificador oficial") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(12.dp))
        DatePickerField(
            label = "Última consulta veterinaria",
            isoDate = lastVetVisit,
            onDateSelected = onLastVetVisitChange,
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Vacunas",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (vaccinations.isNotEmpty()) {
            vaccinations.forEachIndexed { index, vac ->
                VaccinationRecordCard(
                    record = vac,
                    onRemove = { onRemoveVaccination(index) },
                    enabled = enabled
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        HealthOptionDropdown(
            label = "Tipo de vacuna",
            options = PetHealthCatalog.vaccinesForSpecies(species),
            selected = pendingVaccineName,
            onSelected = onPendingVaccineNameChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Fecha de aplicación",
            isoDate = pendingVaccineDate,
            onDateSelected = onPendingVaccineDateChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Próximo refuerzo (opcional)",
            isoDate = pendingVaccineNextDate,
            onDateSelected = onPendingVaccineNextDateChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAddVaccination,
            enabled = enabled && pendingVaccineName.isNotBlank() && pendingVaccineDate.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Agregar vacuna al historial")
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Desparasitación",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        HealthOptionDropdown(
            label = "Producto antiparasitario",
            options = PetHealthCatalog.dewormingProducts,
            selected = dewormingProduct,
            onSelected = onDewormingProductChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Fecha de desparasitación",
            isoDate = lastDeworming,
            onDateSelected = onLastDewormingChange,
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Pulgas, garrapatas y parásitos externos",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        HealthOptionDropdown(
            label = "Producto aplicado",
            options = PetHealthCatalog.fleaAndTickProducts,
            selected = fleaTreatmentProduct,
            onSelected = onFleaProductChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Fecha de aplicación",
            isoDate = lastFleaTreatment,
            onDateSelected = onLastFleaTreatmentChange,
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = healthNotes,
            onValueChange = onHealthNotesChange,
            label = { Text("Notas de salud") },
            placeholder = { Text("Alergias, medicación, condiciones crónicas, observaciones del vet…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            enabled = enabled
        )
    }
}

@Composable
private fun VaccinationRecordCard(
    record: VaccinationRecord,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Aplicada: ${formatDisplayDate(record.date)}",
                    style = MaterialTheme.typography.bodySmall
                )
                record.nextDueDate?.takeIf { it.isNotBlank() }?.let { next ->
                    Text(
                        text = "Próximo refuerzo: ${formatDisplayDate(next)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar vacuna",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
