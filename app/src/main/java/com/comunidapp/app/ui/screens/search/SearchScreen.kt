package com.comunidapp.app.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.comunidapp.app.ui.components.FeedPostCard
import com.comunidapp.app.ui.components.PetCard
import com.comunidapp.app.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onPetClick: (String) -> Unit = {},
    onAdoptionClick: (String) -> Unit = {},
    viewModel: SearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Buscar",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Usuarios, mascotas, publicaciones…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true
            )
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (results.posts.isNotEmpty()) {
                    item { SectionTitle("Publicaciones") }
                    items(results.posts, key = { it.id }) { post ->
                        FeedPostCard(post = post, onAuthorClick = onAuthorClick)
                    }
                }
                if (results.users.isNotEmpty()) {
                    item { SectionTitle("Personas") }
                    items(results.users, key = { it.id }) { user ->
                        Text(
                            text = "${user.name} · ${user.locationText.orEmpty()}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                if (results.pets.isNotEmpty()) {
                    item { SectionTitle("Mascotas") }
                    items(results.pets, key = { it.id }) { pet ->
                        PetCard(pet = pet, onClick = { onPetClick(pet.id) })
                    }
                }
                if (results.adoptions.isNotEmpty()) {
                    item { SectionTitle("Adopciones") }
                    items(results.adoptions, key = { it.id }) { post ->
                        AdoptionCard(post = post, onClick = { onAdoptionClick(post.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
