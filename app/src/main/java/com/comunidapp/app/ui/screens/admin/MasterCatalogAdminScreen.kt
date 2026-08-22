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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.comunidapp.app.data.repository.CatalogSpecies
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.viewmodel.MasterCatalogAdminViewModel
import com.comunidapp.app.viewmodel.MasterCatalogTab

@Composable
fun MasterCatalogAdminScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLocations: () -> Unit = {},
    viewModel: MasterCatalogAdminViewModel = viewModel(factory = MasterCatalogAdminViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Catálogos",
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
                    "Datos maestros de LeoVer. Un cambio de nombre no cambia el identificador estable. " +
                        "Desactivar conserva historial. Admin es autorización interna, no un perfil social.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onNavigateToLocations, modifier = Modifier.fillMaxWidth()) {
                    Text("Provincias y localidades")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MasterCatalogTab.entries.forEach { tab ->
                        FilterChip(
                            selected = uiState.tab == tab,
                            onClick = { viewModel.onTab(tab) },
                            label = { Text(tabLabel(tab)) }
                        )
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
                            FilterChip(
                                selected = uiState.parentSpeciesCode == species.code,
                                onClick = { viewModel.onParentSpecies(species.code) },
                                label = { Text(species.name) }
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
                OutlinedButton(onClick = viewModel::startCreate, modifier = Modifier.fillMaxWidth()) {
                    Text("Crear")
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
                        species = uiState.species,
                        isEdit = uiState.editingSpecies != null ||
                            uiState.editingBreed != null ||
                            uiState.editingProduct != null,
                        onCode = viewModel::onDraftCode,
                        onName = viewModel::onDraftName,
                        onSort = viewModel::onDraftSort,
                        onActive = viewModel::onDraftActive,
                        onSpecies = viewModel::onDraftSpeciesCode,
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
                            subtitle = "${item.code} · orden ${item.sortKey}" +
                                if (item.active) "" else " · inactivo",
                            onEdit = { viewModel.startEditSpecies(item) },
                            onToggle = { viewModel.toggleSpecies(item) },
                            onMoveUp = { viewModel.moveSpecies(item, -1) },
                            onMoveDown = { viewModel.moveSpecies(item, 1) },
                            active = item.active
                        )
                        is CatalogBreed -> CatalogRow(
                            title = item.name,
                            subtitle = item.speciesCode +
                                if (item.active) "" else " · inactivo",
                            onEdit = { viewModel.startEditBreed(item) },
                            onToggle = { viewModel.toggleBreed(item) },
                            onMoveUp = { viewModel.moveBreed(item, -1) },
                            onMoveDown = { viewModel.moveBreed(item, 1) },
                            active = item.active
                        )
                        is CatalogHealthProduct -> CatalogRow(
                            title = item.displayName,
                            subtitle = listOfNotNull(
                                item.code,
                                item.speciesCode,
                                if (item.active) null else "inactivo"
                            ).joinToString(" · "),
                            onEdit = { viewModel.startEditProduct(item) },
                            onToggle = { viewModel.toggleProduct(item) },
                            onMoveUp = { viewModel.moveProduct(item, -1) },
                            onMoveDown = { viewModel.moveProduct(item, 1) },
                            active = item.active
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
    species: List<CatalogSpecies>,
    isEdit: Boolean,
    onCode: (String) -> Unit,
    onName: (String) -> Unit,
    onSort: (String) -> Unit,
    onActive: (Boolean) -> Unit,
    onSpecies: (String) -> Unit,
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
                label = { Text(if (tab == MasterCatalogTab.SPECIES) "Código estable" else "Código") },
                enabled = !isEdit || tab != MasterCatalogTab.SPECIES,
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
        if (tab == MasterCatalogTab.BREEDS || tab == MasterCatalogTab.VACCINES) {
            OutlinedTextField(
                value = draftSpeciesCode,
                onValueChange = onSpecies,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tab == MasterCatalogTab.BREEDS) "Especie (código)" else "Especie opcional") },
                singleLine = true,
                supportingText = {
                    Text(species.joinToString { it.code }.ifBlank { "Sin especies cargadas" })
                }
            )
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Guardar") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        }
    }
}

@Composable
private fun CatalogRow(
    title: String,
    subtitle: String,
    active: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit) { Text("Editar") }
            TextButton(onClick = onToggle) { Text(if (active) "Desactivar" else "Activar") }
            TextButton(onClick = onMoveUp) { Text("Subir") }
            TextButton(onClick = onMoveDown) { Text("Bajar") }
        }
    }
}

private fun tabLabel(tab: MasterCatalogTab): String = when (tab) {
    MasterCatalogTab.SPECIES -> "Especies"
    MasterCatalogTab.BREEDS -> "Razas"
    MasterCatalogTab.VACCINES -> "Vacunas"
    MasterCatalogTab.FLEA -> "Antipulgas"
    MasterCatalogTab.DEWORMERS -> "Desparasitarios"
}
