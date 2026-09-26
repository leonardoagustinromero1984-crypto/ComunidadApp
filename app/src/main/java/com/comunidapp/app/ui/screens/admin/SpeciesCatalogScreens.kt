package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.repository.CatalogSpecies
import com.comunidapp.app.domain.pets.SecondaryClassificationKind
import com.comunidapp.app.domain.pets.SpeciesLifecycle
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandGreenDark
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.SpeciesCatalogEditorViewModel
import com.comunidapp.app.viewmodel.SpeciesCatalogListViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeciesCatalogListScreen(
    onNavigateBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenSpecies: (String) -> Unit,
    viewModel: SpeciesCatalogListViewModel = viewModel(factory = SpeciesCatalogListViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Especies",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            !uiState.accessChecked -> LoadingState(contentModifier = Modifier.padding(padding))
            !uiState.accessAllowed -> {
                LaunchedEffect(Unit) { onNavigateBack() }
                Column(Modifier.padding(padding).padding(24.dp)) {
                    Text("No tenés permiso para ver catálogos.")
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Catálogo canónico de LeoVer. Activar una especie la habilita para altas nuevas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandTextSecondary
                )
                if (uiState.canManage) {
                    LeoPrimaryButton(
                        text = "+ Agregar especie",
                        onClick = onCreate
                    )
                }
                Text(
                    "Especies registradas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                uiState.message?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                }
                if (uiState.items.isEmpty()) {
                    Text(
                        "No hay especies registradas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandTextSecondary
                    )
                }
                uiState.items.forEach { species ->
                    SpeciesCatalogRowCard(
                        species = species,
                        canManage = uiState.canManage,
                        onOpen = { onOpenSpecies(species.code) },
                        onStatus = { viewModel.setStatus(species.code, it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeciesCatalogRowCard(
    species: CatalogSpecies,
    canManage: Boolean,
    onOpen: () -> Unit,
    onStatus: (String) -> Unit
) {
    val status = species.status.ifBlank {
        if (species.active) SpeciesLifecycle.ACTIVE else SpeciesLifecycle.INACTIVE
    }
    val secondLevel = if (species.secondaryClassificationEnabled) {
        species.secondaryLabelPlural?.takeIf { it.isNotBlank() }
            ?: SecondaryClassificationKind.kindLabel(species.secondaryClassificationKind)
    } else {
        SecondaryClassificationKind.kindLabel(SecondaryClassificationKind.NONE)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = LeoDimens.SpaceS)
        ) {
            Text(species.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            CatalogMetaLine("Segundo nivel", secondLevel)
            CatalogMetaLine(
                "Estado",
                SpeciesLifecycle.label(status).ifBlank { species.statusLabel }.ifBlank { status }
            )
            CatalogMetaLine("Salud configurada", "Se asocia en la ficha de la especie")
            if (canManage) {
                Spacer(Modifier.height(8.dp))
                Text("Acciones", style = MaterialTheme.typography.labelMedium, color = BrandTextSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS),
                    verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXs)
                ) {
                    listOf(
                        SpeciesLifecycle.PREPARATION,
                        SpeciesLifecycle.ACTIVE,
                        SpeciesLifecycle.INACTIVE
                    ).forEach { value ->
                        LeoFilterChip(
                            label = SpeciesLifecycle.label(value),
                            selected = status.equals(value, ignoreCase = true),
                            onClick = { onStatus(value) }
                        )
                    }
                }
            }
            TextButton(onClick = onOpen) { Text("Abrir ficha") }
        }
        LeoHairline()
    }
}

@Composable
private fun CatalogMetaLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = BrandTextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = BrandGreenDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SpeciesCatalogCreateScreen(
    onNavigateBack: () -> Unit,
    viewModel: SpeciesCatalogEditorViewModel = viewModel(factory = SpeciesCatalogEditorViewModel.factory(null))
) {
    val uiState by viewModel.uiState.collectAsState()
    if (uiState.saved) {
        LaunchedEffect(uiState.code) { onNavigateBack() }
    }
    SpeciesEditorScaffold(
        title = "Agregar especie",
        isCreate = true,
        onNavigateBack = onNavigateBack,
        viewModel = viewModel
    )
}

@Composable
fun SpeciesCatalogDetailScreen(
    speciesCode: String,
    onNavigateBack: () -> Unit,
    viewModel: SpeciesCatalogEditorViewModel = viewModel(
        key = speciesCode,
        factory = SpeciesCatalogEditorViewModel.factory(speciesCode)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    SpeciesEditorScaffold(
        title = uiState.species?.name?.uppercase() ?: speciesCode,
        isCreate = false,
        onNavigateBack = onNavigateBack,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeciesEditorScaffold(
    title: String,
    isCreate: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: SpeciesCatalogEditorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = title,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            !uiState.accessChecked -> LoadingState(contentModifier = Modifier.padding(padding))
            !uiState.accessAllowed -> {
                LaunchedEffect(Unit) { onNavigateBack() }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.confirmKindChange) {
                    AlertDialog(
                        onDismissRequest = viewModel::cancelKindChange,
                        title = { Text("Cambiar tipo de clasificación") },
                        text = {
                            Text("El almacenamiento es el mismo. Las mascotas existentes conservan su clasificación.")
                        },
                        confirmButton = {
                            TextButton(onClick = viewModel::confirmKindChange) { Text("Cambiar") }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::cancelKindChange) { Text("Cancelar") }
                        }
                    )
                }
                uiState.message?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Información", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    enabled = uiState.canManage,
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.code,
                    onValueChange = viewModel::onCode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Código") },
                    enabled = uiState.canManage && isCreate,
                    supportingText = {
                        if (!isCreate && uiState.species?.codeInUse == true) {
                            Text("El código no se puede cambiar: ya está en uso.")
                        }
                    },
                    singleLine = true
                )
                Text("Estado", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        SpeciesLifecycle.PREPARATION,
                        SpeciesLifecycle.ACTIVE,
                        SpeciesLifecycle.INACTIVE
                    ).forEach { status ->
                        LeoFilterChip(
                            label = SpeciesLifecycle.label(status),
                            selected = uiState.status == status,
                            onClick = {
                                if (!uiState.canManage) return@LeoFilterChip
                                if (isCreate) viewModel.onStatus(status) else viewModel.setStatus(status)
                            }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = uiState.secondaryEnabled,
                        onCheckedChange = viewModel::onSecondaryEnabled,
                        enabled = uiState.canManage
                    )
                    Text("¿Usa clasificación secundaria?", modifier = Modifier.padding(start = 8.dp))
                }
                if (uiState.secondaryEnabled) {
                    Text("Tipo", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            SecondaryClassificationKind.BREED,
                            SecondaryClassificationKind.TYPE,
                            SecondaryClassificationKind.VARIETY,
                            SecondaryClassificationKind.CUSTOM
                        ).forEach { kind ->
                            LeoFilterChip(
                                label = SecondaryClassificationKind.kindLabel(kind),
                                selected = uiState.secondaryKind == kind,
                                onClick = {
                                    if (uiState.canManage) viewModel.onKindSelected(kind)
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.labelSingular,
                        onValueChange = viewModel::onLabelSingular,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Label singular") },
                        enabled = uiState.canManage,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.labelPlural,
                        onValueChange = viewModel::onLabelPlural,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Label plural") },
                        enabled = uiState.canManage,
                        singleLine = true
                    )
                }
                if (uiState.canManage) {
                    LeoPrimaryButton(
                        text = if (isCreate) "Crear especie" else "Guardar información",
                        onClick = viewModel::saveSpecies
                    )
                }
                    LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceS))
                }
                if (!isCreate) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        secondaryClassificationSectionTitle(
                            enabled = uiState.secondaryEnabled,
                            kind = uiState.secondaryKind,
                            pluralLabel = uiState.labelPlural
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Clasificación secundaria: ${
                            if (uiState.secondaryEnabled) {
                                uiState.labelSingular.ifBlank {
                                    SecondaryClassificationKind.kindLabel(uiState.secondaryKind)
                                }
                            } else {
                                SecondaryClassificationKind.kindLabel(SecondaryClassificationKind.NONE)
                            }
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandTextSecondary
                    )
                    if (uiState.secondaryEnabled) {
                        if (uiState.canManage) {
                            OutlinedTextField(
                                value = uiState.newItemName,
                                onValueChange = viewModel::onNewItemName,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("+ Agregar ${uiState.labelSingular.ifBlank { "elemento" }.lowercase()}") },
                                singleLine = true
                            )
                            LeoOutlinedButton(
                                text = "+ Agregar ${uiState.labelSingular.ifBlank { "elemento" }.lowercase()}",
                                onClick = viewModel::addSecondaryItem
                            )
                        }
                        uiState.items.forEachIndexed { index, item ->
                            if (index > 0) {
                                LeoHairline()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (item.active) "Activo" else "Inactivo",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (uiState.canManage) {
                                    TextButton(onClick = { viewModel.toggleSecondaryItem(item) }) {
                                        Text(if (item.active) "Inhabilitar" else "Reactivar")
                                    }
                                }
                            }
                        }
                    }
                        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceS))
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Catálogo sanitario", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Los productos sanitarios se asocian a una o varias especies desde Vacunas, Antiparasitarios o Desparasitantes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandTextSecondary
                    )
                        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceS))
                    }
                }
            }
        }
    }
}

internal fun secondaryClassificationSectionTitle(
    enabled: Boolean,
    kind: String,
    pluralLabel: String
): String {
    if (!enabled || kind.equals(SecondaryClassificationKind.NONE, ignoreCase = true)) {
        return "Clasificación secundaria"
    }
    return pluralLabel.trim().ifBlank {
        SecondaryClassificationKind.defaultLabels(kind).second.ifBlank {
            SecondaryClassificationKind.kindLabel(kind)
        }
    }
}
