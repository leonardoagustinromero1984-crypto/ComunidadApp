package com.comunidapp.app.ui.screens.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.components.ComunidappTopBar

@Composable
fun PublishScreen(
    onNavigateToGeneral: () -> Unit,
    onNavigateToAdoption: () -> Unit,
    onNavigateToLostFound: () -> Unit
) {
    Scaffold(
        topBar = { ComunidappTopBar(title = "Publicar") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "¿Qué querés publicar?",
                style = MaterialTheme.typography.titleLarge
            )
            PublishOptionCard(
                icon = Icons.Default.Add,
                title = "Publicación general",
                description = "Compartí una foto, historia o novedad con la comunidad",
                onClick = onNavigateToGeneral
            )
            PublishOptionCard(
                icon = Icons.Default.Favorite,
                title = "Animal en adopción",
                description = "Publicá un animal que busca familia",
                onClick = onNavigateToAdoption
            )
            PublishOptionCard(
                icon = Icons.Default.Search,
                title = "Perdido / Encontrado",
                description = "Ayudá a reunir mascotas con sus familias",
                onClick = onNavigateToLostFound
            )
        }
    }
}

@Composable
private fun PublishOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
