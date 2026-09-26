package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.social.PendingSocialPublish
import com.comunidapp.app.domain.social.PendingSocialPublishState
import com.comunidapp.app.domain.social.ReelPublishController
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoChip
import com.comunidapp.app.ui.theme.NeutralBorder
import com.comunidapp.app.ui.theme.leoVisual
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CompactReelUploadCopy {
    fun label(job: PendingSocialPublish): String = when (job.state) {
        PendingSocialPublishState.PREPARING -> "Preparando Clip…"
        PendingSocialPublishState.UPLOADING -> {
            val percent = job.progressPercent
            if (percent != null) "Subiendo Clip… $percent %" else "Subiendo Clip…"
        }
        PendingSocialPublishState.PUBLISHING -> "Publicando Clip…"
        PendingSocialPublishState.SUCCESS -> "Clip publicado"
        PendingSocialPublishState.FAILED ->
            com.comunidapp.app.domain.social.PendingSocialPublishErrors.bannerMessage(
                job.errorCategory,
                job.reelCreated
            )
        PendingSocialPublishState.CANCELLED -> "Publicación cancelada"
    }
}

@Composable
fun ReelPublishStatusBanner(modifier: Modifier = Modifier) {
    val controller = runCatching { ReelPublishController.get() }.getOrNull() ?: return
    val job by controller.job.collectAsState()
    val currentUserId = com.comunidapp.app.domain.user.SessionResolvedPerson.current()?.id
        ?: com.comunidapp.app.data.repository.AuthProvider.repository.getCurrentUser()?.id
    val current = job?.takeIf { it.belongsTo(currentUserId) } ?: return
    if (current.state == PendingSocialPublishState.CANCELLED) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(current.jobId, current.state) {
        if (current.state == PendingSocialPublishState.SUCCESS) {
            delay(2_400)
            controller.dismissTerminal()
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .testTag("reel_compact_upload_indicator"),
        shape = RoundedCornerShape(8.dp),
        color = leoVisual().surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = CompactReelUploadCopy.label(current),
                style = LeoCaption,
                color = BrandText,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            when {
                current.canCancel -> TextButton(
                    onClick = { scope.launch { controller.cancel(context) } }
                ) { Text("Cancelar", style = LeoChip) }
                current.state == PendingSocialPublishState.FAILED -> {
                    if (current.canRetry) {
                        TextButton(
                            onClick = { scope.launch { controller.retry(context) } }
                        ) { Text("Reintentar", style = LeoChip) }
                    }
                    TextButton(
                        onClick = { scope.launch { controller.dismissTerminal() } }
                    ) { Text("Cerrar", style = LeoChip) }
                }
            }
        }
    }
}
