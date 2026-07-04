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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetCard
import com.comunidapp.app.ui.util.formatDisplayDate
import com.comunidapp.app.viewmodel.MyPetsViewModel

@Composable
fun MyPetsScreen(
    onNavigateBack: () -> Unit,
    onPetClick: (String) -> Unit,
    onAddPet: () -> Unit = {},
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 72.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pets.isEmpty()) {
                    item {
                        Text(
                            text = "Todavía no tenés mascotas registradas. Tocá + para agregar la primera.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
                items(pets, key = { it.id }) { pet ->
                    PetCard(pet = pet, onClick = { onPetClick(pet.id) })
                    PetHealthCard(pet = pet)
                }
            }
            FloatingActionButton(
                onClick = onAddPet,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp
                    )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar mascota")
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
            pet.sterilized?.let {
                Text(
                    text = "Castración: ${when (it) {
                        SterilizationStatus.YES -> "Sí"
                        SterilizationStatus.NO -> "No"
                        SterilizationStatus.UNKNOWN -> "No especificado"
                    }}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.microchipId?.let {
                Text(
                    text = "Microchip: $it",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.lastVetVisit?.let {
                Text(
                    text = "Última consulta: ${formatDisplayDate(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.vaccinations.forEach { vac ->
                val next = vac.nextDueDate?.takeIf { d -> d.isNotBlank() }?.let { " · Próx: ${formatDisplayDate(it)}" }.orEmpty()
                Text(
                    text = "💉 ${vac.name}: ${formatDisplayDate(vac.date)}$next",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.lastDeworming?.let {
                val product = pet.dewormingProduct?.let { p -> " ($p)" }.orEmpty()
                Text(
                    text = "🪱 Desparasitación: ${formatDisplayDate(it)}$product",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.lastFleaTreatment?.let {
                val product = pet.fleaTreatmentProduct?.let { p -> " ($p)" }.orEmpty()
                Text(
                    text = "🐾 Antiparasitarios: ${formatDisplayDate(it)}$product",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            pet.healthNotes?.let {
                Text(
                    text = "Notas: $it",
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
