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
import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.components.v2.V2SearchableCatalogField
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.LocationCatalogAdminViewModel

@Composable
fun LocationCatalogAdminScreen(
    onNavigateBack: () -> Unit,
    viewModel: LocationCatalogAdminViewModel = viewModel(factory = LocationCatalogAdminViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Ubicaciones",
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
                    "Provincias y localidades de Argentina. País interno = AR. " +
                        "Los cambios se guardan en el catálogo maestro (Admin).",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LocationLevel.entries.forEach { level ->
                        LeoFilterChip(
                            label = levelLabel(level),
                            selected = uiState.level == level,
                            onClick = { viewModel.onLevel(level) }
                        )
                    }
                }
                if (uiState.level != LocationLevel.PROVINCE && uiState.level != LocationLevel.COUNTRY) {
                    AdminParentPicker(
                        label = parentLabel(uiState.level),
                        selected = uiState.parents.firstOrNull { it.id == uiState.parentId },
                        options = uiState.parents,
                        onSelect = { viewModel.onParent(it?.id) }
                    )
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
                LeoOutlinedButton(
                    text = "Crear ${levelLabel(uiState.level).lowercase()}",
                    onClick = viewModel::startCreate,
                    enabled = uiState.level == LocationLevel.PROVINCE ||
                        uiState.level == LocationLevel.COUNTRY ||
                        uiState.parentId != null
                )
                uiState.message?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                }
                if (uiState.showEditor) {
                    LocationEditor(
                        level = uiState.level,
                        parents = uiState.parents,
                        draftName = uiState.draftName,
                        draftCode = uiState.draftCode,
                        draftAliases = uiState.draftAliases,
                        draftParentId = uiState.draftParentId,
                        isEdit = uiState.editing != null,
                        onName = viewModel::onDraftName,
                        onCode = viewModel::onDraftCode,
                        onAliases = viewModel::onDraftAliases,
                        onParent = viewModel::onDraftParent,
                        onSave = viewModel::saveEditor,
                        onCancel = viewModel::cancelEditor
                    )
                }
                if (uiState.items.isEmpty() && !uiState.showEditor) {
                    Text("No hay valores para este filtro.", style = MaterialTheme.typography.bodyMedium)
                }
                uiState.items.forEach { node ->
                    LocationRow(
                        node = node,
                        onEdit = { viewModel.startEdit(node) },
                        onToggle = { viewModel.toggleActive(node) },
                        onMoveUp = { viewModel.move(node, -1) },
                        onMoveDown = { viewModel.move(node, 1) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LocationRow(
    node: LocationNode,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(node.name, style = MaterialTheme.typography.titleMedium)
        Text(
            buildString {
                append(if (node.active) "Activo" else "Inactivo")
                append(" · orden ")
                append(node.order)
                node.code?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXs)) {
            TextButton(onClick = onEdit) { Text("Editar") }
            TextButton(onClick = onToggle) {
                Text(if (node.active) "Desactivar" else "Activar")
            }
            TextButton(onClick = onMoveUp) { Text("Subir") }
            TextButton(onClick = onMoveDown) { Text("Bajar") }
        }
        LeoHairline()
    }
}

@Composable
private fun LocationEditor(
    level: LocationLevel,
    parents: List<LocationNode>,
    draftName: String,
    draftCode: String,
    draftAliases: String,
    draftParentId: String?,
    isEdit: Boolean,
    onName: (String) -> Unit,
    onCode: (String) -> Unit,
    onAliases: (String) -> Unit,
    onParent: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (isEdit) "Editar" else "Nuevo", style = MaterialTheme.typography.titleMedium)
        if (level != LocationLevel.PROVINCE && level != LocationLevel.COUNTRY) {
            AdminParentPicker(
                label = parentLabel(level),
                selected = parents.firstOrNull { it.id == draftParentId },
                options = parents,
                onSelect = { onParent(it?.id) }
            )
        }
        OutlinedTextField(
            value = draftName,
            onValueChange = onName,
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = draftCode,
            onValueChange = onCode,
            label = { Text("Código (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = draftAliases,
            onValueChange = onAliases,
            label = { Text("Aliases (coma)") },
            modifier = Modifier.fillMaxWidth()
        )
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
private fun AdminParentPicker(
    label: String,
    selected: LocationNode?,
    options: List<LocationNode>,
    onSelect: (LocationNode?) -> Unit
) {
    V2SearchableCatalogField(
        label = label,
        selected = selected,
        options = options,
        onSelect = onSelect,
        emptyHint = "Elegí un padre"
    )
}

private fun levelLabel(level: LocationLevel): String = when (level) {
    LocationLevel.COUNTRY -> "Países"
    LocationLevel.PROVINCE -> "División administrativa"
    LocationLevel.MUNICIPALITY -> "Municipios"
    LocationLevel.LOCALITY -> "Localidades"
    LocationLevel.ZONE -> "Zonas"
}

private fun parentLabel(level: LocationLevel): String = when (level) {
    LocationLevel.COUNTRY -> "—"
    LocationLevel.PROVINCE -> "País"
    LocationLevel.MUNICIPALITY -> "División administrativa"
    LocationLevel.LOCALITY -> "Municipio / Partido"
    LocationLevel.ZONE -> "Localidad"
}
