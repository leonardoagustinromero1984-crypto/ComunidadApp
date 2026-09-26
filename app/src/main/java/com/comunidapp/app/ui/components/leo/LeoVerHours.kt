package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.comunidapp.app.domain.schedule.OpenNowStatus
import com.comunidapp.app.domain.schedule.ProviderScheduleClock
import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.domain.schedule.WeeklyHoursBulkApply
import com.comunidapp.app.domain.schedule.WeeklyHoursDay
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.leoVisual
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeoVerWeeklyHoursEditor(
    schedule: ProviderWeeklySchedule,
    onChange: (ProviderWeeklySchedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = leoVisual()
    val labels = listOf("Lun" to 1, "Mar" to 2, "Mié" to 3, "Jue" to 4, "Vie" to 5, "Sáb" to 6, "Dom" to 7)
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var opensAt by remember { mutableStateOf("09:00") }
    var closesAt by remember { mutableStateOf("18:00") }
    var pickingOpen by remember { mutableStateOf<Boolean?>(null) }
    var addingExtra by remember { mutableStateOf(false) }
    val remaining = WeeklyHoursBulkApply.daysWithoutRule(schedule)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
        Text("Horarios", color = visual.textPrimary)
        Text(
            "Elegí varios días, definí un rango y aplicá. Después podés agregar otro horario para los días que faltan.",
            style = LeoCaption,
            color = visual.textSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXs)) {
            labels.forEach { (label, day) ->
                val enabled = addingExtra && remaining.contains(day) || !addingExtra
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = {
                        if (!enabled) return@FilterChip
                        selectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day
                    },
                    enabled = enabled,
                    label = { Text(label) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
            TimeField("Desde", opensAt, { pickingOpen = true }, Modifier.weight(1f))
            TimeField("Hasta", closesAt, { pickingOpen = false }, Modifier.weight(1f))
        }
        LeoOutlinedButton(
            text = "Aplicar horario",
            onClick = {
                onChange(
                    WeeklyHoursBulkApply.apply(
                        schedule,
                        WeeklyHoursBulkApply.Range(selectedDays, opensAt, closesAt)
                    )
                )
                addingExtra = false
            }
        )
        if (remaining.isNotEmpty()) {
            LeoOutlinedButton(
                text = "+ Agregar otro horario",
                onClick = {
                    addingExtra = true
                    selectedDays = remaining
                    opensAt = "09:00"
                    closesAt = "13:00"
                }
            )
        }
        schedule.visibleDays().forEach { day ->
            val hours = when {
                day.closed -> "Cerrado"
                day.open24Hours -> "Abierto 24 horas"
                else -> "${day.opensAt.orEmpty()} – ${day.closesAt.orEmpty()}"
            }
            Text("${day.label}: $hours", style = LeoCaption, color = visual.textSecondary)
        }
    }
    pickingOpen?.let { isOpen ->
        val initial = ProviderWeeklySchedule.parseHm(if (isOpen) opensAt else closesAt) ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingOpen = null },
            confirmButton = {
                TextButton(onClick = {
                    val formatted = "%02d:%02d".format(state.hour, state.minute)
                    if (isOpen) opensAt = formatted else closesAt = formatted
                    pickingOpen = null
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { pickingOpen = null }) { Text("Cancelar") } },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = leoVisual()
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Text(label, style = LeoCaption, color = visual.textSecondary)
        Text(value.ifBlank { "Elegir hora" }, color = visual.textPrimary)
    }
}

@Composable
fun LeoVerHoursDisplay(
    schedule: ProviderWeeklySchedule,
    modifier: Modifier = Modifier
) {
    val visual = leoVisual()
    val status = ProviderScheduleClock.openNow(schedule)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXs)) {
        Text(
            when (status) {
                OpenNowStatus.OPEN -> "Abierto ahora"
                OpenNowStatus.CLOSED -> "Cerrado ahora"
                OpenNowStatus.UNKNOWN -> "Horarios no informados"
            },
            color = visual.textPrimary
        )
        schedule.visibleDays().forEach { day ->
            val hours = when {
                day.closed -> "Cerrado"
                day.open24Hours -> "Abierto 24 horas"
                else -> "${day.opensAt.orEmpty()} – ${day.closesAt.orEmpty()}"
            }
            Text("${day.label}: $hours", style = LeoCaption, color = visual.textSecondary)
        }
    }
}
