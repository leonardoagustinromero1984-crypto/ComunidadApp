package com.comunidapp.shared.lostfound

import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.location.ApproximateLocation
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.poc.m08.model.ImagePickResult
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import com.comunidapp.shared.ui.ErrorSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LostFoundPublishUiState {
    data object Idle : LostFoundPublishUiState
    data object Editing : LostFoundPublishUiState
    data object Validating : LostFoundPublishUiState
    data object Publishing : LostFoundPublishUiState
    data class Success(
        val id: LostFoundId,
        val publicCode: String?,
        val mediaDeferred: Boolean
    ) : LostFoundPublishUiState

    data class Error(val message: String) : LostFoundPublishUiState
}

data class LostFoundPublishFormState(
    val type: LostFoundCaseType = LostFoundCaseType.LOST,
    val displayName: String = "",
    val speciesLabel: String = "Perro",
    val description: String = "",
    val locality: String = "",
    val contactNote: String = "",
    val media: FileRef? = null,
    val mediaLabel: String? = null,
    val ui: LostFoundPublishUiState = LostFoundPublishUiState.Editing
) {
    val canSubmit: Boolean
        get() = ui !is LostFoundPublishUiState.Publishing &&
            ui !is LostFoundPublishUiState.Validating &&
            ui !is LostFoundPublishUiState.Success
}

class LostFoundPublishViewModelShared(
    private val repository: LostFoundRepository,
    private val imagePicker: ImagePicker? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _form = MutableStateFlow(LostFoundPublishFormState())
    val form: StateFlow<LostFoundPublishFormState> = _form.asStateFlow()

    fun setType(type: LostFoundCaseType) {
        _form.update { it.copy(type = type, ui = LostFoundPublishUiState.Editing) }
    }

    fun setDisplayName(value: String) {
        _form.update { it.copy(displayName = value, ui = LostFoundPublishUiState.Editing) }
    }

    fun setSpeciesLabel(value: String) {
        _form.update { it.copy(speciesLabel = value, ui = LostFoundPublishUiState.Editing) }
    }

    fun setDescription(value: String) {
        _form.update { it.copy(description = value, ui = LostFoundPublishUiState.Editing) }
    }

    fun setLocality(value: String) {
        _form.update { it.copy(locality = value, ui = LostFoundPublishUiState.Editing) }
    }

    fun setContactNote(value: String) {
        _form.update { it.copy(contactNote = value, ui = LostFoundPublishUiState.Editing) }
    }

    fun clearMedia() {
        _form.update {
            it.copy(media = null, mediaLabel = null, ui = LostFoundPublishUiState.Editing)
        }
    }

    fun pickMedia() {
        val picker = imagePicker ?: return
        scope.launch {
            when (val result = picker.pickImage()) {
                is ImagePickResult.Success -> _form.update {
                    it.copy(
                        media = result.file,
                        mediaLabel = result.file.name,
                        ui = LostFoundPublishUiState.Editing
                    )
                }
                ImagePickResult.Cancelled -> Unit
                is ImagePickResult.Failure -> _form.update {
                    it.copy(
                        ui = LostFoundPublishUiState.Error(
                            ErrorSanitizer.sanitize(IllegalStateException(result.message))
                        )
                    )
                }
            }
        }
    }

    fun publish() {
        val current = _form.value
        if (!current.canSubmit) return
        // Bloqueo síncrono anti doble-submit antes del coroutine.
        _form.update { it.copy(ui = LostFoundPublishUiState.Validating) }
        scope.launch {
            val snapshot = _form.value
            val locality = snapshot.locality.trim()
            if (locality.isBlank()) {
                _form.update {
                    it.copy(ui = LostFoundPublishUiState.Error("Indicá una zona o localidad aproximada."))
                }
                return@launch
            }
            val draft = LostFoundDraft(
                type = snapshot.type,
                displayName = snapshot.displayName.trim().ifBlank { null },
                speciesLabel = snapshot.speciesLabel.trim(),
                description = snapshot.description,
                approximateLocation = ApproximateLocation(locality),
                contactNote = snapshot.contactNote.trim().ifBlank { null }
            )
            val validation = LostFoundDraftValidator.validate(draft)
            if (validation.isFailure) {
                _form.update {
                    it.copy(
                        ui = LostFoundPublishUiState.Error(
                            ErrorSanitizer.sanitize(validation.exceptionOrNull()!!)
                        )
                    )
                }
                return@launch
            }
            _form.update { it.copy(ui = LostFoundPublishUiState.Publishing) }
            when (val result = repository.publish(LostFoundPublishRequest(draft, snapshot.media))) {
                is LostFoundPublishResult.Success -> {
                    _form.update {
                        LostFoundPublishFormState(
                            ui = LostFoundPublishUiState.Success(
                                id = result.id,
                                publicCode = result.publicCode,
                                mediaDeferred = result.mediaDeferred
                            )
                        )
                    }
                }
                is LostFoundPublishResult.ValidationError ->
                    _form.update { it.copy(ui = LostFoundPublishUiState.Error(result.message)) }
                is LostFoundPublishResult.Unauthenticated ->
                    _form.update { it.copy(ui = LostFoundPublishUiState.Error(result.message)) }
                is LostFoundPublishResult.PermissionDenied ->
                    _form.update { it.copy(ui = LostFoundPublishUiState.Error(result.message)) }
                is LostFoundPublishResult.NetworkError ->
                    _form.update { it.copy(ui = LostFoundPublishUiState.Error(result.message)) }
                is LostFoundPublishResult.MediaError ->
                    _form.update { it.copy(ui = LostFoundPublishUiState.Error(result.message)) }
                is LostFoundPublishResult.BackendError ->
                    _form.update { it.copy(ui = LostFoundPublishUiState.Error(result.message)) }
            }
        }
    }

    fun clear() {
        scope.cancel()
    }
}
