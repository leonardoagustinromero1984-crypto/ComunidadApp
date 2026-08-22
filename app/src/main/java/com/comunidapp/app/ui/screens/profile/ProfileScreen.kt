package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.User
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.v2.V2NavRow
import com.comunidapp.app.ui.components.v2.V2PetsStrip
import com.comunidapp.app.ui.components.v2.V2SectionHeader
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
import com.comunidapp.app.domain.onboarding.onb02.Onb02Copy
import com.comunidapp.app.viewmodel.ProfileViewModel

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
    onNavigateToAccountSecurity: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDonations: () -> Unit = {},
    onNavigateToFirstRunTutorial: () -> Unit = {},
    onNavigateToUseLeoverAs: () -> Unit = {},
    onNavigateToHelpTutorials: () -> Unit = {},
    onNavigateToMyOrganizations: () -> Unit = {},
    onNavigateToPublish: () -> Unit = {},
    onNavigateToMyPublications: () -> Unit = {},
    onFriendClick: (String) -> Unit = {},
    onPetClick: (String) -> Unit = {},
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
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            uiState.user == null -> {
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
                val user = uiState.user!!

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

                    if (showPets) {
                        item(key = "pets_header") {
                            V2SectionHeader(
                                title = "Mis mascotas",
                                actionLabel = "Ver todas",
                                onAction = onNavigateToMyPets
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
                                title = "Mis amigos",
                                description = "Personas con las que te conectaste",
                                icon = Icons.Default.People,
                                onClick = onNavigateToMyFriends
                            )
                            V2NavRow(
                                title = "Solicitudes de amistad",
                                description = if (uiState.pendingFriendRequests > 0) {
                                    "${uiState.pendingFriendRequests} pendiente(s)"
                                } else {
                                    "Revisá quién quiere conectar contigo"
                                },
                                icon = Icons.Default.People,
                                onClick = onNavigateToFriendRequests
                            )
                            V2NavRow(
                                title = "Buscar amigos",
                                description = "Encontrá personas por nombre o usuario",
                                icon = Icons.Default.People,
                                onClick = onNavigateToSearchFriends
                            )
                            V2NavRow(
                                title = "Mensajes",
                                description = "Chats con personas y organizaciones",
                                icon = Icons.AutoMirrored.Filled.Chat,
                                onClick = onNavigateToChat
                            )
                            V2NavRow(
                                title = "Donaciones",
                                description = "Tus aportes y campañas",
                                icon = Icons.Default.Favorite,
                                onClick = onNavigateToDonations
                            )
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visual.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = LeoDimens.SpaceMd)
                    .padding(bottom = 36.dp)
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
                            .border(2.dp, visual.primary, CircleShape)
                            .background(visual.surface)
                    ) {
                        PetImage(
                            imageUrl = avatarUrl ?: user.profileImageUrl,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 42.dp,
                            contentDescription = user.name
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
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = LeoDimens.SpaceMd)
                    .offset(y = 18.dp),
                shape = RoundedCornerShape(LeoDimens.RadiusCard),
                colors = CardDefaults.cardColors(containerColor = visual.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = LeoDimens.SpaceCompact),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStat(postsCount.toString(), "Publicaciones")
                    if (friendsCount > 0) {
                        ProfileStat(friendsCount.toString(), "Amigos")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LeoDimens.SpaceMd),
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = visual.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
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
