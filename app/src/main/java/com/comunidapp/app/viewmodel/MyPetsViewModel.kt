package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class MyPetsViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val petRepository: PetRepository = DataProvider.petRepository
) : ViewModel() {

    val pets: StateFlow<List<Pet>> = authRepository.observeAuthState()
        .flatMapLatest { authUser ->
            if (authUser == null) {
                flowOf(emptyList())
            } else {
                petRepository.observePetsForOwner(authUser.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
