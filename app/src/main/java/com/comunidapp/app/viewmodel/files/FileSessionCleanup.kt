package com.comunidapp.app.viewmodel.files

import com.comunidapp.app.data.files.FileDisplayResolver
import com.comunidapp.app.data.files.FileUploadCoordinator

object FileSessionCleanup {
    @Volatile
    private var coordinator: FileUploadCoordinator? = null

    @Volatile
    private var displayResolver: FileDisplayResolver? = null

    fun register(
        coordinator: FileUploadCoordinator? = null,
        displayResolver: FileDisplayResolver? = null
    ) {
        if (coordinator != null) this.coordinator = coordinator
        if (displayResolver != null) this.displayResolver = displayResolver
    }

    fun clear() {
        coordinator?.clearAllSensitiveState()
        displayResolver?.clearTemporaryState()
    }

    fun resetForTests() {
        clear()
        coordinator = null
        displayResolver = null
    }
}
