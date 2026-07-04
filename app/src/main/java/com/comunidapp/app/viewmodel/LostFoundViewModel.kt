package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.MockLostFoundRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LostFoundViewModel(
    lostFoundRepository: LostFoundRepository = MockLostFoundRepository()
) : ViewModel() {

    val posts: StateFlow<List<LostFoundPost>> = lostFoundRepository.observeLostFoundPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
