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
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.PreparedFileUpload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PublishFormState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
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
                    if (type == PostType.PROMO && !RolePermissions.canPublishPromo(author.accountType)) {
                        _formState.update {
                            PublishFormState(errorMessage = "Tu tipo de cuenta no puede publicar promociones")
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
                    _formState.update { PublishFormState(errorMessage = error.message) }
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
                        status = AdoptionStatus.AVAILABLE
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
                                PublishFormState(errorMessage = error.message ?: "No se pudo publicar la adopción")
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = error.message) }
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
        imageUri: Uri?
    ) {
        if (location.isBlank() || description.isBlank() || contactInfo.isBlank()) {
            _formState.update { it.copy(errorMessage = "Completá los campos obligatorios") }
            return
        }
        if (imageUri == null) {
            _formState.update { it.copy(errorMessage = "La foto es obligatoria para alertas de perdidos/encontrados") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            resolveAuthor()
                .onSuccess { author ->
                    if (!RolePermissions.canPublishLostFound(author.accountType)) {
                        _formState.update {
                            PublishFormState(errorMessage = "Tu cuenta no puede publicar perdidos/encontrados")
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
                        date = date
                    )
                    lostFoundRepository.addLostFoundPost(lostPost)
                        .onSuccess { lostId ->
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
                            _formState.update {
                                PublishFormState(errorMessage = error.message ?: "No se pudo publicar el aviso")
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = error.message) }
                }
        }
    }

    fun resetFormState() {
        _formState.value = PublishFormState()
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
        imageUri: Uri?
    ) {
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
            updatedAt = now
        )

        feedRepository.addFeedPost(post)
            .onSuccess { postId ->
                var finalPost = post.copy(id = postId)
                if (imageUri != null) {
                    when (val upload = uploadMedia(
                        imageUri,
                        FileAssetPurpose.POST_MEDIA,
                        author.id,
                        postId,
                        FileResourceType.POST
                    )) {
                        is AppResult.Success -> {
                            finalPost = finalPost.copy(imageUrl = upload.data.assetId)
                            feedRepository.updateFeedPost(finalPost)
                        }
                        is AppResult.Failure -> {
                            _formState.update {
                                PublishFormState(
                                    errorMessage = FileUiErrorMapper.message(upload.error)
                                )
                            }
                            return
                        }
                    }
                }
                _formState.update { PublishFormState(isSuccess = true) }
            }
            .onFailure { error ->
                _formState.update {
                    PublishFormState(errorMessage = error.message ?: "No se pudo publicar")
                }
            }
    }

    private suspend fun uploadMedia(
        uri: Uri,
        purpose: FileAssetPurpose,
        actorUserId: String,
        resourceId: String,
        resourceType: FileResourceType
    ): AppResult<PreparedFileUpload> =
        DataProvider.fileUploadCoordinator.startUpload(
            uriString = uri.toString(),
            request = FileUploadRequest(
                purpose = purpose,
                owner = FileAssetOwner.User(actorUserId),
                resourceRef = FileResourceRef(resourceType, resourceId),
                originalFilename = "media.jpg",
                declaredMimeType = "image/jpeg",
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
                                PublishFormState(errorMessage = error.message ?: "No se pudo publicar")
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = error.message) }
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
                                PublishFormState(errorMessage = error.message ?: "No se pudo publicar")
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = error.message) }
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
                                PublishFormState(errorMessage = error.message ?: "No se pudo publicar")
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = error.message) }
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
                                PublishFormState(errorMessage = error.message ?: "No se pudo publicar")
                            }
                        }
                }
                .onFailure { error ->
                    _formState.update { PublishFormState(errorMessage = error.message) }
                }
        }
    }
}
