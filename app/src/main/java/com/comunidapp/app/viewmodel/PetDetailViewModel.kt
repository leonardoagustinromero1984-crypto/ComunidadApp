package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ClinicalRecordType
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetClinicalRecord
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.pets.PetPhotoResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Detalle de mascota + ciclo de vida (fallecimiento / restore / gating).
 * Autorización solo por PetAccessContext; nunca por ownerId.
 *
 * Carga explícita con loading/error; no trata “sin mascota” como loading eterno.
 */
class PetDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val petId: String = savedStateHandle["petId"] ?: ""

    private val currentUserId = MutableStateFlow<String?>(null)
    private val accessContext = MutableStateFlow<PetAccessContext?>(null)

    private val _pet = MutableStateFlow<Pet?>(null)
    val pet: StateFlow<Pet?> = _pet.asStateFlow()

    private val _photoDisplayUrl = MutableStateFlow<String?>(null)
    val photoDisplayUrl: StateFlow<String?> = _photoDisplayUrl.asStateFlow()

    private val _isPetLoading = MutableStateFlow(petId.isNotBlank())
    val isPetLoading: StateFlow<Boolean> = _isPetLoading.asStateFlow()

    private val _isHealthLoading = MutableStateFlow(petId.isNotBlank())
    val isHealthLoading: StateFlow<Boolean> = _isHealthLoading.asStateFlow()

    private val _healthLoadError = MutableStateFlow<String?>(null)
    val healthLoadError: StateFlow<String?> = _healthLoadError.asStateFlow()

    private val _petLoadError = MutableStateFlow<String?>(null)
    val petLoadError: StateFlow<String?> = _petLoadError.asStateFlow()

    private val _statusReasonCode = MutableStateFlow<String?>(null)
    val statusReasonCode: StateFlow<String?> = _statusReasonCode.asStateFlow()

    private val _principalDisplayName = MutableStateFlow<String?>(null)
    val principalDisplayName: StateFlow<String?> = _principalDisplayName.asStateFlow()

    private val _principalLoading = MutableStateFlow(petId.isNotBlank())
    val principalLoading: StateFlow<Boolean> = _principalLoading.asStateFlow()

    val clinicalRecords: StateFlow<List<PetClinicalRecord>> =
        if (petId.isBlank()) {
            flowOf(emptyList())
        } else {
            platformRepository.observeClinicalRecords(petId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val access: StateFlow<PetAccessContext?> = accessContext.asStateFlow()

    /** Capability-based manage gate via PetAccessContext (not legacy owner equality). */
    val canManage: StateFlow<Boolean> = combine(accessContext, currentUserId, pet) { ctx, userId, p ->
        userId != null && ctx != null && p?.status == "ACTIVE" &&
            (ctx.canUpdate || ctx.canArchive)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * M08 Etapa 5: entrada "Responsables y permisos" solo con lectura confirmada
     * por el backend (canRead del PetAccessContext), nunca por ownerId.
     * Etapa 6: bloqueada si DECEASED (solo lectura / historial / fotos / clínico).
     */
    val canViewGovernance: StateFlow<Boolean> = combine(accessContext, currentUserId, pet) { ctx, userId, p ->
        userId != null && ctx != null && ctx.canRead && p?.status != "DECEASED"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canMarkDeceased: StateFlow<Boolean> = combine(accessContext, pet) { ctx, p ->
        ctx?.canMarkDeceased == true && p?.status == "ACTIVE"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canRestore: StateFlow<Boolean> = combine(accessContext, pet, statusReasonCode) { ctx, p, reason ->
        ctx?.canRestore == true &&
            p?.status == "ARCHIVED" &&
            !reason.equals("ADOPTED", ignoreCase = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canViewHistory: StateFlow<Boolean> = combine(accessContext, currentUserId) { ctx, userId ->
        userId != null && ctx != null && (ctx.canViewHistory || ctx.canRead)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private val _lifecycleSuccess = MutableStateFlow(false)
    val lifecycleSuccess: StateFlow<Boolean> = _lifecycleSuccess.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _clinicalNote = MutableStateFlow("")
    val clinicalNote: StateFlow<String> = _clinicalNote.asStateFlow()

    private val _clinicalTitle = MutableStateFlow("")
    val clinicalTitle: StateFlow<String> = _clinicalTitle.asStateFlow()

    init {
        viewModelScope.launch {
            currentUserId.value = authRepository.getCurrentUser()?.id
            if (petId.isBlank()) {
                _isPetLoading.value = false
                _isHealthLoading.value = false
                _petLoadError.value = M08PetErrorMapper.userMessage("PET_NOT_FOUND")
                return@launch
            }
            val probe = com.comunidapp.app.domain.perf.ScreenPerfProbe.begin("pet_profile")
            if (_pet.value != null) probe.markFirstContent()
            probe.network { loadPetInternal() }
            if (_pet.value != null) probe.markFirstContent()
            // Custody label hydrates behind core pet content (non-blocking).
            refreshAccess()
            observePetUpdates()
            probe.finish()
        }
    }

    fun loadPet() {
        if (petId.isBlank()) {
            _isPetLoading.value = false
            _isHealthLoading.value = false
            _petLoadError.value = M08PetErrorMapper.userMessage("PET_NOT_FOUND")
            return
        }
        viewModelScope.launch { loadPetInternal() }
    }

    fun refreshFosterTransit() {
        if (petId.isBlank()) return
        viewModelScope.launch {
            val token = com.comunidapp.app.domain.user.SessionGeneration.current()
            val listed = runCatching {
                DataProvider.canonicalFosterTransitRepository.listMyFosterRequests().getOrThrow()
            }.getOrNull() ?: return@launch
            val open = try {
                com.comunidapp.app.data.repository.CanonicalFosterTransitRecovery.requestForPet(listed, petId)
            } catch (_: IllegalStateException) {
                return@launch
            }
            com.comunidapp.app.domain.foster.FosterTransitSignals.live.publish(
                token,
                com.comunidapp.app.domain.foster.FosterTransitSnapshot(
                    petId = petId,
                    requestId = open?.id,
                    status = open?.status
                )
            )
        }
    }

    private suspend fun loadPetInternal() {
        _isPetLoading.value = _pet.value == null
        _isHealthLoading.value = true
        _petLoadError.value = null

        val fetched = runCatching { petRepository.fetchPetById(petId) }
            .onFailure { error ->
                if (_pet.value == null) {
                    _petLoadError.value =
                        M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(error))
                }
            }
            .getOrNull()

        when {
            fetched != null -> {
                val merged = com.comunidapp.app.domain.pets.PetHealthMerge.preferRicherHealth(
                    _pet.value,
                    fetched
                )
                _pet.value = merged
                _healthLoadError.value = if (merged.healthReadFailed &&
                    !com.comunidapp.app.domain.pets.PetHealthPresentation.hasHealthData(merged)
                ) {
                    "No pudimos cargar la información de salud."
                } else {
                    null
                }
                resolvePhoto(merged)
                _petLoadError.value = null
                refreshStatusReason(merged.status)
            }
            _pet.value == null -> {
                val cached = runCatching { petRepository.getPetById(petId) }.getOrNull()
                if (cached != null) {
                    _pet.value = cached
                    resolvePhoto(cached)
                    refreshStatusReason(cached.status)
                } else if (_petLoadError.value == null) {
                    _petLoadError.value = M08PetErrorMapper.userMessage("PET_NOT_FOUND")
                }
            }
            else -> refreshStatusReason(_pet.value?.status)
        }
        _isHealthLoading.value = false
        _isPetLoading.value = false
    }

    private fun observePetUpdates() {
        if (petId.isBlank()) return
        viewModelScope.launch {
            petRepository.observePet(petId)
                .catch { error ->
                    if (_pet.value == null) {
                        _isPetLoading.value = false
                        _petLoadError.value =
                            M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(error))
                    }
                }
                .collect { latest ->
                    if (latest != null) {
                        _pet.value = com.comunidapp.app.domain.pets.PetHealthMerge.preferRicherHealth(
                            _pet.value,
                            latest
                        )
                        val held = _pet.value ?: latest
                        if (latest.healthReadFailed &&
                            !com.comunidapp.app.domain.pets.PetHealthPresentation.hasHealthData(held)
                        ) {
                            _healthLoadError.value = "No pudimos cargar la información de salud."
                        } else if (
                            com.comunidapp.app.domain.pets.PetHealthPresentation.hasHealthData(held) ||
                            !latest.healthReadFailed
                        ) {
                            _healthLoadError.value = null
                        }
                        resolvePhoto(_pet.value)
                        _isPetLoading.value = false
                        _petLoadError.value = null
                        refreshStatusReason(latest.status)
                    }
                }
        }
    }

    fun refreshAccess() {
        if (petId.isBlank()) return
        viewModelScope.launch {
            if (_principalDisplayName.value.isNullOrBlank()) {
                _principalLoading.value = true
            }
            petRepository.getPetAccessContext(petId)
                .onSuccess { ctx ->
                    accessContext.value = ctx
                    resolvePrincipalName(ctx)
                }
                .onFailure {
                    // Keep last-good custody label; only fall back when empty.
                    if (_principalDisplayName.value.isNullOrBlank()) {
                        loadPrincipalFromHolders()
                    } else {
                        _principalLoading.value = false
                    }
                }
        }
    }

    private fun loadPrincipalFromHolders() {
        if (petId.isBlank()) return
        viewModelScope.launch {
            _principalLoading.value = true
            val previous = _principalDisplayName.value
            val repo = DataProvider.petResponsibilityRepository
            if (repo != null) {
                val links = runCatching { repo.listForPet(PetId(petId)) }.getOrNull().orEmpty()
                val principal = links.firstOrNull {
                    it.status == PetLinkStatus.ACTIVE &&
                        it.role == PetResponsibilityRole.PRINCIPAL
                }
                principal?.holderDisplayName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                    _principalDisplayName.value = name
                    _principalLoading.value = false
                    return@launch
                }
            }
            val remote = com.comunidapp.app.data.remote.supabase.m08.SupabasePetM08RemoteDataSource()
            val holders = runCatching { remote.listResponsibilities(petId) }.getOrNull().orEmpty()
            val owner = holders.firstOrNull {
                it.status.equals("ACTIVE", ignoreCase = true) &&
                    (it.roleCode == "PRINCIPAL" || it.roleCode == "OWNER")
            }
            val resolved = owner?.displayName?.trim()?.takeIf { it.isNotEmpty() }
            // Never overwrite a known custodian label with null from holders.
            _principalDisplayName.value = resolved ?: previous
            _principalLoading.value = false
        }
    }

    private fun resolvePhoto(pet: Pet?) {
        viewModelScope.launch {
            _photoDisplayUrl.value = PetPhotoResolver.displayUrl(pet, currentUserId.value)
        }
    }

    private fun resolvePrincipalName(ctx: PetAccessContext) {
        viewModelScope.launch {
            if (_principalDisplayName.value.isNullOrBlank()) {
                _principalLoading.value = true
            }
            val previous = _principalDisplayName.value
            val name = resolvePrincipalDisplayName(ctx)
            _principalDisplayName.value = name?.takeIf { it.isNotBlank() } ?: previous
            _principalLoading.value = false
        }
    }

    private suspend fun resolvePrincipalDisplayName(ctx: PetAccessContext): String? {
        ctx.principalDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
        val personId = ctx.principalPersonId?.takeIf { it.isNotBlank() }
        if (personId != null) {
            val fromUser = runCatching {
                userRepository.getUser(personId)?.resolvedDisplayName?.trim()
            }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            if (fromUser != null) return fromUser
            val viewerId = currentUserId.value ?: authRepository.getCurrentUser()?.id
            if (viewerId != null) {
                val publicName = runCatching {
                    userRepository.getPublicProfile(viewerId, personId)
                        .getOrNull()
                        ?.displayName
                        ?.trim()
                }.getOrNull()?.takeIf { it.isNotBlank() }
                if (publicName != null) return publicName
            }
        }
        val orgId = ctx.principalOrganizationId?.takeIf { it.isNotBlank() }
        if (orgId != null && personId == null) {
            return "Organización"
        }
        val repo = DataProvider.petResponsibilityRepository ?: return null
        val links = runCatching { repo.listForPet(PetId(petId)) }.getOrNull().orEmpty()
        val principal = links.firstOrNull {
            it.role == PetResponsibilityRole.PRINCIPAL && it.status == PetLinkStatus.ACTIVE
        }
        if (principal != null) {
            principal.holderDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
            when (val holder = principal.holder) {
                is PetPrincipalHolder.Person -> {
                    runCatching { userRepository.getUser(holder.userId)?.name?.trim() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { return it }
                }
                is PetPrincipalHolder.Organization -> return "Organización"
            }
        }
        val ownerId = _pet.value?.ownerId?.takeIf { it.isNotBlank() }
        if (ownerId != null) {
            return runCatching { userRepository.getUser(ownerId)?.name?.trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun refreshStatusReason(status: String?) {
        if (petId.isBlank()) return
        if (!status.equals("ARCHIVED", ignoreCase = true)) {
            _statusReasonCode.value = null
            return
        }
        viewModelScope.launch {
            val history = petRepository.listStatusHistory(petId).getOrNull().orEmpty()
            val latest = history.maxByOrNull { it.createdAt.orEmpty() }
            _statusReasonCode.value = latest?.reasonCode
        }
    }

    fun updateClinicalTitle(value: String) = _clinicalTitle.update { value }
    fun updateClinicalNote(value: String) = _clinicalNote.update { value }

    fun addClinicalNote() {
        val user = authRepository.getCurrentUser() ?: return
        if (petId.isBlank()) return
        val title = _clinicalTitle.value.trim().ifBlank { "Nota clínica" }
        val notes = _clinicalNote.value.trim()
        if (notes.isBlank()) {
            _errorMessage.value = "Escribí una nota clínica"
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            platformRepository.addClinicalRecord(
                PetClinicalRecord(
                    id = "",
                    petId = petId,
                    authorId = user.id,
                    authorName = user.name,
                    recordType = ClinicalRecordType.NOTE,
                    title = title,
                    notes = notes
                )
            ).onSuccess {
                _clinicalTitle.value = ""
                _clinicalNote.value = ""
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "No se pudo guardar la nota"
            }
        }
    }

    fun deletePet() {
        if (petId.isBlank()) return
        val ctx = accessContext.value
        if (ctx?.canArchive != true) {
            _errorMessage.value = M08PetErrorMapper.userMessage("FORBIDDEN")
            return
        }
        if (pet.value?.status == "DECEASED") {
            _errorMessage.value = M08PetErrorMapper.userMessage("PET_DECEASED_CANNOT_ARCHIVE")
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            _isSubmitting.value = true
            petRepository.deletePet(petId)
                .onSuccess { _deleteSuccess.value = true }
                .onFailure { error ->
                    _errorMessage.value = M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(error))
                }
            _isSubmitting.value = false
        }
    }

    fun markPetDeceased(reason: String? = null) {
        if (petId.isBlank()) return
        if (accessContext.value?.canMarkDeceased != true) {
            _errorMessage.value = M08PetErrorMapper.userMessage("FORBIDDEN")
            return
        }
        if (pet.value?.status != "ACTIVE") {
            _errorMessage.value = M08PetErrorMapper.userMessage("PET_NOT_ACTIVE")
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            _isSubmitting.value = true
            petRepository.markPetDeceased(petId, reason?.trim()?.takeIf { it.isNotEmpty() })
                .onSuccess {
                    refreshAccess()
                    _lifecycleSuccess.value = true
                }
                .onFailure { error ->
                    _errorMessage.value = M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(error))
                }
            _isSubmitting.value = false
        }
    }

    fun restorePet() {
        if (petId.isBlank()) return
        if (accessContext.value?.canRestore != true) {
            _errorMessage.value = M08PetErrorMapper.userMessage("FORBIDDEN")
            return
        }
        if (pet.value?.status != "ARCHIVED") {
            _errorMessage.value = M08PetErrorMapper.userMessage("PET_NOT_ARCHIVED")
            return
        }
        if (_statusReasonCode.value.equals("ADOPTED", ignoreCase = true)) {
            _errorMessage.value = "Esta mascota fue adoptada; no se restaura desde archivo."
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            _isSubmitting.value = true
            petRepository.restorePet(petId)
                .onSuccess {
                    refreshAccess()
                    _lifecycleSuccess.value = true
                }
                .onFailure { error ->
                    _errorMessage.value = M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(error))
                }
            _isSubmitting.value = false
        }
    }

    fun clearDeleteSuccess() {
        _deleteSuccess.value = false
    }

    fun clearLifecycleSuccess() {
        _lifecycleSuccess.value = false
    }
}
