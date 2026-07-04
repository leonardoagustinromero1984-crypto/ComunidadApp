package com.comunidapp.app.viewmodel

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
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockFeedRepository
import com.comunidapp.app.data.repository.MockLostFoundRepository
import kotlinx.coroutines.delay
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
    private val feedRepository: FeedRepository = MockFeedRepository(),
    private val adoptionRepository: AdoptionRepository = MockAdoptionRepository(),
    private val lostFoundRepository: LostFoundRepository = MockLostFoundRepository()
) : ViewModel() {

    private val _formState = MutableStateFlow(PublishFormState())
    val formState: StateFlow<PublishFormState> = _formState.asStateFlow()

    fun publishGeneral(title: String, content: String, location: String) {
        if (title.isBlank() || content.isBlank()) {
            _formState.update { it.copy(errorMessage = "Título y contenido son requeridos") }
            return
        }
        viewModelScope.launch {
            _formState.update { PublishFormState(isLoading = true) }
            delay(600)
            val user = MockData.currentUser
            feedRepository.addFeedPost(
                FeedPost(
                    id = "feed_${System.currentTimeMillis()}",
                    authorId = user.id,
                    authorName = user.name,
                    authorImageUrl = user.profileImageUrl,
                    type = PostType.GENERAL,
                    title = title.trim(),
                    content = content.trim(),
                    locationText = location.trim().ifBlank { null },
                    date = "Ahora"
                )
            )
            _formState.update { PublishFormState(isSuccess = true) }
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
            delay(600)
            adoptionRepository.addAdoptionPost(
                AdoptionPost(
                    id = "adopt_${System.currentTimeMillis()}",
                    shelterName = MockData.currentUser.name,
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
            feedRepository.addFeedPost(
                FeedPost(
                    id = "feed_${System.currentTimeMillis()}",
                    authorId = MockData.currentUser.id,
                    authorName = MockData.currentUser.name,
                    type = PostType.ADOPTION,
                    title = "$name busca familia",
                    content = description.trim(),
                    locationText = location.trim(),
                    date = "Ahora"
                )
            )
            _formState.update { PublishFormState(isSuccess = true) }
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
            delay(600)
            val user = MockData.currentUser
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            lostFoundRepository.addLostFoundPost(
                LostFoundPost(
                    id = "lf_${System.currentTimeMillis()}",
                    authorId = user.id,
                    authorName = user.name,
                    type = type,
                    petName = petName.trim().ifBlank { null },
                    species = species,
                    location = location.trim(),
                    description = description.trim(),
                    contactInfo = contactInfo.trim(),
                    date = date
                )
            )
            feedRepository.addFeedPost(
                FeedPost(
                    id = "feed_${System.currentTimeMillis()}",
                    authorId = user.id,
                    authorName = user.name,
                    type = PostType.LOST_FOUND,
                    title = if (type == LostFoundType.LOST) "Mascota perdida" else "Mascota encontrada",
                    content = description.trim(),
                    locationText = location.trim(),
                    date = "Ahora"
                )
            )
            _formState.update { PublishFormState(isSuccess = true) }
        }
    }

    fun resetFormState() {
        _formState.value = PublishFormState()
    }
}
