package com.comunidapp.app.ui.screens.admin

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.repository.AdminStaffCredentials
import com.comunidapp.app.data.repository.AdminStaffSummary
import com.comunidapp.app.domain.authorization.AdminAccessPolicy
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.AdminStaffCreateViewModel
import com.comunidapp.app.viewmodel.AdminStaffDetailViewModel
import com.comunidapp.app.viewmodel.AdminStaffListViewModel

@Composable
fun AdminStaffListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: AdminStaffListViewModel = viewModel(factory = AdminStaffListViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Personal administrativo",
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
                if (uiState.canManage) {
                    LeoPrimaryButton(
                        text = "+ Crear usuario",
                        onClick = onNavigateToCreate
                    )
                }
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar") },
                    singleLine = true
                )
                uiState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (uiState.items.isEmpty() && !uiState.loading) {
                    Text("No hay personal administrativo.", style = MaterialTheme.typography.bodyMedium)
                }
                uiState.items.forEach { item ->
                    StaffListRow(item) { onNavigateToDetail(item.userId) }
                }
            }
        }
    }
}

@Composable
private fun StaffListRow(item: AdminStaffSummary, onClick: () -> Unit) {
    LeoListRow(
        title = item.displayName,
        subtitle = "${item.username} · ${AdminAccessPolicy.platformRoleLabel(item.role)} · ${if (item.active) "Activa" else "Inactiva"}",
        onClick = onClick
    )
}

@Composable
fun AdminStaffCreateScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminStaffCreateViewModel = viewModel(factory = AdminStaffCreateViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Crear usuario",
                showBackButton = true,
                onBackClick = {
                    if (uiState.credentials != null) viewModel.consumeCredentials()
                    onNavigateBack()
                }
            )
        }
    ) { padding ->
        when {
            !uiState.accessChecked -> LoadingState(contentModifier = Modifier.padding(padding))
            !uiState.accessAllowed -> {
                LaunchedEffect(Unit) { onNavigateBack() }
                Column(Modifier.padding(padding).padding(24.dp)) {
                    Text("No tenés permiso para crear personal.")
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
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = viewModel::onDisplayName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsername,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Usuario") },
                    singleLine = true
                )
                Text("Rol", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminAccessPolicy.assignableStaffRoles().forEach { role ->
                        LeoFilterChip(
                            label = AdminAccessPolicy.platformRoleLabel(role),
                            selected = uiState.role == role,
                            onClick = { viewModel.onRole(role) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = uiState.active, onCheckedChange = viewModel::onActive)
                    Text(
                        if (uiState.active) "Activa" else "Inactiva",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LeoPrimaryButton(
                    text = if (uiState.saving) "Creando…" else "Crear",
                    onClick = viewModel::submit,
                    enabled = !uiState.saving
                )
            }
        }
    }
    uiState.credentials?.let { creds ->
        TemporaryPasswordDialog(
            title = "Cuenta creada",
            credentials = creds,
            onDismiss = {
                viewModel.consumeCredentials()
                onNavigateBack()
            }
        )
    }
}

@Composable
fun AdminStaffDetailScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    viewModel: AdminStaffDetailViewModel = viewModel(
        key = userId,
        factory = AdminStaffDetailViewModel.factory(userId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.item
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = item?.displayName ?: "Personal administrativo",
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
            item == null -> Column(Modifier.padding(padding).padding(24.dp)) {
                Text("No se encontró el usuario.")
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(item.displayName, style = MaterialTheme.typography.headlineSmall)
                Text("Usuario: ${item.username}")
                Text("Rol: ${AdminAccessPolicy.platformRoleLabel(item.role)}")
                Text("Estado: ${if (item.active) "Activa" else "Inactiva"}")
                item.createdAtIso?.let { Text("Creación: ${it.take(10)}") }
                Text(
                    "Último acceso: ${item.lastSignInAtIso?.take(16)?.replace("T", " ") ?: "Sin registro"}"
                )
                Text("Debe cambiar contraseña: ${if (item.mustChangePassword) "Sí" else "No"}")
                uiState.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                }
                if (uiState.canManage && !item.isRoot && item.role != PlatformRoleCode.SUPERADMIN) {
                    Spacer(Modifier.height(8.dp))
                    Text("Cambiar rol", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                        AdminAccessPolicy.assignableStaffRoles().forEach { role ->
                            LeoOutlinedButton(
                                text = AdminAccessPolicy.platformRoleLabel(role),
                                onClick = { viewModel.requestRole(role) }
                            )
                        }
                    }
                    if (item.active) {
                        LeoOutlinedButton(
                            text = "Desactivar",
                            onClick = viewModel::requestDisable
                        )
                    } else {
                        LeoPrimaryButton(
                            text = "Reactivar",
                            onClick = viewModel::requestEnable
                        )
                    }
                    LeoOutlinedButton(
                        text = "Restablecer contraseña",
                        onClick = viewModel::requestReset
                    )
                    LeoOutlinedButton(
                        text = "Forzar cambio de contraseña",
                        onClick = viewModel::requestForceChange
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Historial administrativo", style = MaterialTheme.typography.titleMedium)
                if (uiState.audit.isEmpty()) {
                    Text("Sin eventos.", style = MaterialTheme.typography.bodySmall)
                } else {
                    uiState.audit.forEach { entry ->
                        Text(
                            "${staffAuditLabel(entry.action)} · ${entry.actorUsername} · ${entry.occurredAtIso.take(16).replace("T", " ")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
    if (uiState.pendingRole != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirm,
            title = { Text("Cambiar rol") },
            text = { Text("¿Asignar ${AdminAccessPolicy.platformRoleLabel(uiState.pendingRole!!)}?") },
            confirmButton = { TextButton(onClick = viewModel::confirmRole) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirm) { Text("Cancelar") } }
        )
    }
    if (uiState.confirmDisable) {
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirm,
            title = { Text("Desactivar") },
            text = { Text("La cuenta no podrá ingresar. El historial se conserva.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDisable) { Text("Desactivar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirm) { Text("Cancelar") } }
        )
    }
    if (uiState.confirmEnable) {
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirm,
            title = { Text("Reactivar") },
            text = { Text("La cuenta podrá volver a ingresar.") },
            confirmButton = { TextButton(onClick = viewModel::confirmEnable) { Text("Reactivar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirm) { Text("Cancelar") } }
        )
    }
    if (uiState.confirmReset) {
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirm,
            title = { Text("Restablecer contraseña") },
            text = { Text("Se generará una contraseña temporal. Se mostrará una sola vez.") },
            confirmButton = { TextButton(onClick = viewModel::confirmReset) { Text("Restablecer") } },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirm) { Text("Cancelar") } }
        )
    }
    if (uiState.confirmForceChange) {
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirm,
            title = { Text("Forzar cambio de contraseña") },
            text = { Text("En el próximo ingreso deberá elegir una contraseña nueva.") },
            confirmButton = { TextButton(onClick = viewModel::confirmForceChange) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirm) { Text("Cancelar") } }
        )
    }
    uiState.credentials?.let { creds ->
        TemporaryPasswordDialog(
            title = "Contraseña restablecida",
            credentials = creds.copy(username = item?.username.orEmpty()),
            onDismiss = viewModel::consumeCredentials
        )
    }
}

@Composable
private fun TemporaryPasswordDialog(
    title: String,
    credentials: AdminStaffCredentials,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (credentials.username.isNotBlank()) {
                    Text("Usuario:")
                    Text(credentials.username, style = MaterialTheme.typography.titleMedium)
                }
                Text("Contraseña temporal:")
                Text(credentials.temporaryPassword, style = MaterialTheme.typography.titleMedium)
                Text("Guardá esta contraseña. No volverá a mostrarse.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(credentials.temporaryPassword))
                }
            ) { Text("Copiar contraseña") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Listo") } }
    )
}

private fun staffAuditLabel(action: String): String = when (action) {
    "ADMIN_STAFF_CREATED" -> "Creación"
    "ADMIN_STAFF_DISABLED" -> "Desactivación"
    "ADMIN_STAFF_ENABLED" -> "Reactivación"
    "ADMIN_STAFF_ROLE_CHANGED" -> "Cambio de rol"
    "ADMIN_STAFF_PASSWORD_RESET" -> "Reset de contraseña"
    "ADMIN_STAFF_FORCE_PASSWORD_CHANGE" -> "Forzar cambio de contraseña"
    else -> action
}
