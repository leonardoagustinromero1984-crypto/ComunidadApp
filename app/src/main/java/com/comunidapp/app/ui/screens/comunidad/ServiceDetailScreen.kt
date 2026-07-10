package com.comunidapp.app.ui.screens.comunidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.ServiceDetailViewModel

@Composable
fun ServiceDetailScreen(
    serviceId: String,
    onNavigateBack: () -> Unit,
    onChatClick: (ownerId: String, name: String) -> Unit,
    viewModel: ServiceDetailViewModel = viewModel(factory = ServiceDetailViewModel.Factory(serviceId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = uiState.service?.name ?: "Servicio",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val service = uiState.service
        if (service == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PetImage(
                    imageUrl = service.photoUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    cornerRadius = 12.dp,
                    contentDescription = service.name
                )
            }
            item {
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "📍 ${service.location}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = service.description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                service.scheduleText?.let {
                    Text(
                        text = "Horarios: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                service.priceFrom?.let {
                    Text(
                        text = "Desde $${it.toInt()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                service.contactInfo?.let {
                    Text(
                        text = "Contacto: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item {
                Button(
                    onClick = { onChatClick(service.ownerId, service.name) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enviar mensaje")
                }
            }
            if (service.acceptsBookings) {
                item {
                    Text(
                        text = "Pedir turno",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Elegí día y horario. El profesional confirma y gestiona el cobro fuera de la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Text("Día", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Hoy", 1 to "Mañana", 2 to "Pasado").forEach { (offset, label) ->
                            FilterChip(
                                selected = uiState.scheduledDayOffset == offset,
                                onClick = { viewModel.updateDayOffset(offset) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                item {
                    Text("Horario", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(9, 10, 12, 16, 18).forEach { hour ->
                            FilterChip(
                                selected = uiState.hour == hour,
                                onClick = { viewModel.updateHour(hour) },
                                label = { Text("${hour}:00") }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = viewModel::updateNotes,
                        label = { Text("Notas (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
                item {
                    Button(
                        onClick = viewModel::requestBooking,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isSubmitting) "Enviando…" else "Solicitar turno")
                    }
                }
            }

            item {
                Text(
                    text = "Reseñas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (reviews.isEmpty()) {
                item {
                    Text(
                        text = "Todavía no hay reseñas para este servicio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(reviews, key = { it.id }) { review ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${review.authorName} · ${"★".repeat(review.rating.coerceIn(1, 5))}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (review.comment.isNotBlank()) {
                            Text(
                                text = review.comment,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Dejá tu reseña",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Calificación", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { rating ->
                        FilterChip(
                            selected = uiState.reviewRating == rating,
                            onClick = { viewModel.updateReviewRating(rating) },
                            label = { Text("$rating") }
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = uiState.reviewComment,
                    onValueChange = viewModel::updateReviewComment,
                    label = { Text("Comentario (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            item {
                Button(
                    onClick = viewModel::submitReview,
                    enabled = !uiState.isSubmittingReview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isSubmittingReview) "Enviando…" else "Publicar reseña")
                }
            }
        }
    }
}
