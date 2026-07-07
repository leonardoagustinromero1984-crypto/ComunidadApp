package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionPost
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
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.UserRepository
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
    private val lostFoundRepository: LostFoundRepository = DataProvider.lostFoundRepository
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

    fun publishAdoption(
        name: String,
        species: PetSpecies,
        sex: PetSex,
        ageYears: Int,
        size: PetSize,
        location: String,
        description: String
    ) {
        if (name.isBlank() || description.isBlank() || location.isBlank()) {
            _formState.update { it.copy(errorMessage = "Completá los campos obligatorios") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            resolveAuthor()
                .onSuccess { author ->
                    if (!RolePermissions.canPublishAdoption(author.accountType)) {
                        _formState.update {
                            PublishFormState(errorMessage = "Solo refugios pueden publicar adopciones")
                        }
                        return@launch
                    }
                    adoptionRepository.addAdoptionPost(
                        AdoptionPost(
                            id = "adopt_${System.currentTimeMillis()}",
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
                    )
                    publishFeedPost(
                        author = author,
                        type = PostType.ADOPTION,
                        title = "$name busca familia",
                        content = description.trim(),
                        locationText = location.trim(),
                        imageUri = null
                    )
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
        contactInfo: String
    ) {
        if (location.isBlank() || description.isBlank() || contactInfo.isBlank()) {
            _formState.update { it.copy(errorMessage = "Completá los campos obligatorios") }
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
                    lostFoundRepository.addLostFoundPost(
                        LostFoundPost(
                            id = "lf_${System.currentTimeMillis()}",
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
                    )
                    publishFeedPost(
                        author = author,
                        type = PostType.LOST_FOUND,
                        title = if (type == LostFoundType.LOST) "Mascota perdida" else "Mascota encontrada",
                        content = description.trim(),
                        locationText = location.trim(),
                        imageUri = null
                    )
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
                    val storage = DataProvider.storageService
                    if (storage != null) {
                        storage.uploadImage(StoragePaths.postImage(postId), imageUri)
                            .onSuccess { url ->
                                finalPost = finalPost.copy(imageUrl = url)
                                feedRepository.updateFeedPost(finalPost)
                            }
                            .onFailure { error ->
                                _formState.update {
                                    PublishFormState(errorMessage = error.message ?: "Error al subir imagen")
                                }
                                return
                            }
                    } else {
                        finalPost = finalPost.copy(imageUrl = imageUri.toString())
                        feedRepository.updateFeedPost(finalPost)
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
}
