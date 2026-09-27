package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.repository.CanonicalLostFoundRepository
import com.comunidapp.app.data.repository.CanonicalVerificationRepository
import com.comunidapp.app.data.repository.LeoverVerificationRequests
import com.comunidapp.app.data.repository.ResponderBaseSnapshot
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.verification.VerificationDisplayPolicy
import com.comunidapp.app.ui.components.leo.LeoRequiredField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeoverVerificationUiState(
    val status: String? = null,
    val note: String? = null,
    val requestId: String? = null,
    val terms: Boolean = false,
    val evidence: String = "",
    val pin: LeoVerGeoPoint? = null,
    val address: String? = null,
    val storedLocation: Boolean = false,
    val message: String? = null,
    val fieldErrors: List<String> = emptyList(),
    val submitting: Boolean = false
) {
    val pendingOrVerified: Boolean
        get() = status?.uppercase() in setOf("PENDING", "VERIFIED", "SUSPENDED")
}

interface ResponderBaseGateway {
    suspend fun get(organizationId: String?): Result<ResponderBaseSnapshot>
    suspend fun upsert(
        latitude: Double,
        longitude: Double,
        organizationId: String?,
        address: String?
    ): Result<Unit>
}

object CanonicalResponderBaseGateway : ResponderBaseGateway {
    override suspend fun get(organizationId: String?): Result<ResponderBaseSnapshot> =
        CanonicalLostFoundRepository.getMyResponderBase(organizationId)

    override suspend fun upsert(
        latitude: Double,
        longitude: Double,
        organizationId: String?,
        address: String?
    ): Result<Unit> = CanonicalLostFoundRepository.upsertResponderBase(
        latitude,
        longitude,
        organizationId,
        address
    )
}

class LeoverVerificationRequestViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: LeoverVerificationRequests = CanonicalVerificationRepository(),
    private val responderBase: ResponderBaseGateway = CanonicalResponderBaseGateway
) : ViewModel() {
    private val functionCode: String = savedStateHandle.get<String>("functionCode").orEmpty()
    private val organizationId: String? = savedStateHandle.get<String>("organizationId")

    private val _ui = MutableStateFlow(LeoverVerificationUiState())
    val ui: StateFlow<LeoverVerificationUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun updateTerms(value: Boolean) = _ui.update { it.copy(terms = value) }
    fun updateEvidence(value: String) = _ui.update { it.copy(evidence = value) }
    fun updatePin(point: LeoVerGeoPoint) = _ui.update { it.copy(pin = point) }
    fun updateAddress(label: String?) = _ui.update { it.copy(address = label) }

    fun refresh() {
        viewModelScope.launch {
            repo.listMine().onSuccess { rows ->
                val row = rows.firstOrNull { row ->
                    row.functionCode.equals(functionCode, true) &&
                        (organizationId.isNullOrBlank() || row.organizationId == organizationId)
                }
                _ui.update {
                    it.copy(
                        status = row?.status,
                        note = row?.reviewNote,
                        requestId = row?.id
                    )
                }
            }
            responderBase.get(organizationId).onSuccess { snap ->
                _ui.update {
                    it.copy(
                        storedLocation = snap.hasBaseLocation,
                        pin = if (snap.latitude != null && snap.longitude != null) {
                            LeoVerGeoPoint(snap.latitude, snap.longitude)
                        } else {
                            it.pin
                        },
                        address = snap.address ?: it.address
                    )
                }
            }
        }
    }

    fun submit(
        needsLocation: Boolean,
        needsOrg: Boolean
    ) {
        val state = _ui.value
        val missing = buildList {
            if (needsLocation && state.pin == null && !state.storedLocation && state.status.isNullOrBlank()) {
                add(LeoRequiredField.label("Ubicación"))
            }
            if (!state.terms) add(LeoRequiredField.label("Aceptar términos"))
            if (needsOrg && organizationId.isNullOrBlank()) add("Organización")
        }
        if (missing.isNotEmpty()) {
            _ui.update { it.copy(fieldErrors = missing, message = null) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true, fieldErrors = emptyList(), message = null) }
            val pin = _ui.value.pin
            if (pin != null) {
                responderBase.upsert(
                    pin.latitude,
                    pin.longitude,
                    organizationId,
                    _ui.value.address
                ).onSuccess {
                    _ui.update { it.copy(storedLocation = true) }
                }.onFailure {
                    _ui.update {
                        it.copy(
                            submitting = false,
                            fieldErrors = listOf(LeoRequiredField.label("Ubicación")),
                            message = "Cargá la ubicación base."
                        )
                    }
                    return@launch
                }
            } else if (!_ui.value.storedLocation) {
                _ui.update {
                    it.copy(
                        submitting = false,
                        fieldErrors = listOf(LeoRequiredField.label("Ubicación")),
                        message = "Cargá la ubicación base."
                    )
                }
                return@launch
            }
            repo.request(functionCode, _ui.value.terms, _ui.value.evidence, organizationId)
                .onSuccess { id ->
                    _ui.update { state ->
                        state.copy(
                            submitting = false,
                            status = "PENDING",
                            requestId = id,
                            message = VerificationDisplayPolicy.PENDING_COPY
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    val raw = error.message.orEmpty()
                    val missingFromBackend = buildList {
                        if (raw.contains("PROFILE_INCOMPLETE")) add(LeoRequiredField.label("Perfil"))
                        if (raw.contains("CONTACT_REQUIRED")) add(LeoRequiredField.label("Teléfono o email"))
                        if (raw.contains("TERMS_REQUIRED")) add(LeoRequiredField.label("Aceptar términos"))
                        if (raw.contains("BASE_LOCATION_REQUIRED")) add(LeoRequiredField.label("Ubicación"))
                        if (raw.contains("ORGANIZATION_REQUIRED") || raw.contains("ORGANIZATION_INCOMPLETE")) {
                            add("Organización")
                        }
                    }
                    _ui.update {
                        it.copy(
                            submitting = false,
                            fieldErrors = missingFromBackend,
                            message = if (missingFromBackend.isNotEmpty()) {
                                "Para solicitar verificación completá:\n" +
                                    missingFromBackend.joinToString("\n") { line -> "- $line" }
                            } else {
                                "No se pudo enviar la solicitud."
                            }
                        )
                    }
                }
        }
    }
}
