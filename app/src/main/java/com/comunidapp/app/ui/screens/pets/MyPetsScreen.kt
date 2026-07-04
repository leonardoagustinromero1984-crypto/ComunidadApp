package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetCard
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.MyPetsViewModel

@Composable
fun MyPetsScreen(
    onNavigateBack: () -> Unit,
    onPetClick: (String) -> Unit,
    viewModel: MyPetsViewModel = viewModel()
) {
    val pets by viewModel.pets.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Mis mascotas",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pets, key = { it.id }) { pet ->
                PetCard(pet = pet, onClick = { onPetClick(pet.id) })
                PetHealthCard(pet = pet)
            }
        }
    }
}

@Composable
private fun PetHealthCard(pet: Pet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Salud de ${pet.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            pet.vaccinations.forEach { vac ->
                Text(
                    text = "💉 ${vac.name}: ${vac.date}${vac.nextDueDate?.let { " (próx: $it)" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.lastDeworming?.let {
                Text(
                    text = "🪱 Desparasitación: $it",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.lastFleaTreatment?.let {
                Text(
                    text = "🐾 Antipulgas: $it",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (pet.reminders.isNotEmpty()) {
                Text(
                    text = "Recordatorios:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                pet.reminders.forEach { reminder ->
                    Text(
                        text = "⏰ ${reminder.title} — ${reminder.date}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
