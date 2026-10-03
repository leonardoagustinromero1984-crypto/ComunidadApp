package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.local.PetFormDraftStore
import com.comunidapp.app.data.local.applyTo
import com.comunidapp.app.data.local.isRecoverableForEdit
import com.comunidapp.app.data.local.toDraft
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.pets.HistoricalDateRules
import com.comunidapp.app.domain.pets.PetAgeRules
import com.comunidapp.app.domain.pets.PetHealthReminders
import com.comunidapp.app.domain.pets.PetHealthSchedule
import com.comunidapp.app.domain.pets.PetInternalId
import com.comunidapp.app.domain.pets.PetPhotoResolver
import com.comunidapp.app.domain.publish.LocalDebugDiagnostic
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetFormUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isEditMode: Boolean = false,
    val petId: String = "",
    val ownerId: String? = null,
    val name: String = "",
    val species: PetSpecies = PetSpecies.DOG,
    val sex: PetSex = PetSex.UNKNOWN,
    val ageYears: Int = 1,
    val ageYearsInput: String = "1",
    val ageMonths: Int = 0,
    val ageMonthsInput: String = "0",
    val size: PetSize = PetSize.MEDIUM,
    val description: String = "",
    val sterilized: SterilizationStatus? = null,
    val microchipId: String = "",
    val lastVetVisit: String = "",
    val vaccinations: List<VaccinationRecord> = emptyList(),
    val pendingVaccineName: String = "",
    val pendingVaccineDate: String = "",
    val pendingVaccineNextDate: String = "",
    val dewormingProduct: String = "",
    val lastDeworming: String = "",
    val nextDeworming: String = "",
    val dewormNextManual: Boolean = false,
    val fleaTreatmentProduct: String = "",
    val lastFleaTreatment: String = "",
    val nextFleaTreatment: String = "",
    val fleaNextManual: Boolean = false,
    val vaccineNextManual: Boolean = false,
    val healthNotes: String = "",
    val allergyName: String = "",
    val medicationName: String = "",
    val conditionName: String = "",
    val photoUrl: String? = null,
    val pendingImageUri: Uri? = null,
    val canManageMedia: Boolean = true,
    val duplicateWarning: String? = null,
    val petStatus: String = "ACTIVE",
    val errorMessage: String? = null,
    val debugDiagnostic: String? = null,
    val saveSuccess: Boolean = false,
    val deleteSuccess: Boolean = false,
    val editDecision: com.comunidapp.app.domain.pets.PetEditAuthorization.Decision? = null,
    val breed: String = "",
    val breedId: String = "",
    val speciesCode: String = PetSpecies.DOG.name,
    val speciesOptions: List<com.comunidapp.app.data.repository.CatalogSpecies> = emptyList(),
    val secondaryClassificationEnabled: Boolean = true,
    val secondaryLabelSingular: String = "Raza",
    val breedOptions: List<String> = emptyList(),
    val vaccineOptions: List<String> = emptyList(),
    val fleaOptions: List<String> = emptyList(),
    val dewormerOptions: List<String> = emptyList()
) {
    val mutationsLocked: Boolean get() = petStatus != "ACTIVE"
}

class PetFormViewModel(
    private val editPetId: String? = null,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val userRepository: com.comunidapp.app.data.repository.UserRepository = DataProvider.userRepository,
    private val duplicateDebounceMs: Long = 400L
) : ViewModel() {

    private var loadedPet: Pet? = null
    private var duplicateJob: Job? = null
    private var draftJob: Job? = null
    private var draftUserId: String? = null
    private var userChangedBreed: Boolean = false

    private val _uiState = MutableStateFlow(PetFormUiState())
    val uiState: StateFlow<PetFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(saveSuccess = false, deleteSuccess = false, errorMessage = null)
            }

            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No hay sesión activa") }
                return@launch
            }

            val petIdToEdit = PetInternalId.resolveForEdit(
                raw = editPetId,
                accessiblePets = petRepository.getPetsByOwner(authUser.id)
            )
            if (editPetId != null && editPetId.isNotBlank() && petIdToEdit == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Mascota no encontrada")
                }
                return@launch
            }
            if (petIdToEdit != null) {
                val pet = petRepository.fetchPetById(petIdToEdit)
                    ?: petRepository.getPetById(petIdToEdit)
                val context = petRepository.getPetAccessContext(petIdToEdit).getOrNull()
                val sessionPerson = resolveSessionPerson(authUser.id)
                val personId = sessionPerson?.id ?: authUser.id
                val decision = com.comunidapp.app.domain.pets.PetEditAuthorization.decide(
                    pet = pet,
                    context = context,
                    sessionUserId = authUser.id,
                    sessionPersonId = personId
                )
                when (decision) {
                    com.comunidapp.app.domain.pets.PetEditAuthorization.Decision.PET_NOT_FOUND -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                editDecision = decision,
                                errorMessage = "Mascota no encontrada"
                            )
                        }
                        return@launch
                    }
                    com.comunidapp.app.domain.pets.PetEditAuthorization.Decision.NO_PERMISSION -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                editDecision = decision,
                                errorMessage = "No tenés permiso para editar esta mascota"
                            )
                        }
                        return@launch
                    }
                    com.comunidapp.app.domain.pets.PetEditAuthorization.Decision.CAN_EDIT -> Unit
                }
                val editablePet = pet ?: return@launch
                if (editablePet.status == "DECEASED") {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            petStatus = editablePet.status,
                            errorMessage = "No se puede editar una mascota fallecida."
                        )
                    }
                    return@launch
                }
                loadedPet = editablePet
                val base = PetFormUiState(
                        isLoading = false,
                        isEditMode = true,
                        editDecision = com.comunidapp.app.domain.pets.PetEditAuthorization.Decision.CAN_EDIT,
                        petId = editablePet.id,
                        ownerId = editablePet.ownerId,
                        name = editablePet.name,
                        species = editablePet.species,
                        speciesCode = editablePet.speciesCode
                            ?: editablePet.species.name,
                        sex = editablePet.sex,
                        ageYears = editablePet.ageYears,
                        ageYearsInput = editablePet.ageYears.toString(),
                        ageMonths = editablePet.ageMonths,
                        ageMonthsInput = editablePet.ageMonths.toString(),
                        size = editablePet.size,
                        description = editablePet.description,
                        sterilized = editablePet.sterilized,
                        microchipId = editablePet.microchipId.orEmpty(),
                        lastVetVisit = editablePet.lastVetVisit.orEmpty(),
                        vaccinations = editablePet.vaccinations,
                        dewormingProduct = editablePet.dewormingProduct.orEmpty(),
                        lastDeworming = editablePet.lastDeworming.orEmpty(),
                        nextDeworming = PetHealthReminders.dateOf(
                            editablePet.reminders,
                            PetHealthReminders.NEXT_DEWORMING
                        ),
                        fleaTreatmentProduct = editablePet.fleaTreatmentProduct.orEmpty(),
                        lastFleaTreatment = editablePet.lastFleaTreatment.orEmpty(),
                        nextFleaTreatment = PetHealthReminders.dateOf(
                            editablePet.reminders,
                            PetHealthReminders.NEXT_FLEA
                        ),
                        healthNotes = editablePet.healthNotes.orEmpty(),
                        allergyName = editablePet.allergies.firstOrNull().orEmpty(),
                        medicationName = editablePet.medications.firstOrNull().orEmpty(),
                        conditionName = editablePet.conditions.firstOrNull().orEmpty(),
                        photoUrl = PetPhotoResolver.displayUrl(editablePet, authUser.id),
                        canManageMedia = context?.canManageMedia ?: true,
                        petStatus = editablePet.status,
                        breed = editablePet.breed.orEmpty(),
                        breedId = editablePet.breedId.orEmpty()
                    )
                draftUserId = authUser.id
                // EDIT mode: backend is source of truth — never apply local draft overlay.
                _uiState.update { base }
                loadCatalogs(base.speciesCode)
            } else {
                draftUserId = authUser.id
                val base = PetFormUiState(isLoading = false, ownerId = authUser.id, canManageMedia = true)
                val drafted = com.comunidapp.app.data.local.PetFormDraftStore
                    .read(authUser.id, null)
                    ?.applyTo(base) ?: base
                _uiState.update { drafted }
                loadCatalogs(drafted.speciesCode)
            }
        }
    }

    fun onNameChange(value: String) {
        updateForm { copy(name = value, errorMessage = null) }
        scheduleDuplicateCheck()
    }
    fun onSpeciesChange(value: com.comunidapp.app.data.repository.CatalogSpecies) {
        userChangedBreed = true
        updateForm {
            copy(
                species = com.comunidapp.app.domain.pets.PetSpeciesCatalog.toPetSpecies(value.code),
                speciesCode = value.code,
                secondaryClassificationEnabled = value.secondaryClassificationEnabled,
                secondaryLabelSingular = value.secondaryLabelSingular
                    ?: com.comunidapp.app.domain.pets.SecondaryClassificationKind
                        .defaultLabels(value.secondaryClassificationKind).first,
                breed = "",
                breedId = "",
                pendingVaccineName = "",
                errorMessage = null
            )
        }
        loadCatalogs(value.code)
    }
    fun onBreedChange(value: String) {
        userChangedBreed = true
        updateForm { copy(breed = value, errorMessage = null) }
    }
    fun onSexChange(value: PetSex) = updateForm { copy(sex = value, errorMessage = null) }
    fun onAgeYearsInput(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(2)
        val parsed = PetAgeRules.parseYears(digits)
        updateForm {
            copy(
                ageYearsInput = digits,
                ageYears = parsed ?: ageYears,
                errorMessage = if (digits.isNotEmpty() && parsed == null) {
                    "Los años deben estar entre 0 y 99"
                } else {
                    null
                }
            )
        }
    }

    fun onAgeMonthsInput(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(2)
        val parsed = PetAgeRules.parseMonths(digits)
        updateForm {
            copy(
                ageMonthsInput = digits,
                ageMonths = parsed ?: ageMonths,
                errorMessage = if (digits.isNotEmpty() && parsed == null) {
                    "Los meses deben estar entre 0 y 11"
                } else {
                    null
                }
            )
        }
    }
    fun onSizeChange(value: PetSize) = updateForm { copy(size = value, errorMessage = null) }
    fun onDescriptionChange(value: String) = updateForm { copy(description = value, errorMessage = null) }
    fun onSterilizedChange(value: SterilizationStatus) = updateForm { copy(sterilized = value, errorMessage = null) }
    fun onMicrochipChange(value: String) {
        updateForm { copy(microchipId = value, errorMessage = null) }
        scheduleDuplicateCheck()
    }
    fun onLastVetVisitChange(value: String) = updateForm { copy(lastVetVisit = value, errorMessage = null) }
    fun onPendingVaccineNameChange(value: String) = updateForm { copy(pendingVaccineName = value, errorMessage = null) }
    fun onPendingVaccineDateChange(value: String) = updateForm {
        val next = if (value.isNotBlank() && !vaccineNextManual) {
            runCatching { PetHealthSchedule.nextVaccineBooster(value) }.getOrDefault(pendingVaccineNextDate)
        } else {
            pendingVaccineNextDate
        }
        copy(pendingVaccineDate = value, pendingVaccineNextDate = next, errorMessage = null)
    }
    fun onPendingVaccineNextDateChange(value: String) = updateForm {
        copy(pendingVaccineNextDate = value, vaccineNextManual = true, errorMessage = null)
    }
    fun onDewormingProductChange(value: String) = updateForm { copy(dewormingProduct = value, errorMessage = null) }
    fun onLastDewormingChange(value: String) = updateForm {
        val next = if (value.isNotBlank() && !dewormNextManual) {
            runCatching { PetHealthSchedule.nextDeworming(value) }.getOrDefault(nextDeworming)
        } else {
            nextDeworming
        }
        copy(lastDeworming = value, nextDeworming = next, errorMessage = null)
    }
    fun onNextDewormingChange(value: String) = updateForm {
        copy(nextDeworming = value, dewormNextManual = true, errorMessage = null)
    }
    fun onFleaProductChange(value: String) = updateForm { copy(fleaTreatmentProduct = value, errorMessage = null) }
    fun onLastFleaTreatmentChange(value: String) = updateForm {
        val next = if (value.isNotBlank() && !fleaNextManual) {
            runCatching { PetHealthSchedule.nextFleaApplication(value) }.getOrDefault(nextFleaTreatment)
        } else {
            nextFleaTreatment
        }
        copy(lastFleaTreatment = value, nextFleaTreatment = next, errorMessage = null)
    }
    fun onNextFleaTreatmentChange(value: String) = updateForm {
        copy(nextFleaTreatment = value, fleaNextManual = true, errorMessage = null)
    }
    fun onHealthNotesChange(value: String) = updateForm { copy(healthNotes = value, errorMessage = null) }
    fun onAllergyNameChange(value: String) = updateForm { copy(allergyName = value, errorMessage = null) }
    fun onMedicationNameChange(value: String) = updateForm { copy(medicationName = value, errorMessage = null) }
    fun onConditionNameChange(value: String) = updateForm { copy(conditionName = value, errorMessage = null) }
    fun onImageSelected(uri: Uri?) {
        val state = _uiState.value
        if (!state.canManageMedia && state.isEditMode) {
            _uiState.update {
                it.copy(errorMessage = M08PetErrorMapper.userMessage("FORBIDDEN"))
            }
            return
        }
        updateForm { copy(pendingImageUri = uri, errorMessage = null) }
    }

    fun onPhotoCropFailed(code: String) {
        _uiState.update {
            it.copy(errorMessage = FileUiErrorMapper.message(code))
        }
    }

    fun addPendingVaccination() {
        val state = _uiState.value
        if (state.pendingVaccineName.isBlank() || state.pendingVaccineDate.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Seleccioná vacuna y fecha de aplicación") }
            return
        }
        val record = VaccinationRecord(
            name = state.pendingVaccineName,
            date = state.pendingVaccineDate,
            nextDueDate = state.pendingVaccineNextDate.takeIf { it.isNotBlank() }
        )
        _uiState.update {
            it.copy(
                vaccinations = listOf(record) + it.vaccinations,
                pendingVaccineName = "",
                pendingVaccineDate = "",
                pendingVaccineNextDate = "",
                errorMessage = null
            )
        }
    }

    fun removeVaccination(index: Int) {
        _uiState.update { state ->
            state.copy(
                vaccinations = state.vaccinations.filterIndexed { i, _ -> i != index },
                errorMessage = null
            )
        }
    }

    fun savePet() {
        val state = _uiState.value
        if (state.mutationsLocked) {
            _uiState.update {
                it.copy(errorMessage = M08PetErrorMapper.userMessage("PET_NOT_ACTIVE"))
            }
            return
        }
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre es obligatorio") }
            return
        }
        if (!PetAgeRules.isValidYears(state.ageYears) ||
            PetAgeRules.parseYears(state.ageYearsInput) == null
        ) {
            _uiState.update { it.copy(errorMessage = "Los años deben estar entre 0 y 99") }
            return
        }
        if (!PetAgeRules.isValidMonths(state.ageMonths) ||
            PetAgeRules.parseMonths(state.ageMonthsInput) == null
        ) {
            _uiState.update { it.copy(errorMessage = "Los meses deben estar entre 0 y 11") }
            return
        }
        val historicalDates = listOf(
            state.lastVetVisit,
            state.pendingVaccineDate,
            state.lastDeworming,
            state.lastFleaTreatment
        ) + state.vaccinations.map { it.date }
        if (historicalDates.any { !HistoricalDateRules.isNotFuture(it) }) {
            _uiState.update { it.copy(errorMessage = "Las fechas históricas no pueden ser futuras") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccess = false) }

            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = com.comunidapp.app.domain.pets.PetCreateDiagnostic.formatUserMessage(
                            M08PetErrorMapper.userMessage("NOT_AUTHENTICATED"),
                            com.comunidapp.app.domain.pets.PetCreateDiagnostic.AUTH
                        )
                    )
                }
                return@launch
            }

            var person = resolveSessionPerson(authUser.id)
            var personLoadAttempts = 0
            while (person == null && personLoadAttempts < 3) {
                personLoadAttempts++
                delay(400)
                person = resolveSessionPerson(authUser.id)
            }
            if (person == null) {
                val code = if (personLoadAttempts > 0) {
                    com.comunidapp.app.domain.pets.PetCreateDiagnostic.PERSON
                } else {
                    com.comunidapp.app.domain.pets.PetCreateDiagnostic.PERSON_LOAD
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = com.comunidapp.app.domain.pets.PetCreateDiagnostic.formatUserMessage(
                            "Todavía no encontramos tu perfil Persona. Esperá un momento e intentá de nuevo.",
                            code
                        )
                    )
                }
                return@launch
            }

            val vaccinations = buildFinalVaccinations(state)
            // Never persist photoUrl from form; avatar goes through M05 + canon_set_pet_avatar.
            var pet = Pet(
                id = state.petId,
                ownerId = state.ownerId?.takeIf { it.isNotBlank() },
                name = state.name.trim(),
                species = state.species,
                speciesCode = state.speciesCode,
                sex = state.sex,
                ageYears = state.ageYears,
                ageMonths = state.ageMonths,
                size = state.size,
                description = state.description.trim(),
                photoUrl = loadedPet?.photoUrl,
                vaccinations = vaccinations,
                lastDeworming = state.lastDeworming.takeIf { it.isNotBlank() },
                dewormingProduct = state.dewormingProduct.takeIf { it.isNotBlank() },
                lastFleaTreatment = state.lastFleaTreatment.takeIf { it.isNotBlank() },
                fleaTreatmentProduct = state.fleaTreatmentProduct.takeIf { it.isNotBlank() },
                sterilized = state.sterilized,
                microchipId = state.microchipId.trim().ifBlank { null },
                lastVetVisit = state.lastVetVisit.takeIf { it.isNotBlank() },
                healthNotes = state.healthNotes.trim().ifBlank { null },
                allergies = listOfNotNull(state.allergyName.trim().takeIf { it.isNotEmpty() }),
                medications = listOfNotNull(state.medicationName.trim().takeIf { it.isNotEmpty() }),
                conditions = listOfNotNull(state.conditionName.trim().takeIf { it.isNotEmpty() }),
                breed = state.breed.trim().ifBlank { null },
                breedId = state.breedId.ifBlank { loadedPet?.breedId },
                reminders = PetHealthReminders.upsert(
                    PetHealthReminders.upsert(
                        loadedPet?.reminders.orEmpty(),
                        PetHealthReminders.NEXT_DEWORMING,
                        state.nextDeworming,
                        "Próxima desparasitación"
                    ),
                    PetHealthReminders.NEXT_FLEA,
                    state.nextFleaTreatment,
                    "Próxima aplicación"
                ),
                createdAt = loadedPet?.createdAt,
                avatarFileAssetId = loadedPet?.avatarFileAssetId,
                status = loadedPet?.status ?: "ACTIVE"
            )

            val petIdResult = if (state.isEditMode) {
                petRepository.updatePet(pet).map { pet.id }
            } else {
                petRepository.createPet(pet)
            }

            petIdResult
                .onSuccess { petId ->
                    pet = pet.copy(id = petId)
                    loadedPet = pet
                    com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                        "PET-STAGE=CREATED edit=${state.isEditMode} petId=$petId personId=${person.id}"
                    )

                    if (state.pendingImageUri != null && !state.canManageMedia &&
                        !com.comunidapp.app.domain.pets.PetCreatePermissionFeedback
                            .suppressPermissionErrorAfterAuthorizedCreate(
                                createSucceeded = true,
                                isEditMode = state.isEditMode
                            )
                    ) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                isEditMode = true,
                                petId = petId,
                                errorMessage = M08PetErrorMapper.userMessage("FORBIDDEN")
                            )
                        }
                        return@launch
                    }

                    state.pendingImageUri?.let { uri ->
                        val previousAssetId = loadedPet?.avatarFileAssetId
                        com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                            "PET-STAGE=MEDIA-UPLOAD petId=$petId"
                        )
                        when (val upload = DataProvider.fileUploadCoordinator.startUpload(
                            uriString = uri.toString(),
                            request = FileUploadRequest(
                                purpose = FileAssetPurpose.PET_AVATAR,
                                owner = FileAssetOwner.User(authUser.id),
                                resourceRef = FileResourceRef(FileResourceType.PET, petId),
                                originalFilename = "pet.jpg",
                                declaredMimeType = "image/jpeg",
                                sizeBytes = com.comunidapp.app.domain.media.ProfileMediaPipeline.resolvedSizeBytes(
                                    uri.toString(),
                                    0L
                                ),
                                requestedVisibility = FileAssetVisibility.PUBLIC
                            ),
                            actorUserId = authUser.id
                        )) {
                            is AppResult.Success -> {
                                val assetId = upload.data.assetId
                                com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                                    "PET-STAGE=SET-AVATAR petId=$petId"
                                )
                                petRepository.setPetAvatarAsset(petId, assetId)
                                    .onSuccess { updated ->
                                        pet = updated
                                        loadedPet = updated
                                    }
                                    .onFailure { err ->
                                        val mapped = M08PetErrorMapper.codeOf(err)
                                        if (mapped == "FORBIDDEN" &&
                                            com.comunidapp.app.domain.pets.PetCreatePermissionFeedback
                                                .suppressPermissionErrorAfterAuthorizedCreate(
                                                    createSucceeded = true,
                                                    isEditMode = state.isEditMode
                                                )
                                        ) {
                                            _uiState.update {
                                                it.copy(
                                                    isSaving = false,
                                                    saveSuccess = true,
                                                    isEditMode = true,
                                                    petId = petId,
                                                    errorMessage = null,
                                                    pendingImageUri = null
                                                )
                                            }
                                            com.comunidapp.app.data.local.PetFormDraftStore.clear()
                                            return@launch
                                        }
                                        com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                                            "PET-STAGE=SET-AVATAR-FAIL type=${err::class.java.simpleName}"
                                        )
                                        _uiState.update {
                                            it.copy(
                                                isSaving = false,
                                                isEditMode = true,
                                                petId = petId,
                                                errorMessage = com.comunidapp.app.domain.pets.PetCreateDiagnostic.formatUserMessage(
                                                    M08PetErrorMapper.userMessage(
                                                        M08PetErrorMapper.codeOf(err)
                                                    ),
                                                    com.comunidapp.app.domain.pets.PetCreateDiagnostic.MEDIA
                                                ),
                                                debugDiagnostic = LocalDebugDiagnostic.forOperation(
                                                    operation = "PET_PHOTO_UPDATE",
                                                    error = err,
                                                    extra = mapOf(
                                                        "step" to com.comunidapp.app.domain.canonical.CanonicalMedia.STEP_SET_PET_AVATAR,
                                                        "rpc" to "canon_set_pet_avatar",
                                                        "petId" to petId,
                                                        "upload" to "ok",
                                                        "previousAssetId" to (previousAssetId ?: ""),
                                                        "bucket" to upload.data.physicalBucket,
                                                        "objectPath" to PetPhotoResolver.sanitizeObjectPath(
                                                            upload.data.storagePath
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                        return@launch
                                    }
                            }
                            is AppResult.Failure -> {
                                if (com.comunidapp.app.domain.pets.PetCreatePermissionFeedback
                                        .suppressPermissionErrorAfterAuthorizedCreate(
                                            createSucceeded = true,
                                            isEditMode = state.isEditMode
                                        ) &&
                                    (
                                        upload.error.code?.contains("FORBIDDEN", ignoreCase = true) == true ||
                                            upload.error.technicalMessage.contains("PERMISSION", ignoreCase = true)
                                        )
                                ) {
                                    _uiState.update {
                                        it.copy(
                                            isSaving = false,
                                            saveSuccess = true,
                                            isEditMode = true,
                                            petId = petId,
                                            errorMessage = null,
                                            pendingImageUri = null
                                        )
                                    }
                                    com.comunidapp.app.data.local.PetFormDraftStore.clear()
                                    return@launch
                                }
                                val step = upload.error.code
                                    ?: com.comunidapp.app.domain.canonical.CanonicalMedia.STEP_STORAGE_UPLOAD
                                com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                                    "PET-STAGE=MEDIA-UPLOAD-FAIL step=$step petId=$petId"
                                )
                                _uiState.update {
                                    it.copy(
                                        isSaving = false,
                                        isEditMode = true,
                                        petId = petId,
                                        errorMessage = com.comunidapp.app.domain.pets.PetCreateDiagnostic.formatUserMessage(
                                            FileUiErrorMapper.message(upload.error),
                                            com.comunidapp.app.domain.pets.PetCreateDiagnostic.MEDIA
                                        ),
                                        debugDiagnostic = LocalDebugDiagnostic.forOperation(
                                            operation = "PET_PHOTO_UPDATE",
                                            error = IllegalStateException(upload.error.technicalMessage),
                                            extra = mapOf(
                                                "step" to step,
                                                "rpc" to "canon_register_media",
                                                "petId" to petId
                                            )
                                        )
                                    )
                                }
                                return@launch
                            }
                        }
                    }

                    loadedPet = pet
                    val displayPhoto = PetPhotoResolver.displayUrl(pet, authUser.id)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                            isEditMode = true,
                            petId = petId,
                            photoUrl = displayPhoto,
                            pendingImageUri = null
                        )
                    }
                    com.comunidapp.app.data.local.PetFormDraftStore.clear()
                }
                .onFailure { error ->
                    val sessionPresent = authRepository.getCurrentUser() != null
                    val code = com.comunidapp.app.domain.pets.PetCreateDiagnostic.fromSaveFailure(
                        error = error,
                        sessionPresent = sessionPresent
                    )
                    val mapped = M08PetErrorMapper.codeOf(error)
                    val friendly = if (mapped == "NOT_AUTHENTICATED" && sessionPresent) {
                        "No pudimos guardar la mascota. Intentá de nuevo."
                    } else {
                        M08PetErrorMapper.userMessage(mapped)
                    }
                    val operation = if (state.isEditMode) "pet_update" else "pet_create"
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = com.comunidapp.app.domain.pets.PetCreateDiagnostic.formatUserMessage(
                                friendly,
                                code
                            ),
                            debugDiagnostic = LocalDebugDiagnostic.forOperation(
                                operation = operation,
                                error = error,
                                extra = mapOf(
                                    "rpc" to if (state.isEditMode) {
                                        "canon_update_pet"
                                    } else {
                                        "canon_create_pet"
                                    },
                                    "code" to code
                                )
                            )
                        )
                    }
                }
        }
    }

    fun deletePet() {
        val petId = _uiState.value.petId
        if (petId.isBlank()) return
        if (_uiState.value.mutationsLocked) {
            _uiState.update {
                it.copy(errorMessage = M08PetErrorMapper.userMessage("PET_NOT_ACTIVE"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            petRepository.deletePet(petId)
                .onSuccess {
                    _uiState.update { it.copy(isDeleting = false, deleteSuccess = true) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = M08PetErrorMapper.userMessage(
                                M08PetErrorMapper.codeOf(error)
                            )
                        )
                    }
                }
        }
    }

    fun clearSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }
    fun clearDeleteSuccess() = _uiState.update { it.copy(deleteSuccess = false) }

    private fun scheduleDuplicateCheck() {
        duplicateJob?.cancel()
        duplicateJob = viewModelScope.launch {
            delay(duplicateDebounceMs)
            val state = _uiState.value
            val chip = state.microchipId.trim().takeIf { it.isNotEmpty() }
            val nm = state.name.trim().takeIf { it.isNotEmpty() }
            if (chip == null && nm == null) {
                _uiState.update { it.copy(duplicateWarning = null) }
                return@launch
            }
            petRepository.detectDuplicateCandidates(microchip = chip, name = nm)
                .onSuccess { candidates ->
                    val others = candidates.filter { it.petId != state.petId }
                    val warning = when {
                        others.any { it.matchReason.equals("MICROCHIP", ignoreCase = true) } ->
                            "Ya tenés otra mascota accesible con ese microchip. Revisá antes de guardar."
                        others.any { it.matchReason.equals("NAME", ignoreCase = true) } ->
                            "Podría haber un registro similar con ese nombre entre tus mascotas."
                        else -> null
                    }
                    _uiState.update { it.copy(duplicateWarning = warning) }
                }
                .onFailure {
                    // Private warning is best-effort; do not block the form.
                    _uiState.update { it.copy(duplicateWarning = null) }
                }
        }
    }

    private fun buildFinalVaccinations(state: PetFormUiState): List<VaccinationRecord> {
        if (state.pendingVaccineName.isBlank() || state.pendingVaccineDate.isBlank()) {
            return state.vaccinations
        }
        return listOf(
            VaccinationRecord(
                name = state.pendingVaccineName,
                date = state.pendingVaccineDate,
                nextDueDate = state.pendingVaccineNextDate.takeIf { it.isNotBlank() }
            )
        ) + state.vaccinations
    }

    fun discardDraft() {
        com.comunidapp.app.data.local.PetFormDraftStore.clear()
    }

    private suspend fun resolveSessionPerson(authUserId: String): com.comunidapp.app.data.model.User? {
        val session = com.comunidapp.app.domain.user.SessionResolvedPerson.current()
        if (session != null &&
            !com.comunidapp.app.domain.user.SessionPersonRouting.isJwtStub(session)
        ) {
            return session
        }
        return userRepository.getUser(authUserId)?.takeIf {
            !com.comunidapp.app.domain.user.SessionPersonRouting.isJwtStub(it)
        }
    }

    private fun loadCatalogs(speciesCode: String) {
        viewModelScope.launch {
            runCatching {
                val repo = DataProvider.masterCatalogRepository
                val activeRows = repo.listSpecies()
                val currentCode = speciesCode.ifBlank { _uiState.value.speciesCode }
                val currentSpecies = repo.getSpecies(currentCode)
                val options = (activeRows + listOfNotNull(currentSpecies))
                    .distinctBy { it.code }
                val selected = options.firstOrNull { it.code.equals(currentCode, true) }
                    ?: currentSpecies
                val secondaryEnabled = selected?.secondaryClassificationEnabled == true
                val secondaryLabel = selected?.secondaryLabelSingular
                    ?: com.comunidapp.app.domain.pets.SecondaryClassificationKind
                        .defaultLabels(selected?.secondaryClassificationKind).first
                    .ifBlank { "Raza" }
                val mappedSpecies = com.comunidapp.app.domain.pets.PetSpeciesCatalog.toPetSpecies(
                    selected?.code ?: currentCode
                )
                val current = _uiState.value
                val catalog = if (secondaryEnabled) {
                    repo.listSecondaryItems(selected?.code ?: currentCode)
                } else {
                    emptyList()
                }
                val petBreedId = current.breedId.ifBlank { loadedPet?.breedId }
                val historical = if (secondaryEnabled) {
                    petBreedId?.let { repo.getSecondaryItem(it) }
                        ?.takeIf { found -> catalog.none { it.id == found.id } }
                } else {
                    null
                }
                val catalogForResolve = catalog + listOfNotNull(historical)
                val resolvedBreed = if (secondaryEnabled) {
                    com.comunidapp.app.data.remote.supabase.m08.PetFormBreedHydration.resolveSelectedName(
                        petBreedName = loadedPet?.breed ?: current.breed,
                        petBreedId = petBreedId,
                        catalog = catalogForResolve,
                        userChangedBreed = userChangedBreed,
                        currentSelection = current.breed
                    )
                } else {
                    ""
                }
                val breeds = if (secondaryEnabled) {
                    (listOf(resolvedBreed).filter { it.isNotEmpty() } + catalog.map { it.name }).distinct()
                } else {
                    emptyList()
                }
                val resolvedId = if (secondaryEnabled) {
                    com.comunidapp.app.data.remote.supabase.m08.PetBreedCatalog.idForName(
                        catalogForResolve,
                        resolvedBreed
                    ) ?: petBreedId.orEmpty()
                } else {
                    ""
                }
                val vaccines = repo.listHealthProducts("VACCINE", selected?.code ?: currentCode)
                    .map { it.displayName }
                    .ifEmpty { com.comunidapp.app.data.model.PetHealthCatalog.vaccinesForSpecies(mappedSpecies) }
                val flea = repo.listHealthProducts("FLEA", selected?.code ?: currentCode)
                    .map { it.displayName }
                    .ifEmpty { com.comunidapp.app.data.model.PetHealthCatalog.fleaAndTickProducts }
                val deworm = repo.listHealthProducts("DEWORMER", selected?.code ?: currentCode)
                    .map { it.displayName }
                    .ifEmpty { com.comunidapp.app.data.model.PetHealthCatalog.dewormingProducts }
                _uiState.update {
                    it.copy(
                        species = mappedSpecies,
                        speciesCode = selected?.code ?: currentCode,
                        speciesOptions = options,
                        secondaryClassificationEnabled = secondaryEnabled,
                        secondaryLabelSingular = secondaryLabel,
                        breed = resolvedBreed,
                        breedId = resolvedId,
                        breedOptions = breeds,
                        vaccineOptions = vaccines,
                        fleaOptions = flea,
                        dewormerOptions = deworm
                    )
                }
            }
        }
    }

    private inline fun updateForm(block: PetFormUiState.() -> PetFormUiState) {
        _uiState.update { it.block() }
        persistDraftSoon()
    }

    private fun persistDraftSoon() {
        val userId = draftUserId ?: return
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(250)
            val state = _uiState.value
            if (state.isLoading || state.saveSuccess) return@launch
            com.comunidapp.app.data.local.PetFormDraftStore.write(
                state.toDraft(userId, editPetId)
            )
        }
    }

    companion object {
        fun factory(editPetId: String? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PetFormViewModel(editPetId = editPetId) as T
                }
            }
    }
}

private fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    fold(onSuccess = { Result.success(transform(it)) }, onFailure = { Result.failure(it) })
