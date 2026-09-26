package com.comunidapp.app.ui.screens.onboarding.onb02

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.comunidapp.app.domain.onboarding.onb02.TutorialStep
import com.comunidapp.app.domain.onboarding.onb02.TutorialVisual
import com.comunidapp.app.ui.theme.LeoSectionTitle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.context.ContextHumanLabels
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionCatalog
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionGroup
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionOption
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.onboarding.onb02.IndependentProfessionalSpecialty
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.Onb02Copy
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.Onb02Planner
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.OrganizationSetupAction
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorKind
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorTaxonomy
import com.comunidapp.app.domain.onboarding.onb02.TutorialCatalog
import com.comunidapp.app.domain.onboarding.onb02.TutorialDefinition
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoVerTutorialPager
import com.comunidapp.app.ui.components.v2.V2NavRow
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoPageTitle
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import kotlinx.coroutines.launch

@Composable
fun Onb02HostScreen(
    kind: Onb02FlowKind,
    reopenId: TutorialId? = null,
    onFinished: (setupRoute: String?) -> Unit,
    viewModel: Onb02ViewModel = viewModel()
) {
    val ui by viewModel.ui.collectAsState()

    LaunchedEffect(kind, reopenId) {
        viewModel.start(kind, reopenId)
    }

    LaunchedEffect(ui.phase) {
        if (ui.phase == Onb02Phase.DONE) {
            onFinished(viewModel.setupRouteAfterTutorials())
        }
    }

    BackHandler(enabled = ui.phase != Onb02Phase.DONE) {
        if (!viewModel.goBack()) {
            onFinished(null)
        }
    }

    VisualDirectionPilot {
    when (ui.phase) {
        Onb02Phase.INTRO, Onb02Phase.TUTORIAL -> {
            val tutorial = ui.currentTutorial
            if (tutorial != null) {
                LeoVerTutorialPager(
                    definition = tutorial,
                    stepIndex = ui.stepIndex,
                    onPrimary = viewModel::nextStepOrFinish,
                    onSkip = viewModel::skipCurrentTutorial,
                    onPageChange = viewModel::setTutorialStep
                )
            }
        }
        Onb02Phase.SELECT -> {
            if (ui.kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
                AddFunctionCatalogScreen(
                    groups = AddFunctionCatalog.groupedAvailable(viewModel.occupiedForAddFunction()),
                    onSelectGroup = viewModel::selectAddFunctionGroup,
                    onBack = {
                        if (!viewModel.goBack()) onFinished(null)
                    }
                )
            } else {
                FunctionSelectorScreen(
                    selectedActor = ui.selection.actorKind,
                    onSelect = viewModel::selectActor,
                    onContinue = viewModel::confirmActor
                )
            }
        }
        Onb02Phase.PROFESSIONAL_SETUP -> ProfessionalServiceSelectorScreen(
            selected = ui.selection.professionalSpecialty,
            specialties = if (ui.kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
                AddFunctionCatalog.availableProfessional(viewModel.occupiedForAddFunction())
                    .mapNotNull { it.professionalSpecialty }
            } else {
                ProfileActorTaxonomy.professionalSpecialties
            },
            onSelect = viewModel::selectProfessionalSpecialty,
            onContinue = viewModel::confirmProfessionalSpecialty,
            onBack = viewModel::backToActorSelector,
            errorMessage = ui.errorMessage
        )
        Onb02Phase.BUSINESS_SETUP -> BusinessKindSelectorScreen(
            selected = ui.selection.organizationKind,
            selectedSubtype = ui.selection.petFriendlySubtype,
            kinds = if (ui.kind == Onb02FlowKind.ADD_FUNCTION_LATER) {
                AddFunctionCatalog.availableBusiness(viewModel.occupiedForAddFunction())
                    .mapNotNull { it.organizationKind }
            } else {
                null
            },
            onSelect = viewModel::selectBusinessKind,
            onSubtype = viewModel::setPetFriendlySubtype,
            onContinue = viewModel::confirmBusinessKind,
            onBack = viewModel::backToActorSelector
        )
        Onb02Phase.ORG_SETUP -> OrganizationFunctionSetupScreen(
            action = ui.selection.organizationAction,
            kind = ui.selection.organizationKind,
            showCommercialKinds = ProfileActorTaxonomy.showCommercialKindPicker(ui.selection),
            onAction = viewModel::setOrganizationAction,
            onKind = viewModel::setOrganizationKind,
            onContinue = viewModel::confirmOrganizationSetup,
            onBack = { viewModel.goBack() }
        )
        Onb02Phase.DONE -> Unit
    }
    }
}

@Composable
fun TutorialPagerScreen(
    definition: TutorialDefinition,
    stepIndex: Int,
    onPrimary: () -> Unit,
    onSkip: () -> Unit
) = LeoVerTutorialPager(definition, stepIndex, onPrimary, onSkip)

@Composable
fun AddFunctionCatalogScreen(
    groups: List<AddFunctionGroup>,
    onSelectGroup: (AddFunctionGroup) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = Onb02Copy.ADD_FUNCTION_LABEL,
                showBackButton = true,
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            Text(
                text = "Elegí una función nueva. Las que ya usás no se vuelven a ofrecer.",
                style = LeoCaption,
                color = BrandText
            )
            groups.forEach { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LeoDimens.RadiusCard))
                        .background(BrandWhite)
                        .clickable { onSelectGroup(group) }
                        .padding(LeoDimens.SpaceCompact)
                        .semantics { contentDescription = "add_function_${group.kind.name}" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.visibleLabel, style = LeoCardTitle, color = BrandText)
                        Text(group.subtitle, style = LeoCaption, color = MutedText)
                    }
                }
            }
            if (groups.isEmpty()) {
                Text(
                    "Ya tenés todas las funciones disponibles activas.",
                    style = LeoCaption,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
fun FunctionSelectorScreen(
    selectedActor: ProfileActorKind?,
    onSelect: (ProfileActorKind) -> Unit,
    onContinue: () -> Unit
) {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(title = Onb02Copy.SELECTOR_TITLE, showBackButton = false)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            Text(
                text = Onb02Copy.SELECTOR_SUBTITLE,
                style = LeoCaption,
                color = BrandText,
                modifier = Modifier.semantics {
                    contentDescription = "actor_selector_explanation"
                }
            )
            ProfileActorTaxonomy.firstLevel.forEach { actor ->
                val personLocked = actor == ProfileActorKind.PERSON
                ActorRow(
                    actor = actor,
                    selected = personLocked || selectedActor == actor,
                    locked = personLocked,
                    onSelect = { if (!personLocked) onSelect(actor) }
                )
            }
            Spacer(modifier = Modifier.height(LeoDimens.SpaceMd))
            LeoPrimaryButton(
                text = "Continuar",
                onClick = onContinue,
                enabled = true
            )
        }
    }
}

@Composable
private fun ActorRow(
    actor: ProfileActorKind,
    selected: Boolean,
    locked: Boolean = false,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !locked, onClick = onSelect)
            .padding(vertical = LeoDimens.SpaceCompact)
            .semantics { contentDescription = "actor_${actor.name}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
    ) {
        RadioButton(selected = selected, onClick = onSelect.takeUnless { locked })
        Column(modifier = Modifier.weight(1f)) {
            Text(actor.visibleLabel, style = LeoCardTitle, color = BrandText)
            Text(
                if (locked) "Siempre activo. ${actor.subtitle}" else actor.subtitle,
                style = LeoCaption,
                color = MutedText
            )
        }
    }
}

@Composable
fun ProfessionalServiceSelectorScreen(
    selected: IndependentProfessionalSpecialty?,
    specialties: List<IndependentProfessionalSpecialty> = ProfileActorTaxonomy.professionalSpecialties,
    onSelect: (IndependentProfessionalSpecialty) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    errorMessage: String? = null
) {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = Onb02Copy.PROFESSIONAL_SERVICE_TITLE,
                showBackButton = true,
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            specialties.forEach { specialty ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LeoDimens.RadiusCard))
                        .background(BrandWhite)
                        .clickable { onSelect(specialty) }
                        .padding(LeoDimens.SpaceCompact)
                        .semantics { contentDescription = "professional_${specialty.name}" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == specialty, onClick = { onSelect(specialty) })
                    Text(specialty.visibleLabel, style = LeoCardTitle, color = BrandText)
                }
            }
            errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(message, style = LeoCaption, color = BrandText)
            }
            LeoPrimaryButton(
                text = "Continuar",
                onClick = onContinue,
                enabled = selected != null
            )
        }
    }
}

@Composable
fun BusinessKindSelectorScreen(
    selected: OrganizationKindOption?,
    selectedSubtype: String? = null,
    kinds: List<OrganizationKindOption>? = null,
    onSelect: (OrganizationKindOption) -> Unit,
    onSubtype: (String) -> Unit = {},
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = Onb02Copy.BUSINESS_KIND_TITLE,
                showBackButton = true,
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            (kinds ?: ProfileActorTaxonomy.businessKinds).forEach { kind ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LeoDimens.RadiusCard))
                        .background(BrandWhite)
                        .clickable { onSelect(kind) }
                        .padding(LeoDimens.SpaceCompact)
                        .semantics { contentDescription = "business_${kind.name}" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == kind, onClick = { onSelect(kind) })
                    Text(
                        ProfileActorTaxonomy.businessVisibleLabel(kind),
                        style = LeoCardTitle,
                        color = BrandText
                    )
                }
            }
            if (selected == OrganizationKindOption.PET_FRIENDLY_VENUE) {
                Text("¿Qué tipo de lugar es?", style = LeoCardTitle, color = BrandText)
                com.comunidapp.app.domain.business.PetFriendlyVenueSubtype.entries.forEach { subtype ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubtype(subtype.storageCode) }
                            .padding(vertical = LeoDimens.SpaceSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedSubtype == subtype.storageCode,
                            onClick = { onSubtype(subtype.storageCode) }
                        )
                        Text(subtype.visibleLabel, style = LeoCaption, color = BrandText)
                    }
                }
            }
            LeoPrimaryButton(
                text = "Continuar",
                onClick = onContinue,
                enabled = selected != null && (
                    selected != OrganizationKindOption.PET_FRIENDLY_VENUE ||
                        com.comunidapp.app.domain.business.PetFriendlyVenueSubtype.required(selectedSubtype)
                    )
            )
        }
    }
}

@Composable
fun OrganizationFunctionSetupScreen(
    action: OrganizationSetupAction?,
    kind: OrganizationKindOption?,
    showCommercialKinds: Boolean = true,
    onAction: (OrganizationSetupAction) -> Unit,
    onKind: (OrganizationKindOption) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit = {}
) {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = Onb02Copy.ORG_SETUP_TITLE,
                showBackButton = true,
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            OrganizationSetupAction.entries.forEach { option ->
                val label = if (option == OrganizationSetupAction.CREATE) {
                    Onb02Copy.ORG_CREATE
                } else {
                    Onb02Copy.ORG_JOIN
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(option) }
                        .padding(vertical = LeoDimens.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = action == option, onClick = { onAction(option) })
                    Text(label, style = LeoCardTitle, color = BrandText)
                }
            }
            if (showCommercialKinds) {
                Text("¿Qué representás?", style = LeoCardTitle, color = BrandText)
                OrganizationKindOption.commercial.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onKind(option) }
                            .padding(vertical = LeoDimens.SpaceSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = kind == option, onClick = { onKind(option) })
                        Text(option.visibleLabel, style = LeoCaption, color = BrandText)
                    }
                }
            }
            LeoPrimaryButton(
                text = "Continuar",
                onClick = onContinue,
                enabled = action != null
            )
        }
    }
}

@Composable
fun UseLeoverAsScreen(
    @Suppress("UNUSED_PARAMETER") onAddFunction: () -> Unit,
    onNavigateBack: () -> Unit,
    onSelected: () -> Unit = onNavigateBack
) {
    val available by OperationalContextProvider.available.collectAsState()
    val active by OperationalContextProvider.active.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = Onb02Copy.USE_LEOVER_AS,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            available.forEach { ctx ->
                val selected = ctx.kind == active.kind && ctx.entityId == active.entityId
                val subtitle = ContextHumanLabels.switcherSubtitle(ctx)
                V2NavRow(
                    title = ctx.displayName,
                    description = if (selected) "$subtitle · Activo" else subtitle,
                    icon = if (ctx is OperationalContext.Personal) Icons.Default.Person else Icons.Default.SwapHoriz,
                    onClick = {
                        scope.launch {
                            OperationalContextProvider.select(ctx)
                            onSelected()
                        }
                    }
                )
            }
            // Add-function lives in Settings. Keep callback for tests/nav wiring.
        }
    }
    }
}

@Composable
fun HelpTutorialsScreen(
    onOpenTutorial: (TutorialId) -> Unit,
    onNavigateBack: () -> Unit
) {
    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = "Ayuda · ${Onb02Copy.HELP_TUTORIALS}",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Text(
                text = "Podés volver a ver cualquier tutorial. Omitirlos no bloquea LeoVer.",
                style = LeoCaption,
                color = MutedText
            )
            TutorialCatalog.libraryEntries().forEach { def ->
                V2NavRow(
                    title = def.libraryTitle,
                    description = def.libraryTitle,
                    icon = Icons.Default.PlayCircle,
                    onClick = { onOpenTutorial(def.id) }
                )
            }
        }
    }
    }
}
