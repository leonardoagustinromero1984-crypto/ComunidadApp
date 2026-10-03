package com.comunidapp.app.ui.screens.lostfound

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoActiveFilter
import com.comunidapp.app.ui.components.leo.LeoFilterBar
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoFilterSheet
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.viewmodel.LostFoundViewModel

@Composable
fun LostFoundScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    onNavigateToM13Sightings: () -> Unit = {},
    onNavigateToCaseMatches: (String) -> Unit = {},
    onNavigateToM13NewSighting: (String?) -> Unit = {},
    onCreateLost: () -> Unit = {},
    onCreateFound: () -> Unit = {},
    onResponderBase: () -> Unit = {},
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Perdidos / Encontrados",
                subtitle = "Alertas activas cerca de vos",
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
            onNavigateToM13Sightings = onNavigateToM13Sightings,
            onNavigateToCaseMatches = onNavigateToCaseMatches,
            onNavigateToM13NewSighting = onNavigateToM13NewSighting,
            onCreateLost = onCreateLost,
            onCreateFound = onCreateFound,
            onResponderBase = onResponderBase,
            viewModel = viewModel
        )
    }
}

@Composable
fun LostFoundContent(
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    onNavigateToMap: () -> Unit = {},
    onNavigateToM13Sightings: () -> Unit = {},
    onNavigateToCaseMatches: (String) -> Unit = {},
    onNavigateToM13NewSighting: (String?) -> Unit = {},
    onCreateLost: () -> Unit = {},
    onCreateFound: () -> Unit = {},
    onResponderBase: () -> Unit = {},
    lockedType: LostFoundType? = null,
    viewModel: LostFoundViewModel = viewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val sightingsByPost by viewModel.sightingsByPost.collectAsState()
    val currentUserId = remember { viewModel.currentUserId() }
    var sightingPostId by remember { mutableStateOf<String?>(null) }
    var sightingNote by remember { mutableStateOf("") }
    var sightingLocation by remember { mutableStateOf("") }
    var filtersOpen by remember { mutableStateOf(false) }
    var draftLocation by remember { mutableStateOf("") }
    var draftType by remember { mutableStateOf<LostFoundType?>(null) }
    var draftStatus by remember { mutableStateOf<LostFoundStatus?>(LostFoundStatus.ACTIVE) }
    var draftSpecies by remember { mutableStateOf<PetSpecies?>(null) }
    val activeFilters = buildList {
        filters.location.trim().takeIf { it.isNotEmpty() }?.let { add(LeoActiveFilter("location", it)) }
        if (lockedType == null) {
            filters.type?.let {
                add(LeoActiveFilter("type", if (it == LostFoundType.LOST) "Perdidos" else "Encontrados"))
            }
        }
        if (filters.status == LostFoundStatus.RESOLVED) add(LeoActiveFilter("status", "Resueltas"))
        filters.species?.let { add(LeoActiveFilter("species", it.toDisplayName())) }
    }

    LaunchedEffect(lockedType) {
        if (lockedType != null) {
            viewModel.onTypeFilterChange(lockedType)
        }
    }

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
                    V2LocationStringPicker(
                        value = sightingLocation,
                        onValueChange = { sightingLocation = it }
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
        Column(modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = LeoDimens.SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
            ) {
                LeoOutlinedButton(text = "Perdí a mi mascota", onClick = onCreateLost)
                LeoOutlinedButton(text = "Encontré un animal", onClick = onCreateFound)
                LeoOutlinedButton(text = "Ubicación base", onClick = onResponderBase)
            }
            LeoFilterBar(
                onOpenFilters = {
                    draftLocation = filters.location
                    draftType = filters.type
                    draftStatus = filters.status
                    draftSpecies = filters.species
                    filtersOpen = true
                },
                activeFilters = activeFilters,
                onRemoveFilter = { id ->
                    when (id) {
                        "location" -> viewModel.onLocationChange("")
                        "type" -> viewModel.onTypeFilterChange(lockedType)
                        "status" -> viewModel.onStatusFilterChange(LostFoundStatus.ACTIVE)
                        "species" -> viewModel.onSpeciesFilterChange(null)
                    }
                },
                onClearFilters = {
                    viewModel.onLocationChange("")
                    viewModel.onTypeFilterChange(lockedType)
                    viewModel.onStatusFilterChange(LostFoundStatus.ACTIVE)
                    viewModel.onSpeciesFilterChange(null)
                }
            )
            LeoFilterSheet(
                visible = filtersOpen,
                onDismiss = { filtersOpen = false },
                onClearDraft = {
                    draftLocation = ""
                    draftType = lockedType
                    draftStatus = LostFoundStatus.ACTIVE
                    draftSpecies = null
                },
                onApply = {
                    viewModel.onLocationChange(draftLocation)
                    viewModel.onTypeFilterChange(if (lockedType != null) lockedType else draftType)
                    viewModel.onStatusFilterChange(draftStatus)
                    viewModel.onSpeciesFilterChange(draftSpecies)
                    filtersOpen = false
                }
            ) {
                Text("Lugar", style = LeoCaption)
                V2LocationStringPicker(
                    value = draftLocation,
                    onValueChange = { draftLocation = it }
                )
                if (lockedType == null) {
                    LeoFilterChip(
                        label = "Perdidos",
                        selected = draftType == LostFoundType.LOST,
                        onClick = {
                            draftType = if (draftType == LostFoundType.LOST) null else LostFoundType.LOST
                        }
                    )
                    LeoFilterChip(
                        label = "Encontrados",
                        selected = draftType == LostFoundType.FOUND,
                        onClick = {
                            draftType = if (draftType == LostFoundType.FOUND) null else LostFoundType.FOUND
                        }
                    )
                }
                LeoFilterChip(
                    label = "Activas",
                    selected = draftStatus == LostFoundStatus.ACTIVE || draftStatus == null,
                    onClick = { draftStatus = LostFoundStatus.ACTIVE }
                )
                LeoFilterChip(
                    label = "Resueltas",
                    selected = draftStatus == LostFoundStatus.RESOLVED,
                    onClick = { draftStatus = LostFoundStatus.RESOLVED }
                )
                PetSpecies.entries.take(4).forEach { species ->
                    LeoFilterChip(
                        label = species.toDisplayName(),
                        selected = draftSpecies == species,
                        onClick = {
                            draftSpecies = if (draftSpecies == species) null else species
                        }
                    )
                }
            }
            LeoOutlinedButton(
                text = "Ver mapa de alertas",
                onClick = onNavigateToMap
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LeoDimens.SpaceMd,
                end = LeoDimens.SpaceMd,
                bottom = bottomPadding + LeoDimens.SpaceSm
            ),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            items(posts, key = { it.id }) { post ->
                val context = LocalContext.current
                val canResolve = post.status == LostFoundStatus.ACTIVE &&
                    currentUserId != null &&
                    post.authorId == currentUserId
                LostFoundCard(
                    post = post,
                    sightings = sightingsByPost[post.id].orEmpty(),
                    onMarkResolved = if (canResolve) {
                        { viewModel.markResolved(post.id) }
                    } else {
                        null
                    },
                    onOpenMap = { openInMaps(context, post) },
                    onReportSighting = if (post.status == LostFoundStatus.ACTIVE) {
                        {
                            sightingPostId = post.id
                            sightingNote = ""
                            sightingLocation = post.location
                        }
                    } else {
                        null
                    },
                    onOpenM13Matches = if (post.status == LostFoundStatus.ACTIVE) {
                        { onNavigateToCaseMatches(post.id) }
                    } else {
                        null
                    },
                    onOpenM13StructuredSighting = if (post.status == LostFoundStatus.ACTIVE) {
                        { onNavigateToM13NewSighting(post.id) }
                    } else {
                        null
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
    onReportSighting: (() -> Unit)? = null,
    onOpenM13Matches: (() -> Unit)? = null,
    onOpenM13StructuredSighting: (() -> Unit)? = null
) {
    val badgeText = com.comunidapp.app.domain.lostfound.LostFoundAlertLabel.forKind(post.type.name)
        ?: if (post.type == LostFoundType.LOST) {
            com.comunidapp.app.domain.lostfound.LostFoundAlertLabel.LOST
        } else {
            com.comunidapp.app.domain.lostfound.LostFoundAlertLabel.FOUND
        }
    val badgeColor = if (post.type == LostFoundType.LOST) UrgentRed else BrandGreen

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = badgeText,
            style = LeoCaption,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            modifier = Modifier.padding(bottom = LeoDimens.SpaceSm)
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
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(LeoDimens.SpaceSm))
        }
        Text(
            text = com.comunidapp.app.domain.pets.PetDisplayName.alertSubject(
                post.type,
                post.petName,
                post.species.toDisplayName()
            ),
            style = LeoCardTitle,
            color = BrandText,
            fontWeight = FontWeight.Bold
        )
        com.comunidapp.app.domain.lostfound.EstimatedAgeYears.displayLabel(post.estimatedAgeMonths)?.let { age ->
            Text(text = age, style = LeoCaption, color = BrandTextSecondary)
        }
        Text(
            text = "Por: ${post.authorName} · ${post.date}",
            style = LeoCaption,
            color = BrandTextSecondary
        )
        if (post.status == LostFoundStatus.RESOLVED) {
            Text(
                text = "Resuelto",
                style = LeoCaption,
                color = BrandGreen,
                modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
            )
        }
        Text(
            text = post.description,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandText,
            modifier = Modifier.padding(top = LeoDimens.SpaceSm)
        )
        Text(
            text = "📍 ${post.location}",
            style = LeoCaption,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
        )
        Text(
            text = "Contacto: ${post.contactInfo}",
            style = LeoCaption,
            color = BrandText,
            modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
        )
        if (post.status == LostFoundStatus.ACTIVE) {
            Column(
                modifier = Modifier.padding(top = LeoDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
            ) {
                onOpenMap?.let { open ->
                    LeoOutlinedButton(text = "Abrir en mapa", onClick = open)
                }
                onReportSighting?.let { report ->
                    LeoOutlinedButton(text = "Avistamiento rápido", onClick = report)
                }
                onOpenM13StructuredSighting?.let { open ->
                    LeoOutlinedButton(text = "Registrar avistamiento", onClick = open)
                }
                onOpenM13Matches?.let { open ->
                    LeoOutlinedButton(text = "Coincidencias", onClick = open)
                }
                onMarkResolved?.let { resolve ->
                    LeoPrimaryButton(text = "Marcar resuelta", onClick = resolve)
                }
            }
        }
        if (sightings.isNotEmpty()) {
            Text(
                text = "Avistamientos (${sightings.size})",
                style = LeoCardTitle,
                color = BrandText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = LeoDimens.SpaceCompact)
            )
            sightings.take(5).forEach { sighting ->
                Text(
                    text = "• ${sighting.reporterName}: ${sighting.note}" +
                        (sighting.locationText?.let { " ($it)" }.orEmpty()),
                    style = LeoCaption,
                    color = BrandTextSecondary,
                    modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
                )
            }
        }
        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceCompact))
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
