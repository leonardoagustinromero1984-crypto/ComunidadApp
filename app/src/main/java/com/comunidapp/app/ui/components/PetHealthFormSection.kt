package com.comunidapp.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.util.formatDisplayDate

@OptIn(ExperimentalLayoutApi::class)
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
    nextDeworming: String,
    fleaTreatmentProduct: String,
    lastFleaTreatment: String,
    nextFleaTreatment: String,
    healthNotes: String,
    allergyName: String = "",
    medicationName: String = "",
    conditionName: String = "",
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
    onNextDewormingChange: (String) -> Unit,
    onFleaProductChange: (String) -> Unit,
    onLastFleaTreatmentChange: (String) -> Unit,
    onNextFleaTreatmentChange: (String) -> Unit,
    onAllergyNameChange: (String) -> Unit = {},
    onMedicationNameChange: (String) -> Unit = {},
    onConditionNameChange: (String) -> Unit = {},
    onHealthNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    vaccineOptions: List<String> = emptyList(),
    dewormerOptions: List<String> = emptyList(),
    fleaOptions: List<String> = emptyList()
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SterilizationStatus.entries.forEach { status ->
                LeoFilterChip(
                    label = status.toDisplayName(),
                    selected = sterilized == status,
                    onClick = { if (enabled) onSterilizedChange(status) }
                )
            }
        }

        // Microchip retirado de la UX (campo legado conservado en DTO/BD).
        @Suppress("UNUSED_PARAMETER", "UNUSED_EXPRESSION")
        val legacyMicrochip = microchipId to onMicrochipChange

        Spacer(modifier = Modifier.height(12.dp))
        DatePickerField(
            label = "Última consulta veterinaria",
            isoDate = lastVetVisit,
            onDateSelected = onLastVetVisitChange,
            enabled = enabled,
            historicalOnly = true
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
            options = vaccineOptions.ifEmpty { PetHealthCatalog.vaccinesForSpecies(species) },
            selected = pendingVaccineName,
            onSelected = onPendingVaccineNameChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Fecha de aplicación",
            isoDate = pendingVaccineDate,
            onDateSelected = onPendingVaccineDateChange,
            enabled = enabled,
            historicalOnly = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Próximo refuerzo",
            isoDate = pendingVaccineNextDate,
            onDateSelected = onPendingVaccineNextDateChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        LeoOutlinedButton(
            text = "Agregar vacuna al historial",
            onClick = onAddVaccination,
            enabled = enabled && pendingVaccineName.isNotBlank() && pendingVaccineDate.isNotBlank()
        )

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
            options = dewormerOptions.ifEmpty { PetHealthCatalog.dewormingProducts },
            selected = dewormingProduct,
            onSelected = onDewormingProductChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Fecha de desparasitación",
            isoDate = lastDeworming,
            onDateSelected = onLastDewormingChange,
            enabled = enabled,
            historicalOnly = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Próxima desparasitación",
            isoDate = nextDeworming,
            onDateSelected = onNextDewormingChange,
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
            options = fleaOptions.ifEmpty { PetHealthCatalog.fleaAndTickProducts },
            selected = fleaTreatmentProduct,
            onSelected = onFleaProductChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Fecha de aplicación",
            isoDate = lastFleaTreatment,
            onDateSelected = onLastFleaTreatmentChange,
            enabled = enabled,
            historicalOnly = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Próxima aplicación",
            isoDate = nextFleaTreatment,
            onDateSelected = onNextFleaTreatmentChange,
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = allergyName,
            onValueChange = onAllergyNameChange,
            label = { Text("Alergia declarada") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = medicationName,
            onValueChange = onMedicationNameChange,
            label = { Text("Medicación declarada") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = conditionName,
            onValueChange = onConditionNameChange,
            label = { Text("Condición declarada") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = healthNotes,
            onValueChange = onHealthNotesChange,
            label = { Text("Indicaciones de cuidado") },
            placeholder = { Text("Alimentación, cuidados especiales, observaciones…") },
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        LeoHairline()
    }
}
