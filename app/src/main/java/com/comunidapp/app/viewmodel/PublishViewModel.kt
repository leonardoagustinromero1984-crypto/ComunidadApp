package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.DonationCampaign
import com.comunidapp.app.data.model.DonationType
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.data.remote.storage.StoragePaths
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.model.ShelterNeed
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.CommunityRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.ShelterRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.PreparedFileUpload
import com.comunidapp.app.domain.social.CanonicalSocialPostVisibility
import com.comunidapp.app.domain.publish.LocalDebugDiagnostic
import com.comunidapp.app.domain.publish.LostFoundPublishError
import com.comunidapp.app.domain.publish.PublishUiErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BitacoraPrompt(
    val kind: com.comunidapp.app.domain.social.SocialContentKind,
    val petId: String,
    val petName: String,
    val contentId: String,
    val compositionJson: String? = null,
    val mediaUrl: String? = null,
    val mediaMime: String? = null,
    val petIds: List<String> = emptyList()
)

data class PublishFormState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val diagnosticText: String? = null,
    val uploadProgress: Int? = null,
    val phaseMessage: String? = null,
    val acceptedBackground: Boolean = false,
    val bitacoraPrompt: BitacoraPrompt? = null
)

class PublishViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val lostFoundRepository: LostFoundRepository = DataProvider.lostFoundRepository,
    private val communityRepository: CommunityRepository = DataProvider.communityRepository,
    private val shelterRepository: ShelterRepository = DataProvider.shelterRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(PublishFormState())
    val formState: StateFlow<PublishFormState> = _formState.asStateFlow()

    fun publishGeneral(
        title: String,
        content: String,
        location: String,
        imageUri: Uri? = null,
        visibility: CanonicalSocialPostVisibility = CanonicalSocialPostVisibility.PUBLIC,
        context: android.content.Context,
        extraImageUris: List<Uri> = emptyList(),
        petIds: List<String> = emptyList(),
        saveToVitaCora: Boolean = false
    ) = publishFeed(
        title,
        content,
        location,
        imageUri,
        PostType.GENERAL,
        visibility,
        context,
        extraImageUris = extraImageUris,
        petIds = petIds,
        saveToVitaCora = saveToVitaCora
    )

    fun publishQuestion(
        title: String,
        content: String,
        location: String,
        imageUri: Uri? = null,
        visibility: CanonicalSocialPostVisibility = CanonicalSocialPostVisibility.PUBLIC,
        context: android.content.Context,
        extraImageUris: List<Uri> = emptyList()
    ) = publishFeed(
        title,
        content,
        location,
        imageUri,
        PostType.QUESTION,
        visibility,
        context,
        extraImageUris = extraImageUris
    )

    fun publishPromo(
        title: String,
        content: String,
        location: String,
        imageUri: Uri? = null,
        visibility: CanonicalSocialPostVisibility = CanonicalSocialPostVisibility.PUBLIC,
        context: android.content.Context,
        extraImageUris: List<Uri> = emptyList()
    ) = publishFeed(
        title,
        content,
        location,
        imageUri,
        PostType.PROMO,
        visibility,
        context,
        extraImageUris = extraImageUris
    )

    fun publishReel(
        description: String,
        location: String,
        videoUri: Uri?,
        petId: String? = null,
        localityId: String? = null,
        compositionJson: String? = null,
        context: android.content.Context,
        petIds: List<String> = emptyList()
    ) {
        if (videoUri == null) {
            _formState.update { it.copy(errorMessage = "Elegí un video para el Clip") }
            return
        }
        viewModelScope.launch {
            com.comunidapp.app.domain.media.ReelPublishTrace.begin()
            _formState.update {
                PublishFormState(isLoading = true, phaseMessage = "Preparando video…")
            }
            kotlinx.coroutines.yield()
            kotlinx.coroutines.delay(150)
            try {
                val ids = (listOfNotNull(petId) + petIds)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                com.comunidapp.app.domain.social.ReelPublishController.get()
                    .accept(
                        context = context,
                        source = videoUri,
                        caption = description.trim(),
                        locationText = location.trim().ifBlank { null },
                        localityId = localityId,
                        compositionJson = com.comunidapp.app.domain.social.SocialPostMedia.withPetIds(
                            compositionJson,
                            ids
                        ),
                        petIds = ids
                    )
                    .onSuccess {
                        com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                            com.comunidapp.app.domain.media.ReelPublishTrace.Stage.UI_SUCCESS,
                            result = "ACCEPTED"
                        )
                        _formState.update {
                            PublishFormState(
                                acceptedBackground = true,
                                phaseMessage = "Tu Clip se está publicando."
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error.message == "REEL_JOB_ACTIVE") {
                            failReelUi("Ya hay un Clip publicándose.")
                        } else {
                            failReelUi(humanizePublishError(error))
                        }
                    }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                    com.comunidapp.app.domain.media.ReelPublishTrace.Stage.UI_ERROR,
                    result = "TIMEOUT"
                )
                failReelUi(com.comunidapp.app.domain.media.ReelPublishTrace.userFacingMessage())
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                failReelUi(humanizePublishError(error))
            } finally {
                _formState.update { current ->
                    if (current.isLoading) current.copy(isLoading = false) else current
                }
            }
        }
    }

    fun publishStory(
        text: String,
        mediaUri: Uri?,
        petId: String? = null,
        isVideo: Boolean = false,
        localityId: String? = null,
        compositionJson: String? = null,
        context: android.content.Context
    ) {
        if (mediaUri == null) {
            _formState.update { it.copy(errorMessage = "Elegí una imagen o un video para la historia") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true, uploadProgress = 0) }
            try {
                resolveAuthor()
                    .onSuccess { author ->
                        val now = System.currentTimeMillis()
                        publishSocialMedia(
                            author = author,
                            type = PostType.STORY,
                            title = "Historia",
                            content = text.trim(),
                            locationText = null,
                            sourceUri = mediaUri,
                            petId = petId?.takeIf { it.isNotBlank() },
                            localityId = localityId,
                            compositionJson = compositionJson,
                            expectVideo = isVideo,
                            expiresAt = com.comunidapp.app.domain.social.StoryExpiration.expiresAtFrom(now),
                            context = context
                        )
                    }
                    .onFailure { error ->
                        _formState.update {
                            PublishFormState(errorMessage = humanizePublishError(error))
                        }
                    }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                _formState.update {
                    PublishFormState(errorMessage = "La carga tardó demasiado. Intentá de nuevo.")
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _formState.update {
                    PublishFormState(errorMessage = humanizePublishError(error))
                }
            } finally {
                _formState.update { current ->
                    if (current.isLoading) current.copy(isLoading = false) else current
                }
            }
        }
    }

    private fun publishFeed(
        title: String,
        content: String,
        location: String,
        imageUri: Uri?,
        type: PostType,
        visibility: CanonicalSocialPostVisibility,
        context: android.content.Context,
        extraImageUris: List<Uri> = emptyList(),
        petIds: List<String> = emptyList(),
        saveToVitaCora: Boolean = false
    ) {
        if (title.isBlank() || content.isBlank()) {
            _formState.update { it.copy(errorMessage = "Título y contenido son requeridos") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            try {
                resolveAuthor()
                    .onSuccess { author ->
                        if (type == PostType.PROMO &&
                            !RolePermissions.canPublishPromo(
                                com.comunidapp.app.domain.context.OperationalContextProvider.active.value
                            )
                        ) {
                            _formState.update {
                                PublishFormState(errorMessage = "Este contexto no puede publicar promociones")
                            }
                            return@launch
                        }
                        publishFeedPost(
                            author = author,
                            type = type,
                            title = title.trim(),
                            content = content.trim(),
                            locationText = location.trim().ifBlank { null },
                            imageUri = imageUri,
                            extraImageUris = extraImageUris,
                            visibility = visibility,
                            petId = petIds.firstOrNull(),
                            context = context,
                            petIds = petIds,
                            saveToVitaCora = saveToVitaCora
                        )
                    }
                    .onFailure { error ->
                        _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
                    }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                _formState.update {
                    PublishFormState(errorMessage = "La carga tardó demasiado. Intentá de nuevo.")
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _formState.update {
                    PublishFormState(errorMessage = humanizePublishError(error))
                }
            } finally {
                _formState.update { current ->
                    if (current.isLoading) current.copy(isLoading = false) else current
                }
            }
        }
    }

    fun publishUrgent(
        title: String,
        content: String,
        location: String,
        imageUri: Uri? = null,
        visibility: CanonicalSocialPostVisibility = CanonicalSocialPostVisibility.PUBLIC,
        context: android.content.Context,
        extraImageUris: List<Uri> = emptyList()
    ) = publishFeed(title, content, location, imageUri, PostType.URGENT, visibility, context, extraImageUris)

    fun publishAdoption(
        name: String,
        species: PetSpecies,
        sex: PetSex,
        ageYears: Int,
        size: PetSize,
        location: String,
        description: String,
        imageUri: Uri? = null
    ) {
        if (name.isBlank() || description.isBlank() || location.isBlank()) {
            _formState.update { it.copy(errorMessage = "Completá los campos obligatorios") }
            return
        }
        if (imageUri == null) {
            _formState.update { it.copy(errorMessage = "La foto es obligatoria para publicar una adopción") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            resolveAuthor()
                .onSuccess { author ->
                    if (!RolePermissions.canPublishAdoption(
                            com.comunidapp.app.domain.context.OperationalContextProvider.active.value
                        )
                    ) {
                        _formState.update {
                            PublishFormState(
                                errorMessage = "Las personas no publican adopciones. Usá un perfil de rescatista o refugio."
                            )
                        }
                        return@onSuccess
                    }
                    if (!RolePermissions.canPublishAdoption(author)) {
                        _formState.update {
                            PublishFormState(
                                errorMessage = "Tu cuenta no tiene el módulo de adopciones activo"
                            )
                        }
                        return@launch
                    }
                    // M03: AccountType no implica organización ni shelter_id institucional.
                    // Vinculación real vía OrganizationResourceLink (Etapa 3+).
                    val adoption = AdoptionPost(
                        id = "",
                        publisherId = author.id,
                        shelterId = null,
                        shelterName = author.name,
                        name = name.trim(),
                        species = species,
                        sex = sex,
                        ageYears = ageYears,
                        size = size,
                        location = location.trim(),
                        description = description.trim(),
                        status = AdoptionStatus.PUBLISHED
                    )
                    adoptionRepository.addAdoptionPost(adoption)
                        .onSuccess { adoptionId ->
                            var finalAdoption = adoption.copy(id = adoptionId)
                            imageUri?.let { uri ->
                                when (val upload = uploadMedia(
                                    uri,
                                    FileAssetPurpose.ADOPTION_MEDIA,
                                    author.id,
                                    adoptionId,
                                    FileResourceType.ADOPTION
                                )) {
                                    is AppResult.Success -> {
                                        finalAdoption = finalAdoption.copy(photoUrl = upload.data.assetId)
                                        adoptionRepository.updateAdoptionPost(finalAdoption)
                                    }
                                    is AppResult.Failure -> {
                                        _formState.update {
                                            PublishFormState(
                                                errorMessage = FileUiErrorMapper.message(upload.error)
                                            )
                                        }
                                        return@launch
                                    }
                                }
                            }
                            publishFeedPost(
                                author = author,
                                type = PostType.ADOPTION,
                                title = "$name busca familia",
                                content = description.trim(),
                                locationText = location.trim(),
                                imageUri = imageUri
                            )
                        }
                        .onFailure { error ->
                            _formState.update {
                                PublishFormState(errorMessage = humanizePublishError(error))
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
                }
        }
    }

    fun publishLostFound(
        type: LostFoundType,
        petName: String,
        species: PetSpecies,
        location: String,
        description: String,
        contactInfo: String,
        imageUri: Uri?,
        petId: String? = null,
        hasExistingPhoto: Boolean = false,
        existingMediaAssetId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        sex: PetSex? = null,
        size: PetSize? = null,
        estimatedAgeMonths: Int? = null
    ) {
        val missing = buildList {
            if (imageUri == null && !hasExistingPhoto) add("Foto")
            if (type == LostFoundType.LOST && petId.isNullOrBlank()) add("Mascota")
            if (latitude == null || longitude == null) add("Ubicación")
            if (description.isBlank()) add("Descripción")
        }
        if (missing.isNotEmpty()) {
            _formState.update {
                it.copy(
                    errorMessage = "Completá: ${missing.joinToString(", ")}.",
                    diagnosticText = LostFoundPublishError.VALIDATION
                )
            }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            resolveAuthor()
                .onSuccess { author ->
                    if (!RolePermissions.canPublishLostFound(
                            com.comunidapp.app.domain.context.OperationalContextProvider.active.value
                        )
                    ) {
                        _formState.update {
                            PublishFormState(errorMessage = "Este contexto no puede publicar perdidos/encontrados")
                        }
                        return@launch
                    }
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                    val seedPhoto = existingMediaAssetId?.trim()?.takeIf {
                        com.comunidapp.app.domain.lostfound.LostFoundCreatePayload.isMediaAssetId(it)
                    }
                    val lostPost = LostFoundPost(
                        id = "",
                        authorId = author.id,
                        authorName = author.name,
                        type = type,
                        petName = petName.trim().ifBlank { null },
                        species = species,
                        location = location.trim(),
                        description = description.trim(),
                        contactInfo = contactInfo.trim(),
                        date = date,
                        photoUrl = seedPhoto,
                        petId = if (type == LostFoundType.FOUND) null else petId,
                        latitude = latitude,
                        longitude = longitude,
                        sex = if (type == LostFoundType.FOUND) sex ?: PetSex.UNKNOWN else sex,
                        size = if (type == LostFoundType.FOUND) size else null,
                        estimatedAgeMonths = if (type == LostFoundType.FOUND) estimatedAgeMonths else null
                    )
                    lostFoundRepository.addLostFoundPost(lostPost)
                        .onSuccess { lostId ->
                            if (imageUri == null && seedPhoto != null) {
                                lostFoundRepository.updateLostFoundPost(
                                    lostPost.copy(id = lostId, photoUrl = seedPhoto)
                                )
                            }
                            if (imageUri != null) {
                                when (val upload = uploadMedia(
                                    imageUri,
                                    FileAssetPurpose.LOST_FOUND_MEDIA,
                                    author.id,
                                    lostId,
                                    FileResourceType.LOST_FOUND_CASE
                                )) {
                                    is AppResult.Success -> lostFoundRepository.updateLostFoundPost(
                                        lostPost.copy(id = lostId, photoUrl = upload.data.assetId)
                                    )
                                    is AppResult.Failure -> {
                                        _formState.update {
                                            PublishFormState(
                                                errorMessage = FileUiErrorMapper.message(upload.error)
                                            )
                                        }
                                        return@launch
                                    }
                                }
                            }
                            publishFeedPost(
                                author = author,
                                type = PostType.LOST_FOUND,
                                title = if (type == LostFoundType.LOST) "Mascota perdida" else "Mascota encontrada",
                                content = buildString {
                                    append(description.trim())
                                    if (location.isNotBlank()) {
                                        append("\n\n")
                                        append(location.trim())
                                    }
                                },
                                locationText = location.trim(),
                                imageUri = imageUri,
                                existingMediaAssetId = existingMediaAssetId,
                                petId = petId
                            )
                        }
                        .onFailure { error ->
                            AppLog.error(
                                "PublishLostFound",
                                "LOST_FOUND_PUBLISH_FAILED ${PublishUiErrorMapper.sanitizeTechnical(error)}",
                                error
                            )
                            _formState.update {
                                lostFoundErrorState(type, error, PublishUiErrorMapper.lostFoundUserMessage(error))
                            }
                        }
                }
                .onFailure { error ->
                    AppLog.error(
                        "PublishLostFound",
                        "LOST_FOUND_PUBLISH_FAILED ${PublishUiErrorMapper.sanitizeTechnical(error)}",
                        error
                    )
                    val sessionMessage = error.message
                        ?.takeIf { it.isNotBlank() && !PublishUiErrorMapper.isUnsafeToShow(it) }
                    _formState.update {
                        lostFoundErrorState(
                            type,
                            error,
                            sessionMessage ?: PublishUiErrorMapper.lostFoundUserMessage(error)
                        )
                    }
                }
        }
    }

    fun resetFormState() {
        _formState.value = PublishFormState()
    }

    private fun lostFoundErrorState(
        type: LostFoundType,
        error: Throwable,
        userMessage: String
    ): PublishFormState {
        val diagnostic = if (LocalDebugDiagnostic.isCopyEnabled()) {
            LocalDebugDiagnostic.lostFoundCreate(type.name, error)
        } else {
            null
        }
        return PublishFormState(errorMessage = userMessage, diagnosticText = diagnostic)
    }

    private suspend fun resolveAuthor(): Result<User> {
        val authUser = authRepository.getCurrentUser()
            ?: return Result.failure(IllegalArgumentException("Debés iniciar sesión para publicar"))
        return Result.success(userRepository.getUser(authUser.id) ?: authUser)
    }

    private suspend fun publishFeedPost(
        author: User,
        type: PostType,
        title: String,
        content: String,
        locationText: String?,
        imageUri: Uri?,
        extraImageUris: List<Uri> = emptyList(),
        existingMediaAssetId: String? = null,
        visibility: CanonicalSocialPostVisibility = CanonicalSocialPostVisibility.PUBLIC,
        petId: String? = null,
        expiresAt: Long? = null,
        requireMedia: Boolean = false,
        mimeType: String = "image/jpeg",
        filename: String = "media.jpg",
        context: android.content.Context? = null,
        petIds: List<String> = emptyList(),
        saveToVitaCora: Boolean = false
    ) {
        val imageUris = (listOfNotNull(imageUri) + extraImageUris)
            .distinct()
            .take(com.comunidapp.app.domain.social.SocialPostMedia.MAX_IMAGES)
        if (requireMedia && imageUris.isEmpty() && existingMediaAssetId.isNullOrBlank()) {
            _formState.update { PublishFormState(errorMessage = "El medio es obligatorio") }
            return
        }
        val now = System.currentTimeMillis()
        var mediaAssetId: String? = null
        val extraAssetIds = mutableListOf<String>()
        var resolvedMime = mimeType
        var resolvedFilename = filename
        var resolvedUri = imageUris.firstOrNull()
        var resolvedSize: Long? = null
        if (resolvedUri != null) {
            val cr = context?.contentResolver
            val detectedMime = cr?.getType(resolvedUri)?.lowercase().orEmpty()
            val isVideo = detectedMime.startsWith("video/") ||
                filename.substringAfterLast('.', "").lowercase() in setOf("mp4", "mov", "m4v", "3gp", "mkv", "webm")
            if (isVideo) {
                val ctx = context ?: run {
                    _formState.update { PublishFormState(errorMessage = "No pudimos procesar el video.") }
                    return
                }
                val prepared = com.comunidapp.app.domain.social.SocialMediaPipeline.prepare(
                    context = ctx,
                    source = resolvedUri,
                    expectVideo = true
                ).getOrElse { error ->
                    _formState.update {
                        PublishFormState(errorMessage = humanizePublishError(error))
                    }
                    return
                }
                resolvedUri = prepared.uri
                resolvedMime = prepared.mimeType
                resolvedFilename = prepared.filename
                resolvedSize = prepared.sizeBytes
                val uploadOwner = java.util.UUID.randomUUID().toString()
                when (
                    val upload = try {
                        uploadMedia(
                            resolvedUri,
                            FileAssetPurpose.POST_MEDIA,
                            author.id,
                            uploadOwner,
                            FileResourceType.POST,
                            mimeType = resolvedMime,
                            filename = resolvedFilename,
                            sizeBytes = resolvedSize
                        )
                    } finally {
                        com.comunidapp.app.domain.social.VideoExportPolicy.deleteExportOutput(prepared.uri.path)
                    }
                ) {
                    is AppResult.Success -> mediaAssetId = upload.data.assetId
                    is AppResult.Failure -> {
                        _formState.update {
                            PublishFormState(errorMessage = FileUiErrorMapper.message(upload.error))
                        }
                        return
                    }
                }
            } else {
                for ((index, uri) in imageUris.withIndex()) {
                    val uploadOwner = java.util.UUID.randomUUID().toString()
                    when (
                        val upload = uploadMedia(
                            uri,
                            FileAssetPurpose.POST_MEDIA,
                            author.id,
                            uploadOwner,
                            FileResourceType.POST,
                            mimeType = mimeType,
                            filename = filename
                        )
                    ) {
                        is AppResult.Success -> {
                            if (index == 0) mediaAssetId = upload.data.assetId
                            else extraAssetIds += upload.data.assetId
                        }
                        is AppResult.Failure -> {
                            _formState.update {
                                PublishFormState(errorMessage = FileUiErrorMapper.message(upload.error))
                            }
                            return
                        }
                    }
                }
            }
        } else {
            mediaAssetId = existingMediaAssetId?.trim()?.takeIf { it.isNotEmpty() }
        }
        val resolvedPetIds = (listOfNotNull(petId) + petIds)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val compositionJson = com.comunidapp.app.domain.social.SocialPostMedia.encode(
            locationLabel = locationText,
            extraMediaAssetIds = extraAssetIds,
            postType = when (type) {
                PostType.LOST_FOUND -> "LOST_FOUND"
                PostType.URGENT -> "URGENT"
                else -> null
            },
            petIds = resolvedPetIds
        )
        val post = FeedPost(
            id = "",
            authorId = author.id,
            authorName = author.name,
            authorImageUrl = author.profileImageUrl,
            type = type,
            title = title,
            content = content,
            locationText = locationText,
            mediaAssetId = mediaAssetId,
            imageUrl = mediaAssetId,
            imageUrls = extraAssetIds,
            createdAt = now,
            updatedAt = now,
            petId = resolvedPetIds.firstOrNull(),
            petIds = resolvedPetIds,
            expiresAt = expiresAt,
            compositionJson = compositionJson,
            visibility = visibility
        )

        feedRepository.addFeedPost(post)
            .onSuccess { contentId ->
                if (resolvedPetIds.isNotEmpty()) {
                    val assetIds = listOfNotNull(mediaAssetId) + extraAssetIds
                    if (assetIds.isNotEmpty()) {
                        com.comunidapp.app.domain.vitacora.VitaCoraSocialSave.saveApprovedPost(
                            contentId = contentId,
                            petIds = resolvedPetIds,
                            mediaAssetIds = assetIds,
                            compositionJson = compositionJson
                        ).onFailure { error ->
                            AppLog.warning("VitaCora", "post association failed", error)
                            _formState.update {
                                PublishFormState(
                                    isSuccess = false,
                                    errorMessage = "La publicación se creó, pero no pudimos guardarla en VitaCora."
                                )
                            }
                            return@onSuccess
                        }
                    }
                }
                _formState.update { PublishFormState(isSuccess = true) }
            }
            .onFailure { error ->
                _formState.update {
                    PublishFormState(errorMessage = humanizePublishError(error))
                }
            }
    }

    fun confirmBitacoraSave() {
        val prompt = _formState.value.bitacoraPrompt ?: return
        viewModelScope.launch {
            val targets = (listOf(prompt.petId) + prompt.petIds)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val title = when (prompt.kind) {
                com.comunidapp.app.domain.social.SocialContentKind.STORY -> "Historia en VitaCora"
                com.comunidapp.app.domain.social.SocialContentKind.POST -> "Publicación en VitaCora"
                else -> "Clip en VitaCora"
            }
            val body = com.comunidapp.app.domain.vitacora.VitaCoraSocialMomentCodec.encode(
                contentId = prompt.contentId,
                compositionJson = prompt.compositionJson,
                mediaUrl = prompt.mediaUrl,
                mediaAssetId = prompt.mediaUrl,
                mediaMime = prompt.mediaMime,
                contentKind = prompt.kind.name
            )
            var failed: Throwable? = null
            for (targetPetId in targets) {
                val result = DataProvider.vitaCoraRepository.saveSocialMoment(
                    petId = targetPetId,
                    title = title,
                    body = body
                )
                result.onFailure { error ->
                    failed = error
                    AppLog.warning("VitaCora", "story association failed", error)
                }
            }
            if (failed != null) {
                _formState.update {
                    PublishFormState(
                        isSuccess = false,
                        errorMessage = "No pudimos guardar esto en VitaCora. Intentá nuevamente."
                    )
                }
            } else {
                _formState.update { PublishFormState(isSuccess = true) }
            }
        }
    }

    fun skipBitacoraSave() {
        _formState.update { PublishFormState(isSuccess = true) }
    }

    private suspend fun publishSocialMedia(
        author: User,
        type: PostType,
        title: String,
        content: String,
        locationText: String?,
        sourceUri: Uri,
        petId: String?,
        localityId: String?,
        compositionJson: String?,
        expectVideo: Boolean,
        expiresAt: Long? = null,
        context: android.content.Context
    ) {
        val tracing = type == PostType.REEL
        if (tracing) {
            com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                com.comunidapp.app.domain.media.ReelPublishTrace.Stage.URI_READY,
                mime = context.contentResolver.getType(sourceUri),
                result = "OK"
            )
        }
        val prepareLabel = if (expectVideo) "Preparando video…" else null
        val uploadLabel = if (expectVideo) "Subiendo video…" else null
        val publishLabel = if (expectVideo) "Publicando…" else null
        val prepared = try {
            kotlinx.coroutines.withTimeout(120_000L) {
                com.comunidapp.app.domain.social.SocialMediaPipeline.prepare(
                    context = context,
                    source = sourceUri,
                    expectVideo = expectVideo
                ) {
                    _formState.update {
                        it.copy(
                            isLoading = true,
                            phaseMessage = prepareLabel,
                            uploadProgress = null
                        )
                    }
                }
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            if (tracing) {
                com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                    com.comunidapp.app.domain.media.ReelPublishTrace.Stage.FILE_METADATA_READY,
                    result = "TIMEOUT"
                )
            }
            throw error
        }.getOrElse { error ->
            if (tracing) {
                com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                    com.comunidapp.app.domain.media.ReelPublishTrace.Stage.FILE_METADATA_READY,
                    result = "URI_READ"
                )
                failReelUi(humanizePublishError(error))
            } else {
                _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
            }
            return
        }
        if (tracing) {
            com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                com.comunidapp.app.domain.media.ReelPublishTrace.Stage.FILE_METADATA_READY,
                fileSize = prepared.sizeBytes,
                mime = prepared.mimeType,
                result = prepared.exportDecision ?: "OK"
            )
        }
        val purpose = if (type == PostType.STORY) FileAssetPurpose.STORY_MEDIA else FileAssetPurpose.REEL_MEDIA
        val resourceType = if (type == PostType.STORY) FileResourceType.STORY else FileResourceType.REEL
        val tempId = java.util.UUID.randomUUID().toString()
        _formState.update {
            it.copy(isLoading = true, phaseMessage = uploadLabel, uploadProgress = null)
        }
        val progressJob = viewModelScope.launch {
            DataProvider.fileUploadCoordinator.uiState.collect { state ->
                if (state.phase == com.comunidapp.app.domain.files.FileUploadPhase.Uploading) {
                    val percent = state.progressPercent.coerceIn(0, 100)
                    _formState.update { current ->
                        current.copy(
                            isLoading = true,
                            uploadProgress = percent,
                            phaseMessage = if (expectVideo) "Subiendo video… $percent %" else current.phaseMessage
                        )
                    }
                }
            }
        }
        val upload = try {
            kotlinx.coroutines.withTimeout(90_000L) {
                uploadMedia(
                    prepared.uri,
                    purpose,
                    author.id,
                    tempId,
                    resourceType,
                    mimeType = prepared.mimeType,
                    filename = prepared.filename,
                    sizeBytes = prepared.sizeBytes
                )
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            if (tracing) {
                val last = com.comunidapp.app.domain.media.ReelPublishTrace.lastStage()
                if (last == com.comunidapp.app.domain.media.ReelPublishTrace.Stage.REGISTER_START) {
                    com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                        com.comunidapp.app.domain.media.ReelPublishTrace.Stage.REGISTER_FAIL,
                        fileSize = prepared.sizeBytes,
                        mime = prepared.mimeType,
                        result = "TIMEOUT"
                    )
                } else {
                    com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                        com.comunidapp.app.domain.media.ReelPublishTrace.Stage.UPLOAD_FAIL,
                        fileSize = prepared.sizeBytes,
                        mime = prepared.mimeType,
                        result = "TIMEOUT"
                    )
                }
            }
            throw error
        } finally {
            progressJob.cancel()
            com.comunidapp.app.domain.social.VideoExportPolicy.deleteExportOutput(prepared.uri.path)
        }
        when (upload) {
            is AppResult.Failure -> {
                val message = if (type == PostType.STORY) {
                    FileUiErrorMapper.storyLimitMessage(upload.error)
                } else {
                    reelAwareFileMessage(upload.error, tracing)
                }
                if (tracing) failReelUi(message) else {
                    _formState.update { PublishFormState(errorMessage = message) }
                }
                return
            }
            is AppResult.Success -> {
                _formState.update {
                    it.copy(isLoading = true, phaseMessage = publishLabel, uploadProgress = null)
                }
                val now = System.currentTimeMillis()
                val post = FeedPost(
                    id = "",
                    authorId = author.id,
                    authorName = author.name,
                    authorImageUrl = author.profileImageUrl,
                    type = type,
                    title = title,
                    content = content,
                    locationText = locationText,
                    createdAt = now,
                    updatedAt = now,
                    petId = petId,
                    petIds = com.comunidapp.app.domain.social.SocialPostMedia.petIds(compositionJson)
                        .ifEmpty { listOfNotNull(petId) },
                    expiresAt = expiresAt,
                    localityId = localityId,
                    compositionJson = compositionJson,
                    mediaMime = prepared.mimeType
                )
                if (tracing) {
                    com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                        com.comunidapp.app.domain.media.ReelPublishTrace.Stage.CREATE_REEL_START,
                        fileSize = prepared.sizeBytes,
                        mime = prepared.mimeType,
                        result = "OK"
                    )
                }
                val created = try {
                    kotlinx.coroutines.withTimeout(20_000L) {
                        if (type == PostType.STORY) {
                            feedRepository.addStory(post, upload.data.assetId)
                        } else {
                            feedRepository.addReel(post, upload.data.assetId)
                        }
                    }
                } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                    if (tracing) {
                        com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                            com.comunidapp.app.domain.media.ReelPublishTrace.Stage.CREATE_REEL_FAIL,
                            fileSize = prepared.sizeBytes,
                            mime = prepared.mimeType,
                            result = "TIMEOUT"
                        )
                    }
                    throw error
                }
                created.onSuccess { contentId ->
                    if (tracing) {
                        com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                            com.comunidapp.app.domain.media.ReelPublishTrace.Stage.CREATE_REEL_SUCCESS,
                            result = "OK"
                        )
                        com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                            com.comunidapp.app.domain.media.ReelPublishTrace.Stage.UI_SUCCESS,
                            result = "OK"
                        )
                    }
                    val selectedPetIds = com.comunidapp.app.domain.social.SocialPostMedia.petIds(compositionJson)
                        .ifEmpty { listOfNotNull(petId) }
                    val names = selectedPetIds.mapNotNull { id ->
                        DataProvider.petRepository.getPetById(id)?.name?.takeIf { it.isNotBlank() }
                    }
                    if (selectedPetIds.isNotEmpty() && names.isNotEmpty()) {
                        val kind = if (type == PostType.STORY) {
                            com.comunidapp.app.domain.social.SocialContentKind.STORY
                        } else {
                            com.comunidapp.app.domain.social.SocialContentKind.REEL
                        }
                        _formState.update {
                            PublishFormState(
                                bitacoraPrompt = BitacoraPrompt(
                                    kind = kind,
                                    petId = selectedPetIds.first(),
                                    petName = names.joinToString(" · "),
                                    contentId = contentId,
                                    compositionJson = compositionJson,
                                    mediaUrl = upload.data.assetId,
                                    mediaMime = prepared.mimeType,
                                    petIds = selectedPetIds
                                )
                            )
                        }
                    } else {
                        _formState.update { PublishFormState(isSuccess = true) }
                    }
                    viewModelScope.launch {
                        runCatching { feedRepository.refreshStories() }
                        runCatching { feedRepository.refreshPosts() }
                    }
                }.onFailure { error ->
                    if (tracing) {
                        com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                            com.comunidapp.app.domain.media.ReelPublishTrace.Stage.CREATE_REEL_FAIL,
                            result = createFailCategory(error)
                        )
                        failReelUi(com.comunidapp.app.domain.media.ReelPublishTrace.userFacingMessage())
                    } else {
                        _formState.update {
                            PublishFormState(
                                errorMessage = if (type == PostType.STORY) {
                                    storyCreateMessage(error)
                                } else {
                                    humanizePublishError(error)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun failReelUi(message: String) {
        if (com.comunidapp.app.domain.media.ReelPublishTrace.lastStage() !=
            com.comunidapp.app.domain.media.ReelPublishTrace.Stage.UI_ERROR
        ) {
            com.comunidapp.app.domain.media.ReelPublishTrace.mark(
                com.comunidapp.app.domain.media.ReelPublishTrace.Stage.UI_ERROR,
                result = com.comunidapp.app.domain.media.ReelPublishTrace.userFacingCategory()
            )
        }
        _formState.update {
            PublishFormState(
                errorMessage = message,
                diagnosticText = if (com.comunidapp.app.BuildConfig.DEBUG) {
                    com.comunidapp.app.domain.media.ReelPublishTrace.compact()
                } else {
                    null
                }
            )
        }
    }

    private fun reelAwareFileMessage(
        error: com.comunidapp.app.core.result.AppError,
        tracing: Boolean
    ): String {
        val mapped = FileUiErrorMapper.message(error)
        if (!tracing) return mapped
        val keep = mapped.contains("tamaño máximo", ignoreCase = true) ||
            mapped.contains("límite diario", ignoreCase = true) ||
            mapped.contains("demasiado rápido", ignoreCase = true) ||
            mapped.contains("conexión", ignoreCase = true) ||
            mapped.contains("tardó demasiado", ignoreCase = true)
        return if (keep && !mapped.contains("MEDIA-DB", ignoreCase = true)) {
            mapped
        } else {
            com.comunidapp.app.domain.media.ReelPublishTrace.userFacingMessage()
        }
    }

    private fun createFailCategory(error: Throwable): String {
        val blob = buildString {
            append(error.message.orEmpty())
            generateSequence(error.cause) { it.cause }.forEach { append(' ').append(it.message.orEmpty()) }
        }.uppercase()
        return when {
            "FILE_TOO_LARGE" in blob || "TOO_LARGE" in blob -> "FILE_TOO_LARGE"
            "RATE_LIMITED" in blob -> "RATE_LIMITED"
            "QUOTA" in blob -> "DAILY_QUOTA"
            "TIMEOUT" in blob -> "TIMEOUT"
            else -> "CREATE_FAILED"
        }
    }

    private fun storyCreateMessage(error: Throwable): String {
        val blob = buildString {
            append(error.message.orEmpty())
            generateSequence(error.cause) { it.cause }.forEach { append(' ').append(it.message.orEmpty()) }
        }.uppercase()
        return when {
            "FILE_TOO_LARGE" in blob || "TOO_LARGE" in blob ->
                "El video supera el tamaño máximo permitido."
            "VIDEO.COUNT" in blob || "MEDIA.VIDEO" in blob || "BYTES.DAILY" in blob ->
                FileUiErrorMapper.VIDEO_DAILY_LIMIT
            "RATE_LIMITED" in blob || "QUOTA" in blob || "STORY" in blob ->
                FileUiErrorMapper.STORY_DAILY_LIMIT
            else -> humanizePublishError(error)
        }
    }

    private fun humanizePublishError(error: Throwable): String {
        val blob = buildString {
            append(error.message.orEmpty())
            generateSequence(error.cause) { it.cause }.forEach { append(' ').append(it.message.orEmpty()) }
        }
        val schemaGap = (blob.contains("schema cache", ignoreCase = true) ||
            blob.contains("Could not find", ignoreCase = true)) &&
            (blob.contains("expires_at", ignoreCase = true) || blob.contains("pet_id", ignoreCase = true))
        return when {
            blob.contains("timeout", ignoreCase = true) ||
                blob.contains("timed out", ignoreCase = true) ->
                "La publicación tardó demasiado. Intentá de nuevo."
            blob.contains("wrong thread", ignoreCase = true) ||
                blob.contains("Transformer is accessed", ignoreCase = true) ->
                "No pudimos procesar el video. Intentá nuevamente."
            schemaGap ->
                "No pudimos publicar la historia. Revisá tu conexión e intentá nuevamente."
            blob.contains("JWT", ignoreCase = true) ||
                blob.contains("Bearer", ignoreCase = true) ||
                blob.contains("apikey", ignoreCase = true) ->
                "No pudimos publicar. Revisá tu sesión e intentá nuevamente."
            else -> PublishUiErrorMapper.userFacing(
                error,
                "No pudimos publicar. Intentá nuevamente."
            )
        }
    }

    private suspend fun uploadMedia(
        uri: Uri,
        purpose: FileAssetPurpose,
        actorUserId: String,
        resourceId: String,
        resourceType: FileResourceType,
        mimeType: String = "image/jpeg",
        filename: String = "media.jpg",
        sizeBytes: Long? = null
    ): AppResult<PreparedFileUpload> =
        DataProvider.fileUploadCoordinator.startUpload(
            uriString = uri.toString(),
            request = FileUploadRequest(
                purpose = purpose,
                owner = FileAssetOwner.User(actorUserId),
                resourceRef = FileResourceRef(resourceType, resourceId),
                originalFilename = filename,
                declaredMimeType = mimeType,
                sizeBytes = sizeBytes?.takeIf { it > 0L } ?: 1L,
                requestedVisibility = FileAssetVisibility.PUBLIC
            ),
            actorUserId = actorUserId
        )

    fun publishFosterHome(
        location: String,
        capacity: Int,
        species: List<PetSpecies>,
        notes: String,
        contactInfo: String
    ) {
        if (location.isBlank() || contactInfo.isBlank()) {
            _formState.update { it.copy(errorMessage = "Zona y contacto son obligatorios") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            resolveAuthor()
                .onSuccess { host ->
                    communityRepository.createFosterHome(
                        host,
                        FosterHomeListing(
                            id = "",
                            hostId = host.id,
                            hostName = host.name,
                            location = location.trim(),
                            capacity = capacity.coerceAtLeast(1),
                            acceptedSpecies = species.ifEmpty { listOf(PetSpecies.DOG, PetSpecies.CAT) },
                            notes = notes.trim(),
                            available = true,
                            contactInfo = contactInfo.trim()
                        )
                    ).onSuccess { _formState.update { PublishFormState(isSuccess = true) } }
                        .onFailure { error ->
                            _formState.update {
                                PublishFormState(errorMessage = humanizePublishError(error))
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
                }
        }
    }

    fun publishEvent(
        title: String,
        date: String,
        location: String,
        description: String,
        contactInfo: String
    ) {
        if (title.isBlank() || date.isBlank() || location.isBlank()) {
            _formState.update { it.copy(errorMessage = "Título, fecha y zona son obligatorios") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            resolveAuthor()
                .onSuccess { organizer ->
                    communityRepository.createEvent(
                        organizer,
                        AdoptionEvent(
                            id = "",
                            organizerId = organizer.id,
                            title = title.trim(),
                            location = location.trim(),
                            date = date.trim(),
                            organizerName = organizer.name,
                            description = description.trim(),
                            contactInfo = contactInfo.trim()
                        )
                    ).onSuccess { _formState.update { PublishFormState(isSuccess = true) } }
                        .onFailure { error ->
                            _formState.update {
                                PublishFormState(errorMessage = humanizePublishError(error))
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
                }
        }
    }

    fun publishDonationCampaign(
        title: String,
        description: String,
        location: String,
        goalAmount: Double?,
        donationType: DonationType
    ) {
        if (title.isBlank() || description.isBlank() || location.isBlank()) {
            _formState.update { it.copy(errorMessage = "Completá los campos obligatorios") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            resolveAuthor()
                .onSuccess { organizer ->
                    communityRepository.createDonationCampaign(
                        organizer,
                        DonationCampaign(
                            id = "",
                            organizerId = organizer.id,
                            title = title.trim(),
                            description = description.trim(),
                            location = location.trim(),
                            goalAmount = goalAmount,
                            donationType = donationType
                        )
                    ).onSuccess { _formState.update { PublishFormState(isSuccess = true) } }
                        .onFailure { error ->
                            _formState.update {
                                PublishFormState(errorMessage = humanizePublishError(error))
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
                }
        }
    }

    fun publishShelter(
        name: String,
        location: String,
        description: String,
        contactPhone: String,
        contactEmail: String,
        needsText: String
    ) {
        if (name.isBlank() || location.isBlank() || description.isBlank()) {
            _formState.update { it.copy(errorMessage = "Nombre, zona y descripción son obligatorios") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            resolveAuthor()
                .onSuccess { owner ->
                    val needs = needsText.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { line ->
                            val parts = line.split("|", limit = 2)
                            ShelterNeed(
                                item = parts[0].trim(),
                                quantity = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "1" }
                            )
                        }
                    shelterRepository.createShelter(
                        owner,
                        Shelter(
                            id = "",
                            ownerId = owner.id,
                            name = name.trim(),
                            location = location.trim(),
                            description = description.trim(),
                            contactPhone = contactPhone.trim().ifBlank { null },
                            contactEmail = contactEmail.trim().ifBlank { null },
                            needs = needs
                        )
                    ).onSuccess { _formState.update { PublishFormState(isSuccess = true) } }
                        .onFailure { error ->
                            _formState.update {
                                PublishFormState(errorMessage = humanizePublishError(error))
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
                }
        }
    }
}
