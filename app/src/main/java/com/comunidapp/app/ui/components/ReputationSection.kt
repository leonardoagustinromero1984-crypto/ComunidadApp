package com.comunidapp.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.BadgeType
import com.comunidapp.app.data.model.UserBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReputationSection(
    reputationScore: Int,
    badges: List<UserBadge>,
    modifier: Modifier = Modifier
) {
    if (reputationScore <= 0 && badges.isEmpty()) return

    Text(
        text = "Reputación: $reputationScore pts",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
    if (badges.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            badges.forEach { badge ->
                AssistChip(
                    onClick = {},
                    label = { Text(badge.badgeType.displayName, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

fun defaultBadgesForScore(score: Int): List<UserBadge> = buildList {
    if (score >= 50) add(UserBadge("", "", BadgeType.SOLIDARY_HOME))
    if (score >= 100) add(UserBadge("", "", BadgeType.COMMUNITY_LEADER))
}
