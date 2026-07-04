package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockShelterRepository
import com.comunidapp.app.data.repository.ShelterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShelterDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val shelterRepository: ShelterRepository = MockShelterRepository(),
    private val adoptionRepository: AdoptionRepository = MockAdoptionRepository()
) : ViewModel() {

    private val shelterId: String = savedStateHandle["shelterId"] ?: ""

    private val _shelter = MutableStateFlow<Shelter?>(null)
    val shelter: StateFlow<Shelter?> = _shelter.asStateFlow()

    private val _adoptions = MutableStateFlow<List<AdoptionPost>>(emptyList())
    val adoptions: StateFlow<List<AdoptionPost>> = _adoptions.asStateFlow()

    init {
        _shelter.value = shelterRepository.getShelterById(shelterId)
        _adoptions.value = adoptionRepository.getAdoptionsByShelter(shelterId)
    }
}
