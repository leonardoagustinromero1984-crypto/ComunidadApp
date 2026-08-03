package com.comunidapp.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.onboarding.OnboardingIntent
import com.comunidapp.app.domain.onboarding.OnboardingIntentRoutes
import com.comunidapp.app.domain.onboarding.OnboardingStep
import com.comunidapp.app.ui.components.BrandLogo
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.viewmodel.FirstRunOnboardingNavEffect
import com.comunidapp.app.viewmodel.FirstRunOnboardingViewModel

@Composable
fun FirstRunOnboardingScreen(
    onExit: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onOpenPrivacy: () -> Unit,
    forceVisualRestart: Boolean = false,
    viewModel: FirstRunOnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(forceVisualRestart) {
        if (forceVisualRestart) {
            viewModel.restartTutorialVisual()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navEffects.collect { effect ->
            when (effect) {
                is FirstRunOnboardingNavEffect.ExitOnboarding -> onExit()
                is FirstRunOnboardingNavEffect.NavigateToRoute -> {
                    onExit()
                    onNavigateToRoute(effect.route)
                }
                is FirstRunOnboardingNavEffect.OpenPrivacy -> onOpenPrivacy()
            }
        }
    }

    val step = uiState.progress.currentStep
    val showBack = step != OnboardingStep.WELCOME && step != OnboardingStep.COMPLETION

    Scaffold(
        topBar = {
            if (step != OnboardingStep.WELCOME) {
                ComunidappTopBar(
                    title = "LeoVer",
                    showBackButton = showBack,
                    onBackClick = viewModel::onBack
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.persistFailed) {
                    Text(
                        text = "No pudimos guardar el progreso local. Podés continuar igual.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep(viewModel)
                    OnboardingStep.IDENTITY -> InfoStep(
                        title = "Toda la información de tu mascota en un mismo perfil",
                        body = "Registrá sus datos, fotografías, responsables y acontecimientos importantes.",
                        indicator = viewModel.infoIndicatorLabel(step),
                        onNext = viewModel::onNextInfoStep,
                        onSkip = viewModel::onSkipTutorial
                    )
                    OnboardingStep.HELP_NETWORK -> InfoStep(
                        title = "Una red que puede activarse cuando hace falta",
                        body = "Publicá pérdidas o hallazgos, recibí avistamientos y conectate con personas y organizaciones cercanas.",
                        indicator = viewModel.infoIndicatorLabel(step),
                        onNext = viewModel::onNextInfoStep,
                        onSkip = viewModel::onSkipTutorial
                    )
                    OnboardingStep.COMMUNITY_AND_CARE -> InfoStep(
                        title = "Adopciones, tránsito, refugios y servicios",
                        body = "Participá, ayudá y encontrá opciones confiables según tus necesidades.",
                        indicator = viewModel.infoIndicatorLabel(step),
                        onNext = viewModel::onNextInfoStep,
                        onSkip = viewModel::onSkipTutorial
                    )
                    OnboardingStep.FIRST_INTENT -> IntentStep(
                        selected = uiState.progress.selectedIntent,
                        onSelect = viewModel::onSelectIntent,
                        onSkip = viewModel::onSkipTutorial
                    )
                    OnboardingStep.MINIMAL_SETUP -> MinimalSetupStep(
                        displayName = uiState.displayName,
                        zone = uiState.approximateZone,
                        error = uiState.profileSaveError,
                        onDisplayNameChange = viewModel::onDisplayNameChange,
                        onZoneChange = viewModel::onZoneChange,
                        onContinue = viewModel::onMinimalSetupContinue,
                        onSkip = viewModel::onMinimalSetupSkip
                    )
                    OnboardingStep.PRIVACY -> PrivacyStep(
                        onUnderstood = viewModel::onPrivacyUnderstood,
                        onReview = viewModel::onReviewPrivacy,
                        onSkip = viewModel::onSkipTutorial
                    )
                    OnboardingStep.COMPLETION -> CompletionStep(
                        intent = uiState.progress.selectedIntent ?: OnboardingIntent.EXPLORE,
                        onPrimary = viewModel::onCompletePrimaryAction,
                        onHome = viewModel::onGoToHome
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WelcomeStep(viewModel: FirstRunOnboardingViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        BrandLogo(modifier = Modifier.height(72.dp))
        Text(
            text = "Bienvenido a LeoVer",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Una identidad para cada mascota y una red para ayudarla durante toda su vida.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = viewModel::onBeginTutorial,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Comenzar")
        }
        OutlinedButton(
            onClick = viewModel::onExploreFirst,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Explorar primero")
        }
    }
}

@Composable
private fun InfoStep(
    title: String,
    body: String,
    indicator: String?,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        indicator?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(text = body, style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("Siguiente")
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Omitir tutorial")
        }
    }
}

@Composable
private fun IntentStep(
    selected: OnboardingIntent?,
    onSelect: (OnboardingIntent) -> Unit,
    onSkip: () -> Unit
) {
    val options = listOf(
        OnboardingIntent.REGISTER_PET to "Registrar mi mascota",
        OnboardingIntent.LOST_PET to "Buscar o informar una mascota perdida",
        OnboardingIntent.FOUND_ANIMAL to "Encontré un animal",
        OnboardingIntent.ADOPT to "Quiero adoptar",
        OnboardingIntent.OFFER_FOSTER to "Quiero ofrecer tránsito",
        OnboardingIntent.ORGANIZATION to "Participo en un refugio u organización",
        OnboardingIntent.VOLUNTEER to "Quiero ayudar como voluntario",
        OnboardingIntent.EXPLORE to "Solo quiero explorar"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "¿Qué querés hacer primero?",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        options.forEach { (intent, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == intent,
                        onClick = { onSelect(intent) }
                    )
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == intent, onClick = { onSelect(intent) })
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Omitir tutorial")
        }
    }
}

@Composable
private fun MinimalSetupStep(
    displayName: String,
    zone: String,
    error: String?,
    onDisplayNameChange: (String) -> Unit,
    onZoneChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Configuración inicial",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Solo pedimos lo mínimo. La zona es aproximada (por ejemplo: San Vicente, Buenos Aires).",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Nombre visible") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = zone,
            onValueChange = onZoneChange,
            label = { Text("Localidad o zona general") },
            placeholder = { Text("San Vicente, Buenos Aires") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Continuar")
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Omitir por ahora")
        }
    }
}

@Composable
private fun PrivacyStep(
    onUnderstood: () -> Unit,
    onReview: () -> Unit,
    onSkip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Vos decidís qué información compartir",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Tus datos personales y ubicaciones exactas no se mostrarán públicamente de forma automática. Cada publicación tendrá controles de privacidad.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onUnderstood, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Entendido")
        }
        OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Revisar privacidad")
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Omitir tutorial")
        }
    }
}

@Composable
private fun CompletionStep(
    intent: OnboardingIntent,
    onPrimary: () -> Unit,
    onHome: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Tu cuenta está lista",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Podés completar tu perfil y tus preferencias cuando quieras.",
            style = MaterialTheme.typography.bodyLarge
        )
        if (intent == OnboardingIntent.ORGANIZATION) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Opciones para organizaciones", style = MaterialTheme.typography.titleSmall)
                    Text("• Crear una organización", style = MaterialTheme.typography.bodySmall)
                    Text("• Ingresar con invitación", style = MaterialTheme.typography.bodySmall)
                    Text("• Explorar refugios", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(OnboardingIntentRoutes.primaryCtaLabel(intent))
        }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Ir al inicio")
        }
    }
}
