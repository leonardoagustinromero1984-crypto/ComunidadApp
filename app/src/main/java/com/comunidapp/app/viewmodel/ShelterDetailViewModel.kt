package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.ShelterRepository
import com.comunidapp.app.domain.m16.M11M16ShelterCompatibilityAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShelterDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val shelterRepository: ShelterRepository = DataProvider.shelterRepository,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository
) : ViewModel() {

    private val shelterId: String = savedStateHandle["shelterId"] ?: ""

    private val _shelter = MutableStateFlow<Shelter?>(null)
    val shelter: StateFlow<Shelter?> = _shelter.asStateFlow()

    private val _adoptions = MutableStateFlow<List<com.comunidapp.app.data.model.AdoptionPost>>(emptyList())
    val adoptions: StateFlow<List<com.comunidapp.app.data.model.AdoptionPost>> = _adoptions.asStateFlow()

    private val _m16ShelterId = MutableStateFlow<String?>(null)
    val m16ShelterId: StateFlow<String?> = _m16ShelterId.asStateFlow()

    private val _legacyCompatLabel = MutableStateFlow<String?>(null)
    val legacyCompatLabel: StateFlow<String?> = _legacyCompatLabel.asStateFlow()

    init {
        val data = shelterRepository.getShelterById(shelterId)
        _shelter.value = data
        _adoptions.value = adoptionRepository.getAdoptionsByShelter(shelterId)
        data?.let { shelter ->
            _legacyCompatLabel.value = M11M16ShelterCompatibilityAdapter.legacyFlowLabel(shelter)
            viewModelScope.launch {
                _m16ShelterId.value = M11M16ShelterCompatibilityAdapter.resolveM16PublicShelterId(
                    shelter,
                    DataProvider.m16ShelterRepository
                )
            }
        }
    }
}
