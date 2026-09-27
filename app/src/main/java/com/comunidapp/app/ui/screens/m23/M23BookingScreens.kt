package com.comunidapp.app.ui.screens.m23

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M23BookingStatus
import com.comunidapp.app.data.model.M23BookingStatusFilter
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.*
import java.time.LocalDate

@Composable
fun M23HomeScreen(
    onBack: () -> Unit,
    onMyBookings: () -> Unit,
    onManage: () -> Unit,
    viewModel: M23HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    M23Scaffold("Agenda y reservas", onBack) {
        when (val s = state) {
            M23HomeUiState.Loading -> LoadingState()
            is M23HomeUiState.Error -> ErrorState(s.message)
            is M23HomeUiState.Content -> {
                Text("${s.bookingCount} reservas próximas")
                LeoPrimaryButton(text = "Mis reservas", onClick = onMyBookings)
                LeoPrimaryButton(text = "Gestionar agenda", onClick = onManage)
            }
        }
    }
}

@Composable
fun M23AvailabilityScreen(onBack: () -> Unit, viewModel: M23AvailabilityViewModel) {
    val state by viewModel.uiState.collectAsState()
    M23Scaffold("Disponibilidad", onBack) {
        when (val s = state) {
            M23AvailabilityUiState.Loading -> LoadingState()
            M23AvailabilityUiState.Empty -> EmptyState(title = "Sin horarios", message = "No hay horarios disponibles.")
            is M23AvailabilityUiState.Error -> ErrorState(s.message)
            is M23AvailabilityUiState.Content -> LazyColumn {
                s.page.days.forEach { day ->
                    item { Text(day.date.toString()) }
                    items(day.slots) { slot -> Text("${slot.startsAt} · ${slot.modality}") }
                }
            }
        }
    }
}

@Composable
fun M23MyBookingsScreen(
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
    viewModel: M23MyBookingsViewModel = viewModel(factory = M23ViewModelFactories.myBookings())
) {
    val state by viewModel.uiState.collectAsState()
    M23Scaffold("Mis reservas", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS), modifier = Modifier.fillMaxWidth()) {
            LeoFilterChip(label = "Próximas", selected = false, onClick = { viewModel.showUpcoming() })
            LeoFilterChip(label = "Historial", selected = false, onClick = { viewModel.showHistory() })
            LeoFilterChip(label = "Confirmadas", selected = false, onClick = { viewModel.filterStatus(M23BookingStatusFilter.CONFIRMED) })
        }
        when (val s = state) {
            M23MyBookingsUiState.Loading -> LoadingState()
            M23MyBookingsUiState.Empty -> EmptyState(title = "Sin reservas", message = "Todavía no tenés reservas.")
            is M23MyBookingsUiState.Error -> ErrorState(s.message)
            is M23MyBookingsUiState.Content -> LazyColumn {
                items(s.bookings) { booking ->
                    LeoListRow(
                        title = booking.offeringName,
                        subtitle = booking.booking.status.toString(),
                        onClick = { onDetail(booking.booking.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun M23BookingDetailScreen(
    onBack: () -> Unit,
    onConversation: (String) -> Unit = {},
    viewModel: M23BookingDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    M23Scaffold("Reserva", onBack) {
        when (val s = state) {
            M23BookingDetailUiState.Loading -> LoadingState()
            M23BookingDetailUiState.Empty -> EmptyState(title = "No disponible", message = "La reserva no está disponible.")
            is M23BookingDetailUiState.Error -> ErrorState(s.message)
            is M23BookingDetailUiState.Content -> {
                Text("${s.providerName} · ${s.offeringName}")
                Text("${s.booking.startsAt} · ${s.booking.status}")
                s.policy?.let { Text("Cancelación: ${it.cancellation.minimumNoticeMinutes} min de aviso") }
                if (s.history.isNotEmpty()) {
                    Text("Historial")
                    s.history.forEach { entry -> Text("${entry.from ?: "-"} → ${entry.to}") }
                }
                if (s.reviewEligible) Text("Podés dejar una reseña cuando las reseñas estén disponibles.")
                when (s.booking.status) {
                    M23BookingStatus.REQUESTED -> {
                        LeoPrimaryButton(text = "Confirmar (prestador)", onClick = { viewModel.confirm() })
                        LeoPrimaryButton(text = "Rechazar", onClick = { viewModel.reject(publicReason = "Horario no disponible") })
                    }
                    M23BookingStatus.CONFIRMED -> {
                        LeoPrimaryButton(text = "Cancelar", onClick = { viewModel.cancel() })
                        LeoPrimaryButton(text = "Completar", onClick = { viewModel.complete() })
                        LeoPrimaryButton(text = "Marcar no-show", onClick = { viewModel.noShow() })
                    }
                    else -> Unit
                }
                if (s.canOpenConversation) {
                    LeoPrimaryButton(text = "Abrir conversación", onClick = { viewModel.openConversation() })
                }
            }
        }
    }
}

@Composable
fun M23ManageScreen(onBack: () -> Unit, onCalendar: () -> Unit, onBookings: () -> Unit) =
    M23Scaffold("Gestionar agenda", onBack) {
        LeoPrimaryButton(text = "Disponibilidad", onClick = onCalendar)
        LeoPrimaryButton(text = "Reservas recibidas", onClick = onBookings)
    }

@Composable
fun M23ManageCalendarScreen(onBack: () -> Unit, viewModel: M23ManageCalendarViewModel) {
    val state by viewModel.uiState.collectAsState()
    M23Scaffold("Mi calendario", onBack) {
        when (val s = state) {
            M23ManageCalendarUiState.Loading -> LoadingState()
            M23ManageCalendarUiState.Empty -> EmptyState(title = "Sin reglas", message = "Configurá horarios de atención.")
            is M23ManageCalendarUiState.Error -> ErrorState(s.message)
            is M23ManageCalendarUiState.Content -> LazyColumn {
                items(s.rules) { Text("${it.dayOfWeek}: ${it.startTime} - ${it.endTime}") }
            }
        }
    }
}

@Composable
fun M23ManageBookingsScreen(
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
    viewModel: M23ManageBookingsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    M23Scaffold("Reservas recibidas", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS), modifier = Modifier.fillMaxWidth()) {
            LeoFilterChip(label = "Hoy demo", selected = false, onClick = { viewModel.filterDay(LocalDate.of(2030, 1, 7)) })
            LeoFilterChip(label = "Pendientes", selected = false, onClick = { viewModel.filterStatus(M23BookingStatusFilter.REQUESTED) })
            LeoFilterChip(label = "Confirmadas", selected = false, onClick = { viewModel.filterStatus(M23BookingStatusFilter.CONFIRMED) })
        }
        when (val s = state) {
            M23ManageBookingsUiState.Loading -> LoadingState()
            M23ManageBookingsUiState.Empty -> EmptyState(title = "Sin reservas", message = "No hay reservas para gestionar.")
            is M23ManageBookingsUiState.Error -> ErrorState(s.message)
            is M23ManageBookingsUiState.Content -> {
                Text("Métricas: ${s.metrics.requested} pend. · ${s.metrics.confirmed} conf.")
                LazyColumn {
                    items(s.bookings) { booking ->
                        LeoListRow(
                            title = booking.startsAt.toString(),
                            subtitle = booking.status.toString(),
                            onClick = { onDetail(booking.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun M23Scaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) =
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title, showBackButton = true, onBackClick = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    }
