package com.comunidapp.app.ui.screens.comunidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.map.LeoVerMapMarker
import com.comunidapp.app.ui.map.LeoVerMap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoVerHoursDisplay
import com.comunidapp.app.ui.components.v2.V2SurfaceCard
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoSectionTitle
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                        .height(220.dp),
                    cornerRadius = 16.dp,
                    contentDescription = service.name
                )
            }
            item {
                val public = com.comunidapp.app.domain.business.PublicCommercialProfileMapper.fromService(service)
                V2SurfaceCard {
                    Text(
                        text = public.name,
                        style = LeoSectionTitle,
                        color = BrandText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = public.categoryLabel,
                        style = LeoCaption,
                        color = BrandTextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    public.location?.let {
                        Text(
                            text = it,
                            style = LeoCaption,
                            color = BrandTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    public.description?.let {
                        Text(
                            text = it,
                            style = LeoCaption,
                            color = BrandText,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    LeoVerHoursDisplay(
                        schedule = ProviderWeeklySchedule(public.hours),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    service.distanceKm?.let {
                        Text(
                            text = "%.1f km".format(it),
                            style = LeoCaption,
                            color = BrandTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    service.priceFrom?.takeIf { it.isFinite() }?.let {
                        Text(
                            text = "Desde $${it.toInt()}",
                            style = LeoCaption,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandText,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    public.phone?.let {
                        Text(
                            text = it,
                            style = LeoCaption,
                            color = BrandText,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (public.bookingsEnabled) {
                        Text(
                            text = "Turnos online disponibles",
                            style = LeoCaption,
                            color = BrandText,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            if (service.geoIsPublicPremises && service.latitude != null && service.longitude != null) {
                val mapPoint = LeoVerGeoPoint.parseOrNull(service.latitude, service.longitude)
                if (mapPoint != null) {
                item {
                    LeoVerMap(
                        markers = listOf(
                            LeoVerMapMarker(
                                id = service.id,
                                position = mapPoint,
                                title = service.name
                            )
                        ),
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        camera = LeoVerMapCameraState.cameraOrFallback(
                            service.latitude,
                            service.longitude,
                            zoom = 15f
                        ),
                        interactive = true
                    )
                }
                }
            }
            item {
                LeoPrimaryButton(
                    text = "Enviar mensaje",
                    onClick = { onChatClick(service.ownerId, service.name) }
                )
            }
            if (service.acceptsBookings) {
                item {
                    V2SurfaceCard {
                        Text(
                            text = "Pedir turno",
                            style = LeoSectionTitle,
                            color = BrandText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Elegí día y horario. El profesional confirma y gestiona el cobro fuera de la app.",
                            style = LeoCaption,
                            color = BrandTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                item {
                    Text("Día", style = LeoCaption, color = BrandText, fontWeight = FontWeight.SemiBold)
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
                    Text("Horario", style = LeoCaption, color = BrandText, fontWeight = FontWeight.SemiBold)
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
                    LeoPrimaryButton(
                        text = if (uiState.isSubmitting) "Enviando…" else "Solicitar turno",
                        onClick = viewModel::requestBooking,
                        enabled = !uiState.isSubmitting
                    )
                }
            }

            item {
                Text(
                    text = "Reseñas",
                    style = LeoSectionTitle,
                    color = BrandText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (reviews.isEmpty()) {
                item {
                    Text(
                        text = "Todavía no hay reseñas para este servicio.",
                        style = LeoCaption,
                        color = BrandTextSecondary
                    )
                }
            } else {
                items(reviews, key = { it.id }) { review ->
                    V2SurfaceCard {
                        Text(
                            text = "${review.authorName} · ${"★".repeat(review.rating.coerceIn(1, 5))}",
                            style = LeoCaption,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandText
                        )
                        if (review.comment.isNotBlank()) {
                            Text(
                                text = review.comment,
                                style = LeoCaption,
                                color = BrandText,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
            item {
                V2SurfaceCard {
                    Text(
                        text = "Dejá tu reseña",
                        style = LeoCaption,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandText
                    )
                    Text(
                        text = "Calificación",
                        style = LeoCaption,
                        color = BrandTextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        (1..5).forEach { rating ->
                            FilterChip(
                                selected = uiState.reviewRating == rating,
                                onClick = { viewModel.updateReviewRating(rating) },
                                label = { Text("$rating") }
                            )
                        }
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
                LeoPrimaryButton(
                    text = if (uiState.isSubmittingReview) "Enviando…" else "Publicar reseña",
                    onClick = viewModel::submitReview,
                    enabled = !uiState.isSubmittingReview
                )
            }
        }
    }
}
