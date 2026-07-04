package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionState {
    Loading,
    LoggedOut,
    LoggedIn
}

class SessionViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _sessionState = MutableStateFlow(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                _sessionState.update {
                    if (user != null) SessionState.LoggedIn else SessionState.LoggedOut
                }
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}
