package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PlatformRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel(
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    val notifications: StateFlow<List<AppNotification>> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else platformRepository.observeNotifications(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = notifications
        .map { list -> list.count { it.isUnread } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markRead(id: String) {
        viewModelScope.launch {
            platformRepository.markNotificationRead(id)
        }
    }

    fun markAllRead() {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            platformRepository.markAllNotificationsRead(userId)
        }
    }
}
