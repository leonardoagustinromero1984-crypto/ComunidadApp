package com.comunidapp.app.ui.screens.daycare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.DaycareGuestRow
import com.comunidapp.app.data.repository.DaycareReservationRow
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoVerCard
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import kotlinx.coroutines.launch

class DaycareReservationsViewModel : ViewModel() {
    var rows by mutableStateOf<List<DaycareReservationRow>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            DataProvider.canonicalDaycareRepository.listReservations()
                .onSuccess { rows = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }
}

class DaycareGuestsViewModel : ViewModel() {
    var rows by mutableStateOf<List<DaycareGuestRow>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            DataProvider.canonicalDaycareRepository.listGuests()
                .onSuccess { rows = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }
}

@Composable
fun DaycareReservationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DaycareReservationsViewModel = viewModel()
) {
    VisualDirectionPilot {
        val visual = leoVisual()
        Scaffold(
            containerColor = visual.background,
            topBar = {
                LeoTopAppBar(
                    title = "Reservas",
                    subtitle = "Turnos futuros o confirmados, todavía no ingresados",
                    showBackButton = true,
                    onBackClick = onNavigateBack
                )
            }
        ) { padding ->
            when {
                viewModel.loading -> Text("Cargando…", modifier = Modifier.padding(padding).padding(LeoDimens.SpaceMd))
                viewModel.error != null -> LeoEmptyState(
                    title = "No pudimos cargar las reservas",
                    message = viewModel.error.orEmpty()
                )
                viewModel.rows.isEmpty() -> LeoEmptyState(
                    title = "Sin reservas",
                    message = "Las reservas aparecen acá hasta el check-in. Los huéspedes actuales están en Huéspedes."
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(LeoDimens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
                ) {
                    items(viewModel.rows, key = { it.id }) { row ->
                        LeoVerCard {
                            Text(row.petName ?: "Mascota", color = visual.textPrimary)
                            Text(
                                "Estado: ${row.status.orEmpty()}",
                                style = LeoCaption,
                                color = visual.textSecondary
                            )
                            row.startsAt?.let {
                                Text(it, style = LeoCaption, color = visual.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DaycareGuestsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DaycareGuestsViewModel = viewModel()
) {
    VisualDirectionPilot {
        val visual = leoVisual()
        Scaffold(
            containerColor = visual.background,
            topBar = {
                LeoTopAppBar(
                    title = "Huéspedes",
                    subtitle = "Mascotas con estadía activa",
                    showBackButton = true,
                    onBackClick = onNavigateBack
                )
            }
        ) { padding ->
            when {
                viewModel.loading -> Text("Cargando…", modifier = Modifier.padding(padding).padding(LeoDimens.SpaceMd))
                viewModel.error != null -> LeoEmptyState(
                    title = "No pudimos cargar los huéspedes",
                    message = viewModel.error.orEmpty()
                )
                viewModel.rows.isEmpty() -> LeoEmptyState(
                    title = "Sin huéspedes ahora",
                    message = "Solo se muestran mascotas con check-in. Una reserva sin ingreso no es un huésped."
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(LeoDimens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
                ) {
                    items(viewModel.rows, key = { it.id }) { row ->
                        LeoVerCard {
                            Text(row.petName ?: "Mascota", color = visual.textPrimary)
                            Text("Estadía activa", style = LeoCaption, color = visual.textSecondary)
                            row.checkedInAt?.let {
                                Text("Ingreso: $it", style = LeoCaption, color = visual.textSecondary)
                            }
                            row.plannedCheckoutAt?.let {
                                Text("Egreso previsto: $it", style = LeoCaption, color = visual.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
