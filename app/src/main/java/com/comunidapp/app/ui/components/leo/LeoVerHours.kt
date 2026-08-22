package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
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
    var picking by remember { mutableStateOf<Pair<WeeklyHoursDay, Boolean>?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
        Text("Horarios", color = visual.textPrimary)
        Text(
            "Indicá cada día con el selector de hora. Si no hay horarios guardados, el perfil público muestra “Horarios no informados”.",
            style = LeoCaption,
            color = visual.textSecondary
        )
        val days = if (schedule.days.isEmpty()) ProviderWeeklySchedule.emptyTemplate().days else schedule.days
        days.sortedBy { it.weekday }.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(day.label, color = visual.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (day.closed) "Cerrado" else "Abierto", style = LeoCaption, color = visual.textSecondary)
                        Switch(
                            checked = !day.closed,
                            onCheckedChange = { open ->
                                onChange(schedule.replace(day.copy(closed = !open, open24Hours = if (!open) false else day.open24Hours)))
                            }
                        )
                    }
                }
                if (!day.closed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Abierto 24 horas", style = LeoCaption, color = visual.textSecondary)
                        Switch(
                            checked = day.open24Hours,
                            onCheckedChange = { allDay ->
                                onChange(
                                    schedule.replace(
                                        day.copy(
                                            open24Hours = allDay,
                                            opensAt = if (allDay) "00:00" else day.opensAt,
                                            closesAt = if (allDay) "23:59" else day.closesAt
                                        )
                                    )
                                )
                            }
                        )
                    }
                    if (!day.open24Hours) {
                        Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                            TimeField(
                                label = "Desde",
                                value = day.opensAt.orEmpty(),
                                onClick = { picking = day to true },
                                modifier = Modifier.weight(1f)
                            )
                            TimeField(
                                label = "Hasta",
                                value = day.closesAt.orEmpty(),
                                onClick = { picking = day to false },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
    picking?.let { (day, isOpen) ->
        val initial = ProviderWeeklySchedule.parseHm(if (isOpen) day.opensAt else day.closesAt) ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val formatted = "%02d:%02d".format(state.hour, state.minute)
                    onChange(
                        schedule.replace(
                            if (isOpen) day.copy(opensAt = formatted) else day.copy(closesAt = formatted)
                        )
                    )
                    picking = null
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancelar") } },
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
        schedule.days.sortedBy { it.weekday }.forEach { day ->
            val hours = when {
                day.closed -> "Cerrado"
                day.open24Hours -> "Abierto 24 horas"
                else -> "${day.opensAt.orEmpty()} – ${day.closesAt.orEmpty()}"
            }
            Text("${day.label}: $hours", style = LeoCaption, color = visual.textSecondary)
        }
    }
}
