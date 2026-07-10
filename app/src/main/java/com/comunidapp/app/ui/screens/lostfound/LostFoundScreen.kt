package com.comunidapp.app.ui.screens.lostfound

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundSighting
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.LostFoundViewModel

@Composable
fun LostFoundScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    viewModel: LostFoundViewModel = viewModel()
) {
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Perdidos / Encontrados",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LostFoundContent(
            topPadding = padding.calculateTopPadding(),
            bottomPadding = padding.calculateBottomPadding(),
            onNavigateToMap = onNavigateToMap,
            viewModel = viewModel
        )
    }
}

@Composable
fun LostFoundContent(
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    onNavigateToMap: () -> Unit = {},
    viewModel: LostFoundViewModel = viewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val sightingsByPost by viewModel.sightingsByPost.collectAsState()
    var sightingPostId by remember { mutableStateOf<String?>(null) }
    var sightingNote by remember { mutableStateOf("") }
    var sightingLocation by remember { mutableStateOf("") }

    if (sightingPostId != null) {
        AlertDialog(
            onDismissRequest = {
                sightingPostId = null
                sightingNote = ""
                sightingLocation = ""
            },
            title = { Text("Reportar avistamiento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sightingNote,
                        onValueChange = { sightingNote = it },
                        label = { Text("Nota") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = sightingLocation,
                        onValueChange = { sightingLocation = it },
                        label = { Text("Ubicación") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = sightingPostId ?: return@TextButton
                        viewModel.addSighting(id, sightingNote, sightingLocation)
                        sightingPostId = null
                        sightingNote = ""
                        sightingLocation = ""
                    }
                ) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        sightingPostId = null
                        sightingNote = ""
                        sightingLocation = ""
                    }
                ) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = filters.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text("Filtrar por zona") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filters.type == LostFoundType.LOST,
                    onClick = {
                        viewModel.onTypeFilterChange(
                            if (filters.type == LostFoundType.LOST) null else LostFoundType.LOST
                        )
                    },
                    label = { Text("Perdidos") }
                )
                FilterChip(
                    selected = filters.type == LostFoundType.FOUND,
                    onClick = {
                        viewModel.onTypeFilterChange(
                            if (filters.type == LostFoundType.FOUND) null else LostFoundType.FOUND
                        )
                    },
                    label = { Text("Encontrados") }
                )
                PetSpecies.entries.take(4).forEach { species ->
                    FilterChip(
                        selected = filters.species == species,
                        onClick = {
                            viewModel.onSpeciesFilterChange(
                                if (filters.species == species) null else species
                            )
                        },
                        label = { Text(species.toDisplayName()) }
                    )
                }
            }
            OutlinedButton(
                onClick = onNavigateToMap,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver mapa de alertas")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = bottomPadding + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(posts, key = { it.id }) { post ->
                val context = LocalContext.current
                LostFoundCard(
                    post = post,
                    sightings = sightingsByPost[post.id].orEmpty(),
                    onMarkResolved = { viewModel.markResolved(post.id) },
                    onOpenMap = { openInMaps(context, post) },
                    onReportSighting = {
                        sightingPostId = post.id
                        sightingNote = ""
                        sightingLocation = post.location
                    }
                )
            }
        }
    }
}

@Composable
fun LostFoundCard(
    post: LostFoundPost,
    sightings: List<LostFoundSighting> = emptyList(),
    onMarkResolved: (() -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null,
    onReportSighting: (() -> Unit)? = null
) {
    val badgeText = if (post.type == LostFoundType.LOST) "PERDIDO" else "ENCONTRADO"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (post.type == LostFoundType.LOST) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
            post.photoUrl?.let { url ->
                PetImage(
                    imageUrl = url,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    cornerRadius = 8.dp,
                    contentDescription = post.petName
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = post.petName ?: "${post.species.toDisplayName()} sin nombre",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Por: ${post.authorName} · ${post.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (post.status == LostFoundStatus.RESOLVED) {
                Text(
                    text = "Resuelto",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = post.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "📍 ${post.location}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Contacto: ${post.contactInfo}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (post.status == LostFoundStatus.ACTIVE) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    onOpenMap?.let { open ->
                        OutlinedButton(onClick = open) { Text("Abrir en mapa") }
                    }
                    onReportSighting?.let { report ->
                        OutlinedButton(onClick = report) { Text("Reportar avistamiento") }
                    }
                    onMarkResolved?.let { resolve ->
                        Button(onClick = resolve) { Text("Marcar resuelta") }
                    }
                }
            }
            if (sightings.isNotEmpty()) {
                Text(
                    text = "Avistamientos (${sightings.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                sightings.take(5).forEach { sighting ->
                    Text(
                        text = "• ${sighting.reporterName}: ${sighting.note}" +
                            (sighting.locationText?.let { " ($it)" }.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LostFoundMapScreen(
    onNavigateBack: () -> Unit,
    viewModel: LostFoundViewModel = viewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val context = LocalContext.current
    val withCoords = posts.filter { it.latitude != null && it.longitude != null }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Mapa de alertas",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${posts.size} alertas · ${withCoords.size} con GPS",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "Tocá una alerta para abrirla en el mapa (geo:)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(posts, key = { it.id }) { post ->
                    Card(onClick = { openInMaps(context, post) }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${post.petName ?: post.species.toDisplayName()} · ${post.location}"
                            )
                            if (post.latitude != null && post.longitude != null) {
                                Text(
                                    text = "${post.latitude}, ${post.longitude}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openInMaps(context: android.content.Context, post: LostFoundPost) {
    val query = Uri.encode(post.location)
    val uri = if (post.latitude != null && post.longitude != null) {
        Uri.parse("geo:${post.latitude},${post.longitude}?q=${post.latitude},${post.longitude}")
    } else {
        Uri.parse("geo:0,0?q=$query")
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
