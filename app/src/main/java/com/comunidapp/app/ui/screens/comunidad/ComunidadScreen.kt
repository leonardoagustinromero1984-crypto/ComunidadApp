package com.comunidapp.app.ui.screens.comunidad

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.CommunityCategory
import com.comunidapp.app.data.model.CommunityListing
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetImage

private data class ComunidadCategory(
    val category: CommunityCategory,
    val label: String
)

private val comunidadCategories = listOf(
    ComunidadCategory(CommunityCategory.VET, "Veterinarias"),
    ComunidadCategory(CommunityCategory.TRAINER, "Educadores"),
    ComunidadCategory(CommunityCategory.WALKER, "Paseadores"),
    ComunidadCategory(CommunityCategory.SHOP, "Tiendas")
)

@Composable
fun ComunidadScreen() {
    var selectedCategory by remember { mutableStateOf(comunidadCategories.first()) }

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
                            selected = selectedCategory == item,
                            onClick = { selectedCategory = item },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val listings = MockData.communityListings.filter {
            it.category == selectedCategory.category
        }
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
            items(listings, key = { it.id }) { listing ->
                CommunityListingCard(listing = listing)
            }
        }
    }
}

@Composable
fun CommunityListingCard(listing: CommunityListing) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetImage(
                imageUrl = listing.photoUrl,
                modifier = Modifier.size(72.dp),
                contentDescription = listing.name
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = listing.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "📍 ${listing.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = listing.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2
                )
                if (listing.tags.isNotEmpty()) {
                    Text(
                        text = listing.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                listing.contactInfo?.let { contact ->
                    Text(
                        text = "Contacto: $contact",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
