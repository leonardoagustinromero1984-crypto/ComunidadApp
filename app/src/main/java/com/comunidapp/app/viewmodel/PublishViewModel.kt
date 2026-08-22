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
import com.comunidapp.app.domain.publish.LocalDebugDiagnostic
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
    val mediaUrl: String? = null
)

data class PublishFormState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val diagnosticText: String? = null,
    val uploadProgress: Int? = null,
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
        imageUri: Uri? = null
    ) = publishFeed(title, content, location, imageUri, PostType.GENERAL)

    fun publishQuestion(
        title: String,
        content: String,
        location: String,
        imageUri: Uri? = null
    ) = publishFeed(title, content, location, imageUri, PostType.QUESTION)

    fun publishPromo(
        title: String,
        content: String,
        location: String,
        imageUri: Uri? = null
    ) = publishFeed(title, content, location, imageUri, PostType.PROMO)

    fun publishReel(
        description: String,
        location: String,
        videoUri: Uri?,
        petId: String? = null,
        localityId: String? = null,
        compositionJson: String? = null,
        context: android.content.Context
    ) {
        if (videoUri == null) {
            _formState.update { it.copy(errorMessage = "Elegí un video para el Reel") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true, uploadProgress = 0) }
            resolveAuthor()
                .onSuccess { author ->
                    publishSocialMedia(
                        author = author,
                        type = PostType.REEL,
                        title = "Reel",
                        content = description.trim().ifBlank { "Reel" },
                        locationText = location.trim().ifBlank { null },
                        sourceUri = videoUri,
                        petId = petId?.takeIf { it.isNotBlank() },
                        localityId = localityId,
                        compositionJson = compositionJson,
                        expectVideo = true,
                        context = context
                    )
                }
                .onFailure { error ->
                    _formState.update {
                        PublishFormState(errorMessage = humanizePublishError(error))
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
        }
    }

    private fun publishFeed(
        title: String,
        content: String,
        location: String,
        imageUri: Uri?,
        type: PostType
    ) {
        if (title.isBlank() || content.isBlank()) {
            _formState.update { it.copy(errorMessage = "Título y contenido son requeridos") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
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
                        imageUri = imageUri
                    )
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
                }
        }
    }

    fun publishUrgent(
        title: String,
        content: String,
        location: String,
        imageUri: Uri? = null
    ) = publishFeed(title, content, location, imageUri, PostType.URGENT)

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
        hasExistingPhoto: Boolean = false
    ) {
        if (location.isBlank() || description.isBlank() || contactInfo.isBlank()) {
            _formState.update { it.copy(errorMessage = "Completá los campos obligatorios", diagnosticText = null) }
            return
        }
        if (imageUri == null && !hasExistingPhoto) {
            _formState.update {
                it.copy(
                    errorMessage = "La foto es obligatoria para alertas de perdidos/encontrados",
                    diagnosticText = null
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
                        petId = petId
                    )
                    lostFoundRepository.addLostFoundPost(lostPost)
                        .onSuccess { lostId ->
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
                                content = description.trim(),
                                locationText = location.trim(),
                                imageUri = imageUri
                            )
                        }
                        .onFailure { error ->
                            AppLog.error(
                                "PublishLostFound",
                                "LOST_FOUND_PUBLISH_FAILED ${PublishUiErrorMapper.sanitizeTechnical(error)}",
                                error
                            )
                            _formState.update {
                                lostFoundErrorState(type, error, PublishUiErrorMapper.lostFoundUserMessage())
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
                            sessionMessage ?: PublishUiErrorMapper.lostFoundUserMessage()
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
        petId: String? = null,
        expiresAt: Long? = null,
        requireMedia: Boolean = false,
        mimeType: String = "image/jpeg",
        filename: String = "media.jpg"
    ) {
        if (requireMedia && imageUri == null) {
            _formState.update { PublishFormState(errorMessage = "El medio es obligatorio") }
            return
        }
        val now = System.currentTimeMillis()
        var mediaAssetId: String? = null
        if (imageUri != null) {
            val uploadOwner = java.util.UUID.randomUUID().toString()
            when (
                val upload = uploadMedia(
                    imageUri,
                    FileAssetPurpose.POST_MEDIA,
                    author.id,
                    uploadOwner,
                    FileResourceType.POST,
                    mimeType = mimeType,
                    filename = filename
                )
            ) {
                is AppResult.Success -> mediaAssetId = upload.data.assetId
                is AppResult.Failure -> {
                    _formState.update {
                        PublishFormState(errorMessage = FileUiErrorMapper.message(upload.error))
                    }
                    return
                }
            }
        }
        val post = FeedPost(
            id = "",
            authorId = author.id,
            authorName = author.name,
            authorImageUrl = author.profileImageUrl,
            type = type,
            title = title,
            content = content,
            locationText = locationText,
            imageUrl = mediaAssetId,
            createdAt = now,
            updatedAt = now,
            petId = petId,
            expiresAt = expiresAt
        )

        feedRepository.addFeedPost(post)
            .onSuccess {
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
            val result = DataProvider.vitaCoraRepository.createMoment(
                petId = prompt.petId,
                kind = "SOCIAL",
                title = if (prompt.kind == com.comunidapp.app.domain.social.SocialContentKind.STORY) {
                    "Historia en VitaCora"
                } else {
                    "Reel en VitaCora"
                },
                body = com.comunidapp.app.domain.vitacora.VitaCoraSocialMomentCodec.encode(
                    contentId = prompt.contentId,
                    compositionJson = prompt.compositionJson,
                    mediaUrl = prompt.mediaUrl
                )
            )
            result.fold(
                onSuccess = {
                    _formState.update { PublishFormState(isSuccess = true) }
                },
                onFailure = { error ->
                    AppLog.warning("VitaCora", "story association failed", error)
                    _formState.update {
                        PublishFormState(
                            isSuccess = false,
                            errorMessage = "No pudimos guardar esto en VitaCora. Intentá nuevamente."
                        )
                    }
                }
            )
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
        val prepared = com.comunidapp.app.domain.social.SocialMediaPipeline.prepare(
            context = context,
            source = sourceUri,
            expectVideo = expectVideo
        ) { progress ->
            _formState.update { it.copy(isLoading = true, uploadProgress = progress) }
        }.getOrElse { error ->
            _formState.update { PublishFormState(errorMessage = humanizePublishError(error)) }
            return
        }
        val purpose = if (type == PostType.STORY) FileAssetPurpose.STORY_MEDIA else FileAssetPurpose.REEL_MEDIA
        val resourceType = if (type == PostType.STORY) FileResourceType.STORY else FileResourceType.REEL
        val tempId = java.util.UUID.randomUUID().toString()
        _formState.update { it.copy(uploadProgress = 80) }
        when (
            val upload = uploadMedia(
                prepared.uri,
                purpose,
                author.id,
                tempId,
                resourceType,
                mimeType = prepared.mimeType,
                filename = prepared.filename
            )
        ) {
            is AppResult.Failure -> {
                _formState.update {
                    PublishFormState(errorMessage = FileUiErrorMapper.message(upload.error))
                }
                return
            }
            is AppResult.Success -> {
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
                    expiresAt = expiresAt,
                    localityId = localityId,
                    compositionJson = compositionJson,
                    mediaMime = prepared.mimeType
                )
                val created = if (type == PostType.STORY) {
                    feedRepository.addStory(post, upload.data.assetId)
                } else {
                    feedRepository.addReel(post, upload.data.assetId)
                }
                created.onSuccess { contentId ->
                    feedRepository.refreshStories()
                    feedRepository.refreshPosts()
                    val petName = petId?.let { DataProvider.petRepository.getPetById(it)?.name }
                    if (!petId.isNullOrBlank() && !petName.isNullOrBlank()) {
                        val kind = if (type == PostType.STORY) {
                            com.comunidapp.app.domain.social.SocialContentKind.STORY
                        } else {
                            com.comunidapp.app.domain.social.SocialContentKind.REEL
                        }
                        _formState.update {
                            PublishFormState(
                                bitacoraPrompt = BitacoraPrompt(
                                    kind = kind,
                                    petId = petId,
                                    petName = petName,
                                    contentId = contentId,
                                    compositionJson = compositionJson,
                                    mediaUrl = upload.data.assetId
                                )
                            )
                        }
                    } else {
                        _formState.update { PublishFormState(isSuccess = true) }
                    }
                }.onFailure { error ->
                    _formState.update {
                        PublishFormState(errorMessage = humanizePublishError(error))
                    }
                }
            }
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
        filename: String = "media.jpg"
    ): AppResult<PreparedFileUpload> =
        DataProvider.fileUploadCoordinator.startUpload(
            uriString = uri.toString(),
            request = FileUploadRequest(
                purpose = purpose,
                owner = FileAssetOwner.User(actorUserId),
                resourceRef = FileResourceRef(resourceType, resourceId),
                originalFilename = filename,
                declaredMimeType = mimeType,
                sizeBytes = 1L,
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
