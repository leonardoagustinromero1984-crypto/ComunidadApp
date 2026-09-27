package com.comunidapp.app.ui.screens.social

import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.CanonicalStoryInteraction
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.domain.social.SocialEditorUxFlags
import com.comunidapp.app.domain.social.StoryComposition
import com.comunidapp.app.domain.social.StoryOverlay
import kotlinx.serialization.json.Json

@Composable
fun StoryViewerScreen(
    stories: List<FeedPost>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    if (stories.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var index by remember(stories) { mutableIntStateOf(initialIndex.coerceIn(0, stories.lastIndex)) }
    val story = stories[index]
    val currentUserId = AuthProvider.repository.getCurrentUser()?.id
    val isOwner = currentUserId != null && currentUserId == story.authorId
    val scope = rememberCoroutineScope()
    var liked by remember(story.id) { mutableStateOf(false) }
    var reply by remember(story.id) { mutableStateOf("") }
    var viewers by remember(story.id) { mutableStateOf<List<com.comunidapp.app.data.repository.StoryViewerPerson>>(emptyList()) }
    var comments by remember(story.id) { mutableStateOf<List<com.comunidapp.app.data.repository.StoryCommentRow>>(emptyList()) }
    var showViewers by remember(story.id) { mutableStateOf(false) }
    LaunchedEffect(story.id, currentUserId) {
        if (!story.id.isBlank()) CanonicalStoryInteraction.recordView(story.id)
        if (isOwner) {
            viewers = CanonicalStoryInteraction.listViewers(story.id)
            comments = CanonicalStoryInteraction.listComments(story.id)
        }
    }
    val composition = remember(story.compositionJson) { decodeComposition(story.compositionJson) }
    val isVideo = story.mediaMime?.startsWith("video/") == true ||
        story.imageUrl?.contains(".mp4", ignoreCase = true) == true
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(index) {
                detectTapGestures { offset ->
                    if (offset.x < size.width / 2f) {
                        if (index > 0) index -= 1 else onClose()
                    } else {
                        if (index < stories.lastIndex) index += 1 else onClose()
                    }
                }
            }
    ) {
        if (isVideo && !story.imageUrl.isNullOrBlank()) {
            StoryVideoPlayer(
                url = story.imageUrl,
                muted = composition.audio.originalMuted || (composition.audio.originalAudioVolume ?: composition.audio.originalVolume) <= 0f,
                volume = composition.audio.originalAudioVolume ?: composition.audio.originalVolume,
                onEnded = {
                    if (index < stories.lastIndex) index += 1 else onClose()
                }
            )
        } else if (!story.imageUrl.isNullOrBlank()) {
            StoryPhotoCanvas(
                imageModel = story.imageUrl,
                transform = composition.photo,
                editable = false,
                modifier = Modifier.fillMaxSize(),
                contentDescription = story.authorName
            )
        } else {
            Text("No se pudo cargar el medio", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        composition.overlays.takeIf { it.isNotEmpty() }?.let {
            StoryOverlayStage(overlays = it, onChange = {}, editable = false)
        }
        val music = composition.audio.catalogId?.let { com.comunidapp.app.domain.social.LeoVerOwnedMusicCatalog.byId(it) }
        if (SocialEditorUxFlags.MUSIC_UI_VISIBLE && music != null) {
            CatalogMusicPlayer(
                assetUri = music.assetUri,
                volume = (composition.audio.musicVolume ?: composition.audio.catalogVolume),
                startMs = composition.audio.startMs
            )
        }
        LinearProgressIndicator(
            progress = { (index + 1f) / stories.size.coerceAtLeast(1) },
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(3.dp)
                .padding(horizontal = 8.dp)
        )
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(story.authorName, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Cerrar", color = Color.White) }
        }
        story.locationText?.let {
            Text(
                text = "📍 $it",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 96.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {})
                .padding(12.dp)
        ) {
            if (isOwner && showViewers) {
                Text(
                    text = if (viewers.isEmpty()) "Todavía no hay vistas" else
                        viewers.joinToString { it.displayName?.ifBlank { it.username }.orEmpty().ifBlank { "Usuario" } },
                    color = Color.White
                )
            }
            if (isOwner && comments.isNotEmpty()) {
                comments.takeLast(3).forEach { c ->
                    Text(
                        "${c.displayName ?: c.username ?: "Alguien"}: ${c.body}",
                        color = Color.White
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    scope.launch { liked = CanonicalStoryInteraction.toggleHeart(story.id) }
                }) { Text(if (liked) "♥" else "♡", color = Color.White) }
                if (isOwner) {
                    TextButton(onClick = { showViewers = !showViewers }) {
                        Text("Visto por ${viewers.size}", color = Color.White)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Responder", color = Color.White.copy(alpha = 0.7f)) },
                    colors = TextFieldDefaults.colors()
                )
                TextButton(
                    onClick = {
                        val body = reply.trim()
                        if (body.isBlank()) return@TextButton
                        scope.launch {
                            CanonicalStoryInteraction.comment(story.id, body)
                                .onSuccess {
                                    reply = ""
                                    if (isOwner) comments = CanonicalStoryInteraction.listComments(story.id)
                                }
                        }
                    }
                ) { Text("Enviar", color = Color.White) }
            }
        }
    }
}

@Composable
private fun OverlayView(overlay: StoryOverlay) {
    StoryOverlayStage(overlays = listOf(overlay), onChange = {}, editable = false)
}

@Composable
fun StoryVideoPlayer(
    url: String,
    muted: Boolean,
    volume: Float = 1f,
    onEnded: () -> Unit = {},
    showController: Boolean = false,
    playWhenReady: Boolean = true,
    cropToFill: Boolean = false
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            this.volume = if (muted) 0f else volume.coerceIn(0f, 1f)
            prepare()
            this.playWhenReady = playWhenReady
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = showController
                this.player = player
                resizeMode = if (cropToFill) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    )
}

private fun decodeComposition(raw: String?): StoryComposition {
    if (raw.isNullOrBlank() || raw == "{}") return StoryComposition()
    return runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString(StoryComposition.serializer(), raw)
    }.getOrDefault(StoryComposition())
}
