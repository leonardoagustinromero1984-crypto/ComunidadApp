package com.comunidapp.app.ui.screens.m17

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M17InKindListUiState
import com.comunidapp.app.viewmodel.M17InKindListViewModel
import com.comunidapp.app.viewmodel.M17VolunteerListUiState
import com.comunidapp.app.viewmodel.M17VolunteerListViewModel

@Composable
fun M17HubScreen(
    onNavigateBack: () -> Unit,
    onCampaigns: () -> Unit,
    onInKindDetail: (String) -> Unit = {},
    onVolunteerDetail: (String) -> Unit = {}
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Campañas", "Bienes", "Voluntariado")

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Donaciones y voluntariado",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Los pagos reales todavía no están habilitados.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = {
                            tab = index
                            if (index == 0) onCampaigns()
                        },
                        text = { Text(label) }
                    )
                }
            }
            when (tab) {
                0 -> Column(Modifier.padding(16.dp)) {
                    Text("Directorio de campañas solidarias con transparencia mock.")
                    Button(onClick = onCampaigns, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Ver campañas")
                    }
                }
                1 -> M17InKindListContent(onItemClick = onInKindDetail)
                2 -> M17VolunteerListContent(onItemClick = onVolunteerDetail)
            }
        }
    }
}

@Composable
private fun M17InKindListContent(
    onItemClick: (String) -> Unit,
    viewModel: M17InKindListViewModel = viewModel(factory = M17InKindListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    when (val s = state) {
        M17InKindListUiState.Loading -> LoadingState(Modifier.fillMaxSize())
        M17InKindListUiState.Empty -> EmptyState("No hay necesidades publicadas", Modifier.fillMaxSize())
        is M17InKindListUiState.Error -> ErrorState(s.message, Modifier.fillMaxSize())
        is M17InKindListUiState.Content -> LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(s.items, key = { it.id }) { need ->
                Card(
                    Modifier.fillMaxWidth().clickable { onItemClick(need.id) }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(need.title, fontWeight = FontWeight.Bold)
                        Text(need.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
                        Text("${need.quantityDelivered}/${need.quantityRequested} ${need.quantityUnit}")
                        LinearProgressIndicator(
                            progress = { need.coveragePercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun M17VolunteerListContent(
    onItemClick: (String) -> Unit,
    viewModel: M17VolunteerListViewModel = viewModel(factory = M17VolunteerListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    when (val s = state) {
        M17VolunteerListUiState.Loading -> LoadingState(Modifier.fillMaxSize())
        M17VolunteerListUiState.Empty -> EmptyState("No hay oportunidades publicadas", Modifier.fillMaxSize())
        is M17VolunteerListUiState.Error -> ErrorState(s.message, Modifier.fillMaxSize())
        is M17VolunteerListUiState.Content -> LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(s.items, key = { it.id }) { opp ->
                Card(
                    Modifier.fillMaxWidth().clickable { onItemClick(opp.id) }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(opp.title, fontWeight = FontWeight.Bold)
                        Text(opp.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
                        Text("Cupos: ${opp.slotsFilled}/${opp.slotsNeeded}")
                        opp.scheduleHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
