package com.comunidapp.app.ui.screens.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.FeedPostCard
import com.comunidapp.app.ui.components.PetCard
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    onNavigateToMyPets: () -> Unit = {},
    onNavigateToLostFound: () -> Unit = {},
    onPetClick: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val user by viewModel.user.collectAsState()
    val pets by viewModel.pets.collectAsState()
    val posts by viewModel.posts.collectAsState()

    Scaffold(
        topBar = { ComunidappTopBar(title = "Mi perfil") }
    ) { padding ->
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
                    user.bio?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    user.location?.let { location ->
                        RowWithIcon(
                            icon = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary) },
                            text = location
                        )
                    }
                }
            }

            item {
                RowButtons(onNavigateToMyPets, onNavigateToLostFound)
            }

            item {
                SectionTitle("Mis mascotas (${pets.size})")
            }
            items(pets, key = { it.id }) { pet ->
                PetCard(pet = pet, onClick = { onPetClick(pet.id) })
            }

            item {
                SectionTitle("Mis publicaciones")
            }
            items(posts, key = { it.id }) { post ->
                FeedPostCard(post = post)
            }
        }
    }
}

@Composable
private fun RowButtons(
    onNavigateToMyPets: () -> Unit,
    onNavigateToLostFound: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onNavigateToMyPets,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" Mis mascotas", modifier = Modifier.padding(start = 4.dp))
        }
        OutlinedButton(
            onClick = onNavigateToLostFound,
            modifier = Modifier.weight(1f)
        ) {
            Text("Perdidos")
        }
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
