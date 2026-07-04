package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.ageDisplay
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.PetDetailViewModel

@Composable
fun PetDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: PetDetailViewModel = viewModel()
) {
    val pet by viewModel.pet.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = pet?.name ?: "Mascota",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val data = pet) {
            null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                PetImage(
                    imageUrl = data.photoUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    cornerRadius = 12.dp,
                    contentDescription = data.name
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = data.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${data.species.toDisplayName()} · ${data.sex.toDisplayName()} · ${data.ageDisplay()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tamaño: ${data.size.toDisplayName()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = data.description, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                PetHealthSection(pet = data)
            }
        }
    }
}

@Composable
private fun PetHealthSection(pet: Pet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Salud y cuidados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            pet.vaccinations.forEach { vac ->
                Text(
                    text = "💉 ${vac.name}: ${vac.date}${vac.nextDueDate?.let { " (próx: $it)" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            pet.lastDeworming?.let {
                Text("🪱 Desparasitación: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            pet.lastFleaTreatment?.let {
                Text("🐾 Antipulgas: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (pet.reminders.isNotEmpty()) {
                Text(
                    text = "Recordatorios",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                pet.reminders.forEach { reminder ->
                    Text(
                        text = "⏰ ${reminder.title} — ${reminder.date}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
