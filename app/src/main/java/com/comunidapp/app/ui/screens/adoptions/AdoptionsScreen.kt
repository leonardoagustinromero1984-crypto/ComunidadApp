package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.ui.components.AdoptionCard
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.AdoptionListUiState
import com.comunidapp.app.viewmodel.AdoptionsViewModel

@Composable
fun AdoptionsScreen(
    onAdoptionClick: (String) -> Unit,
    viewModel: AdoptionsViewModel = viewModel()
) {
    Scaffold(
        topBar = { ComunidappTopBar(title = "Adopciones") }
    ) { padding ->
        AdoptionsContent(
            onAdoptionClick = onAdoptionClick,
            topPadding = padding.calculateTopPadding(),
            bottomPadding = padding.calculateBottomPadding(),
            viewModel = viewModel
        )
    }
}

@Composable
fun AdoptionsContent(
    onAdoptionClick: (String) -> Unit,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    viewModel: AdoptionsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = filters.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text("Filtrar por zona") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PetSex.entries.forEach { sex ->
                    FilterChip(
                        selected = filters.sex == sex,
                        onClick = {
                            viewModel.onSexFilterChange(
                                if (filters.sex == sex) null else sex
                            )
                        },
                        label = { Text(sex.toDisplayName()) }
                    )
                }
                PetSize.entries.forEach { size ->
                    FilterChip(
                        selected = filters.size == size,
                        onClick = {
                            viewModel.onSizeFilterChange(
                                if (filters.size == size) null else size
                            )
                        },
                        label = { Text(size.toDisplayName()) }
                    )
                }
            }
        }

        when (val state = uiState) {
            AdoptionListUiState.Loading -> LoadingState()
            AdoptionListUiState.Empty -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay publicaciones activas por ahora.")
            }
            is AdoptionListUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message)
                    Button(onClick = viewModel::refresh) { Text("Reintentar") }
                }
            }
            is AdoptionListUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomPadding + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.posts, key = { it.id }) { post ->
                    AdoptionCard(post = post, onClick = { onAdoptionClick(post.id) })
                }
            }
        }
    }
}
