package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.pets.PetCareTransferCopy
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.v2.V2NavRow
import com.comunidapp.app.ui.components.v2.V2PetsStrip
import com.comunidapp.app.ui.components.v2.V2SectionHeader
import com.comunidapp.app.ui.components.v2.V2SurfaceCard
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoPageTitle
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.pets.PetManagementContext
import com.comunidapp.app.domain.onboarding.onb02.Onb02Copy
import com.comunidapp.app.viewmodel.CareNetworkPersonViewModel
import com.comunidapp.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToMyPets: () -> Unit = {},
    onNavigateToAddPet: () -> Unit = {},
    onNavigateToMyAdoptions: () -> Unit = {},
    onNavigateToMyApplications: () -> Unit = {},
    onNavigateToReceivedApplications: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToFriendRequests: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToAdministration: () -> Unit = {},
    onNavigateToModeration: () -> Unit = {},
    onNavigateToPlatformAdmin: () -> Unit = {},
    onNavigateToCases: () -> Unit = {},
    onNavigateToAppealsStaff: () -> Unit = {},
    onNavigateToMyAppeals: () -> Unit = {},
    onNavigateToVerification: () -> Unit = {},
    onNavigateToMySupport: () -> Unit = {},
    onNavigateToSupportStaff: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToObservability: () -> Unit = {},
    onNavigateToSearchFriends: () -> Unit = {},
    onNavigateToMyFriends: () -> Unit = {},
    onNavigateToMiManada: () -> Unit = onNavigateToMyFriends,
    onNavigateToSavedPosts: () -> Unit = {},
    onNavigateToMyMemories: () -> Unit = {},
    onNavigateToAccountSecurity: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDonations: () -> Unit = {},
    onNavigateToMyEvents: () -> Unit = {},
    onNavigateToFirstRunTutorial: () -> Unit = {},
    onNavigateToUseLeoverAs: () -> Unit = {},
    onNavigateToHelpTutorials: () -> Unit = {},
    onNavigateToMyOrganizations: () -> Unit = {},
    onNavigateToPublish: () -> Unit = {},
    onNavigateToMyPublications: () -> Unit = {},
    onFriendClick: (String) -> Unit = {},
    onPetClick: (String) -> Unit = {},
    onOpenIncomingTransfer: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
    val preservedCallbacks = remember {
        listOf(
            onNavigateToMyAdoptions, onFriendClick, onNavigateToModeration, onNavigateToCases,
            onNavigateToAppealsStaff, onNavigateToVerification, onNavigateToSupportStaff,
            onNavigateToAudit, onNavigateToObservability, onNavigateToPlatformAdmin,
            onNavigateToMyAppeals, onNavigateToFriendRequests, onNavigateToNotifications,
            onNavigateToAccountSecurity, onNavigateToMyApplications, onNavigateToReceivedApplications,
            onNavigateToMyOrganizations, onNavigateToFirstRunTutorial, onNavigateToHelpTutorials
        )
    }

    val uiState by viewModel.uiState.collectAsState()
    val incomingInbox = DataProvider.incomingCareTransferInbox
    val incomingTransfers by incomingInbox.items.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val inboxScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, incomingInbox) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inboxScope.launch {
                    val probe = com.comunidapp.app.domain.perf.ScreenPerfProbe.begin("profile")
                    if (uiState.user != null || incomingTransfers.isNotEmpty()) {
                        probe.markFirstContent()
                    }
                    probe.network { incomingInbox.refresh("profile_resume") }
                    probe.finish("inbox=${incomingInbox.items.value.size}")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val showPets = uiState.user != null

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading && uiState.user == null && incomingTransfers.isEmpty() ->
                LoadingState(Modifier.padding(padding))
            uiState.user == null && incomingTransfers.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LeoEmptyState(
                        title = "Iniciá sesión para ver tu perfil",
                        message = "Tu perfil muestra publicaciones, mascotas y actividad.",
                        icon = Icons.Default.Pets
                    )
                }
            }
            else -> {
                val user = uiState.user
                if (user == null) {
                            IncomingCareTransfersSection(
                                transfers = incomingTransfers,
                                enabled = true,
                                onAccept = { transfer ->
                                    inboxScope.launch {
                                        incomingInbox.accept(transfer)
                                        onNavigateToMyPets()
                                    }
                                },
                                onReject = { transfer ->
                                    inboxScope.launch { incomingInbox.reject(transfer) }
                                },
                                modifier = Modifier.padding(padding)
                            )
                    return@Scaffold
                }
                val operationalContext by OperationalContextProvider.active.collectAsState()
                val showCareNetwork = PetManagementContext.keyOf(
                    operationalContext,
                    user.id
                ).kind == PetManagementContext.PERSON

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + LeoDimens.SpaceMd
                    ),
                    verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
                ) {
                    item(key = "header") {
                        PersonaProfileHeader(
                            user = user,
                            avatarUrl = uiState.avatarDisplayUrl,
                            postsCount = uiState.posts.size,
                            friendsCount = uiState.friends.size,
                            onSettings = onNavigateToSettings,
                            onEditProfile = onNavigateToEditProfile
                        )
                    }

                    item(key = "use_leover_as") {
                        CompactUseLeoverAsRow(onClick = onNavigateToUseLeoverAs)
                    }

                    if (incomingTransfers.isNotEmpty()) {
                        item(key = "incoming_care_transfers") {
                            IncomingCareTransfersSection(
                                transfers = incomingTransfers,
                                enabled = true,
                                onAccept = { transfer ->
                                    inboxScope.launch {
                                        incomingInbox.accept(transfer)
                                        onNavigateToMyPets()
                                    }
                                },
                                onReject = { transfer ->
                                    inboxScope.launch { incomingInbox.reject(transfer) }
                                }
                            )
                        }
                    }

                    if (showPets) {
                        item(key = "pets_header") {
                            V2SectionHeader(
                                title = "Mis mascotas",
                                actionLabel = "Ver todas",
                                onAction = onNavigateToMyPets,
                                modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd)
                            )
                        }
                        item(key = "pets_row") {
                            V2PetsStrip(
                                pets = uiState.pets,
                                onPetClick = onPetClick,
                                onAddPet = onNavigateToAddPet
                            )
                        }
                    }

                    if (showCareNetwork) {
                        item(key = "pet_responsible_invites") {
                            PetResponsibleInvitesSection()
                        }
                    }

                    item(key = "menu") {
                        Column(
                            modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd),
                            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
                        ) {
                            V2NavRow(
                                title = "Mis publicaciones",
                                description = "Lo que compartiste en Inicio",
                                icon = Icons.Default.PostAdd,
                                onClick = onNavigateToMyPublications
                            )
                            V2NavRow(
                                title = if (uiState.pendingFriendRequests > 0) {
                                    "Mi manada · ${uiState.pendingFriendRequests}"
                                } else {
                                    "Mi manada"
                                },
                                description = if (uiState.pendingFriendRequests > 0) {
                                    if (uiState.pendingFriendRequests == 1) {
                                        "1 solicitud pendiente"
                                    } else {
                                        "${uiState.pendingFriendRequests} solicitudes pendientes"
                                    }
                                } else {
                                    "Conexiones, solicitudes y personas"
                                },
                                icon = Icons.Default.People,
                                onClick = onNavigateToMiManada
                            )
                            V2NavRow(
                                title = "Guardados",
                                description = "Publicaciones y clips que guardaste",
                                icon = Icons.Default.Bookmark,
                                onClick = onNavigateToSavedPosts
                            )
                            V2NavRow(
                                title = PetCareTransferCopy.MEMORIES_TITLE,
                                description = PetCareTransferCopy.MEMORIES_SUBTITLE,
                                icon = Icons.Default.PhotoLibrary,
                                onClick = onNavigateToMyMemories
                            )
                            V2NavRow(
                                title = "Mensajes",
                                description = "Chats con personas y organizaciones",
                                icon = Icons.AutoMirrored.Filled.Chat,
                                onClick = onNavigateToChat
                            )
                            V2NavRow(
                                title = "Mi ayuda",
                                description = "Aportes, bienes y voluntariado",
                                icon = Icons.Default.Favorite,
                                onClick = onNavigateToDonations
                            )
                            V2NavRow(
                                title = "Mis eventos",
                                description = "Inscripciones y lista de espera",
                                icon = Icons.Default.DateRange,
                                onClick = onNavigateToMyEvents
                            )
                            if (uiState.canEnterAdministration) {
                                V2NavRow(
                                    title = "Administración",
                                    description = "Gestión de la plataforma LeoVer",
                                    icon = Icons.Default.Shield,
                                    onClick = onNavigateToAdministration
                                )
                            }
                            V2NavRow(
                                title = "Configuración",
                                description = "Cuenta, privacidad y seguridad",
                                icon = Icons.Default.Settings,
                                onClick = onNavigateToSettings
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PersonaProfileHeader(
    user: User,
    avatarUrl: String? = null,
    postsCount: Int,
    friendsCount: Int,
    onSettings: () -> Unit,
    onEditProfile: () -> Unit = {}
) {
    val visual = leoVisual()
    val resolvedAvatarUrl = avatarUrl ?: user.profileImageUrl
    var showAvatarViewer by remember(resolvedAvatarUrl) { mutableStateOf(false) }

    if (showAvatarViewer) {
        ProfileAvatarViewer(
            imageUrl = resolvedAvatarUrl,
            displayName = user.resolvedDisplayName,
            onDismiss = { showAvatarViewer = false }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visual.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = LeoDimens.SpaceMd)
                    .padding(bottom = LeoDimens.SpaceMd)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = visual.textPrimary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(visual.surface)
                            .clickable { showAvatarViewer = true }
                    ) {
                        PetImage(
                            imageUrl = resolvedAvatarUrl,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 42.dp,
                            contentDescription = "Abrir foto de perfil de ${user.resolvedDisplayName}"
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = LeoDimens.SpaceCompact)
                    ) {
                        Text(
                            text = user.resolvedDisplayName,
                            style = LeoPageTitle,
                            color = visual.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        user.username?.takeIf { it.isNotBlank() }?.let { username ->
                            Text(
                                text = "@$username",
                                style = LeoCaption,
                                color = visual.textSecondary
                            )
                        }
                        publicPlaceLabel(user)?.let { place ->
                            Text(
                                text = place,
                                style = LeoCaption,
                                color = visual.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(LeoDimens.SpaceSm))
                        androidx.compose.material3.Surface(
                            onClick = onEditProfile,
                            shape = RoundedCornerShape(LeoDimens.RadiusChip),
                            color = visual.primarySoft
                        ) {
                            Text(
                                text = "Editar perfil",
                                style = LeoCaption,
                                color = visual.primaryDark,
                                modifier = Modifier.padding(
                                    horizontal = LeoDimens.SpaceCompact,
                                    vertical = LeoDimens.SpaceMicro
                                )
                            )
                        }
                    }
                }
                user.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Text(
                        text = bio,
                        style = LeoCaption,
                        color = visual.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = LeoDimens.SpaceSm)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceS),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXl)
        ) {
            ProfileStat(postsCount.toString(), "Publicaciones")
            if (friendsCount > 0) {
                ProfileStat(friendsCount.toString(), "Amigos")
            }
        }
    }
}

@Composable
private fun ProfileAvatarViewer(
    imageUrl: String?,
    displayName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            PetImage(
                imageUrl = imageUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceLg),
                cornerRadius = 0.dp,
                contentDescription = "Foto de perfil de $displayName",
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(LeoDimens.SpaceSm)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar foto de perfil",
                    tint = Color.White
                )
            }
            if (imageUrl.isNullOrBlank()) {
                Text(
                    text = "Sin foto de perfil",
                    style = LeoCaption,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(LeoDimens.SpaceLg)
                )
            }
        }
    }
}

private fun publicPlaceLabel(user: User): String? {
    val city = user.city?.takeIf { it.isNotBlank() }
    val province = user.province?.takeIf { it.isNotBlank() }
    return when {
        city != null && province != null -> "$city, $province"
        city != null -> city
        province != null -> province
        else -> user.locationText?.takeIf { it.isNotBlank() }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = LeoCardTitle, color = BrandText)
        Text(text = label, style = LeoCaption, color = MutedText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactUseLeoverAsRow(onClick: () -> Unit) {
    val visual = leoVisual()
    val active by OperationalContextProvider.active.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LeoDimens.SpaceMd)
            .clickable(onClick = onClick)
            .padding(horizontal = LeoDimens.SpaceSm, vertical = LeoDimens.SpaceCompact),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = visual.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = LeoDimens.SpaceM)
            ) {
                Text(
                    text = "Usar LeoVer como",
                    style = LeoCardTitle,
                    color = visual.textPrimary
                )
                Text(
                    text = active.displayName,
                    style = LeoCaption,
                    color = visual.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = visual.textSecondary
            )
    }
}

@Composable
private fun IncomingCareTransfersSection(
    transfers: List<com.comunidapp.app.domain.pets.PetTransfer>,
    enabled: Boolean,
    onAccept: (com.comunidapp.app.domain.pets.PetTransfer) -> Unit,
    onReject: (com.comunidapp.app.domain.pets.PetTransfer) -> Unit,
    modifier: Modifier = Modifier
) {
    if (transfers.isEmpty()) return
    Column(
        modifier = modifier.padding(horizontal = LeoDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        transfers.forEach { transfer ->
            com.comunidapp.app.ui.screens.pets.IncomingCareTransferCard(
                transfer = transfer,
                enabled = enabled,
                onAccept = { onAccept(transfer) },
                onReject = { onReject(transfer) }
            )
        }
    }
}

@Composable
private fun PetResponsibleInvitesSection(
    viewModel: CareNetworkPersonViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (state.pendingInvites.isEmpty()) {
        return
    }
    Column(
        modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        V2SectionHeader(title = "Invitaciones de mascotas")
        if (state.isLoading && state.pendingInvites.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        state.loadErrorMessage?.let { error ->
            Text(
                text = error,
                style = LeoCaption,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = viewModel::refresh) { Text("Reintentar") }
        }
        state.pendingInvites.forEach { invite ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = invite.petName.ifBlank { "Mascota" },
                    style = LeoCardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandText
                )
                Text(
                    text = listOf("Responsable", invite.ownerName.takeIf { it.isNotBlank() })
                        .filterNotNull()
                        .joinToString(" · "),
                    style = LeoCaption,
                    color = MutedText
                )
                Column(
                    modifier = Modifier.padding(top = LeoDimens.SpaceSm),
                    verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
                ) {
                    LeoPrimaryButton(
                        text = "Aceptar",
                        onClick = { viewModel.accept(invite.linkId) },
                        enabled = !state.isSubmitting
                    )
                    LeoOutlinedButton(
                        text = "Rechazar",
                        onClick = { viewModel.reject(invite.linkId) },
                        enabled = !state.isSubmitting
                    )
                }
                LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceCompact))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8, widthDp = 390, name = "PersonaProfileHeaderPreview")
@Composable
private fun PersonaProfileHeaderPreview() {
    ComunidappTheme {
        PersonaProfileHeader(
            user = User(
                id = "1",
                name = "Leonardo",
                email = "a@b.c",
                username = "leover",
                bio = "Amante de los perros",
                city = "Palermo",
                province = "Buenos Aires"
            ),
            postsCount = 12,
            friendsCount = 8,
            onSettings = {}
        )
    }
}
