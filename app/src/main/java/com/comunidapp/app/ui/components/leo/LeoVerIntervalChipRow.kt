package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onSelect(minutes) },
                label = {
                    Text(
                        text = AppointmentSlotPolicy.label(minutes),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false
                    )
                }
            )
        }
    }
}
