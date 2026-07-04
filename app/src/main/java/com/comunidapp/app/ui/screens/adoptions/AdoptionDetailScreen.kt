package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.comunidapp.app.ui.components.AdoptionStatusBadge
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.ageDisplay
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.AdoptionDetailViewModel

@Composable
fun AdoptionDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdoptionDetailViewModel = viewModel()
) {
    val post by viewModel.post.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = post?.name ?: "Adopción",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val adoption = post) {
            null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                PetImage(
                    imageUrl = adoption.photoUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    cornerRadius = 12.dp,
                    contentDescription = adoption.name
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = adoption.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = adoption.shelterName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                AdoptionStatusBadge(status = adoption.status)
                Spacer(modifier = Modifier.height(16.dp))
                DetailRow("Especie", adoption.species.toDisplayName())
                DetailRow("Sexo", adoption.sex.toDisplayName())
                DetailRow("Edad", adoption.ageDisplay())
                DetailRow("Tamaño", adoption.size.toDisplayName())
                DetailRow("Zona", adoption.location)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Descripción",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = adoption.description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (adoption.status == com.comunidapp.app.data.model.AdoptionStatus.AVAILABLE) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { /* TODO: contacto refugio */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quiero adoptar")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
