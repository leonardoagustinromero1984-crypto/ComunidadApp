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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.R
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.EditTopBarAction
import com.comunidapp.app.ui.components.FeedPostCard
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetCard
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.ReputationSection
import com.comunidapp.app.ui.components.defaultBadgesForScore
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.theme.GreenPrimary
import com.comunidapp.app.ui.theme.OrangePrimary
import com.comunidapp.app.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToMyPets: () -> Unit = {},
    onNavigateToMyAdoptions: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToFriendRequests: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToModeration: () -> Unit = {},
    onNavigateToSearchFriends: () -> Unit = {},
    onNavigateToAccountSecurity: () -> Unit = {},
    onFriendClick: (String) -> Unit = {},
    onPetClick: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Mi perfil",
                actions = {
                    if (uiState.user != null) {
                        EditTopBarAction(onClick = onNavigateToEditProfile)
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            uiState.user == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Iniciá sesión para ver tu perfil",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val user = uiState.user!!
                val showPets = RolePermissions.canManagePets(user.accountType)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = padding.calculateBottomPadding() + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ProfileHeaderCard(
                            user = user,
                            petsCount = uiState.pets.size,
                            postsCount = uiState.posts.size,
                            friendsCount = uiState.friends.size,
                            pendingRequestsCount = uiState.pendingFriendRequests,
                            topPadding = padding.calculateTopPadding()
                        )
                    }

                    item {
                        FriendsSection(
                            friends = uiState.friends,
                            pendingCount = uiState.pendingFriendRequests,
                            onSearchFriends = onNavigateToSearchFriends,
                            onFriendClick = onFriendClick,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    item {
                        val badges = uiState.badges.ifEmpty {
                            user.badges.ifEmpty { defaultBadgesForScore(user.reputationScore) }
                        }
                        ReputationSection(
                            reputationScore = user.reputationScore,
                            badges = badges,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (showPets) {
                        item {
                            OutlinedButton(
                                onClick = onNavigateToMyPets,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Mis mascotas", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        if (uiState.pets.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Mis mascotas",
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            items(uiState.pets, key = { it.id }) { pet ->
                                PetCard(
                                    pet = pet,
                                    onClick = { onPetClick(pet.id) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }

                    if (RolePermissions.canPublishAdoption(user.accountType)) {
                        item {
                            Button(
                                onClick = onNavigateToMyAdoptions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Mis adopciones publicadas")
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = onNavigateToChat,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Mensajes")
                        }
                    }

                    item {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotifications > 0) {
                                    Badge {
                                        Text(uiState.unreadNotifications.toString())
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToNotifications,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Notificaciones")
                            }
                        }
                    }

                    if (uiState.canViewModeration) {
                        item {
                            OutlinedButton(
                                onClick = onNavigateToModeration,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Moderación")
                            }
                        }
                    }

                    item {
                        BadgedBox(
                            badge = {
                                if (uiState.pendingFriendRequests > 0) {
                                    Badge {
                                        Text(uiState.pendingFriendRequests.toString())
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToFriendRequests,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Solicitudes de amistad")
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Mis publicaciones",
                            subtitle = if (uiState.posts.isEmpty()) "Aún no publicaste nada" else null,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (uiState.posts.isEmpty()) {
                        item {
                            EmptyPostsCard(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    } else {
                        items(uiState.posts, key = { it.id }) { post ->
                            FeedPostCard(
                                post = post,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = onNavigateToAccountSecurity,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Seguridad de la cuenta")
                        }
                    }

                    item {
                        TextButton(
                            onClick = viewModel::logout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.logout),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    user: User,
    petsCount: Int,
    postsCount: Int,
    friendsCount: Int,
    pendingRequestsCount: Int,
    topPadding: androidx.compose.ui.unit.Dp
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp + topPadding)
                .background(
                    Brush.linearGradient(
                        colors = listOf(OrangePrimary, GreenPrimary)
                    )
                )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-56).dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    PetImage(
                        imageUrl = user.profileImageUrl,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 48.dp,
                        contentDescription = user.name
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = user.accountType.toDisplayName(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                val bio = user.bio?.takeIf { it.isNotBlank() }
                Text(
                    text = bio ?: "Completá tu biografía para que la comunidad te conozca mejor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bio != null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )

                if (user.locationText != null || user.phone != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        user.locationText?.let { location ->
                            ProfileInfoChip(
                                icon = {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                text = location
                            )
                        }
                        user.phone?.let { phone ->
                            if (user.locationText != null) Spacer(modifier = Modifier.width(12.dp))
                            ProfileInfoChip(
                                icon = {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = OrangePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                text = phone
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ProfileStatsRow(
                    petsCount = petsCount,
                    postsCount = postsCount,
                    friendsCount = friendsCount,
                    pendingRequestsCount = pendingRequestsCount
                )
            }
        }

        Spacer(modifier = Modifier.height((-40).dp))
    }
}

@Composable
private fun ProfileInfoChip(
    icon: @Composable () -> Unit,
    text: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileStatsRow(
    petsCount: Int,
    postsCount: Int,
    friendsCount: Int,
    pendingRequestsCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ProfileStat(value = petsCount.toString(), label = "Mascotas")
        ProfileStatDivider()
        ProfileStat(value = postsCount.toString(), label = "Publicaciones")
        ProfileStatDivider()
        ProfileStat(
            value = friendsCount.toString(),
            label = "Amigos",
            badgeCount = pendingRequestsCount.takeIf { it > 0 }
        )
    }
}

@Composable
private fun ProfileStatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ProfileStat(
    value: String,
    label: String,
    badgeCount: Int? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (badgeCount != null) {
            BadgedBox(
                badge = { Badge { Text(badgeCount.toString()) } }
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FriendsSection(
    friends: List<User>,
    pendingCount: Int,
    onSearchFriends: () -> Unit,
    onFriendClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Mis amigos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    if (pendingCount > 0) {
                        Badge(modifier = Modifier.padding(start = 8.dp)) {
                            Text("$pendingCount")
                        }
                    }
                }
                TextButton(onClick = onSearchFriends) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Buscar", modifier = Modifier.padding(start = 4.dp))
                }
            }

            if (friends.isEmpty()) {
                Text(
                    text = "Todavía no tenés amigos. Buscá personas de la comunidad para conectar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    items(friends, key = { it.id }) { friend ->
                        FriendAvatarItem(
                            user = friend,
                            onClick = { onFriendClick(friend.id) }
                        )
                    }
                }
            }

            Button(
                onClick = onSearchFriends,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Buscar amigos", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun FriendAvatarItem(
    user: User,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            PetImage(
                imageUrl = user.profileImageUrl,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 28.dp,
                contentDescription = user.name
            )
        }
        Text(
            text = user.name.split(" ").firstOrNull() ?: user.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyPostsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text = "Compartí novedades, preguntas o avisos desde la pestaña Publicar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
