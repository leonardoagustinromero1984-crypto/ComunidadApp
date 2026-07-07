package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionState {
    Loading,
    LoggedOut,
    LoggedIn
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _sessionState = MutableStateFlow(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState()
                .flatMapLatest { authUser ->
                    if (authUser == null) {
                        _sessionState.update { SessionState.LoggedOut }
                        flowOf(null)
                    } else {
                        _sessionState.update { SessionState.LoggedIn }
                        userRepository.observeUser(authUser.id).map { profile -> profile ?: authUser }
                    }
                }
                .collect { user ->
                    _currentUser.value = user
                    if (_sessionState.value == SessionState.Loading) {
                        _sessionState.value = if (user != null) SessionState.LoggedIn else SessionState.LoggedOut
                    }
                }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}
