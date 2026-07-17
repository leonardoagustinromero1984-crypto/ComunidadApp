package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.NotificationInboxRepository
import com.comunidapp.app.data.repository.PlatformRepository
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val notificationInboxRepository: NotificationInboxRepository = DataProvider.notificationInboxRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    val notifications: StateFlow<List<AppNotification>> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else {
                refreshTick.flatMapLatest {
                    platformRepository.observeNotifications(user.id)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = notifications
        .map { list -> list.count { it.isUnread } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun refreshFromSignal() {
        refreshTick.value = refreshTick.value + 1
    }

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

    fun archive(id: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            notificationInboxRepository.archive(userId, id, Instant.now())
            refreshFromSignal()
        }
    }

    fun deleteLogical(id: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            notificationInboxRepository.deleteLogical(userId, id, Instant.now())
            refreshFromSignal()
        }
    }
}
