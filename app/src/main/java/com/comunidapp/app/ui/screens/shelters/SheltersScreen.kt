package com.comunidapp.app.ui.screens.shelters

import com.comunidapp.app.ui.theme.BrandBackground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.SheltersViewModel
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.theme.LeoDimens

@Composable
fun SheltersScreen(
    onShelterClick: (String) -> Unit,
    viewModel: SheltersViewModel = viewModel()
) {
    val shelters by viewModel.shelters.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Refugios") }
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
            items(shelters, key = { it.id }) { shelter ->
                ShelterCard(shelter = shelter, onClick = { onShelterClick(shelter.id) })
            }
        }
    }
}

@Composable
private fun ShelterCard(shelter: Shelter, onClick: () -> Unit) {
    ShelterListCard(shelter = shelter, onClick = onClick)
}

@Composable
fun ShelterListCard(shelter: Shelter, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(LeoDimens.SpaceCompact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetImage(
                imageUrl = shelter.photoUrl,
                modifier = Modifier.size(72.dp),
                contentDescription = shelter.name
            )
            Column(modifier = Modifier.padding(start = LeoDimens.SpaceCompact)) {
                Text(
                    text = shelter.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "📍 ${shelter.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = shelter.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2
                )
                Text(
                    text = "${shelter.adoptionPetIds.size} en adopción",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        LeoHairline()
    }
}
