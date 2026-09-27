package com.comunidapp.app.ui.screens.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.social.SharedContentReference
import com.comunidapp.app.domain.social.SocialContentKind
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.theme.LeoDimens

@Composable
fun SharedContentCard(share: SharedContentReference) {
    val kindLabel = when (share.contentType) {
        SocialContentKind.POST -> "Publicación"
        SocialContentKind.REEL -> "Clip"
        SocialContentKind.STORY -> "Historia"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(kindLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(
            share.authorName.ifBlank { "LeoVer" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        share.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            PetImage(
                imageUrl = url,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 8.dp),
                cornerRadius = 8.dp,
                contentDescription = kindLabel
            )
        }
        if (share.captionPreview.isNotBlank()) {
            Text(
                share.captionPreview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceCompact))
    }
}
