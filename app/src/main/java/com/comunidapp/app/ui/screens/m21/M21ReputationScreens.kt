package com.comunidapp.app.ui.screens.m21

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.M21ReputationBreakdown
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.ReputationSection
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M21HubUiState
import com.comunidapp.app.viewmodel.M21HubViewModel
import com.comunidapp.app.viewmodel.M21ReviewDetailUiState
import com.comunidapp.app.viewmodel.M21ReviewDetailViewModel
import com.comunidapp.app.viewmodel.M21ReviewsUiState
import com.comunidapp.app.viewmodel.M21ReviewsViewModel
import com.comunidapp.app.viewmodel.M21SubjectUiState
import com.comunidapp.app.viewmodel.M21SubjectViewModel
import com.comunidapp.app.viewmodel.M21VerificationsUiState
import com.comunidapp.app.viewmodel.M21VerificationsViewModel
import com.comunidapp.app.viewmodel.M21ViewModelFactories

@Composable
fun M21HubScreen(
    onNavigateBack: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenVerifications: () -> Unit,
    onOpenSubject: (M21ReviewTargetType, String) -> Unit = { _, _ -> },
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
                    Text("Con respuesta: ${s.summary.reviewsWithResponseCount}")
                    s.summary.ratingDistribution.let { dist ->
                        if (dist.total > 0) {
                            Text(
                                "Distribución: ★5 ${dist.fiveStars} · ★4 ${dist.fourStars} · ★3 ${dist.threeStars} · ★2 ${dist.twoStars} · ★1 ${dist.oneStar}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                    OutlinedButton(
                        onClick = {
                            onOpenSubject(M21ReviewTargetType.ORGANIZATION, com.comunidapp.app.data.model.M21MockTargetIds.ORGANIZATION)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ver reputación de refugio demo")
                    }
                }
            }
        }
    }
}

@Composable
fun M21SubjectScreen(
    targetType: M21ReviewTargetType,
    targetId: String,
    onNavigateBack: () -> Unit,
    onReviewClick: (String) -> Unit,
    viewModel: M21SubjectViewModel = viewModel(
        factory = M21ViewModelFactories.subjectFactory(targetType, targetId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Reputación · ${targetType.name.lowercase()}",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M21SubjectUiState.Loading -> LoadingState()
                is M21SubjectUiState.Error -> ErrorState(message = s.message)
                is M21SubjectUiState.NotEligible -> {
                    Text("No podés dejar reseña todavía.", fontWeight = FontWeight.Bold)
                    Text("Motivo: ${s.eligibility.reason.name.lowercase().replace('_', ' ')}")
                }
                is M21SubjectUiState.Content -> {
                    M21BreakdownHeader(s.breakdown)
                    if (s.eligibility?.eligible == true) {
                        Text("Podés dejar una reseña por experiencia completada.", color = MaterialTheme.colorScheme.primary)
                    }
                    if (s.breakdown.reviews.isEmpty()) {
                        EmptyState(title = "Sin reseñas públicas", message = "Todavía no hay reseñas visibles.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(s.breakdown.reviews, key = { it.id }) { item ->
                                M21ReviewCard(item, onClick = { onReviewClick(item.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M21ReviewDetailScreen(
    reviewId: String,
    onNavigateBack: () -> Unit,
    viewModel: M21ReviewDetailViewModel = viewModel(
        factory = M21ViewModelFactories.reviewDetailFactory(reviewId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle de reseña", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                M21ReviewDetailUiState.Loading -> LoadingState()
                is M21ReviewDetailUiState.Error -> ErrorState(message = s.message)
                is M21ReviewDetailUiState.Content -> {
                    M21ReviewCard(s.review)
                    s.review.publicResponse?.let { response ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Respuesta del sujeto", fontWeight = FontWeight.Bold)
                                Text(response.content)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.canEdit) {
                            OutlinedButton(onClick = {
                                viewModel.editReview("Contenido editado desde UI", 4) {}
                            }) { Text("Editar") }
                        }
                        if (s.canRespond) {
                            Button(onClick = {
                                viewModel.respond("Gracias por tu feedback constructivo.") {}
                            }) { Text("Responder") }
                        }
                        if (s.canDispute) {
                            OutlinedButton(onClick = {
                                viewModel.dispute("Solicito revisión por error factual en la reseña.") {}
                            }) { Text("Disputar") }
                        }
                        if (s.canReport) {
                            OutlinedButton(onClick = {
                                viewModel.report("spam") {}
                            }) { Text("Reportar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun M21BreakdownHeader(breakdown: M21ReputationBreakdown) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        Text(breakdown.subject.displayLabel, fontWeight = FontWeight.Bold)
        breakdown.averageRating?.let { Text("Promedio: ${"%.1f".format(it)} / 5") }
        Text("Reseñas: ${breakdown.publishedReviewCount}")
        Text("Con respuesta: ${breakdown.reviewsWithResponseCount}")
    }
}

@Composable
fun M21ReviewsScreen(
    onNavigateBack: () -> Unit,
    onReviewClick: (String) -> Unit = {},
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
                            items(s.items, key = { it.id }) { item ->
                                M21ReviewCard(item, onClick = { onReviewClick(item.id) })
                            }
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
private fun M21ReviewCard(review: M21PublicReview, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            review.title?.let { Text(it, fontWeight = FontWeight.Bold) }
            Text(review.targetDisplayLabel, fontWeight = FontWeight.Bold)
            Text("${review.rating}/5 · ${review.targetType.name.lowercase()}")
            Text(review.content, style = MaterialTheme.typography.bodyMedium)
            review.eligibleExperienceBadge?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (review.hasResponse) {
                Text("Incluye respuesta", style = MaterialTheme.typography.labelSmall)
            }
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
