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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoSecondaryButton
import com.comunidapp.app.ui.components.leo.LeoSocialPostCard
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoPageTitle
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.NeutralBorder
import com.comunidapp.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

private enum class ProfileContentTab { Posts, Reels, Tagged }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToMyPets: () -> Unit = {},
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
    onNavigateToAccountSecurity: () -> Unit = {},
    onNavigateToFirstRunTutorial: () -> Unit = {},
    onNavigateToMyOrganizations: () -> Unit = {},
    onNavigateToPublish: () -> Unit = {},
    onFriendClick: (String) -> Unit = {},
    onPetClick: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var contentTab by remember { mutableStateOf(ProfileContentTab.Posts) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = BrandCream,
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
                        message = "Tu perfil social muestra publicaciones, mascotas y actividad.",
                        icon = Icons.Default.Pets
                    )
                }
            }
            else -> {
                val user = uiState.user!!
                val showPets = RolePermissions.canManagePets(user.accountType)
                val postsWithImage = uiState.posts.filter {
                    it.type != com.comunidapp.app.data.model.PostType.STORY &&
                        it.type != com.comunidapp.app.data.model.PostType.REEL &&
                        !it.imageUrl.isNullOrBlank()
                }
                val textPosts = uiState.posts.filter {
                    it.type != com.comunidapp.app.data.model.PostType.STORY &&
                        it.type != com.comunidapp.app.data.model.PostType.REEL &&
                        it.imageUrl.isNullOrBlank()
                }
                val reelPosts = uiState.posts.filter {
                    it.type == com.comunidapp.app.data.model.PostType.REEL
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + LeoDimens.SpaceMd
                    )
                ) {
                    item {
                        SocialProfileHeader(
                            user = user,
                            postsCount = uiState.posts.size,
                            followersCount = uiState.friends.size,
                            followingCount = uiState.friends.size,
                            onEdit = onNavigateToEditProfile,
                            onCreate = {
                                menuOpen = false
                                onNavigateToPublish()
                            },
                            onMenu = { menuOpen = true }
                        )
                    }

                    if (showPets) {
                        item {
                            Text(
                                text = "Mis mascotas",
                                style = LeoCardTitle,
                                color = BrandText,
                                modifier = Modifier.padding(
                                    horizontal = LeoDimens.SpaceMd,
                                    vertical = LeoDimens.SpaceSm
                                )
                            )
                        }
                        item {
                            if (uiState.pets.isEmpty()) {
                                Text(
                                    text = "Todavía no cargaste mascotas",
                                    style = LeoCaption,
                                    color = MutedText,
                                    modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd)
                                )
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LeoDimens.SpaceMd),
                                    horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
                                ) {
                                    items(uiState.pets, key = { it.id }) { pet ->
                                        ProfilePetChip(pet = pet, onClick = { onPetClick(pet.id) })
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(LeoDimens.SpaceMd),
                            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
                        ) {
                            LeoFilterChip(
                                label = "Publicaciones",
                                selected = contentTab == ProfileContentTab.Posts,
                                onClick = { contentTab = ProfileContentTab.Posts }
                            )
                            LeoFilterChip(
                                label = "Reels",
                                selected = contentTab == ProfileContentTab.Reels,
                                onClick = { contentTab = ProfileContentTab.Reels }
                            )
                            LeoFilterChip(
                                label = "Etiquetadas",
                                selected = contentTab == ProfileContentTab.Tagged,
                                onClick = { contentTab = ProfileContentTab.Tagged }
                            )
                        }
                    }

                    when (contentTab) {
                        ProfileContentTab.Posts -> {
                            if (uiState.posts.isEmpty()) {
                                item {
                                    LeoEmptyState(
                                        title = "Todavía no hay publicaciones",
                                        message = "Mostrá tu día a día con tus mascotas.",
                                        actionLabel = "Crear tu primera publicación",
                                        onAction = onNavigateToPublish,
                                        icon = Icons.Default.PostAdd
                                    )
                                }
                            } else {
                                if (postsWithImage.isNotEmpty()) {
                                    item {
                                        ProfilePostsGrid(posts = postsWithImage)
                                    }
                                }
                                items(textPosts, key = { it.id }) { post ->
                                    LeoSocialPostCard(
                                        post = post,
                                        modifier = Modifier.padding(
                                            horizontal = LeoDimens.SpaceMd,
                                            vertical = LeoDimens.SpaceSm
                                        )
                                    )
                                }
                            }
                        }
                        ProfileContentTab.Reels -> {
                            item {
                                if (reelPosts.isEmpty()) {
                                    LeoEmptyState(
                                        title = "Sin Reels todavía",
                                        message = "Cuando publiques videos cortos, aparecerán aquí.",
                                        actionLabel = "Crear",
                                        onAction = onNavigateToPublish,
                                        icon = Icons.Default.PlayArrow
                                    )
                                } else {
                                    ProfilePostsGrid(
                                        posts = reelPosts,
                                        showPlay = true
                                    )
                                }
                            }
                        }
                        ProfileContentTab.Tagged -> {
                            item {
                                LeoEmptyState(
                                    title = "Sin etiquetas todavía",
                                    message = "Cuando te etiqueten a vos o a tus mascotas, lo vas a ver aquí.",
                                    icon = Icons.Default.Pets
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (menuOpen) {
        ProfileMenuSheet(
            onDismiss = { menuOpen = false },
            actions = ProfileMenuActions(
                onMyPets = onNavigateToMyPets,
                onMyApplications = onNavigateToMyApplications,
                onReceivedApplications = onNavigateToReceivedApplications,
                onMyOrganizations = onNavigateToMyOrganizations,
                onMessages = onNavigateToChat,
                onMyPosts = { contentTab = ProfileContentTab.Posts },
                onMyReels = { contentTab = ProfileContentTab.Reels },
                onMyStories = {
                    scope.launch { snackbarHostState.showSnackbar("Historias próximamente") }
                },
                onSaved = {
                    scope.launch { snackbarHostState.showSnackbar("Guardados próximamente") }
                },
                onDrafts = {
                    scope.launch { snackbarHostState.showSnackbar("Borradores próximamente") }
                },
                onNotifications = onNavigateToNotifications,
                onSettings = onNavigateToAccountSecurity,
                onPrivacy = onNavigateToAccountSecurity,
                onSupport = onNavigateToMySupport,
                onLogout = viewModel::logout,
                onModeration = onNavigateToModeration.takeIf { uiState.canViewModeration },
                onCases = onNavigateToCases.takeIf { uiState.canViewModeration },
                onAppealsStaff = onNavigateToAppealsStaff.takeIf { uiState.canReviewAppeals },
                onVerification = onNavigateToVerification.takeIf { uiState.canReviewVerification },
                onSupportStaff = onNavigateToSupportStaff.takeIf { uiState.canViewSupportStaff },
                onAudit = onNavigateToAudit.takeIf { uiState.canViewAudit },
                onObservability = onNavigateToObservability.takeIf { uiState.canViewObservability },
                onPlatformAdmin = onNavigateToPlatformAdmin.takeIf { uiState.canViewPlatformAdmin },
                onMyAppeals = onNavigateToMyAppeals,
                onFriendRequests = onNavigateToFriendRequests,
                onTutorial = onNavigateToFirstRunTutorial
            )
        )
    }
}

@Composable
private fun SocialProfileHeader(
    user: User,
    postsCount: Int,
    followersCount: Int,
    followingCount: Int,
    onEdit: () -> Unit,
    onCreate: () -> Unit,
    onMenu: () -> Unit
) {
    Surface(color = BrandWhite) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LeoDimens.SpaceMd)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: user.resolvedDisplayName,
                    style = LeoCardTitle,
                    color = BrandText
                )
                IconButton(onClick = onMenu) {
                    Icon(Icons.Default.Menu, contentDescription = "Menú", tint = BrandText)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .border(2.dp, BrandOrangeContainer, CircleShape)
                        .background(BrandCream)
                ) {
                    PetImage(
                        imageUrl = user.profileImageUrl,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 44.dp,
                        contentDescription = user.name
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = LeoDimens.SpaceMd),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStat(postsCount.toString(), "Publicaciones")
                    ProfileStat(followersCount.toString(), "Seguidores")
                    ProfileStat(followingCount.toString(), "Seguidos")
                }
            }
            Text(
                text = user.resolvedDisplayName,
                style = LeoPageTitle,
                color = BrandText,
                modifier = Modifier.padding(top = LeoDimens.SpaceCompact)
            )
            user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = LeoCaption, color = BrandText, modifier = Modifier.padding(top = 4.dp))
            }
            user.locationText?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = LeoCaption, color = MutedText, modifier = Modifier.padding(top = 2.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LeoDimens.SpaceCompact),
                horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
            ) {
                LeoSecondaryButton(
                    text = "Editar perfil",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                )
                LeoPrimaryButton(
                    text = "Crear",
                    onClick = onCreate,
                    icon = Icons.Default.Add,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = LeoCardTitle, color = BrandText)
        Text(text = label, style = LeoCaption, color = MutedText)
    }
}

@Composable
private fun ProfilePetChip(pet: Pet, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(2.dp, BrandOrangeSoft, CircleShape)
                .background(BrandOrangeContainer)
        ) {
            PetImage(
                imageUrl = pet.photoUrl,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 32.dp,
                contentDescription = pet.name
            )
        }
        Text(
            text = pet.name,
            style = LeoCaption,
            color = BrandText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = pet.species.name.lowercase().replaceFirstChar { it.titlecase() },
            style = LeoCaption,
            color = MutedText,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfilePostsGrid(
    posts: List<FeedPost>,
    showPlay: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        posts.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                row.forEach { post ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(if (showPlay) 0.75f else 1f)
                            .background(BrandCream),
                        contentAlignment = Alignment.Center
                    ) {
                        PetImage(
                            imageUrl = post.imageUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = post.title
                        )
                        if (showPlay) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BrandWhite)
                        }
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "SocialProfileEmptyPreview")
@Composable
private fun SocialProfileEmptyPreview() {
    ComunidappTheme {
        LeoEmptyState(
            title = "Todavía no hay publicaciones",
            message = "Mostrá tu día a día con tus mascotas.",
            actionLabel = "Crear tu primera publicación",
            onAction = {},
            icon = Icons.Default.PostAdd
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "SocialProfilePreview")
@Composable
private fun SocialProfilePreview() {
    ComunidappTheme {
        SocialProfileHeader(
            user = User(
                id = "1",
                name = "Leonardo",
                email = "a@b.c",
                username = "leover",
                bio = "Amante de los perros",
                locationText = "Buenos Aires"
            ),
            postsCount = 12,
            followersCount = 40,
            followingCount = 33,
            onEdit = {},
            onCreate = {},
            onMenu = {}
        )
    }
}
