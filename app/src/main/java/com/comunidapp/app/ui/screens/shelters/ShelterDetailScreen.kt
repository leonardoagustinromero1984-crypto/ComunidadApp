package com.comunidapp.app.ui.screens.shelters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.comunidapp.app.ui.components.AdoptionCard
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.ShelterDetailViewModel

@Composable
fun ShelterDetailScreen(
    onNavigateBack: () -> Unit,
    onAdoptionClick: (String) -> Unit,
    viewModel: ShelterDetailViewModel = viewModel()
) {
    val shelter by viewModel.shelter.collectAsState()
    val adoptions by viewModel.adoptions.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = shelter?.name ?: "Refugio",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val data = shelter) {
            null -> LoadingState(Modifier.padding(padding))
            else -> LazyColumn(
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
                    PetImage(
                        imageUrl = data.photoUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        cornerRadius = 12.dp,
                        contentDescription = data.name
                    )
                }
                item {
                    Text(
                        text = data.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📍 ${data.location}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = data.description,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    data.contactPhone?.let {
                        Text("Tel: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    data.contactEmail?.let {
                        Text("Email: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (data.needs.isNotEmpty()) {
                    item {
                        Text(
                            text = "Necesidades del refugio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        data.needs.forEach { need ->
                            Text(
                                text = "• ${need.item}: ${need.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = "Animales en adopción (${adoptions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(adoptions, key = { it.id }) { post ->
                    AdoptionCard(post = post, onClick = { onAdoptionClick(post.id) })
                }
            }
        }
    }
}
