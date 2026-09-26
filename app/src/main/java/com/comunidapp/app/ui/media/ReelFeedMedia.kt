package com.comunidapp.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.screens.social.StoryVideoPlayer
import com.comunidapp.app.ui.theme.BrandWhite

/**
 * Lightweight video surface. ExoPlayer is created only after the user taps Play.
 * Idle state shows a cached real frame + a discreet play affordance — never a green disc on black.
 */
@Composable
fun ReelFeedMedia(
    url: String,
    modifier: Modifier = Modifier,
    previewLabel: String = "Clip",
    allowPlayback: Boolean = true,
    cropPreview: Boolean = true,
    onOpenFull: (() -> Unit)? = null
) {
    var playing by remember(url) { mutableStateOf(false) }
    val showPlayer = playing && allowPlayback && onOpenFull == null
    Box(
        modifier = modifier
            .background(Color(0xFF1A1A1A))
            .semantics { contentDescription = previewLabel },
        contentAlignment = Alignment.Center
    ) {
        if (showPlayer) {
            StoryVideoPlayer(
                url = url,
                muted = false,
                showController = true,
                playWhenReady = true,
                cropToFill = cropPreview
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = allowPlayback) {
                        if (onOpenFull != null) onOpenFull() else playing = true
                    },
                contentAlignment = Alignment.Center
            ) {
                VideoPreviewFrame(url = url, contentDescription = previewLabel)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = BrandWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
