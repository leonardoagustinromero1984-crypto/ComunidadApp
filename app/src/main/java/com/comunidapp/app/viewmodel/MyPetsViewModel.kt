package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.pets.IncomingCareTransferInbox
import com.comunidapp.app.domain.pets.PetManagementContext
import com.comunidapp.app.domain.pets.PetTransfer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MyPetsViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val incomingInbox: IncomingCareTransferInbox = DataProvider.incomingCareTransferInbox
) : ViewModel() {

    val incomingTransfers: StateFlow<List<PetTransfer>> = incomingInbox.items
    val acceptNotice: StateFlow<String?> = incomingInbox.notice

    val pets: StateFlow<List<Pet>> = combine(
        authRepository.observeAuthState(),
        OperationalContextProvider.active
    ) { authUser, context -> authUser to context }
        .flatMapLatest { (authUser, context) ->
            if (authUser == null) {
                flowOf(emptyList())
            } else {
                petRepository.observePetsForOwner(authUser.id).map { listed ->
                    PetManagementContext.filter(listed, context, authUser.id)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastVisibleRefreshAtMs: Long = 0L

    init {
        onVisible(force = true)
    }

    fun onVisible(force: Boolean = false) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (!force && now - lastVisibleRefreshAtMs < 8_000L) {
                return@launch
            }
            lastVisibleRefreshAtMs = now
            val probe = com.comunidapp.app.domain.perf.ScreenPerfProbe.begin("my_pets")
            if (pets.value.isNotEmpty()) probe.markFirstContent()
            incomingInbox.refresh("mypets_visible")
            probe.network { petRepository.refreshAccessiblePets() }
            if (pets.value.isNotEmpty()) probe.markFirstContent()
            probe.finish("pets=${pets.value.size}")
        }
    }

    fun acceptIncoming(transfer: PetTransfer) {
        viewModelScope.launch {
            incomingInbox.accept(transfer)
            lastVisibleRefreshAtMs = 0L
            petRepository.refreshAccessiblePets()
        }
    }

    fun rejectIncoming(transfer: PetTransfer) {
        viewModelScope.launch { incomingInbox.reject(transfer) }
    }

    fun consumeAcceptNotice() {
        incomingInbox.consumeNotice()
    }
}
