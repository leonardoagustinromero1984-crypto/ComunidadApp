package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.OrganizationPermissionRepository
import com.comunidapp.app.data.repository.OrganizationRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationType
import com.comunidapp.app.domain.organization.OrganizationValidationException
import com.comunidapp.app.domain.organization.OrganizationValidators
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.organization.PublicOrganization
import com.comunidapp.app.domain.organization.UpdateOrganizationCommand
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyOrganizationsUiState(
    val isLoading: Boolean = true,
    val organizations: List<Organization> = emptyList(),
    val errorMessage: String? = null
)

class MyOrganizationsViewModel(
    private val organizationRepository: OrganizationRepository = DataProvider.organizationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyOrganizationsUiState())
    val uiState: StateFlow<MyOrganizationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val list = organizationRepository.getMyOrganizations()
                _uiState.update { it.copy(isLoading = false, organizations = list) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "No se pudieron cargar las organizaciones"
                    )
                }
            }
        }
    }
}

data class CreateOrganizationUiState(
    val publicName: String = "",
    val legalName: String = "",
    val slug: String = "",
    val type: OrganizationType = OrganizationType.SHELTER,
    val typeDescription: String = "",
    val city: String = "",
    val province: String = "",
    val countryCode: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val createdOrganizationId: String? = null
)

class CreateOrganizationViewModel(
    private val organizationRepository: OrganizationRepository = DataProvider.organizationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateOrganizationUiState())
    val uiState: StateFlow<CreateOrganizationUiState> = _uiState.asStateFlow()

    fun onPublicNameChange(value: String) =
        _uiState.update { it.copy(publicName = value, errorMessage = null) }

    fun onLegalNameChange(value: String) =
        _uiState.update { it.copy(legalName = value, errorMessage = null) }

    fun onSlugChange(value: String) =
        _uiState.update { it.copy(slug = value, errorMessage = null) }

    fun onTypeChange(value: OrganizationType) =
        _uiState.update { it.copy(type = value, errorMessage = null) }

    fun onTypeDescriptionChange(value: String) =
        _uiState.update { it.copy(typeDescription = value, errorMessage = null) }

    fun onCityChange(value: String) =
        _uiState.update { it.copy(city = value, errorMessage = null) }

    fun onProvinceChange(value: String) =
        _uiState.update { it.copy(province = value, errorMessage = null) }

    fun onCountryCodeChange(value: String) =
        _uiState.update { it.copy(countryCode = value.uppercase(), errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        val draft = OrganizationValidators.validateCreate(
            legalName = state.legalName,
            publicName = state.publicName,
            type = state.type,
            typeDescription = state.typeDescription.ifBlank { null },
            slugRaw = state.slug,
            countryCode = state.countryCode.ifBlank { null },
            province = state.province.ifBlank { null },
            city = state.city.ifBlank { null }
        ).getOrElse { error ->
            val message = (error as? OrganizationValidationException)?.error?.userMessage
                ?: error.message
                ?: "Datos inválidos"
            _uiState.update { it.copy(errorMessage = message) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            organizationRepository.createOrganization(draft)
                .onSuccess { org ->
                    _uiState.update {
                        it.copy(isSaving = false, createdOrganizationId = org.id.value)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudo crear la organización"
                        )
                    }
                }
        }
    }

    fun clearCreated() {
        _uiState.update { it.copy(createdOrganizationId = null) }
    }
}

data class EditOrganizationUiState(
    val isLoading: Boolean = true,
    val canEdit: Boolean = false,
    val organizationId: String = "",
    val publicName: String = "",
    val description: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val showEmail: Boolean = false,
    val showPhone: Boolean = false,
    val verificationStatus: OrganizationVerificationStatus =
        OrganizationVerificationStatus.NOT_REQUESTED,
    val canRequestVerification: Boolean = false,
    val logoPath: String? = null,
    val logoUrl: String? = null,
    val pendingLogoUri: android.net.Uri? = null,
    val isSaving: Boolean = false,
    val isRequestingVerification: Boolean = false,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
)

class EditOrganizationViewModel(
    private val organizationId: String,
    private val organizationRepository: OrganizationRepository = DataProvider.organizationRepository,
    private val permissionRepository: OrganizationPermissionRepository =
        DataProvider.organizationPermissionRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditOrganizationUiState(organizationId = organizationId))
    val uiState: StateFlow<EditOrganizationUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, canEdit = false, errorMessage = null) }
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update {
                    it.copy(isLoading = false, canEdit = false, errorMessage = "No hay sesión activa")
                }
                return@launch
            }
            val accountStatus = parseAccountStatus(
                userRepository.getUser(authUser.id)?.accountStatus ?: authUser.accountStatus
            )
            // Deny while loading / on error.
            val allowed = permissionRepository.hasPermission(
                organizationId = OrganizationId(organizationId),
                userId = authUser.id,
                accountStatus = accountStatus,
                permission = OrganizationPermissionCode.ORGANIZATION_UPDATE
            )
            if (!allowed) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        canEdit = false,
                        errorMessage = "No tenés permiso para editar esta organización"
                    )
                }
                return@launch
            }
            val org = organizationRepository.getById(OrganizationId(organizationId))
            if (org == null) {
                _uiState.update {
                    it.copy(isLoading = false, canEdit = false, errorMessage = "Organización no encontrada")
                }
                return@launch
            }
            val canRequest = permissionRepository.hasPermission(
                organizationId = org.id,
                userId = authUser.id,
                accountStatus = accountStatus,
                permission = OrganizationPermissionCode.ORGANIZATION_REQUEST_VERIFICATION
            )
            var logoUrl: String? = null
            org.logoPath?.let { path ->
                DataProvider.organizationMediaStorage
                    ?.createSignedUrl(path)
                    ?.onSuccess { logoUrl = it }
            }
            _uiState.update {
                EditOrganizationUiState(
                    isLoading = false,
                    canEdit = true,
                    organizationId = org.id.value,
                    publicName = org.publicName,
                    description = org.description.orEmpty(),
                    contactEmail = org.institutionalEmail.orEmpty(),
                    contactPhone = org.institutionalPhone.orEmpty(),
                    showEmail = org.contactVisibility.showEmail,
                    showPhone = org.contactVisibility.showPhone,
                    verificationStatus = org.verificationStatus,
                    canRequestVerification = canRequest &&
                        org.verificationStatus in setOf(
                            OrganizationVerificationStatus.NOT_REQUESTED,
                            OrganizationVerificationStatus.REJECTED,
                            OrganizationVerificationStatus.EXPIRED
                        ),
                    logoPath = org.logoPath,
                    logoUrl = logoUrl
                )
            }
        }
    }

    fun onDescriptionChange(value: String) =
        _uiState.update { it.copy(description = value, errorMessage = null) }

    fun onContactEmailChange(value: String) =
        _uiState.update { it.copy(contactEmail = value, errorMessage = null) }

    fun onContactPhoneChange(value: String) =
        _uiState.update { it.copy(contactPhone = value, errorMessage = null) }

    fun onShowEmailChange(value: Boolean) =
        _uiState.update { it.copy(showEmail = value, errorMessage = null) }

    fun onShowPhoneChange(value: Boolean) =
        _uiState.update { it.copy(showPhone = value, errorMessage = null) }

    fun onLogoSelected(uri: android.net.Uri?) =
        _uiState.update { it.copy(pendingLogoUri = uri, errorMessage = null) }

    fun save() {
        val state = _uiState.value
        if (!state.canEdit || state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccess = false) }
            var logoPath = state.logoPath
            state.pendingLogoUri?.let { uri ->
                val media = DataProvider.organizationMediaStorage
                if (media != null) {
                    media.uploadLogo(state.organizationId, uri)
                        .onSuccess { path -> logoPath = path }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    errorMessage = error.message ?: "No se pudo subir el logo"
                                )
                            }
                            return@launch
                        }
                }
            }
            organizationRepository.updateMyOrganization(
                UpdateOrganizationCommand(
                    organizationId = OrganizationId(state.organizationId),
                    description = state.description,
                    contactEmail = state.contactEmail,
                    contactPhone = state.contactPhone,
                    contactEmailPublic = state.showEmail,
                    contactPhonePublic = state.showPhone,
                    logoPath = logoPath
                )
            ).onSuccess { org ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        logoPath = org.logoPath,
                        pendingLogoUri = null,
                        verificationStatus = org.verificationStatus,
                        canRequestVerification = it.canRequestVerification &&
                            org.verificationStatus in setOf(
                                OrganizationVerificationStatus.NOT_REQUESTED,
                                OrganizationVerificationStatus.REJECTED,
                                OrganizationVerificationStatus.EXPIRED
                            )
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "No se pudo guardar"
                    )
                }
            }
        }
    }

    fun requestVerification() {
        val state = _uiState.value
        if (!state.canEdit || !state.canRequestVerification) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingVerification = true, errorMessage = null) }
            organizationRepository.requestVerification(OrganizationId(state.organizationId))
                .onSuccess { org ->
                    _uiState.update {
                        it.copy(
                            isRequestingVerification = false,
                            verificationStatus = org.verificationStatus,
                            canRequestVerification = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRequestingVerification = false,
                            errorMessage = error.message ?: "No se pudo solicitar verificación"
                        )
                    }
                }
        }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    private fun parseAccountStatus(raw: String): AccountStatus =
        runCatching { AccountStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(AccountStatus.ACTIVE)

    companion object {
        fun factory(organizationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EditOrganizationViewModel(organizationId = organizationId) as T
                }
            }
    }
}

data class PublicOrganizationUiState(
    val isLoading: Boolean = true,
    val organization: PublicOrganization? = null,
    val errorMessage: String? = null
)

class PublicOrganizationViewModel(
    private val slug: String,
    private val organizationRepository: OrganizationRepository = DataProvider.organizationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublicOrganizationUiState())
    val uiState: StateFlow<PublicOrganizationUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            organizationRepository.getPublicBySlug(slug)
                .onSuccess { org ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            organization = org,
                            errorMessage = if (org == null) "Organización no encontrada" else null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "No se pudo cargar"
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(slug: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PublicOrganizationViewModel(slug = slug) as T
                }
            }
    }
}
