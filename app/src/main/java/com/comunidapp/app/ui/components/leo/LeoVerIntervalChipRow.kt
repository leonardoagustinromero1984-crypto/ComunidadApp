package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comunidapp.app.domain.schedule.AppointmentSlotPolicy
import com.comunidapp.app.ui.theme.LeoDimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeoVerIntervalChipRow(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceMicro),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceMicro)
    ) {
        AppointmentSlotPolicy.INTERVAL_MINUTES.forEach { minutes ->
            LeoFilterChip(
                label = AppointmentSlotPolicy.label(minutes),
                selected = selectedMinutes == minutes,
                onClick = { onSelect(minutes) }
            )
        }
    }
}
