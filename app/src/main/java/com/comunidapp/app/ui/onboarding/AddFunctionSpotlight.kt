package com.comunidapp.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Survives recomposition in the same process. A completed onboarding never sets it.
 * Only the add-function row stays tappable while it is pending.
 */
object AddFunctionSpotlight {
    var pending by mutableStateOf(false)
        private set

    fun request() {
        pending = true
    }

    fun clear() {
        pending = false
    }

    fun restore(savedPending: Boolean) {
        pending = savedPending
    }
}
