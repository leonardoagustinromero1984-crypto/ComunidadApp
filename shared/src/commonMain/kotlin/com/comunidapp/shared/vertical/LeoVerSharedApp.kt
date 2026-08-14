package com.comunidapp.shared.vertical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.adoption.AdoptionApplicationId
import com.comunidapp.shared.adoption.AdoptionApplicationRepository
import com.comunidapp.shared.adoption.AdoptionId
import com.comunidapp.shared.adoption.AdoptionRepository
import com.comunidapp.shared.auth.AppleSignInController
import com.comunidapp.shared.auth.AuthRepository
import com.comunidapp.shared.deeplink.DeepLinkNavigationController
import com.comunidapp.shared.deeplink.DeepLinkPendingStore
import com.comunidapp.shared.deeplink.DeepLinkTarget
import com.comunidapp.shared.deeplink.SharedDeepLinkLandingScreen
import com.comunidapp.shared.lostfound.LostFoundId
import com.comunidapp.shared.lostfound.LostFoundListFilter
import com.comunidapp.shared.lostfound.LostFoundRepository
import com.comunidapp.shared.media.MediaResolver
import com.comunidapp.shared.media.SharedRemoteImage
import com.comunidapp.shared.pets.PetDetailView
import com.comunidapp.shared.pets.PetSummary
import com.comunidapp.shared.pets.SharedPetsRepository
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import com.comunidapp.shared.profile.ProfileLoadState
import com.comunidapp.shared.profile.UserProfileRepository
import com.comunidapp.shared.profile.UserProfileSummary
import com.comunidapp.shared.push.PushInstallationRepository
import com.comunidapp.shared.push.PushRegistrationCoordinator
import com.comunidapp.shared.session.SessionDataMode
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.VerticalLoadState

private sealed class SharedRoute {
    data object Home : SharedRoute()
    data object Profile : SharedRoute()
    data object ProfileEdit : SharedRoute()
    data object Pets : SharedRoute()
    data class PetDetail(val petId: PetId) : SharedRoute()
    data object Alerts : SharedRoute()
    data object LostList : SharedRoute()
    data object FoundList : SharedRoute()
    data object LostFoundPublish : SharedRoute()
    data class LostFoundDetail(val id: LostFoundId, val backTo: SharedRoute) : SharedRoute()
    data object Adoptions : SharedRoute()
    data object AdoptionPublish : SharedRoute()
    data class AdoptionDetail(val id: AdoptionId) : SharedRoute()
    data class AdoptionApply(val id: AdoptionId, val title: String) : SharedRoute()
    data object MyApplications : SharedRoute()
    data object ReceivedApplications : SharedRoute()
    data class ApplicationReviewDetail(val id: AdoptionApplicationId) : SharedRoute()
    data object PetCreate : SharedRoute()
    data class DeepLinkLanding(val target: DeepLinkTarget) : SharedRoute()
}

/**
 * Shell vertical KMP:
 * Login (REAL_REMOTE) → Home → Perfil / Mascotas / Alertas (read + publish) / Adopciones.
 */
@Composable
fun LeoVerSharedApp(
    sessionRepository: SessionRepository,
    profileRepository: UserProfileRepository,
    petsRepository: SharedPetsRepository,
    lostFoundRepository: LostFoundRepository,
    adoptionRepository: AdoptionRepository,
    adoptionApplicationRepository: AdoptionApplicationRepository? = null,
    authRepository: AuthRepository? = sessionRepository as? AuthRepository,
    mediaResolver: MediaResolver? = null,
    imagePicker: ImagePicker? = null,
    deepLinkController: DeepLinkNavigationController? = null,
    pushInstallationRepository: PushInstallationRepository? = null,
    pushRegistrationCoordinator: PushRegistrationCoordinator? = null,
    appleSignInController: AppleSignInController? = null,
    onOpenLegacyPocs: (() -> Unit)? = null
) {
    var route by remember { mutableStateOf<SharedRoute>(SharedRoute.Home) }
    val controller = remember(deepLinkController) {
        deepLinkController ?: DeepLinkNavigationController()
    }
    val badge = VerticalDataBadge(
        sessionMode = sessionRepository.dataMode.name,
        profileMode = profileRepository.dataMode.name,
        petsMode = petsRepository.dataMode.name,
        lostFoundMode = lostFoundRepository.dataMode.name,
        adoptionMode = adoptionRepository.dataMode.name
    )

    val sessionVm = remember(sessionRepository, pushInstallationRepository) {
        SessionViewModelShared(sessionRepository, pushInstallationRepository)
    }
    DisposableEffect(sessionVm) { onDispose { sessionVm.clear() } }
    val session by sessionVm.state.collectAsState()

    LaunchedEffect(authRepository) {
        authRepository?.restoreSession()
    }
    LaunchedEffect(session, mediaResolver) {
        if (session !is SessionState.Authenticated) {
            mediaResolver?.clearCache()
        }
    }

    fun applyDeepLink(target: DeepLinkTarget) {
        when (target) {
            DeepLinkTarget.SafeHome -> route = SharedRoute.Home
            is DeepLinkTarget.Unsupported -> route = SharedRoute.DeepLinkLanding(target)
            else -> route = SharedRoute.DeepLinkLanding(target)
        }
    }

    LaunchedEffect(session, controller) {
        if (session is SessionState.Authenticated) {
            val fromStore = DeepLinkPendingStore.consume()
            val fromController = controller.consume()
            val target = fromController ?: fromStore
            if (target != null) {
                applyDeepLink(target)
            }
        } else if (
            session is SessionState.Unauthenticated ||
            session is SessionState.Expired
        ) {
            // Keep pending for post-login; do not clear.
        }
    }

    if (authRepository != null) {
        when (session) {
            SessionState.Unknown -> {
                Scaffold { padding ->
                    Column(
                        Modifier.padding(padding).fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text("Restaurando sesión…", Modifier.padding(top = 12.dp))
                    }
                }
                return
            }
            SessionState.Unauthenticated,
            SessionState.Expired,
            is SessionState.Error -> {
                if (route !is SharedRoute.DeepLinkLanding) {
                    route = SharedRoute.Home
                }
                val hint = when (val s = session) {
                    SessionState.Expired -> "Tu sesión expiró."
                    is SessionState.Error -> s.message
                    else -> null
                }
                SharedLoginScreen(
                    authRepository = authRepository,
                    sessionHint = hint,
                    appleSignInController = appleSignInController
                )
                return
            }
            is SessionState.Authenticated -> Unit
        }
    }

    when (val r = route) {
        SharedRoute.Home -> SharedHomeVerticalScreen(
            sessionRepository = sessionRepository,
            badge = badge,
            pushRegistrationCoordinator = pushRegistrationCoordinator,
            onOpenProfile = { route = SharedRoute.Profile },
            onOpenPets = { route = SharedRoute.Pets },
            onOpenAlerts = { route = SharedRoute.Alerts },
            onOpenAdoptions = { route = SharedRoute.Adoptions },
            onOpenLegacyPocs = onOpenLegacyPocs
        )
        SharedRoute.Profile -> SharedProfileScreen(
            sessionRepository = sessionRepository,
            profileRepository = profileRepository,
            mediaResolver = mediaResolver,
            onBack = { route = SharedRoute.Home },
            onEdit = { route = SharedRoute.ProfileEdit }
        )
        SharedRoute.ProfileEdit -> {
            val sessionUser = (session as? SessionState.Authenticated)?.user
            if (sessionUser == null) {
                route = SharedRoute.Home
            } else {
                var loaded by remember { mutableStateOf<UserProfileSummary?>(null) }
                LaunchedEffect(sessionUser.userId, profileRepository) {
                    profileRepository.observeMyProfile(sessionUser.userId).collect { st ->
                        if (st is ProfileLoadState.Content) loaded = st.profile
                    }
                }
                val profile = loaded
                if (profile == null) {
                    Scaffold { padding ->
                        Column(
                            Modifier.padding(padding).fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    SharedProfileEditScreen(
                        profile = profile,
                        profileRepository = profileRepository,
                        imagePicker = imagePicker,
                        onBack = { route = SharedRoute.Profile },
                        onSaved = { route = SharedRoute.Profile }
                    )
                }
            }
        }
        SharedRoute.Pets -> SharedPetsListScreen(
            sessionRepository = sessionRepository,
            petsRepository = petsRepository,
            mediaResolver = mediaResolver,
            onBack = { route = SharedRoute.Home },
            onOpenDetail = { route = SharedRoute.PetDetail(it) },
            onOpenCreate = { route = SharedRoute.PetCreate }
        )
        SharedRoute.PetCreate -> SharedPetCreateScreen(
            petsRepository = petsRepository,
            imagePicker = imagePicker,
            onBack = { route = SharedRoute.Pets },
            onCreated = { id -> route = SharedRoute.PetDetail(id) }
        )
        is SharedRoute.PetDetail -> SharedPetDetailScreen(
            petId = r.petId,
            petsRepository = petsRepository,
            mediaResolver = mediaResolver,
            onBack = { route = SharedRoute.Pets }
        )
        SharedRoute.Alerts -> SharedAlertsHubScreen(
            onBack = { route = SharedRoute.Home },
            onOpenLost = { route = SharedRoute.LostList },
            onOpenFound = { route = SharedRoute.FoundList },
            onOpenPublish = { route = SharedRoute.LostFoundPublish }
        )
        SharedRoute.LostList -> SharedLostFoundListScreen(
            title = "Mascotas perdidas",
            filter = LostFoundListFilter.LOST,
            lostFoundRepository = lostFoundRepository,
            mediaResolver = mediaResolver,
            onBack = { route = SharedRoute.Alerts },
            onOpenDetail = { route = SharedRoute.LostFoundDetail(it, SharedRoute.LostList) }
        )
        SharedRoute.FoundList -> SharedLostFoundListScreen(
            title = "Animales encontrados",
            filter = LostFoundListFilter.FOUND,
            lostFoundRepository = lostFoundRepository,
            mediaResolver = mediaResolver,
            onBack = { route = SharedRoute.Alerts },
            onOpenDetail = { route = SharedRoute.LostFoundDetail(it, SharedRoute.FoundList) }
        )
        SharedRoute.LostFoundPublish -> SharedLostFoundPublishScreen(
            lostFoundRepository = lostFoundRepository,
            imagePicker = imagePicker,
            onBack = { route = SharedRoute.Alerts },
            onPublished = { id ->
                route = SharedRoute.LostFoundDetail(id, SharedRoute.Alerts)
            }
        )
        is SharedRoute.LostFoundDetail -> SharedLostFoundDetailScreen(
            id = r.id,
            lostFoundRepository = lostFoundRepository,
            mediaResolver = mediaResolver,
            onBack = { route = r.backTo }
        )
        SharedRoute.Adoptions -> SharedAdoptionsListScreen(
            adoptionRepository = adoptionRepository,
            mediaResolver = mediaResolver,
            onBack = { route = SharedRoute.Home },
            onOpenDetail = { route = SharedRoute.AdoptionDetail(it) },
            onOpenPublish = { route = SharedRoute.AdoptionPublish },
            onOpenMyApplications = {
                if (adoptionApplicationRepository != null) {
                    route = SharedRoute.MyApplications
                }
            },
            onOpenReceivedApplications = {
                if (adoptionApplicationRepository != null) {
                    route = SharedRoute.ReceivedApplications
                }
            }
        )
        SharedRoute.AdoptionPublish -> SharedAdoptionPublishScreen(
            sessionRepository = sessionRepository,
            petsRepository = petsRepository,
            adoptionRepository = adoptionRepository,
            onBack = { route = SharedRoute.Adoptions },
            onPublished = { id -> route = SharedRoute.AdoptionDetail(id) }
        )
        is SharedRoute.AdoptionDetail -> SharedAdoptionDetailScreen(
            id = r.id,
            adoptionRepository = adoptionRepository,
            mediaResolver = mediaResolver,
            onBack = { route = SharedRoute.Adoptions },
            onApply = { title ->
                if (adoptionApplicationRepository != null) {
                    route = SharedRoute.AdoptionApply(r.id, title)
                }
            }
        )
        is SharedRoute.AdoptionApply -> {
            val appRepo = adoptionApplicationRepository
            if (appRepo == null) {
                route = SharedRoute.AdoptionDetail(r.id)
            } else {
                SharedAdoptionApplyScreen(
                    adoptionId = r.id,
                    adoptionTitle = r.title,
                    applicationRepository = appRepo,
                    onBack = { route = SharedRoute.AdoptionDetail(r.id) },
                    onSubmitted = { route = SharedRoute.MyApplications }
                )
            }
        }
        SharedRoute.MyApplications -> {
            val appRepo = adoptionApplicationRepository
            if (appRepo == null) {
                route = SharedRoute.Adoptions
            } else {
                SharedMyApplicationsScreen(
                    applicationRepository = appRepo,
                    onBack = { route = SharedRoute.Adoptions }
                )
            }
        }
        SharedRoute.ReceivedApplications -> {
            val appRepo = adoptionApplicationRepository
            if (appRepo == null) {
                route = SharedRoute.Adoptions
            } else {
                SharedReceivedApplicationsScreen(
                    applicationRepository = appRepo,
                    onBack = { route = SharedRoute.Adoptions },
                    onOpenDetail = { route = SharedRoute.ApplicationReviewDetail(it) }
                )
            }
        }
        is SharedRoute.ApplicationReviewDetail -> {
            val appRepo = adoptionApplicationRepository
            if (appRepo == null) {
                route = SharedRoute.Adoptions
            } else {
                SharedApplicationReviewDetailScreen(
                    applicationId = r.id,
                    applicationRepository = appRepo,
                    onBack = { route = SharedRoute.ReceivedApplications }
                )
            }
        }
        is SharedRoute.DeepLinkLanding -> SharedDeepLinkLandingScreen(
            target = r.target,
            onBack = { route = SharedRoute.Home },
            onOpenAdoptions = { route = SharedRoute.Adoptions },
            onOpenLost = { route = SharedRoute.LostList },
            onOpenFound = { route = SharedRoute.FoundList },
            onOpenPetsHub = { route = SharedRoute.Pets },
            onOpenHome = { route = SharedRoute.Home }
        )
    }
}

@Composable
private fun SharedHomeVerticalScreen(
    sessionRepository: SessionRepository,
    badge: VerticalDataBadge,
    pushRegistrationCoordinator: PushRegistrationCoordinator?,
    onOpenProfile: () -> Unit,
    onOpenPets: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenAdoptions: () -> Unit,
    onOpenLegacyPocs: (() -> Unit)?
) {
    val vm = remember(sessionRepository) { SessionViewModelShared(sessionRepository) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val session by vm.state.collectAsState()
    var pushStatus by remember { mutableStateOf<String?>(null) }
    val authLabel = if (sessionRepository.dataMode == SessionDataMode.REAL_REMOTE) {
        "Cerrar sesión"
    } else {
        "Cerrar sesión (local)"
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("LeoVer", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Tu comunidad de cuidado animal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                sessionLabel(session),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Datos: ${badge.sessionMode} · ${badge.profileMode} · ${badge.petsMode} · " +
                    "${badge.lostFoundMode} · ${badge.adoptionMode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth(), enabled = session is SessionState.Authenticated) {
                Text("Mi perfil")
            }
            Button(onClick = onOpenPets, modifier = Modifier.fillMaxWidth(), enabled = session is SessionState.Authenticated) {
                Text("Mis mascotas")
            }
            Button(onClick = onOpenAlerts, modifier = Modifier.fillMaxWidth()) {
                Text("Alertas")
            }
            Button(onClick = onOpenAdoptions, modifier = Modifier.fillMaxWidth()) {
                Text("Adopciones")
            }
            if (
                session is SessionState.Authenticated &&
                pushRegistrationCoordinator != null
            ) {
                OutlinedButton(
                    onClick = {
                        vm.requestPush(pushRegistrationCoordinator) { msg ->
                            pushStatus = msg
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Activar notificaciones")
                }
                pushStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (session is SessionState.Authenticated) {
                OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth()) {
                    Text(authLabel)
                }
            }
            if (onOpenLegacyPocs != null) {
                TextButton(onClick = onOpenLegacyPocs) {
                    Text("Herramientas de desarrollo")
                }
            }
        }
    }
}

private fun sessionLabel(state: SessionState): String = when (state) {
    SessionState.Unknown -> "Sesión: comprobando…"
    SessionState.Unauthenticated -> "Sesión: no iniciada"
    SessionState.Expired -> "Sesión: expirada"
    is SessionState.Error -> "Sesión: ${state.message}"
    is SessionState.Authenticated -> {
        val name = state.user.displayName ?: state.user.email ?: "usuario"
        "Hola, $name"
    }
}

@Composable
private fun SharedProfileScreen(
    sessionRepository: SessionRepository,
    profileRepository: UserProfileRepository,
    mediaResolver: MediaResolver?,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val vm = remember(sessionRepository, profileRepository) {
        ProfileViewModelShared(sessionRepository, profileRepository)
    }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Mi perfil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when (val s = state) {
                ProfileLoadState.Loading -> CenterLoading()
                is ProfileLoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is ProfileLoadState.Content -> {
                    ProfileContent(s.profile, mediaResolver)
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Text("Editar perfil")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(profile: UserProfileSummary, mediaResolver: MediaResolver?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SharedRemoteImage(
            mediaRef = profile.mediaRef,
            mediaResolver = mediaResolver,
            contentDescription = "Avatar de ${profile.displayName}",
            size = 96.dp
        )
        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
        profile.email?.let { Text(it) }
        profile.approximateLocation?.let {
            Text("Zona: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (profile.mediaRef == null && profile.avatarRef != null) {
            Text(
                "Avatar: referencia no resoluble en shared",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SharedPetsListScreen(
    sessionRepository: SessionRepository,
    petsRepository: SharedPetsRepository,
    mediaResolver: MediaResolver?,
    onBack: () -> Unit,
    onOpenDetail: (PetId) -> Unit,
    onOpenCreate: () -> Unit = {}
) {
    val vm = remember(sessionRepository, petsRepository) {
        PetListViewModelShared(sessionRepository, petsRepository)
    }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Mis mascotas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = onOpenCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar mascota")
            }
            TextButton(onClick = { vm.refresh() }) { Text("Actualizar") }
            when (val s = state) {
                VerticalLoadState.Loading -> CenterLoading()
                VerticalLoadState.Empty -> Text("Todavía no cargaste mascotas.")
                is VerticalLoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is VerticalLoadState.Content -> {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.data, key = { it.id.value }) { pet ->
                            PetRow(pet, mediaResolver) { onOpenDetail(pet.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PetRow(pet: PetSummary, mediaResolver: MediaResolver?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SharedRemoteImage(
            mediaRef = pet.mediaRef,
            mediaResolver = mediaResolver,
            contentDescription = "Foto de ${pet.displayName}",
            size = 56.dp
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pet.displayName, fontWeight = FontWeight.SemiBold)
            Text("${pet.speciesLabel} · ${pet.status.name}")
        }
    }
}

@Composable
private fun SharedPetDetailScreen(
    petId: PetId,
    petsRepository: SharedPetsRepository,
    mediaResolver: MediaResolver?,
    onBack: () -> Unit
) {
    val vm = remember(petId, petsRepository) { PetDetailViewModelShared(petId, petsRepository) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Detalle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when (val s = state) {
                VerticalLoadState.Loading -> CenterLoading()
                VerticalLoadState.Empty -> Text("Sin datos")
                is VerticalLoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is VerticalLoadState.Content -> PetDetailBody(s.data, mediaResolver)
            }
        }
    }
}

@Composable
private fun PetDetailBody(detail: PetDetailView, mediaResolver: MediaResolver?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SharedRemoteImage(
            mediaRef = detail.mediaRef,
            mediaResolver = mediaResolver,
            contentDescription = "Foto de ${detail.displayName}",
            size = 160.dp
        )
        Text(detail.displayName, style = MaterialTheme.typography.headlineSmall)
        Text("Especie: ${detail.speciesLabel}")
        detail.breedText?.let { Text("Raza: $it") }
        detail.sexLabel?.let { Text("Sexo: $it") }
        Text("Estado: ${detail.status.name}")
        detail.passportHint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CenterLoading() {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}
