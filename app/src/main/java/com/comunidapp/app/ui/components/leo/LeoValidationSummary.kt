package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comunidapp.app.domain.validation.ValidationSummary
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import androidx.compose.material3.MaterialTheme

@Composable
fun LeoValidationSummary(
    summary: ValidationSummary,
    modifier: Modifier = Modifier,
    onItemClick: (String) -> Unit = {}
) {
    if (summary.isEmpty) return
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = LeoDimens.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceMicro)
    ) {
        Text(summary.title, style = LeoSectionTitle, color = MaterialTheme.colorScheme.error)
        summary.items.forEach { item ->
            Text(
                text = "• ${item.label}",
                style = LeoCaption,
                color = BrandText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.sectionId != null) {
                        item.sectionId?.let(onItemClick)
                    }
            )
        }
    }
}
