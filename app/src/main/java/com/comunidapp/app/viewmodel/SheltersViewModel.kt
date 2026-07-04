package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.repository.MockShelterRepository
import com.comunidapp.app.data.repository.ShelterRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SheltersViewModel(
    shelterRepository: ShelterRepository = MockShelterRepository()
) : ViewModel() {

    val shelters: StateFlow<List<Shelter>> = shelterRepository.observeShelters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
