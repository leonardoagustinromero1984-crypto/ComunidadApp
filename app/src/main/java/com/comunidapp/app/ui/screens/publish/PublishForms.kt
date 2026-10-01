package com.comunidapp.app.ui.screens.publish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.DonationType
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.media.LeoVerCaptureCamera
import com.comunidapp.app.ui.media.LeoVerCaptureMode
import com.comunidapp.app.ui.media.LeoVerMediaSource
import com.comunidapp.app.ui.media.LeoVerMediaSourceSheet
import com.comunidapp.app.ui.media.VideoPreviewFrame
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.components.v2.V2FormErrorBanner
import com.comunidapp.app.ui.components.v2.V2FormImagePreview
import com.comunidapp.app.ui.components.v2.V2FormScaffold
import com.comunidapp.app.ui.components.v2.V2FormTextField
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
import com.comunidapp.app.ui.components.v2.splitUserFacingFormError
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.domain.social.CanonicalSocialPostVisibility
import com.comunidapp.app.viewmodel.PublishViewModel

@Composable
fun PublishGeneralScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) = PublishFeedTypeScreen(
    screenTitle = "Publicación general",
    onNavigateBack = onNavigateBack,
    onPublishSuccess = onPublishSuccess,
    onSubmit = { title, content, location, uris, visibility, context, petIds, saveToVitaCora ->
        viewModel.publishGeneral(
            title,
            content,
            location,
            uris.firstOrNull(),
            visibility,
            context,
            extraImageUris = uris.drop(1),
            petIds = petIds,
            saveToVitaCora = saveToVitaCora
        )
    },
    viewModel = viewModel
)

@Composable
fun PublishUrgentScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) = PublishFeedTypeScreen(
    screenTitle = "Aviso urgente",
    onNavigateBack = onNavigateBack,
    onPublishSuccess = onPublishSuccess,
    onSubmit = { title, content, location, uris, visibility, context, _, _ ->
        viewModel.publishUrgent(
            title,
            content,
            location,
            uris.firstOrNull(),
            visibility,
            context,
            extraImageUris = uris.drop(1)
        )
    },
    viewModel = viewModel
)

@Composable
fun PublishReelScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    com.comunidapp.app.ui.screens.social.ReelComposerScreen(
        onNavigateBack = onNavigateBack,
        onPublishSuccess = onPublishSuccess,
        viewModel = viewModel
    )
}

/**
 * Flujo social directo de Historia.
 * Cámara real o galería (Photo Picker). Nunca abre galería al tocar Cámara.
 */
@Composable
fun PublishStoryScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    origin: String = "PUBLISH",
    autoOpenPicker: Boolean = false,
    viewModel: PublishViewModel = viewModel()
) {
    com.comunidapp.app.ui.screens.social.StoryComposerScreen(
        onNavigateBack = onNavigateBack,
        onPublishSuccess = onPublishSuccess,
        origin = origin,
        autoOpenPicker = autoOpenPicker,
        viewModel = viewModel
    )
}
@Composable
fun PublishQuestionScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) = PublishFeedTypeScreen(
    screenTitle = "Pregunta a la comunidad",
    onNavigateBack = onNavigateBack,
    onPublishSuccess = onPublishSuccess,
    onSubmit = { title, content, location, uris, visibility, context, _, _ ->
        viewModel.publishQuestion(
            title,
            content,
            location,
            uris.firstOrNull(),
            visibility,
            context,
            extraImageUris = uris.drop(1)
        )
    },
    viewModel = viewModel
)

@Composable
fun PublishPromoScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) = PublishFeedTypeScreen(
    screenTitle = "Publicidad / promo",
    onNavigateBack = onNavigateBack,
    onPublishSuccess = onPublishSuccess,
    onSubmit = { title, content, location, uris, visibility, context, _, _ ->
        viewModel.publishPromo(
            title,
            content,
            location,
            uris.firstOrNull(),
            visibility,
            context,
            extraImageUris = uris.drop(1)
        )
    },
    viewModel = viewModel
)

@Composable
private fun PublishFeedTypeScreen(
    screenTitle: String,
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    onSubmit: (
        String,
        String,
        String,
        List<android.net.Uri>,
        CanonicalSocialPostVisibility,
        android.content.Context,
        List<String>,
        Boolean
    ) -> Unit,
    viewModel: PublishViewModel
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(CanonicalSocialPostVisibility.PUBLIC) }
    var imageUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var selectedPetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPetPicker by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var captureMode by remember { mutableStateOf<LeoVerCaptureMode?>(null) }
    val formState by viewModel.formState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pets by DataProvider.petRepository.observePets().collectAsState()
    val resolver = context.contentResolver

    val pickPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            com.comunidapp.app.domain.social.SocialPostMedia.MAX_IMAGES
        )
    ) { uris ->
        imageUris = uris.take(com.comunidapp.app.domain.social.SocialPostMedia.MAX_IMAGES)
    }
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) imageUris = listOf(uri)
    }

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    val activeCapture = captureMode
    if (activeCapture != null) {
        LeoVerCaptureCamera(
            mode = activeCapture,
            onCaptured = { uri, _ ->
                imageUris = listOf(uri)
                captureMode = null
            },
            onCancel = { captureMode = null },
            purposeLabel = "Publicación"
        )
        return
    }

    PublishFormScaffold(
        title = screenTitle,
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            onSubmit(
                title,
                content,
                location,
                imageUris,
                visibility,
                context,
                selectedPetIds.toList(),
                selectedPetIds.isNotEmpty()
            )
        },
        viewModel = viewModel
    ) {
        Text(
            text = "Visibilidad",
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CanonicalSocialPostVisibility.uiPublishOptions().forEach { option ->
                LeoFilterChip(
                    label = CanonicalSocialPostVisibility.label(option),
                    selected = visibility == option,
                    onClick = { visibility = option }
                )
            }
        }
        Spacer(modifier = Modifier.height(LeoDimens.SpaceSm))
        V2FormTextField(
            value = title,
            onValueChange = { title = it },
            label = "Título",
            imeAction = ImeAction.Next
        )
        V2FormTextField(
            value = content,
            onValueChange = { content = it },
            label = "Contenido",
            singleLine = false,
            minLines = 4,
            maxLines = 8,
            imeAction = ImeAction.Default
        )
        V2LocationStringPicker(
            value = location,
            onValueChange = { location = it }
        )
        Text(
            text = "Guardar en VitaCora",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Mascotas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
        val selectedNames = pets.filter { it.id in selectedPetIds }.map { it.name }
        LeoListRow(
            title = when {
                selectedNames.isEmpty() -> "Mascota"
                selectedNames.size == 1 -> selectedNames.first()
                else -> "${selectedNames.size} mascotas"
            },
            subtitle = if (selectedNames.isEmpty()) "Opcional. Si elegís, se guarda en VitaCora." else selectedNames.joinToString(" · "),
            onClick = { showPetPicker = true }
        )
        Text(
            text = com.comunidapp.app.domain.social.BitacoraCopy.composerPetSelectionHint(
                selectedPetIds.size
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 4.dp, bottom = LeoDimens.SpaceSm)
        )
        if (imageUris.isNotEmpty()) {
            val firstIsVideo = resolver.getType(imageUris.first())?.startsWith("video/") == true
            if (firstIsVideo) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    VideoPreviewFrame(
                        url = imageUris.first().toString(),
                        contentDescription = "Video de la publicación"
                    )
                }
                Text(
                    "Video en publicación — no es un Clip.",
                    style = LeoCaption,
                    modifier = Modifier.padding(top = 4.dp, bottom = LeoDimens.SpaceSm)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imageUris.forEach { uri ->
                        V2FormImagePreview(
                            imageUrl = uri.toString(),
                            contentDescription = "Imagen de la publicación",
                            compact = true
                        )
                    }
                }
            }
        }
        LeoOutlinedButton(
            text = if (imageUris.isEmpty()) "Agregar foto o video" else "Cambiar media",
            onClick = { showMediaSheet = true }
        )
    }
    LeoVerMediaSourceSheet(
        visible = showMediaSheet,
        onDismiss = { showMediaSheet = false },
        onSelect = { source ->
            when (source) {
                LeoVerMediaSource.TAKE_PHOTO -> captureMode = LeoVerCaptureMode.PHOTO
                LeoVerMediaSource.RECORD_VIDEO -> captureMode = LeoVerCaptureMode.VIDEO
                LeoVerMediaSource.PICK_PHOTOS -> pickPhotosLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                LeoVerMediaSource.PICK_VIDEO -> pickVideoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
        }
    )
    if (showPetPicker) {
        com.comunidapp.app.ui.screens.social.ManagedPetPickerSheet(
            pets = pets,
            selectedPetId = selectedPetIds.firstOrNull(),
            onSelect = {},
            onDismiss = { showPetPicker = false },
            selectedPetIds = selectedPetIds,
            onConfirmSelection = { selectedPetIds = it }
        )
    }
}

@Composable
fun PublishAdoptionScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf(PetSpecies.DOG) }
    var sex by remember { mutableStateOf(PetSex.MALE) }
    var ageYears by remember { mutableIntStateOf(1) }
    var size by remember { mutableStateOf(PetSize.MEDIUM) }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val formState by viewModel.formState.collectAsState()
    val pickPhoto = com.comunidapp.app.ui.media.rememberLeoVerPhotoSourcePicker(
        sheetTitle = "Agregar foto",
        onSourceSelected = { uri -> imageUri = uri }
    )

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Publicar adopción",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishAdoption(name, species, sex, ageYears, size, location, description, imageUri)
        },
        viewModel = viewModel
    ) {
        imageUri?.let { uri ->
            V2FormImagePreview(
                imageUrl = uri.toString(),
                contentDescription = "Foto del animal"
            )
        }
        Text(com.comunidapp.app.ui.components.leo.LeoRequiredField.label("Foto"), style = MaterialTheme.typography.labelLarge)
        LeoOutlinedButton(
            text = if (imageUri == null) "Agregar foto" else "Cambiar foto",
            onClick = pickPhoto
        )
        V2FormTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre del animal",
            required = true,
            imeAction = ImeAction.Next
        )
        SpeciesChipRow(selected = species, onSelect = { species = it })
        SexChipRow(selected = sex, onSelect = { sex = it })
        SizeChipRow(selected = size, onSelect = { next -> if (next != null) size = next }, allowUnknown = false)
        V2FormTextField(
            value = ageYears.toString(),
            onValueChange = { ageYears = it.toIntOrNull() ?: 0 },
            label = "Edad (años)",
            imeAction = ImeAction.Next,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
        V2LocationStringPicker(
            value = location,
            onValueChange = { location = it }
        )
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            required = true,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
    }
}

@Composable
fun PublishLostFoundScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    initialType: LostFoundType = LostFoundType.LOST,
    prefillPetId: String? = null,
    onCreateMinimalPet: () -> Unit = {},
    viewModel: PublishViewModel = viewModel()
) {
    var type by remember { mutableStateOf(initialType) }
    var petName by remember { mutableStateOf("") }
    var species by remember { mutableStateOf(PetSpecies.DOG) }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var boundPetId by remember { mutableStateOf(prefillPetId) }
    var existingPhotoUrl by remember { mutableStateOf<String?>(null) }
    var existingAvatarAssetId by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf<com.comunidapp.app.domain.map.LeoVerGeoPoint?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLocationIntro by remember {
        mutableStateOf(
            !com.comunidapp.app.domain.location.ForegroundLocation.hasForegroundPermission(context)
        )
    }
    var foundSex by remember { mutableStateOf(PetSex.UNKNOWN) }
    var foundSize by remember { mutableStateOf<PetSize?>(null) }
    var estimatedAgeMonths by remember { mutableStateOf("") }
    val myPets by DataProvider.petRepository.observePets().collectAsState()
    val lostPets = remember(myPets) {
        com.comunidapp.app.domain.pets.LostPetSelector.selectable(myPets)
    }
    val locationScope = rememberCoroutineScope()
    val formState by viewModel.formState.collectAsState()
    val pickPhoto = com.comunidapp.app.ui.media.rememberLeoVerPhotoSourcePicker(
        sheetTitle = "Agregar foto",
        onSourceSelected = { uri -> imageUri = uri }
    )

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }
    fun applyLostSelection(pet: com.comunidapp.app.data.model.Pet) {
        val prefill = com.comunidapp.app.domain.pets.LostPetCasePrefillMapper.from(pet)
        boundPetId = prefill.petId
        petName = prefill.name
        species = prefill.species
        existingAvatarAssetId = prefill.avatarAssetId
        existingPhotoUrl = pet.photoUrl?.trim()?.takeIf { it.isNotEmpty() }
        prefill.location?.let { home ->
            if (location.isBlank()) location = home
        }
        if (description.isBlank()) description = prefill.description
    }

    LaunchedEffect(prefillPetId) {
        val id = prefillPetId?.trim().orEmpty()
        if (id.isBlank()) return@LaunchedEffect
        val pet = DataProvider.petRepository.fetchPetById(id) ?: DataProvider.petRepository.getPetById(id)
        if (pet != null) {
            applyLostSelection(pet)
            val url = com.comunidapp.app.domain.pets.PetPhotoResolver.displayUrl(
                pet,
                com.comunidapp.app.data.repository.AuthProvider.repository.getCurrentUser()?.id
            )
            if (!url.isNullOrBlank()) existingPhotoUrl = url
        }
    }
    LaunchedEffect(lostPets, type) {
        if (type != LostFoundType.LOST || !boundPetId.isNullOrBlank()) return@LaunchedEffect
        val only = lostPets.singleOrNull() ?: return@LaunchedEffect
        applyLostSelection(only)
        val url = com.comunidapp.app.domain.pets.PetPhotoResolver.displayUrl(
            only,
            com.comunidapp.app.data.repository.AuthProvider.repository.getCurrentUser()?.id
        )
        if (!url.isNullOrBlank()) existingPhotoUrl = url
    }

    PublishFormScaffold(
        title = if (type == LostFoundType.LOST) "Perdí a mi mascota" else "Encontré un animal",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        diagnosticText = formState.diagnosticText,
        onSubmit = {
            viewModel.publishLostFound(
                type,
                petName,
                species,
                location,
                description,
                contactInfo,
                imageUri,
                petId = boundPetId,
                hasExistingPhoto = !existingPhotoUrl.isNullOrBlank() || !existingAvatarAssetId.isNullOrBlank(),
                existingMediaAssetId = existingAvatarAssetId,
                latitude = pin?.latitude,
                longitude = pin?.longitude,
                sex = if (type == LostFoundType.FOUND) foundSex else null,
                size = if (type == LostFoundType.FOUND) foundSize else null,
                estimatedAgeMonths = if (type == LostFoundType.FOUND) {
                    estimatedAgeMonths.trim().toIntOrNull()?.takeIf { it >= 0 }
                } else {
                    null
                }
            )
        },
        viewModel = viewModel
    ) {
        com.comunidapp.app.ui.components.ContextualFirstVisitHelp(
            helpId = com.comunidapp.app.domain.onboarding.ContextualHelpId.ALERTS,
            message = com.comunidapp.app.ui.components.ContextualHelpMessages.ALERTS
        )
        Text(
            text = "La foto se publica completa. Revisala antes de enviar.",
            style = LeoCaption
        )
        Text(com.comunidapp.app.ui.components.leo.LeoRequiredField.label("Foto"), style = MaterialTheme.typography.labelLarge)
        V2FormImagePreview(
            imageUrl = imageUri?.toString() ?: existingPhotoUrl,
            contentDescription = "Vista previa de la foto"
        )
        LeoOutlinedButton(
            text = if (imageUri == null) "Agregar foto" else "Cambiar foto",
            onClick = pickPhoto
        )
        if (formState.errorMessage?.contains("Foto", ignoreCase = true) == true && imageUri == null && existingPhotoUrl.isNullOrBlank()) {
            Text("Agregá una foto.", style = LeoCaption, color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            LeoFilterChip(
                label = "Perdido",
                selected = type == LostFoundType.LOST,
                onClick = { type = LostFoundType.LOST }
            )
            LeoFilterChip(
                label = "Encontrado",
                selected = type == LostFoundType.FOUND,
                onClick = { type = LostFoundType.FOUND }
            )
        }
        SpeciesChipRow(selected = species, onSelect = { species = it })
        if (type == LostFoundType.LOST) {
            Text(com.comunidapp.app.ui.components.leo.LeoRequiredField.label("Mascota LeoVer"), style = MaterialTheme.typography.labelLarge)
            lostPets.take(8).forEach { pet ->
                LeoFilterChip(
                    label = pet.name,
                    selected = boundPetId == pet.id,
                    onClick = {
                        applyLostSelection(pet)
                        locationScope.launch {
                            val url = com.comunidapp.app.domain.pets.PetPhotoResolver.displayUrl(
                                pet,
                                com.comunidapp.app.data.repository.AuthProvider.repository.getCurrentUser()?.id
                            )
                            if (!url.isNullOrBlank()) existingPhotoUrl = url
                        }
                    }
                )
            }
            LeoOutlinedButton(
                text = "Cargar mascota perdida",
                onClick = onCreateMinimalPet
            )
        } else {
            Text(
                text = "Al publicar se crea una ficha provisional y una VitaCora. No te convierte en dueño: sos quien tiene al animal ahora.",
                style = LeoCaption
            )
            SexChipRow(selected = foundSex, onSelect = { foundSex = it })
            SizeChipRow(selected = foundSize, onSelect = { foundSize = it }, allowUnknown = true)
            V2FormTextField(
                value = estimatedAgeMonths,
                onValueChange = { raw ->
                    estimatedAgeMonths = raw.filter { it.isDigit() }.take(3)
                },
                label = "Edad estimada (meses, opcional)",
                imeAction = ImeAction.Next,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        }
        V2FormTextField(
            value = petName,
            onValueChange = { petName = it },
            label = if (type == LostFoundType.LOST) "Nombre" else "Nombre (si se conoce)",
            imeAction = ImeAction.Next
        )
        if (showLocationIntro && pin == null) {
            com.comunidapp.app.ui.screens.location.LocationPermissionOnboarding(
                onGranted = { showLocationIntro = false },
                onContinueWithout = { showLocationIntro = false }
            )
        }
        com.comunidapp.app.ui.screens.location.LocationPinPicker(
            selected = pin,
            onSelected = { point ->
                if (com.comunidapp.app.domain.location.SharedLocationCapture.isFallback(point)) return@LocationPinPicker
                pin = point
                locationScope.launch {
                    val suggestion = com.comunidapp.app.domain.location.AddressGeocoder.reverse(context, point)
                    val label = com.comunidapp.app.domain.lostfound.LostFoundLocationDisplay.humanLabel(
                        suggestion = suggestion
                    )
                    location = label.ifBlank {
                        com.comunidapp.app.domain.lostfound.LostFoundLocationDisplay.NEARBY_FALLBACK
                    }
                }
            },
            zoneLabel = location.takeIf {
                it.isNotBlank() && !com.comunidapp.app.domain.lostfound.LostFoundLocationDisplay.isPlaceholder(it)
            },
            address = location.takeIf { it.isNotBlank() },
            onAddressChange = { suggestion ->
                pin = suggestion.point
                location = com.comunidapp.app.domain.lostfound.LostFoundLocationDisplay.fromSuggestion(suggestion)
                    ?: suggestion.label
            },
            required = true,
            showError = formState.errorMessage?.contains("Ubicación", ignoreCase = true) == true
        )
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            required = true,
            errorMessage = if (formState.errorMessage?.contains("Descripción", ignoreCase = true) == true && description.isBlank()) {
                "Completá la descripción."
            } else {
                null
            },
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2FormTextField(
            value = contactInfo,
            onValueChange = { contactInfo = it },
            label = "Contacto",
            imeAction = ImeAction.Done,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
        )
    }
}

@Composable
private fun PublishFormScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    viewModel: PublishViewModel,
    diagnosticText: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val split = splitUserFacingFormError(errorMessage)
    val retryable = errorMessage?.contains("No pudimos publicar", ignoreCase = true) == true
    val clipboard = LocalClipboardManager.current
    val copyDiagnostic = diagnosticText?.takeIf { it.isNotBlank() }?.let { text ->
        {
            clipboard.setText(AnnotatedString(text))
        }
    }
    V2FormScaffold(
        title = title,
        onNavigateBack = {
            viewModel.resetFormState()
            onNavigateBack()
        },
        isLoading = isLoading,
        onSubmit = onSubmit,
        errorTitle = split?.first,
        errorMessage = split?.second,
        onRetry = if (retryable) onSubmit else null,
        onDismissError = viewModel::resetFormState,
        onCopyDiagnostic = copyDiagnostic,
        content = content
    )
}

@Composable
private fun SpeciesChipRow(selected: PetSpecies, onSelect: (PetSpecies) -> Unit) {
    com.comunidapp.app.ui.components.leo.LeoEnumChipRow(
        items = PetSpecies.entries,
        selected = selected,
        onSelect = onSelect,
        labelOf = { it.toDisplayName() },
        label = "Especie",
        required = true
    )
}

@Composable
private fun SexChipRow(selected: PetSex, onSelect: (PetSex) -> Unit) {
    com.comunidapp.app.ui.components.leo.LeoEnumChipRow(
        items = PetSex.entries,
        selected = selected,
        onSelect = onSelect,
        labelOf = { it.toDisplayName() },
        label = "Sexo",
        required = false
    )
}

@Composable
private fun SizeChipRow(
    selected: PetSize?,
    onSelect: (PetSize?) -> Unit,
    allowUnknown: Boolean
) {
    val items = if (allowUnknown) listOf(null) + PetSize.entries else PetSize.entries
    com.comunidapp.app.ui.components.leo.LeoEnumChipRow(
        items = items,
        selected = selected,
        onSelect = onSelect,
        labelOf = { it?.toDisplayName() ?: "No se sabe" },
        label = "Tamaño",
        required = false
    )
}

@Composable
fun PublishFosterScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var location by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    var species by remember { mutableStateOf(setOf(PetSpecies.DOG, PetSpecies.CAT)) }
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Hogar de tránsito",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishFosterHome(location, capacity, species.toList(), notes, contactInfo)
        },
        viewModel = viewModel
    ) {
        V2LocationStringPicker(
            value = location,
            onValueChange = { location = it }
        )
        V2FormTextField(
            value = capacity.toString(),
            onValueChange = { capacity = it.toIntOrNull() ?: 1 },
            label = "Capacidad",
            imeAction = ImeAction.Next,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
        SpeciesChipRow(
            selected = species.firstOrNull() ?: PetSpecies.DOG,
            onSelect = { selected ->
                species = if (species.contains(selected)) species - selected else species + selected
            }
        )
        V2FormTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notas",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2FormTextField(
            value = contactInfo,
            onValueChange = { contactInfo = it },
            label = "Contacto",
            imeAction = ImeAction.Done
        )
    }
}

@Composable
fun PublishEventScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Evento de adopción",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishEvent(title, date, location, description, contactInfo)
        },
        viewModel = viewModel
    ) {
        V2FormTextField(value = title, onValueChange = { title = it }, label = "Título", required = true)
        V2FormTextField(value = date, onValueChange = { date = it }, label = "Fecha", required = true)
        V2LocationStringPicker(value = location, onValueChange = { location = it })
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2FormTextField(
            value = contactInfo,
            onValueChange = { contactInfo = it },
            label = "Contacto",
            imeAction = ImeAction.Done
        )
    }
}

@Composable
fun PublishDonationScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var donationType by remember { mutableStateOf(DonationType.MONEY) }
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Campaña solidaria",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishDonationCampaign(
                title,
                description,
                location,
                goal.toDoubleOrNull(),
                donationType
            )
        },
        viewModel = viewModel
    ) {
        V2FormTextField(value = title, onValueChange = { title = it }, label = "Título", required = true)
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            required = true,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2LocationStringPicker(value = location, onValueChange = { location = it })
        V2FormTextField(
            value = goal,
            onValueChange = { goal = it },
            label = "Meta ($, opcional)",
            imeAction = ImeAction.Done,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
    }
}

@Composable
fun PublishShelterScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var needs by remember { mutableStateOf("") }
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Perfil de refugio",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishShelter(name, location, description, phone, email, needs)
        },
        viewModel = viewModel
    ) {
        V2FormTextField(value = name, onValueChange = { name = it }, label = "Nombre del refugio", required = true)
        V2LocationStringPicker(value = location, onValueChange = { location = it })
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2FormTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Teléfono",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
        )
        V2FormTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
        )
        V2FormTextField(
            value = needs,
            onValueChange = { needs = it },
            label = "Necesidades (una por línea: ítem|cantidad)",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "StoryMediaPickerEntryPreview")
@Composable
private fun StoryMediaPickerEntryPreview() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subir historia", style = MaterialTheme.typography.titleMedium)
        Text("Imagen o video Â· se publica 24 horas.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Galería") }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Cámara") }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "StoryImagePreview")
@Composable
private fun StoryImagePreview() {
    Column(Modifier.padding(16.dp)) {
        Text("Vista previa imagen")
        Spacer(Modifier.height(8.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Subir historia") }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "StoryVideoPreview")
@Composable
private fun StoryVideoPreview() {
    Column(Modifier.padding(16.dp)) {
        Text("Video seleccionado", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Subir historia") }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "StoryUploadErrorPreview")
@Composable
private fun StoryUploadErrorPreview() {
    Column(Modifier.padding(16.dp)) {
        Text("No pudimos publicar la historia", color = MaterialTheme.colorScheme.error)
        Text("Podés cambiar el medio o reintentar. No quedás atrapado.")
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {}) { Text("Cancelar") }
    }
}
