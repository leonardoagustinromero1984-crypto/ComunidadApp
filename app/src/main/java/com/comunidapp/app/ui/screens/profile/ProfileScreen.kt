package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import com.comunidapp.app.data.model.AccountType
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.R
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.EditTopBarAction
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.ui.components.FeedPostCard
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetCard
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.ReputationSection
import com.comunidapp.app.ui.components.defaultBadgesForScore
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToMyPets: () -> Unit = {},
    onNavigateToMyAdoptions: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToFriendRequests: () -> Unit = {},
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
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
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
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = user.accountType.toDisplayName(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                            user.bio?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            user.locationText?.let { location ->
                                RowWithIcon(
                                    icon = {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    text = location
                                )
                            }
                            user.phone?.let { phone ->
                                Text(
                                    text = phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            val badges = user.badges.ifEmpty {
                                defaultBadgesForScore(user.reputationScore)
                            }
                            ReputationSection(
                                reputationScore = user.reputationScore,
                                badges = badges,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }

                    if (RolePermissions.canPublishAdoption(user.accountType)) {
                        item {
                            Button(
                                onClick = onNavigateToMyAdoptions,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Mis adopciones publicadas")
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = onNavigateToChat,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Mensajes")
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
                            }
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToFriendRequests,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Solicitudes de amistad")
                            }
                        }
                    }

                    if (showPets) {
                        item {
                            MyPetsButton(onNavigateToMyPets)
                        }
                        item {
                            SectionTitle("Mis mascotas (${uiState.pets.size})")
                        }
                        items(uiState.pets, key = { it.id }) { pet ->
                            PetCard(pet = pet, onClick = { onPetClick(pet.id) })
                        }
                    }

                    item {
                        SectionTitle("Mis publicaciones")
                    }
                    items(uiState.posts, key = { it.id }) { post ->
                        FeedPostCard(post = post)
                    }

                    item {
                        TextButton(
                            onClick = viewModel::logout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
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
private fun MyPetsButton(onNavigateToMyPets: () -> Unit) {
    Button(
        onClick = onNavigateToMyPets,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(" Mis mascotas", modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun RowWithIcon(
    icon: @Composable () -> Unit,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
