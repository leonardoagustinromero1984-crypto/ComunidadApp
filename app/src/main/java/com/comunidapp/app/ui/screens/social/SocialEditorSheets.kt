package com.comunidapp.app.ui.screens.social

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.comunidapp.app.domain.social.AudioMixPlanner
import com.comunidapp.app.domain.social.AudioSelection
import com.comunidapp.app.domain.social.AudioTrack
import com.comunidapp.app.domain.social.LeoVerEmojiCatalog
import com.comunidapp.app.domain.social.LeoVerMusicRecents
import com.comunidapp.app.domain.social.LeoVerOwnedMusicCatalog
import com.comunidapp.app.domain.social.LeoVerSticker
import com.comunidapp.app.domain.social.LeoVerStickerCatalog
import com.comunidapp.app.domain.social.LeoVerStickerCategory
import com.comunidapp.app.domain.social.OverlayTransform
import com.comunidapp.app.domain.social.RichMediaItem
import com.comunidapp.app.domain.social.RichMediaKind
import com.comunidapp.app.domain.social.RichMediaQuery
import com.comunidapp.app.domain.social.RichMediaResult
import com.comunidapp.app.domain.social.StoryOverlay
import com.comunidapp.app.domain.social.toSelection
import com.comunidapp.app.domain.social.toStoryAudio
import com.comunidapp.app.data.remote.klipy.RichMediaProviders
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.LeoDimens
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StoryOverlayStage(
    overlays: List<StoryOverlay>,
    onChange: (List<StoryOverlay>) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        overlays.sortedBy { it.zIndex }.forEach { overlay ->
            val sticker = overlay.stickerId?.let { LeoVerStickerCatalog.byId(it) }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            ((overlay.x * widthPx) - 64).roundToInt(),
                            ((overlay.y * heightPx) - 64).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        scaleX = overlay.scale
                        scaleY = overlay.scale
                        rotationZ = overlay.rotation
                    }
                    .pointerInput(overlay.id, editable) {
                        if (!editable) return@pointerInput
                        detectTransformGestures { _, pan, zoom, rotation ->
                            val moved = OverlayTransform.move(
                                overlay,
                                overlay.x + pan.x / widthPx,
                                overlay.y + pan.y / heightPx
                            )
                            val resized = OverlayTransform.resize(moved, overlay.scale * zoom)
                            val rotated = OverlayTransform.rotate(resized, overlay.rotation + rotation * 180f / Math.PI.toFloat())
                            onChange(OverlayTransform.bringToFront(overlays, overlay.id).map {
                                if (it.id == overlay.id) rotated else it
                            })
                        }
                    }
                    .clickable(enabled = editable) {
                        onChange(OverlayTransform.bringToFront(overlays, overlay.id))
                    }
            ) {
                when {
                    overlay.gifUrl != null -> AsyncImage(
                        model = overlay.gifUrl,
                        contentDescription = overlay.text,
                        modifier = Modifier.size(96.dp)
                    )
                    sticker != null -> StickerBadge(sticker)
                    else -> Text(
                        text = overlay.emoji ?: overlay.text,
                        fontSize = 32.sp,
                        color = Color(overlay.colorArgb.toInt())
                    )
                }
            }
        }
    }
}

@Composable
fun StickerBadge(sticker: LeoVerSticker) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(LeoVerStickerCatalog.BRAND_CREAM))
            .border(2.dp, Color(LeoVerStickerCatalog.BRAND_ORANGE), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(sticker.glyph, fontSize = 28.sp)
        Text(sticker.label, fontSize = 11.sp, color = Color(LeoVerStickerCatalog.BRAND_GREEN))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPickerSheet(
    selection: AudioSelection,
    mediaDurationMs: Long?,
    onChange: (AudioSelection) -> Unit,
    onDismiss: () -> Unit,
    isVideo: Boolean
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }
    val tracks = remember(query, category) {
        val base = if (query.isNotBlank()) LeoVerOwnedMusicCatalog.search(query)
        else if (category != null) LeoVerOwnedMusicCatalog.byCategory(category!!)
        else LeoVerOwnedMusicCatalog.forYou()
        base
    }
    val recents = LeoVerMusicRecents.list()
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    fun play(track: AudioTrack) {
        previewId = track.trackId
        player.setMediaItem(MediaItem.fromUri(track.assetUri))
        player.prepare()
        player.playWhenReady = true
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(LeoDimens.SpaceMd)) {
            Text("🎵 Música")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar música") }
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null && query.isBlank(), onClick = { category = null; query = "" }, label = { Text("Para vos") })
                LeoVerOwnedMusicCatalog.CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat; query = "" },
                        label = { Text(LeoVerOwnedMusicCatalog.CATEGORY_LABELS[cat] ?: cat) }
                    )
                }
            }
            if (recents.isNotEmpty() && query.isBlank() && category == null) {
                Text("Recientes", modifier = Modifier.padding(top = 8.dp))
                recents.forEach { track ->
                    TrackRow(track, previewId == track.trackId, { play(track) }) {
                        LeoVerMusicRecents.record(track.trackId)
                        val segment = AudioMixPlanner.plan(mediaDurationMs, track.durationMs, 0L)
                        onChange(selection.copy(track = track, segment = segment, source = com.comunidapp.app.domain.social.AudioTrackSource.LEOVER_CATALOG))
                    }
                }
            }
            LazyColumn(Modifier.height(280.dp)) {
                items(tracks, key = { it.trackId }) { track ->
                    TrackRow(track, previewId == track.trackId, { play(track) }) {
                        LeoVerMusicRecents.record(track.trackId)
                        val segment = AudioMixPlanner.plan(mediaDurationMs, track.durationMs, selection.segment.startMs)
                        onChange(selection.copy(track = track, segment = segment, source = com.comunidapp.app.domain.social.AudioTrackSource.LEOVER_CATALOG))
                    }
                }
            }
            selection.track?.let { track ->
                val maxStart = (track.durationMs - selection.segment.durationMs).coerceAtLeast(0L).toFloat()
                Text("${formatMs(selection.segment.startMs)} ───────────── ${formatMs(selection.segment.endMs())}")
                Slider(
                    value = selection.segment.startMs.toFloat().coerceIn(0f, maxStart.coerceAtLeast(1f)),
                    onValueChange = { start ->
                        onChange(
                            selection.copy(
                                segment = AudioMixPlanner.plan(mediaDurationMs, track.durationMs, start.toLong())
                            )
                        )
                    },
                    valueRange = 0f..maxStart.coerceAtLeast(1f)
                )
                Text("Música ${(selection.mix.musicVolume * 100).toInt()}%")
                Slider(
                    value = selection.mix.musicVolume,
                    onValueChange = { onChange(selection.copy(mix = selection.mix.copy(musicVolume = it))) }
                )
                if (isVideo) {
                    Text("Audio original ${(selection.mix.originalVolume * 100).toInt()}%")
                    Slider(
                        value = selection.mix.originalVolume,
                        onValueChange = {
                            onChange(selection.copy(mix = selection.mix.copy(originalVolume = it, originalMuted = it <= 0f)))
                        }
                    )
                    TextButton(onClick = {
                        onChange(selection.copy(mix = selection.mix.copy(originalMuted = true, originalVolume = 0f)))
                    }) { Text("Silenciar audio original") }
                }
                TextButton(onClick = {
                    onChange(AudioSelection())
                    player.stop()
                }) { Text("Quitar música") }
            }
            TextButton(onClick = onDismiss) { Text("Listo") }
        }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, playing: Boolean, onPlay: () -> Unit, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (playing) "⏸" else "▶", modifier = Modifier.clickable(onClick = onPlay))
        Column(Modifier.weight(1f)) {
            Text(track.title)
            Text("${LeoVerOwnedMusicCatalog.CATEGORY_LABELS[track.category] ?: track.category} · LeoVer Music · ${formatMs(track.durationMs)}")
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerSheet(
    overlays: List<StoryOverlay>,
    onChange: (List<StoryOverlay>) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf("leover") }
    var category by remember { mutableStateOf(LeoVerStickerCategory.LEOVER) }
    var external by remember { mutableStateOf<List<RichMediaItem>>(emptyList()) }
    var externalError by remember { mutableStateOf<String?>(null) }
    val provider = remember { RichMediaProviders.active() }
    val showExternal = provider.isConfigured()
    LaunchedEffect(tab) {
        if (tab == "gif" && showExternal) {
            externalError = null
            val result = withContext(Dispatchers.IO) {
                provider.featured(RichMediaQuery(RichMediaKind.GIF))
            }
            when (result) {
                is RichMediaResult.Ok -> external = result.page.items
                is RichMediaResult.Disabled -> {
                    tab = "leover"
                    externalError = null
                }
                is RichMediaResult.Failure -> externalError = result.message
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(LeoDimens.SpaceMd)) {
            Text("😀 Stickers")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "leover", onClick = { tab = "leover" }, label = { Text("LeoVer") })
                FilterChip(selected = tab == "emoji", onClick = { tab = "emoji" }, label = { Text("Emojis") })
                if (showExternal && com.comunidapp.app.domain.social.SocialEditorUxFlags.GIF_UI_VISIBLE) {
                    FilterChip(selected = tab == "gif", onClick = { tab = "gif" }, label = { Text("GIFs") })
                }
            }
            when (tab) {
                "leover" -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LeoVerStickerCategory.entries.forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.name) })
                        }
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(320.dp)) {
                        items(LeoVerStickerCatalog.byCategory(category), key = { it.id }) { sticker ->
                            Box(
                                Modifier
                                    .padding(6.dp)
                                    .clickable {
                                        val overlay = StoryOverlay(
                                            id = "sticker-${sticker.id}-${overlays.size}",
                                            kind = "STICKER",
                                            text = sticker.label,
                                            stickerId = sticker.id,
                                            zIndex = overlays.size
                                        )
                                        onChange(overlays + overlay)
                                    }
                            ) { StickerBadge(sticker) }
                        }
                    }
                }
                "emoji" -> {
                    LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.height(320.dp)) {
                        items(LeoVerEmojiCatalog.items) { emoji ->
                            Text(
                                emoji,
                                fontSize = 28.sp,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clickable {
                                        onChange(
                                            overlays + StoryOverlay(
                                                id = "emoji-${overlays.size}",
                                                kind = "EMOJI",
                                                text = emoji,
                                                emoji = emoji,
                                                zIndex = overlays.size
                                            )
                                        )
                                    }
                            )
                        }
                    }
                }
                else -> {
                    if (externalError != null) Text(externalError!!)
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(320.dp)) {
                        items(external, key = { it.id }) { item ->
                            AsyncImage(
                                model = item.previewUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onChange(
                                            overlays + StoryOverlay(
                                                id = "gif-${item.id}",
                                                kind = "GIF",
                                                text = item.title.orEmpty(),
                                                gifUrl = item.contentUrl,
                                                zIndex = overlays.size
                                            )
                                        )
                                    }
                            )
                        }
                    }
                }
            }
            TextButton(onClick = {
                overlays.lastOrNull()?.let { onChange(OverlayTransform.remove(overlays, it.id)) }
            }) { Text("Eliminar último") }
            TextButton(onClick = onDismiss) { Text("Listo") }
        }
    }
}

@Composable
fun CatalogMusicPlayer(assetUri: String?, volume: Float, startMs: Long) {
    if (assetUri.isNullOrBlank()) return
    val context = LocalContext.current
    val player = remember(assetUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(assetUri))
            this.volume = volume
            prepare()
            playWhenReady = true
            seekTo(startMs)
        }
    }
    DisposableEffect(player, volume, startMs) {
        player.volume = volume
        onDispose { player.release() }
    }
}
