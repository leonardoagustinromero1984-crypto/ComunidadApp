package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.repository.CatalogBreed
import com.comunidapp.app.data.repository.CatalogHealthProduct
import com.comunidapp.app.data.repository.CatalogServiceCategory
import com.comunidapp.app.data.repository.CatalogSpecies
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.MasterCatalogAdminViewModel
import com.comunidapp.app.viewmodel.MasterCatalogTab

@Composable
fun MasterCatalogAdminScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLocations: () -> Unit = {},
    allowGeography: Boolean = false,
    initialTab: MasterCatalogTab = MasterCatalogTab.SPECIES,
    lockTab: Boolean = false,
    viewModel: MasterCatalogAdminViewModel = viewModel(
        key = "${initialTab.name}-$lockTab",
        factory = MasterCatalogAdminViewModel.factory(initialTab, lockTab)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = if (lockTab) MasterCatalogTab.title(uiState.tab) else "Catálogos",
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
                    Text("No tenés permiso para administrar.")
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
                    "Desactivar conserva el historial. Las mascotas existentes siguen mostrando el valor.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (allowGeography) {
                    LeoOutlinedButton(
                        text = "Provincias y localidades",
                        onClick = onNavigateToLocations
                    )
                }
                if (!uiState.lockTab) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MasterCatalogTab.entries.forEach { tab ->
                            LeoFilterChip(
                                label = MasterCatalogTab.title(tab),
                                selected = uiState.tab == tab,
                                onClick = { viewModel.onTab(tab) }
                            )
                        }
                    }
                }
                if (uiState.tab == MasterCatalogTab.BREEDS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.species.forEach { species ->
                            LeoFilterChip(
                                label = species.name,
                                selected = uiState.parentSpeciesCode == species.code,
                                onClick = { viewModel.onParentSpecies(species.code) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = uiState.includeInactive,
                        onCheckedChange = viewModel::onIncludeInactive
                    )
                    Text("Incluir inactivos", modifier = Modifier.padding(start = 8.dp))
                }
                if (uiState.canManage) {
                    LeoOutlinedButton(
                        text = "+ Agregar",
                        onClick = viewModel::startCreate
                    )
                }
                uiState.message?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                }
                if (uiState.showEditor) {
                    CatalogEditor(
                        tab = uiState.tab,
                        draftCode = uiState.draftCode,
                        draftName = uiState.draftName,
                        draftSort = uiState.draftSort,
                        draftActive = uiState.draftActive,
                        draftSpeciesCode = uiState.draftSpeciesCode,
                        draftSpeciesCodes = uiState.draftSpeciesCodes,
                        species = uiState.species,
                        isEdit = uiState.editingSpecies != null ||
                            uiState.editingBreed != null ||
                            uiState.editingProduct != null ||
                            uiState.editingServiceCategory != null,
                        onCode = viewModel::onDraftCode,
                        onName = viewModel::onDraftName,
                        onSort = viewModel::onDraftSort,
                        onActive = viewModel::onDraftActive,
                        onSpecies = viewModel::onDraftSpeciesCode,
                        onToggleSpecies = viewModel::toggleDraftSpecies,
                        onSave = viewModel::saveEditor,
                        onCancel = viewModel::cancelEditor
                    )
                }
                val items = viewModel.visibleItems()
                if (items.isEmpty() && !uiState.showEditor) {
                    Text("No hay valores para este filtro.", style = MaterialTheme.typography.bodyMedium)
                }
                items.forEach { item ->
                    when (item) {
                        is CatalogSpecies -> CatalogRow(
                            title = item.name,
                            subtitle = if (item.active) "Activa" else "Inactiva",
                            onEdit = { viewModel.startEditSpecies(item) },
                            onToggle = { viewModel.toggleSpecies(item) },
                            onMoveUp = { viewModel.moveSpecies(item, -1) },
                            onMoveDown = { viewModel.moveSpecies(item, 1) },
                            active = item.active,
                            canManage = uiState.canManage
                        )
                        is CatalogBreed -> CatalogRow(
                            title = item.name,
                            subtitle = if (item.active) "Activa" else "Inactiva",
                            onEdit = { viewModel.startEditBreed(item) },
                            onToggle = { viewModel.toggleBreed(item) },
                            onMoveUp = { viewModel.moveBreed(item, -1) },
                            onMoveDown = { viewModel.moveBreed(item, 1) },
                            active = item.active,
                            canManage = uiState.canManage
                        )
                        is CatalogHealthProduct -> CatalogRow(
                            title = item.displayName,
                            subtitle = buildString {
                                append(if (item.active) "Activo" else "Inactivo")
                                append(" · ")
                                append(
                                    if (item.unscoped || item.speciesCodes.isEmpty()) {
                                        "Sin especies asignadas"
                                    } else {
                                        item.speciesCodes.joinToString()
                                    }
                                )
                            },
                            onEdit = { viewModel.startEditProduct(item) },
                            onToggle = { viewModel.toggleProduct(item) },
                            onMoveUp = { viewModel.moveProduct(item, -1) },
                            onMoveDown = { viewModel.moveProduct(item, 1) },
                            active = item.active,
                            canManage = uiState.canManage
                        )
                        is CatalogServiceCategory -> CatalogRow(
                            title = item.name,
                            subtitle = if (item.active) "Activa" else "Inactiva",
                            onEdit = { viewModel.startEditServiceCategory(item) },
                            onToggle = { viewModel.toggleServiceCategory(item) },
                            onMoveUp = { viewModel.moveServiceCategory(item, -1) },
                            onMoveDown = { viewModel.moveServiceCategory(item, 1) },
                            active = item.active,
                            canManage = uiState.canManage
                        )
                        else -> Unit
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CatalogEditor(
    tab: MasterCatalogTab,
    draftCode: String,
    draftName: String,
    draftSort: String,
    draftActive: Boolean,
    draftSpeciesCode: String,
    draftSpeciesCodes: Set<String>,
    species: List<CatalogSpecies>,
    isEdit: Boolean,
    onCode: (String) -> Unit,
    onName: (String) -> Unit,
    onSort: (String) -> Unit,
    onActive: (Boolean) -> Unit,
    onSpecies: (String) -> Unit,
    onToggleSpecies: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (isEdit) "Editar" else "Nuevo", style = MaterialTheme.typography.titleMedium)
        if (tab != MasterCatalogTab.BREEDS) {
            OutlinedTextField(
                value = draftCode,
                onValueChange = onCode,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tab == MasterCatalogTab.SPECIES || tab == MasterCatalogTab.SERVICE_CATEGORIES) "Código estable" else "Código") },
                enabled = !isEdit || (tab != MasterCatalogTab.SPECIES && tab != MasterCatalogTab.SERVICE_CATEGORIES),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = draftName,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre visible") },
            singleLine = true
        )
        if (tab == MasterCatalogTab.BREEDS) {
            OutlinedTextField(
                value = draftSpeciesCode,
                onValueChange = onSpecies,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Especie (código)") },
                singleLine = true
            )
        }
        if (tab == MasterCatalogTab.VACCINES || tab == MasterCatalogTab.FLEA || tab == MasterCatalogTab.DEWORMERS) {
            Text("Especies compatibles", style = MaterialTheme.typography.titleSmall)
            if (draftSpeciesCodes.isEmpty()) {
                Text(
                    "Sin especies asignadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            species.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = row.code in draftSpeciesCodes,
                        onCheckedChange = { onToggleSpecies(row.code) }
                    )
                    Text(row.name, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        OutlinedTextField(
            value = draftSort,
            onValueChange = onSort,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Orden") },
            singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = draftActive, onCheckedChange = onActive)
            Text("Activo", modifier = Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
            LeoPrimaryButton(
                text = "Guardar",
                onClick = onSave,
                modifier = Modifier.weight(1f),
                fillMaxWidth = false
            )
            LeoOutlinedButton(
                text = "Cancelar",
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CatalogRow(
    title: String,
    subtitle: String,
    active: Boolean,
    canManage: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        if (canManage) {
            Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXs)) {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onToggle) { Text(if (active) "Inhabilitar" else "Habilitar") }
                TextButton(onClick = onMoveUp) { Text("Subir") }
                TextButton(onClick = onMoveDown) { Text("Bajar") }
            }
        }
        LeoHairline()
    }
}
