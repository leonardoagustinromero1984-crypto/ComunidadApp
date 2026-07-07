package com.comunidapp.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.User
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.FeedPostCard
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onAuthorClick: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val nearbyUsers = MockData.users.filter { it.id != MockData.currentUser.id }

    Scaffold(
        topBar = { ComunidappTopBar(title = "Inicio") }
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
                Text(
                    text = "Personas cerca",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dueños, voluntarios y refugios en tu zona",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(nearbyUsers, key = { it.id }) { user ->
                        NearbyUserCard(user = user, onClick = { onAuthorClick(user.id) })
                    }
                }
            }
            item {
                Text(
                    text = "Feed de la comunidad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            itemsIndexed(
                items = posts,
                key = { index, post -> post.id.ifBlank { "post_$index" } }
            ) { _, post ->
                FeedPostCard(post = post, onAuthorClick = onAuthorClick)
            }
        }
    }
}

@Composable
private fun NearbyUserCard(user: User, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PetImage(
                imageUrl = user.profileImageUrl,
                modifier = Modifier.size(56.dp),
                contentDescription = user.name
            )
            Text(
                text = user.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = user.locationText.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = "${user.petIds.size} mascotas",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
