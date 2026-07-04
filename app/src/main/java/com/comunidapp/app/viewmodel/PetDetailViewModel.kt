package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.repository.MockPetRepository
import com.comunidapp.app.data.repository.PetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PetDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val petRepository: PetRepository = MockPetRepository()
) : ViewModel() {

    private val petId: String = savedStateHandle["petId"] ?: ""

    private val _pet = MutableStateFlow<Pet?>(null)
    val pet: StateFlow<Pet?> = _pet.asStateFlow()

    init {
        _pet.value = petRepository.getPetById(petId)
    }
}
