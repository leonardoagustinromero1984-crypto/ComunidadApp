package com.comunidapp.app.ui.screens.m21

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.ReputationSection
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M21HubUiState
import com.comunidapp.app.viewmodel.M21HubViewModel
import com.comunidapp.app.viewmodel.M21ReviewsUiState
import com.comunidapp.app.viewmodel.M21ReviewsViewModel
import com.comunidapp.app.viewmodel.M21VerificationsUiState
import com.comunidapp.app.viewmodel.M21VerificationsViewModel
import com.comunidapp.app.viewmodel.M21ViewModelFactories

@Composable
fun M21HubScreen(
    onNavigateBack: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenVerifications: () -> Unit,
    viewModel: M21HubViewModel = viewModel(factory = M21ViewModelFactories.hubFactory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Reputación", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "LeoVer M21 — reseñas transaccionales, verificaciones y apelaciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            when (val s = state) {
                M21HubUiState.Loading -> LoadingState()
                is M21HubUiState.Error -> ErrorState(message = s.message)
                is M21HubUiState.Content -> {
                    ReputationSection(
                        reputationScore = s.summary.reputationScore,
                        badges = s.summary.badges
                    )
                    Text("Reseñas publicadas: ${s.summary.publishedReviewCount}")
                    s.summary.averageRating?.let {
                        Text("Promedio: ${"%.1f".format(it)} / 5")
                    }
                    Text(
                        buildString {
                            append("Identidad: ")
                            append(if (s.summary.identityVerified) "verificada" else "pendiente")
                            append(" · Matrícula: ")
                            append(if (s.summary.licenseVerified) "verificada" else "pendiente")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onOpenReviews, modifier = Modifier.fillMaxWidth()) {
                        Text("Mis reseñas")
                    }
                    OutlinedButton(onClick = onOpenVerifications, modifier = Modifier.fillMaxWidth()) {
                        Text("Verificaciones")
                    }
                }
            }
        }
    }
}

@Composable
fun M21ReviewsScreen(
    onNavigateBack: () -> Unit,
    viewModel: M21ReviewsViewModel = viewModel(factory = M21ViewModelFactories.reviewsFactory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Mis reseñas", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M21ReviewsUiState.Loading -> LoadingState()
                is M21ReviewsUiState.Error -> ErrorState(message = s.message)
                is M21ReviewsUiState.Content -> {
                    if (s.items.isEmpty()) {
                        EmptyState(title = "Sin reseñas", message = "Todavía no dejaste reseñas.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(s.items, key = { it.id }) { item -> M21ReviewCard(item) }
                        }
                    }
                    Button(
                        onClick = { viewModel.submitDemoReview(onDone = {}) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("Dejar reseña demo")
                    }
                }
            }
        }
    }
}

@Composable
private fun M21ReviewCard(review: M21PublicReview) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(review.targetDisplayLabel, fontWeight = FontWeight.Bold)
            Text("${review.rating}/5 · ${review.targetType.name.lowercase()}")
            Text(review.content, style = MaterialTheme.typography.bodyMedium)
            Text(review.status.name, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun M21VerificationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: M21VerificationsViewModel = viewModel(factory = M21ViewModelFactories.verificationsFactory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Verificaciones", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M21VerificationsUiState.Loading -> LoadingState()
                is M21VerificationsUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.refresh() })
                is M21VerificationsUiState.Content -> {
                    if (s.items.isEmpty()) {
                        EmptyState(title = "Sin verificaciones", message = "Podés solicitar verificación de identidad.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(s.items, key = { it.id }) { item -> M21VerificationCard(item) }
                        }
                    }
                    Button(
                        onClick = { viewModel.submitIdentityVerification() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("Solicitar verificación de identidad")
                    }
                }
            }
        }
    }
}

@Composable
private fun M21VerificationCard(item: M21PublicVerification) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.displayLabel, fontWeight = FontWeight.Bold)
            Text("${item.verificationType.name} · ${item.status.name}")
            item.licenseSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
