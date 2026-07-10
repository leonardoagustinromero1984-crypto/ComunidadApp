package com.comunidapp.app.ui.screens.comunidad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.ComunidadViewModel

private data class ComunidadCategoryChip(
    val category: ServiceCategory,
    val label: String
)

private val comunidadCategories = listOf(
    ComunidadCategoryChip(ServiceCategory.VET, "Veterinarias"),
    ComunidadCategoryChip(ServiceCategory.TRAINER, "Educadores"),
    ComunidadCategoryChip(ServiceCategory.WALKER, "Paseadores"),
    ComunidadCategoryChip(ServiceCategory.SHOP, "Tiendas")
)

@Composable
fun ComunidadScreen(
    onServiceClick: (String) -> Unit,
    viewModel: ComunidadViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val services by viewModel.services.collectAsState()

    Scaffold(
        topBar = {
            Column {
                ComunidappTopBar(title = "Comunidad")
                Text(
                    text = "Veterinarias, tiendas, paseadores y más para tu mascota",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    comunidadCategories.forEach { item ->
                        FilterChip(
                            selected = uiState.selectedCategory == item.category,
                            onClick = { viewModel.selectCategory(item.category) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (services.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Todavía no hay servicios en esta categoría",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(services, key = { it.id }) { service ->
                    ServiceProfileCard(
                        service = service,
                        onClick = { onServiceClick(service.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceProfileCard(
    service: ServiceProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetImage(
                imageUrl = service.photoUrl,
                modifier = Modifier.size(72.dp),
                contentDescription = service.name
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "📍 ${service.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = service.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (service.acceptsBookings) {
                        Text(
                            text = "Turnos online",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    service.priceFrom?.let { price ->
                        Text(
                            text = "Desde $${price.toInt()}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
