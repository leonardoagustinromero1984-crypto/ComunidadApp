package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdoptionDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val adoptionRepository: AdoptionRepository = MockAdoptionRepository()
) : ViewModel() {

    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""

    private val _post = MutableStateFlow<AdoptionPost?>(null)
    val post: StateFlow<AdoptionPost?> = _post.asStateFlow()

    init {
        _post.value = adoptionRepository.getAdoptionPostById(adoptionId)
    }
}
