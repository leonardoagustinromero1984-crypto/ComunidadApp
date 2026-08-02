package com.comunidapp.app.ui.screens.m23

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.*

@Composable fun M23HomeScreen(onBack: () -> Unit, onMyBookings: () -> Unit, onManage: () -> Unit, viewModel: M23HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    M23Scaffold("Agenda y reservas", onBack) {
        when (val s = state) { M23HomeUiState.Loading -> LoadingState(); is M23HomeUiState.Error -> ErrorState(s.message); is M23HomeUiState.Content -> { Text("${s.bookingCount} reservas personales"); Button(onClick = onMyBookings, modifier = Modifier.fillMaxWidth()) { Text("Mis reservas") }; Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text("Gestionar agenda") } } }
    }
}
@Composable fun M23AvailabilityScreen(onBack: () -> Unit, viewModel: M23AvailabilityViewModel) {
    val state by viewModel.uiState.collectAsState(); M23Scaffold("Disponibilidad", onBack) { when (val s = state) { M23AvailabilityUiState.Loading -> LoadingState(); M23AvailabilityUiState.Empty -> EmptyState(title = "Sin horarios", message = "No hay horarios disponibles."); is M23AvailabilityUiState.Error -> ErrorState(s.message); is M23AvailabilityUiState.Content -> LazyColumn { s.page.days.forEach { day -> item { Text(day.date.toString()) }; items(day.slots) { slot -> Text("${slot.startsAt} · ${slot.modality}") } } } } }
}
@Composable fun M23MyBookingsScreen(onBack: () -> Unit, onDetail: (String) -> Unit, viewModel: M23MyBookingsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState(); M23Scaffold("Mis reservas", onBack) { when (val s = state) { M23MyBookingsUiState.Loading -> LoadingState(); M23MyBookingsUiState.Empty -> EmptyState(title = "Sin reservas", message = "Todavía no tenés reservas."); is M23MyBookingsUiState.Error -> ErrorState(s.message); is M23MyBookingsUiState.Content -> LazyColumn { items(s.bookings) { booking -> Button(onClick = { onDetail(booking.booking.id) }, modifier = Modifier.fillMaxWidth()) { Text("${booking.offeringName} · ${booking.booking.status}") } } } } }
}
@Composable fun M23BookingDetailScreen(onBack: () -> Unit, viewModel: M23BookingDetailViewModel) {
    val state by viewModel.uiState.collectAsState(); M23Scaffold("Reserva", onBack) { when (val s = state) { M23BookingDetailUiState.Loading -> LoadingState(); M23BookingDetailUiState.Empty -> EmptyState(title = "No disponible", message = "La reserva no está disponible."); is M23BookingDetailUiState.Error -> ErrorState(s.message); is M23BookingDetailUiState.Content -> { Text("${s.booking.startsAt} · ${s.booking.status}"); Button(onClick = { viewModel.confirm() }) { Text("Confirmar") } } } }
}
@Composable fun M23ManageScreen(onBack: () -> Unit, onCalendar: () -> Unit, onBookings: () -> Unit) = M23Scaffold("Gestionar agenda", onBack) { Button(onClick = onCalendar, modifier = Modifier.fillMaxWidth()) { Text("Disponibilidad") }; Button(onClick = onBookings, modifier = Modifier.fillMaxWidth()) { Text("Reservas recibidas") } }
@Composable fun M23ManageCalendarScreen(onBack: () -> Unit, viewModel: M23ManageCalendarViewModel) {
    val state by viewModel.uiState.collectAsState(); M23Scaffold("Mi calendario", onBack) { when (val s = state) { M23ManageCalendarUiState.Loading -> LoadingState(); M23ManageCalendarUiState.Empty -> EmptyState(title = "Sin reglas", message = "Configurá horarios de atención."); is M23ManageCalendarUiState.Error -> ErrorState(s.message); is M23ManageCalendarUiState.Content -> LazyColumn { items(s.rules) { Text("${it.dayOfWeek}: ${it.startTime} - ${it.endTime}") } } } }
}
@Composable fun M23ManageBookingsScreen(onBack: () -> Unit, onDetail: (String) -> Unit, viewModel: M23ManageBookingsViewModel) {
    val state by viewModel.uiState.collectAsState(); M23Scaffold("Reservas recibidas", onBack) { when (val s = state) { M23ManageBookingsUiState.Loading -> LoadingState(); M23ManageBookingsUiState.Empty -> EmptyState(title = "Sin reservas", message = "No hay reservas para gestionar."); is M23ManageBookingsUiState.Error -> ErrorState(s.message); is M23ManageBookingsUiState.Content -> LazyColumn { items(s.bookings) { booking -> Button(onClick = { onDetail(booking.id) }, modifier = Modifier.fillMaxWidth()) { Text("${booking.startsAt} · ${booking.status}") } } } } }
}
@Composable private fun M23Scaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) = Scaffold(topBar = { ComunidappTopBar(title, showBackButton = true, onBackClick = onBack) }) { padding -> Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
