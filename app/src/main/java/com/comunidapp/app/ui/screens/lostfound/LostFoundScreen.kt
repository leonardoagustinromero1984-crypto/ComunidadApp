package com.comunidapp.app.ui.screens.lostfound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.LostFoundViewModel

@Composable
fun LostFoundScreen(
    onNavigateBack: () -> Unit,
    viewModel: LostFoundViewModel = viewModel()
) {
    val posts by viewModel.posts.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Perdidos / Encontrados",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
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
            items(posts, key = { it.id }) { post ->
                LostFoundCard(post = post)
            }
        }
    }
}

@Composable
private fun LostFoundCard(post: LostFoundPost) {
    val badgeText = if (post.type == LostFoundType.LOST) "PERDIDO" else "ENCONTRADO"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (post.type == LostFoundType.LOST) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
            )
            Text(
                text = post.petName ?: "${post.species.toDisplayName()} sin nombre",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Por: ${post.authorName} · ${post.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = post.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "📍 ${post.location}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Contacto: ${post.contactInfo}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
