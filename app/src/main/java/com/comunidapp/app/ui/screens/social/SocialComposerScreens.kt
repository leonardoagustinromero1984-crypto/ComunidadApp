package com.comunidapp.app.ui.screens.social

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.LocationSelection
import com.comunidapp.app.data.model.displayOf
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.media.PhotoCanvasTransform
import com.comunidapp.app.domain.social.AudioSelection
import com.comunidapp.app.domain.social.BitacoraCopy
import com.comunidapp.app.domain.social.SocialEditorUxFlags
import com.comunidapp.app.domain.social.StoryComposition
import com.comunidapp.app.domain.social.StoryOverlay
import com.comunidapp.app.domain.social.toStoryAudio
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2FormErrorBanner
import com.comunidapp.app.ui.components.v2.splitUserFacingFormError
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.viewmodel.PublishViewModel
import kotlinx.serialization.json.Json

@Composable
fun StoryComposerScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    origin: String = "PUBLISH",
    autoOpenPicker: Boolean = false,
    viewModel: PublishViewModel = viewModel()
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var selectedPetId by remember { mutableStateOf<String?>(null) }
    var selectedPetName by remember { mutableStateOf<String?>(null) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showPetPicker by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(LocationSelection()) }
    var audio by remember { mutableStateOf(AudioSelection()) }
    var overlays by remember { mutableStateOf<List<StoryOverlay>>(emptyList()) }
    var photoTransform by remember { mutableStateOf(PhotoCanvasTransform()) }
    var baselineTransform by remember { mutableStateOf(PhotoCanvasTransform()) }
    var pickerOpened by remember { mutableStateOf(false) }
    val formState by viewModel.formState.collectAsState()
    val pets by DataProvider.petRepository.observePets().collectAsState()

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            mediaUri = uri
            isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            photoTransform = PhotoCanvasTransform()
            baselineTransform = PhotoCanvasTransform()
        }
    }

    fun compositionJson(): String {
        val built = overlays.toMutableList()
        if (text.isNotBlank() && built.none { it.kind == "TEXT" && it.text == text }) {
            built += StoryOverlay(id = "text", kind = "TEXT", text = text, y = 0.78f)
        }
        if (!selectedPetName.isNullOrBlank() && built.none { it.kind == "PET" }) {
            built += StoryOverlay(id = "pet", kind = "PET", text = "🐾 $selectedPetName", y = 0.88f)
        }
        val loc = DataProvider.locationCatalogRepository.snapshot().displayOf(location).label
        if (loc.isNotBlank() && built.none { it.kind == "LOCATION" }) {
            built += StoryOverlay(id = "place", kind = "LOCATION", text = "📍 $loc", y = 0.92f)
        }
        return Json.encodeToString(
            StoryComposition.serializer(),
            StoryComposition(
                overlays = built,
                audio = audio.toStoryAudio(),
                photo = if (isVideo) PhotoCanvasTransform() else photoTransform
            )
        )
    }

    LaunchedEffect(autoOpenPicker) {
        if (autoOpenPicker && !pickerOpened && mediaUri == null) pickerOpened = true
    }
    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    if (showCamera) {
        StoryCameraScreen(
            onCaptured = { uri, video ->
                mediaUri = uri
                isVideo = video
                photoTransform = PhotoCanvasTransform()
                baselineTransform = PhotoCanvasTransform()
                showCamera = false
            },
            onCancel = { showCamera = false }
        )
        return
    }
    if (showPetPicker) {
        ManagedPetPickerSheet(
            pets = pets,
            selectedPetId = selectedPetId,
            onSelect = { pet ->
                selectedPetId = pet?.id
                selectedPetName = pet?.name
                showPetPicker = false
            },
            onDismiss = { showPetPicker = false }
        )
    }
    if (showLocation) {
        LocationPickerSheet(
            selection = location,
            onSelectionChange = { location = it },
            onDismiss = { showLocation = false }
        )
    }
    if (SocialEditorUxFlags.MUSIC_UI_VISIBLE && showMusic) {
        MusicPickerSheet(
            selection = audio,
            mediaDurationMs = if (isVideo) 15_000L else 15_000L,
            onChange = { audio = it },
            onDismiss = { showMusic = false },
            isVideo = isVideo
        )
    }
    if (SocialEditorUxFlags.STICKERS_UI_VISIBLE && showStickers) {
        StickerPickerSheet(
            overlays = overlays,
            onChange = { overlays = it },
            onDismiss = { showStickers = false }
        )
    }
    formState.bitacoraPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.skipBitacoraSave() },
            title = { Text(BitacoraCopy.JOURNAL_NAME) },
            text = { Text(BitacoraCopy.savePrompt(prompt.kind, prompt.petName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmBitacoraSave() }) { Text(BitacoraCopy.SAVE_BUTTON) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.skipBitacoraSave() }) { Text("AHORA NO") }
            }
        )
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Crear historia",
                showBackButton = true,
                onBackClick = {
                    viewModel.resetFormState()
                    onNavigateBack()
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            @Suppress("UNUSED_VARIABLE")
            val tracked = origin
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                mediaUri?.let { uri ->
                    if (!isVideo) {
                        StoryPhotoCanvas(
                            imageModel = uri,
                            transform = photoTransform,
                            editable = true,
                            onTransformChange = { photoTransform = it },
                            onReset = { photoTransform = photoTransform.reset() },
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = "Vista previa"
                        )
                    } else {
                        StoryVideoPlayer(url = uri.toString(), muted = false)
                    }
                }
                if (overlays.isNotEmpty()) {
                    StoryOverlayStage(
                        overlays = overlays,
                        onChange = { overlays = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 56.dp)
                    )
                }
                if (SocialEditorUxFlags.MUSIC_UI_VISIBLE) {
                    audio.track?.let {
                        CatalogMusicPlayer(it.assetUri, audio.mix.musicVolume, audio.segment.startMs)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LeoOutlinedButton(text = "Cámara", onClick = { showCamera = true })
            Spacer(Modifier.height(8.dp))
            LeoOutlinedButton(
                text = "Galería",
                onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
            )
            Spacer(Modifier.height(8.dp))
            LeoListRow(
                title = "Mascota",
                subtitle = selectedPetName ?: "Opcional",
                onClick = { showPetPicker = true }
            )
            LeoListRow(
                title = "Ubicación",
                subtitle = DataProvider.locationCatalogRepository.snapshot().displayOf(location).label
                    .ifBlank { "Agregar ubicación" },
                onClick = { showLocation = true }
            )
            if (SocialEditorUxFlags.STICKERS_UI_VISIBLE || SocialEditorUxFlags.MUSIC_UI_VISIBLE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (SocialEditorUxFlags.STICKERS_UI_VISIBLE) {
                        TextButton(onClick = { showStickers = true }) { Text("Sticker") }
                    }
                    if (SocialEditorUxFlags.MUSIC_UI_VISIBLE) {
                        TextButton(onClick = { showMusic = true }) { Text("Música") }
                    }
                }
            }
            LeoTextField(
                value = text,
                onValueChange = { text = it },
                label = "Texto",
                singleLine = false,
                minLines = 2
            )
            formState.uploadProgress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
            formState.errorMessage?.let { raw ->
                val split = splitUserFacingFormError(raw)
                V2FormErrorBanner(
                    title = split?.first ?: "No pudimos publicar la historia",
                    message = split?.second,
                    onDismiss = viewModel::resetFormState,
                    onRetry = {
                        viewModel.publishStory(
                            text = text,
                            mediaUri = mediaUri,
                            petId = selectedPetId,
                            isVideo = isVideo,
                            localityId = location.localityId,
                            compositionJson = compositionJson(),
                            context = context
                        )
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
            LeoPrimaryButton(
                text = if (formState.isLoading) "Publicando…" else "Subir historia",
                onClick = {
                    viewModel.publishStory(
                        text = text,
                        mediaUri = mediaUri,
                        petId = selectedPetId,
                        isVideo = isVideo,
                        localityId = location.localityId,
                        compositionJson = compositionJson(),
                        context = context
                    )
                },
                enabled = !formState.isLoading && mediaUri != null
            )
        }
    }
}

@Composable
fun ReelComposerScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var selectedPetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var location by remember { mutableStateOf(LocationSelection()) }
    var showPetPicker by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var audio by remember { mutableStateOf(AudioSelection()) }
    var overlays by remember { mutableStateOf<List<StoryOverlay>>(emptyList()) }
    val formState by viewModel.formState.collectAsState()
    val pets by DataProvider.petRepository.observePets().collectAsState()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            videoUri = uri
            overlays = emptyList()
            text = ""
            audio = AudioSelection()
        }
    }

    fun compositionJson(): String {
        val built = overlays.toMutableList()
        if (text.isNotBlank() && built.none { it.kind == "TEXT" }) {
            built += StoryOverlay(id = "text", kind = "TEXT", text = text)
        }
        return Json.encodeToString(
            StoryComposition.serializer(),
            StoryComposition(built, audio.toStoryAudio())
        )
    }

    LaunchedEffect(formState.isSuccess, formState.acceptedBackground) {
        if (formState.isSuccess || formState.acceptedBackground) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }
    if (showCamera) {
        com.comunidapp.app.ui.media.LeoVerCaptureCamera(
            mode = com.comunidapp.app.ui.media.LeoVerCaptureMode.VIDEO,
            onCaptured = { uri, _ ->
                videoUri = uri
                overlays = emptyList()
                text = ""
                audio = AudioSelection()
                showCamera = false
            },
            onCancel = { showCamera = false },
            purposeLabel = "Clip"
        )
        return
    }
    if (showPetPicker) {
        ManagedPetPickerSheet(
            pets = pets,
            selectedPetId = selectedPetIds.firstOrNull(),
            onSelect = {},
            onDismiss = { showPetPicker = false },
            selectedPetIds = selectedPetIds,
            onConfirmSelection = { selectedPetIds = it }
        )
    }
    if (showLocation) {
        LocationPickerSheet(
            selection = location,
            onSelectionChange = { location = it },
            onDismiss = { showLocation = false }
        )
    }
    if (SocialEditorUxFlags.MUSIC_UI_VISIBLE && showMusic) {
        MusicPickerSheet(
            selection = audio,
            mediaDurationMs = 30_000L,
            onChange = { audio = it },
            onDismiss = { showMusic = false },
            isVideo = true
        )
    }
    if (SocialEditorUxFlags.STICKERS_UI_VISIBLE && showStickers) {
        StickerPickerSheet(overlays = overlays, onChange = { overlays = it }, onDismiss = { showStickers = false })
    }
    formState.bitacoraPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.skipBitacoraSave() },
            title = { Text(BitacoraCopy.JOURNAL_NAME) },
            text = { Text(BitacoraCopy.savePrompt(prompt.kind, prompt.petName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmBitacoraSave() }) { Text(BitacoraCopy.SAVE_BUTTON) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.skipBitacoraSave() }) { Text("AHORA NO") }
            }
        )
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Nuevo Clip",
                showBackButton = true,
                onBackClick = {
                    viewModel.resetFormState()
                    onNavigateBack()
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                if (videoUri != null) {
                    com.comunidapp.app.ui.media.ReelFeedMedia(
                        url = videoUri.toString(),
                        modifier = Modifier.fillMaxSize(),
                        previewLabel = "Video listo",
                        allowPlayback = !formState.isLoading
                    )
                } else {
                    Text("Elegí un video")
                }
                StoryOverlayStage(overlays, { overlays = it })
                if (SocialEditorUxFlags.MUSIC_UI_VISIBLE) {
                    audio.track?.let { CatalogMusicPlayer(it.assetUri, audio.mix.musicVolume, audio.segment.startMs) }
                }
            }
            LeoOutlinedButton(
                text = "Grabar Clip",
                onClick = { showCamera = true }
            )
            Spacer(Modifier.height(8.dp))
            LeoOutlinedButton(
                text = if (videoUri == null) "Elegir video de galería" else "Cambiar video",
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
            )
            Spacer(Modifier.height(8.dp))
            if (SocialEditorUxFlags.MUSIC_UI_VISIBLE) {
                LeoOutlinedButton(
                    text = if (audio.track == null) "Música" else audio.track?.title.orEmpty(),
                    onClick = { showMusic = true }
                )
            }
            if (SocialEditorUxFlags.STICKERS_UI_VISIBLE) {
                LeoOutlinedButton(text = "Stickers", onClick = { showStickers = true })
            }
            LeoTextField(
                value = description,
                onValueChange = { description = it },
                label = "Descripción",
                singleLine = false,
                minLines = 3
            )
            val names = pets.filter { it.id in selectedPetIds }.map { it.name }
            LeoListRow(
                title = "Mascota",
                subtitle = if (names.isEmpty()) "Opcional. Si elegís, se guarda en VitaCora." else names.joinToString(" · "),
                onClick = { showPetPicker = true }
            )
            LeoListRow(
                title = "Ubicación",
                subtitle = DataProvider.locationCatalogRepository.snapshot().displayOf(location).label
                    .ifBlank { "Agregar ubicación" },
                onClick = { showLocation = true }
            )
            formState.phaseMessage?.takeIf { formState.isLoading }?.let { phase ->
                Text(phase, style = LeoCaption, color = MutedText, modifier = Modifier.padding(top = 8.dp))
            }
            formState.uploadProgress?.let {
                LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
            }
            formState.errorMessage?.let { raw ->
                val split = splitUserFacingFormError(raw)
                V2FormErrorBanner(
                    title = split?.first ?: "No pudimos publicar el Clip",
                    message = split?.second,
                    onRetry = {
                        viewModel.publishReel(
                            description = description,
                            location = DataProvider.locationCatalogRepository.snapshot().displayOf(location).label,
                            videoUri = videoUri,
                            petId = selectedPetIds.firstOrNull(),
                            localityId = location.localityId,
                            compositionJson = compositionJson(),
                            context = context,
                            petIds = selectedPetIds.toList()
                        )
                    },
                    onDismiss = viewModel::resetFormState
                )
            }
            formState.diagnosticText?.let { trace ->
                Text(
                    text = trace,
                    style = LeoCaption,
                    color = MutedText,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            LeoPrimaryButton(
                text = if (formState.isLoading) {
                    formState.phaseMessage ?: "Publicando…"
                } else {
                    "Publicar Clip"
                },
                onClick = {
                    val locLabel = DataProvider.locationCatalogRepository.snapshot().displayOf(location).label
                    viewModel.publishReel(
                        description = description,
                        location = locLabel,
                        videoUri = videoUri,
                        petId = selectedPetIds.firstOrNull(),
                        localityId = location.localityId,
                        compositionJson = compositionJson(),
                        context = context,
                        petIds = selectedPetIds.toList()
                    )
                },
                enabled = !formState.isLoading && videoUri != null
            )
        }
    }
}
