package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.local.Onb02Store
import com.comunidapp.app.data.local.Onb02StoreProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.CanonicalTutorialProgressRepository
import com.comunidapp.app.data.repository.PersonCapabilityRepository
import com.comunidapp.app.domain.capability.PersonCapabilityCode
import com.comunidapp.app.domain.context.OperationalContextProvider
import kotlinx.coroutines.launch
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionCatalog
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionGroup
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionGroupKind
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionOption
import com.comunidapp.app.domain.onboarding.onb02.OccupiedFunctions
import com.comunidapp.app.domain.onboarding.onb02.FunctionSelection
import com.comunidapp.app.domain.onboarding.onb02.IndependentProfessionalSpecialty
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.Onb02Copy
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.Onb02PlanItem
import com.comunidapp.app.domain.onboarding.onb02.Onb02Planner
import com.comunidapp.app.domain.onboarding.onb02.Onb02StepKind
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.OrganizationSetupAction
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorKind
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorTaxonomy
import com.comunidapp.app.domain.onboarding.onb02.TutorialCatalog
import com.comunidapp.app.domain.onboarding.onb02.TutorialDefinition
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.onboarding.onb03.CanonicalTutorialEvent
import com.comunidapp.app.domain.onboarding.onb03.PendingTutorialQueue
import com.comunidapp.app.domain.onboarding.onb03.PendingTutorialRun
import com.comunidapp.app.domain.onboarding.onb03.TutorialQueueResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Onb02Phase {
    INTRO,
    SELECT,
    PROFESSIONAL_SETUP,
    BUSINESS_SETUP,
    ORG_SETUP,
    TUTORIAL,
    DONE
}

data class Onb02UiState(
    val kind: Onb02FlowKind,
    val phase: Onb02Phase,
    val selection: FunctionSelection = FunctionSelection(),
    val queue: List<TutorialId> = emptyList(),
    val queueIndex: Int = 0,
    val stepIndex: Int = 0,
    val reopenId: TutorialId? = null,
    val plan: List<Onb02PlanItem> = emptyList(),
    val planIndex: Int = 0,
    val errorMessage: String? = null
) {
    val currentTutorial: TutorialDefinition?
        get() = when {
            phase == Onb02Phase.INTRO -> TutorialCatalog.definition(TutorialId.T00_MULTI_FUNCTION_INTRO)
            phase == Onb02Phase.TUTORIAL && reopenId != null -> TutorialCatalog.definition(reopenId)
            phase == Onb02Phase.TUTORIAL -> {
                val fromPlan = plan.getOrNull(planIndex)?.tutorialId
                val id = fromPlan
                    ?: queue.getOrNull(queueIndex)
                id?.let { TutorialCatalog.definition(it) }
            }
            else -> null
        }

    val selectorSubtitle: String = Onb02Copy.SELECTOR_SUBTITLE
}

class Onb02ViewModel(
    private val store: Onb02Store = Onb02StoreProvider.instance,
    private val userIdProvider: () -> String = {
        AuthProvider.repository.getCurrentUser()?.id.orEmpty()
    },
    private val capabilityOverride: PersonCapabilityRepository? = null,
    private val persistIndependentVeterinaryOverride: (suspend (String) -> Result<Boolean>)? = null
) : ViewModel() {

    private val _ui = MutableStateFlow(
        Onb02UiState(kind = Onb02FlowKind.FULL_ONBOARDING, phase = Onb02Phase.INTRO)
    )
    val ui: StateFlow<Onb02UiState> = _ui.asStateFlow()

    private val userId: String get() = userIdProvider()
    private var newlyAdded: Set<LeoverFunction> = emptySet()

    private var eventLandingRoute: String? = null

    fun start(kind: Onb02FlowKind, reopenId: TutorialId? = null, seedExtras: Set<LeoverFunction> = emptySet()) {
        val existing = store.selection(userId)
        val selection = if (kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
            existing.copy(
                extras = emptySet(),
                organizationAction = null,
                organizationKind = null,
                actorKind = null,
                professionalSpecialty = null
            )
        } else {
            existing
        }
        _ui.value = Onb02UiState(
            kind = kind,
            phase = Onb02Phase.INTRO,
            selection = selection,
            reopenId = reopenId
        )
        if (seedExtras.isNotEmpty()) {
            _ui.value = _ui.value.copy(selection = _ui.value.selection.copy(extras = seedExtras))
        }
        when (kind) {
            Onb02FlowKind.REOPEN_FROM_HELP -> {
                _ui.value = _ui.value.copy(
                    phase = Onb02Phase.TUTORIAL,
                    queue = if (reopenId != null) listOf(reopenId) else emptyList(),
                    plan = if (reopenId != null) {
                        listOf(
                            Onb02PlanItem(
                                id = "help_${reopenId.key}",
                                kind = Onb02StepKind.TUTORIAL,
                                function = null,
                                tutorialId = reopenId
                            )
                        )
                    } else {
                        emptyList()
                    },
                    stepIndex = if (reopenId != null) store.tutorialPage(userId, reopenId) else 0
                )
                currentTutorialId()?.let { store.markViewed(userId, it) }
                persistTutorial(currentTutorialId(), "VIEWED")
            }
            Onb02FlowKind.EVENT_QUEUE -> startEventQueue()
            Onb02FlowKind.ADD_FUNCTION_LATER -> {
                _ui.value = _ui.value.copy(phase = Onb02Phase.SELECT)
            }
            Onb02FlowKind.FULL_ONBOARDING, Onb02FlowKind.EXISTING_USER_T00 -> resumeOrStartFresh()
        }
        ensureDefaultPersonSelected()
    }

    private fun ensureDefaultPersonSelected() {
        val state = _ui.value
        if (state.kind != Onb02FlowKind.FULL_ONBOARDING &&
            state.kind != Onb02FlowKind.EXISTING_USER_T00
        ) {
            return
        }
        if (state.phase != Onb02Phase.SELECT) return
        if (state.selection.actorKind != null) return
        _ui.value = state.copy(
            selection = ProfileActorTaxonomy.applyActor(ProfileActorKind.PERSON, state.selection)
        )
    }

    private fun resumeOrStartFresh() {
        val t00Done = Onb02Planner.tutorialFinished(store.progress(userId, TutorialId.T00_MULTI_FUNCTION_INTRO))
        if (!t00Done) {
            _ui.value = _ui.value.copy(
                phase = Onb02Phase.INTRO,
                stepIndex = store.tutorialPage(userId, TutorialId.T00_MULTI_FUNCTION_INTRO)
            )
            store.markViewed(userId, TutorialId.T00_MULTI_FUNCTION_INTRO)
            persistTutorial(TutorialId.T00_MULTI_FUNCTION_INTRO, "VIEWED")
            return
        }
        if (!store.selectionConfirmed(userId)) {
            _ui.value = _ui.value.copy(phase = Onb02Phase.SELECT, stepIndex = 0)
            ensureDefaultPersonSelected()
            return
        }
        startPlan(_ui.value.selection, resume = true)
    }

    fun selectActor(kind: ProfileActorKind) {
        if (kind == ProfileActorKind.PERSON) return
        val current = _ui.value.selection.actorKind
        if (current == kind) {
            _ui.value = _ui.value.copy(
                selection = ProfileActorTaxonomy.applyActor(ProfileActorKind.PERSON, _ui.value.selection)
            )
            return
        }
        _ui.value = _ui.value.copy(selection = ProfileActorTaxonomy.applyActor(kind, _ui.value.selection))
    }

    fun selectAddFunctionGroup(group: AddFunctionGroup) {
        when (group.kind) {
            AddFunctionGroupKind.PROFESSIONAL ->
                _ui.value = _ui.value.copy(phase = Onb02Phase.PROFESSIONAL_SETUP)
            AddFunctionGroupKind.BUSINESS ->
                _ui.value = _ui.value.copy(phase = Onb02Phase.BUSINESS_SETUP)
            AddFunctionGroupKind.RESCUER,
            AddFunctionGroupKind.SHELTER,
            AddFunctionGroupKind.FOSTER ->
                group.leaf?.let { selectAddable(it) }
        }
    }

    fun confirmActor() {
        val kind = _ui.value.selection.actorKind ?: return
        when {
            ProfileActorTaxonomy.needsProfessionalLevel(kind) -> {
                _ui.value = _ui.value.copy(phase = Onb02Phase.PROFESSIONAL_SETUP)
            }
            ProfileActorTaxonomy.needsBusinessLevel(kind) -> {
                _ui.value = _ui.value.copy(phase = Onb02Phase.BUSINESS_SETUP)
            }
            else -> confirmSelection()
        }
    }

    fun selectProfessionalSpecialty(specialty: IndependentProfessionalSpecialty) {
        _ui.value = _ui.value.copy(
            selection = ProfileActorTaxonomy.applyProfessional(specialty, _ui.value.selection),
            errorMessage = null
        )
    }

    fun confirmProfessionalSpecialty() {
        if (_ui.value.selection.professionalSpecialty == null) return
        confirmSelection()
    }

    fun selectBusinessKind(kind: OrganizationKindOption) {
        _ui.value = _ui.value.copy(
            selection = ProfileActorTaxonomy.applyBusiness(kind, _ui.value.selection)
                .copy(petFriendlySubtype = if (kind == OrganizationKindOption.PET_FRIENDLY_VENUE) _ui.value.selection.petFriendlySubtype else null)
        )
    }

    fun setPetFriendlySubtype(code: String) {
        _ui.value = _ui.value.copy(
            selection = _ui.value.selection.copy(petFriendlySubtype = code)
        )
    }

    fun confirmBusinessKind() {
        val kind = _ui.value.selection.organizationKind ?: return
        if (kind == OrganizationKindOption.PET_FRIENDLY_VENUE &&
            !com.comunidapp.app.domain.business.PetFriendlyVenueSubtype.required(_ui.value.selection.petFriendlySubtype)
        ) {
            return
        }
        if (_ui.value.kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
            _ui.value = _ui.value.copy(phase = Onb02Phase.ORG_SETUP)
            return
        }
        confirmSelection()
    }

    fun backToActorSelector() {
        _ui.value = _ui.value.copy(phase = Onb02Phase.SELECT)
        ensureDefaultPersonSelected()
    }

    /**
     * In-flow back. Returns true when the host must stay inside ONB-02.
     */
    fun goBack(): Boolean {
        val state = _ui.value
        return when (state.phase) {
            Onb02Phase.INTRO -> {
                if (state.stepIndex > 0) {
                    setTutorialStep(state.stepIndex - 1)
                }
                true
            }
            Onb02Phase.SELECT -> {
                if (state.kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
                    false
                } else {
                    _ui.value = state.copy(
                        phase = Onb02Phase.INTRO,
                        stepIndex = store.tutorialPage(userId, TutorialId.T00_MULTI_FUNCTION_INTRO)
                    )
                    true
                }
            }
            Onb02Phase.PROFESSIONAL_SETUP,
            Onb02Phase.BUSINESS_SETUP -> {
                backToActorSelector()
                true
            }
            Onb02Phase.ORG_SETUP -> {
                val actor = state.selection.actorKind
                _ui.value = state.copy(
                    phase = when {
                        actor == ProfileActorKind.BUSINESS -> Onb02Phase.BUSINESS_SETUP
                        state.kind == Onb02FlowKind.ADD_FUNCTION_LATER &&
                            state.selection.organizationKind != OrganizationKindOption.SHELTER &&
                            state.selection.organizationKind != null -> Onb02Phase.BUSINESS_SETUP
                        else -> Onb02Phase.SELECT
                    }
                )
                if (_ui.value.phase == Onb02Phase.SELECT) ensureDefaultPersonSelected()
                true
            }
            Onb02Phase.TUTORIAL -> {
                if (state.stepIndex > 0) {
                    setTutorialStep(state.stepIndex - 1)
                    return true
                }
                if (state.planIndex > 0) {
                    applyPlanIndex(state.plan, state.planIndex - 1, state.selection, state.queue)
                    return true
                }
                _ui.value = state.copy(phase = Onb02Phase.SELECT, stepIndex = 0)
                ensureDefaultPersonSelected()
                true
            }
            Onb02Phase.DONE -> false
        }
    }

    fun occupiedForAddFunction(): OccupiedFunctions {
        val stored = store.selection(userId)
        val caps = runCatching {
            (capabilityOverride ?: com.comunidapp.app.data.provider.DataProvider.personCapabilityRepository)
                .cachedActive()
        }.getOrDefault(emptySet())
        return OccupiedFunctions(
            extras = stored.extras,
            capabilities = caps,
            contexts = OperationalContextProvider.available.value
        )
    }

    fun selectAddable(option: AddFunctionOption) {
        if (option.function == LeoverFunction.PROFILE_PERSONAL) return
        if (AddFunctionCatalog.isOccupied(option, occupiedForAddFunction())) return
        val next = when {
            option.professionalSpecialty != null ->
                ProfileActorTaxonomy.applyProfessional(option.professionalSpecialty, _ui.value.selection)
            option.organizationKind == OrganizationKindOption.SHELTER ->
                ProfileActorTaxonomy.applyActor(ProfileActorKind.REFUGE, _ui.value.selection)
            option.organizationKind != null ->
                ProfileActorTaxonomy.applyBusiness(option.organizationKind, _ui.value.selection)
            option.function == LeoverFunction.RESCUER ->
                ProfileActorTaxonomy.applyActor(ProfileActorKind.INDEPENDENT_RESCUER, _ui.value.selection)
            option.function == LeoverFunction.FOSTER ->
                ProfileActorTaxonomy.applyActor(ProfileActorKind.FOSTER, _ui.value.selection)
            else -> _ui.value.selection.copy(extras = setOf(option.function))
        }
        _ui.value = _ui.value.copy(selection = next)
        when {
            option.organizationKind != null -> {
                _ui.value = _ui.value.copy(phase = Onb02Phase.ORG_SETUP)
            }
            else -> confirmSelection()
        }
    }

    fun toggleExtra(function: LeoverFunction) {
        if (function.isBaseProfile) return
        _ui.value = _ui.value.copy(selection = _ui.value.selection.withToggled(function))
    }

    fun setOrganizationAction(action: OrganizationSetupAction) {
        _ui.value = _ui.value.copy(selection = _ui.value.selection.copy(organizationAction = action))
    }

    fun setOrganizationKind(kind: OrganizationKindOption) {
        _ui.value = _ui.value.copy(selection = _ui.value.selection.copy(organizationKind = kind))
    }

    fun skipCurrentTutorial() {
        skipRemainingTutorials()
    }

    fun skipRemainingTutorials() {
        val state = _ui.value
        if (state.phase == Onb02Phase.INTRO &&
            (state.kind == Onb02FlowKind.FULL_ONBOARDING || state.kind == Onb02FlowKind.EXISTING_USER_T00)
        ) {
            currentTutorialId()?.let {
                store.markSkipped(userId, it)
                persistTutorial(it, "SKIPPED")
            }
            advanceAfterTutorial()
            return
        }
        if (state.kind == Onb02FlowKind.REOPEN_FROM_HELP) {
            currentTutorialId()?.let {
                store.markSkipped(userId, it)
                persistTutorial(it, "SKIPPED")
            }
            finish()
            return
        }
        val remaining = if (state.plan.isNotEmpty()) {
            state.plan.drop(state.planIndex)
        } else {
            emptyList()
        }
        remaining.forEach { item ->
            item.tutorialId?.let {
                store.markSkipped(userId, it)
                persistTutorial(it, "SKIPPED")
            }
        }
        currentTutorialId()?.let {
            store.markSkipped(userId, it)
            persistTutorial(it, "SKIPPED")
        }
        finish()
    }

    fun nextStepOrFinish() {
        val tutorial = _ui.value.currentTutorial ?: return
        val lastStep = _ui.value.stepIndex >= tutorial.steps.lastIndex
        if (!lastStep) {
            val next = _ui.value.stepIndex + 1
            currentTutorialId()?.let { store.markTutorialPage(userId, it, next) }
            _ui.value = _ui.value.copy(stepIndex = next)
            return
        }
        currentTutorialId()?.let {
            store.markCompleted(userId, it)
            persistTutorial(it, "COMPLETED")
        }
        advanceAfterTutorial()
    }

    fun confirmSelection() {
        val state = _ui.value
        val selectedExtras = if (state.kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
            state.selection.extras
        } else {
            state.selection.extras
        }
        if (state.kind == Onb02FlowKind.ADD_FUNCTION_LATER &&
            LeoverFunction.VETERINARY_PROFESSIONAL in selectedExtras
        ) {
            viewModelScope.launch {
                val persisted = persistIndependentVeterinary(userId)
                val active = persisted.getOrDefault(false)
                if (!active) {
                    _ui.value = _ui.value.copy(
                        errorMessage = "No pudimos activar Veterinario independiente. Intentá de nuevo."
                    )
                    return@launch
                }
                completeConfirmSelection()
            }
            return
        }
        completeConfirmSelection()
    }

    private fun completeConfirmSelection() {
        val state = _ui.value
        val selected = if (state.kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
            newlyAdded = state.selection.extras
            val merged = store.addExtras(userId, state.selection.extras)
            val next = merged.copy(
                organizationAction = state.selection.organizationAction ?: merged.organizationAction,
                organizationKind = state.selection.organizationKind ?: merged.organizationKind,
                actorKind = state.selection.actorKind ?: merged.actorKind,
                professionalSpecialty = state.selection.professionalSpecialty ?: merged.professionalSpecialty
            )
            store.saveSelection(userId, next)
            next
        } else {
            newlyAdded = emptySet()
            store.saveSelection(userId, state.selection)
            state.selection
        }
        persistCapabilities(selected)
        store.markSelectionConfirmed(userId)
        persistOnb02FlowCompleted()
        startPlan(selected, resume = false)
    }

    fun confirmOrganizationSetup() {
        val state = _ui.value
        newlyAdded = state.selection.extras
        if (state.kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
            val merged = store.addExtras(userId, state.selection.extras)
            store.saveSelection(
                userId,
                merged.copy(
                    organizationAction = state.selection.organizationAction ?: merged.organizationAction,
                    organizationKind = state.selection.organizationKind ?: merged.organizationKind,
                    actorKind = state.selection.actorKind ?: merged.actorKind
                )
            )
        } else {
            store.saveSelection(userId, state.selection)
        }
        persistCapabilities(state.selection)
        val kind = state.selection.organizationKind
        val commercial = kind != null &&
            kind != OrganizationKindOption.SHELTER &&
            kind != OrganizationKindOption.OTHER_SERVICE
        val consumed: (TutorialId) -> Boolean = { id ->
            Onb02Planner.tutorialFinished(store.progress(userId, id))
        }
        val queued = TutorialQueueResolver.queue(
            event = CanonicalTutorialEvent.ORGANIZATION_CREATED,
            consumed = consumed,
            organizationKind = kind,
            commercialOrg = commercial,
            availableContextCount = 2,
            landingRouteHint = com.comunidapp.app.domain.onboarding.onb02.FunctionSetupMapping
                .landingRouteForOrganization(kind)
        )
        PendingTutorialQueue.set(
            PendingTutorialRun(
                tutorials = queued.tutorials,
                landingRoute = queued.landingRouteHint,
                popSetupRoute = com.comunidapp.app.navigation.NavRoutes.CREATE_ORGANIZATION
            )
        )
        finish()
    }

    fun finish() {
        val kind = _ui.value.kind
        when (kind) {
            Onb02FlowKind.FULL_ONBOARDING,
            Onb02FlowKind.EXISTING_USER_T00 -> {
                store.markCompleted(userId)
                persistOnb02FlowCompleted()
            }
            Onb02FlowKind.ADD_FUNCTION_LATER,
            Onb02FlowKind.REOPEN_FROM_HELP,
            Onb02FlowKind.EVENT_QUEUE -> Unit
        }
        _ui.value = _ui.value.copy(phase = Onb02Phase.DONE)
    }

    fun setupRouteAfterTutorials(): String? {
        if (_ui.value.kind == Onb02FlowKind.REOPEN_FROM_HELP) {
            return if (_ui.value.currentTutorial?.id == TutorialId.T10B_SHELTER) {
                com.comunidapp.app.navigation.NavRoutes.leoverVerification("SHELTER")
            } else {
                null
            }
        }
        if (_ui.value.kind == Onb02FlowKind.EVENT_QUEUE) {
            return eventLandingRoute
                ?: com.comunidapp.app.domain.onboarding.onb03.PendingTutorialQueue.peek()?.landingRoute
        }
        val extras = when (_ui.value.kind) {
            Onb02FlowKind.ADD_FUNCTION_LATER ->
                newlyAdded.ifEmpty { _ui.value.selection.extras }
            else -> store.selection(userId).extras
        }
        val action = store.selection(userId).organizationAction
            ?: _ui.value.selection.organizationAction
        val orgKind = store.selection(userId).organizationKind
            ?: _ui.value.selection.organizationKind
        val mapped = com.comunidapp.app.domain.onboarding.onb02.FunctionSetupMapping.setupRouteAfterSelection(
            extras = extras,
            organizationAction = action
        )
        val route = when {
            mapped != null &&
                mapped.startsWith("create_organization") &&
                orgKind != null ->
                com.comunidapp.app.navigation.NavRoutes.createOrganization(
                    preselect = orgKind.name,
                    welfare = orgKind == OrganizationKindOption.SHELTER
                )
            mapped != null -> mapped
            extras.contains(LeoverFunction.FOSTER) -> com.comunidapp.app.navigation.NavRoutes.FOSTER_PLACEMENTS
            extras.contains(LeoverFunction.RESCUER) -> com.comunidapp.app.navigation.NavRoutes.HOME
            _ui.value.kind == Onb02FlowKind.ADD_FUNCTION_LATER ->
                com.comunidapp.app.navigation.NavRoutes.HOME
            else -> null
        }
        return route
    }

    private suspend fun persistIndependentVeterinary(userId: String): Result<Boolean> {
        persistIndependentVeterinaryOverride?.let { return it(userId) }
        return persistVeterinaryIndependentDefault(userId)
    }

    private suspend fun persistVeterinaryIndependentDefault(userId: String): Result<Boolean> {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        }
        val upsert = com.comunidapp.app.data.provider.DataProvider.serviceRepository.upsertServiceProfile(
            com.comunidapp.app.data.model.ServiceProfile(
                id = "",
                ownerId = userId,
                category = com.comunidapp.app.data.model.ServiceCategory.VET,
                name = com.comunidapp.app.domain.onboarding.onb02.IndependentVeterinaryActivation.DISPLAY_NAME,
                location = ""
            )
        )
        val providerId = upsert.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(upsert.exceptionOrNull() ?: IllegalStateException("VET_PERSIST_FAILED"))
        val created = com.comunidapp.app.domain.context.OperationalContext.Provider(
            entityId = providerId,
            displayName = com.comunidapp.app.domain.onboarding.onb02.IndependentVeterinaryActivation.DISPLAY_NAME,
            category = com.comunidapp.app.domain.onboarding.onb02.IndependentVeterinaryActivation.STORAGE_CATEGORY
        )
        repeat(4) { attempt ->
            runCatching { OperationalContextProvider.refresh(userId) }
            val fromBackend = com.comunidapp.app.domain.onboarding.onb02.IndependentVeterinaryActivation
                .isPersistedActive(OperationalContextProvider.available.value)
            if (fromBackend) {
                runCatching { OperationalContextProvider.activateNewlyCreated(created) }
                return Result.success(true)
            }
            if (attempt < 3) kotlinx.coroutines.delay(350)
        }
        return Result.failure(IllegalStateException("VET_READBACK_EMPTY"))
    }

    private fun persistCapabilities(selection: FunctionSelection) {
        val repo = capabilityOverride
            ?: runCatching { com.comunidapp.app.data.provider.DataProvider.personCapabilityRepository }
                .getOrNull()
        viewModelScope.launch {
            val focus = newlyAdded.ifEmpty { selection.extras }
            if (LeoverFunction.RESCUER in selection.extras) {
                runCatching { repo?.setActive(PersonCapabilityCode.RESCUER, true) }
            }
            if (LeoverFunction.FOSTER in selection.extras) {
                runCatching { repo?.setActive(PersonCapabilityCode.FOSTER, true) }
                runCatching {
                    com.comunidapp.app.data.provider.DataProvider.fosterHomeRepository.createFosterHome(
                        com.comunidapp.app.data.repository.CreateFosterHomeInput(
                            displayName = "Hogar de tránsito",
                            totalCapacity = 1,
                            acceptedSpecies = emptySet(),
                            acceptedSizes = emptySet(),
                            zoneText = "",
                            activate = true
                        )
                    )
                }
            }
            if (LeoverFunction.VETERINARY_PROFESSIONAL in selection.extras) {
                runCatching { persistIndependentVeterinary(userId) }
            }
            runCatching { OperationalContextProvider.refresh(userId) }
            val target = com.comunidapp.app.domain.context.NewContextActivation.contextForSelection(
                userId = userId,
                selection = selection,
                available = OperationalContextProvider.available.value,
                prefer = focus.firstOrNull()
            )
            if (target != null && target !is com.comunidapp.app.domain.context.OperationalContext.Personal) {
                runCatching { OperationalContextProvider.activateNewlyCreated(target) }
            }
        }
    }

    fun setTutorialStep(index: Int) {
        val tutorial = _ui.value.currentTutorial ?: return
        val coerced = index.coerceIn(0, tutorial.steps.lastIndex)
        currentTutorialId()?.let { store.markTutorialPage(userId, it, coerced) }
        _ui.value = _ui.value.copy(stepIndex = coerced)
    }

    private fun startPlan(selection: FunctionSelection, resume: Boolean) {
        val t11Done = Onb02Planner.tutorialFinished(store.progress(userId, TutorialId.T11_USE_LEOVER_AS))
        val t01Done = Onb02Planner.tutorialFinished(store.progress(userId, TutorialId.T01_PROFILE_PERSONAL))
        val includeT01 = Onb02Planner.shouldIncludeT01(_ui.value.kind, t01Done)
        val plan = when (_ui.value.kind) {
            Onb02FlowKind.ADD_FUNCTION_LATER -> Onb02Planner.buildInHostPlan(
                selection = FunctionSelection(
                    extras = newlyAdded,
                    organizationAction = selection.organizationAction,
                    organizationKind = selection.organizationKind
                ),
                t11AlreadyCompleted = t11Done,
                includeT01 = false
            )
            Onb02FlowKind.REOPEN_FROM_HELP -> _ui.value.plan
            Onb02FlowKind.EVENT_QUEUE -> _ui.value.plan
            else -> Onb02Planner.buildInHostPlan(selection, t11Done, includeT01 = includeT01)
        }
        val queue = plan.mapNotNull { it.tutorialId }
        val startIndex = Onb02Planner.firstIncompleteIndex(plan, selection) { id ->
            Onb02Planner.tutorialFinished(store.progress(userId, id))
        } ?: plan.size
        if (startIndex >= plan.size) {
            finish()
            return
        }
        applyPlanIndex(plan, startIndex, selection, queue)
    }

    private fun startEventQueue() {
        val run = PendingTutorialQueue.consume()
        if (run == null || run.tutorials.isEmpty()) {
            finish()
            return
        }
        eventLandingRoute = run.landingRoute
        val plan = run.tutorials.map { id ->
            Onb02PlanItem(
                id = "tut_${id.key}",
                kind = Onb02StepKind.TUTORIAL,
                function = null,
                tutorialId = id
            )
        }
        applyPlanIndex(plan, 0, _ui.value.selection, run.tutorials)
    }

    private fun applyPlanIndex(
        plan: List<Onb02PlanItem>,
        index: Int,
        selection: FunctionSelection,
        queue: List<TutorialId>
    ) {
        val item = plan[index]
        val phase = when (item.kind) {
            Onb02StepKind.TUTORIAL -> Onb02Phase.TUTORIAL
            Onb02StepKind.ORG_CHOICE -> Onb02Phase.ORG_SETUP
        }
        _ui.value = _ui.value.copy(
            phase = phase,
            selection = selection,
            plan = plan,
            planIndex = index,
            queue = queue,
            queueIndex = queue.indexOf(item.tutorialId).coerceAtLeast(0),
            stepIndex = item.tutorialId?.let { store.tutorialPage(userId, it) } ?: 0
        )
        item.tutorialId?.let {
            store.markViewed(userId, it)
            persistTutorial(it, "VIEWED")
        }
    }

    private fun startTutorialQueue(selection: FunctionSelection) = startPlan(selection, resume = false)

    private fun advanceAfterTutorial() {
        val state = _ui.value
        when (state.kind) {
            Onb02FlowKind.REOPEN_FROM_HELP -> finish()
            Onb02FlowKind.FULL_ONBOARDING, Onb02FlowKind.EXISTING_USER_T00 -> {
                if (state.phase == Onb02Phase.INTRO) {
                    _ui.value = state.copy(phase = Onb02Phase.SELECT, stepIndex = 0)
                    ensureDefaultPersonSelected()
                } else {
                    advancePlan()
                }
            }
            Onb02FlowKind.ADD_FUNCTION_LATER,
            Onb02FlowKind.EVENT_QUEUE -> advancePlan()
        }
    }

    private fun advancePlan() {
        val state = _ui.value
        if (state.plan.isEmpty()) {
            advanceQueue()
            return
        }
        val next = state.planIndex + 1
        if (next >= state.plan.size) {
            finish()
            return
        }
        applyPlanIndex(state.plan, next, state.selection, state.queue)
    }

    private fun advanceQueue() {
        val state = _ui.value
        val next = state.queueIndex + 1
        if (next >= state.queue.size) {
            finish()
            return
        }
        _ui.value = state.copy(queueIndex = next, stepIndex = 0)
        store.markViewed(userId, state.queue[next])
        persistTutorial(state.queue[next], "VIEWED")
    }

    private fun persistTutorial(id: TutorialId?, state: String) {
        val tutorial = id ?: return
        if (userId.isBlank()) return
        viewModelScope.launch {
            runCatching { CanonicalTutorialProgressRepository.upsert(tutorial, state) }
        }
    }

    private fun persistOnb02FlowCompleted() {
        if (userId.isBlank()) return
        viewModelScope.launch {
            runCatching { CanonicalTutorialProgressRepository.upsertFlowCompleted() }
        }
    }

    private fun currentTutorialId(): TutorialId? = _ui.value.currentTutorial?.id
}
