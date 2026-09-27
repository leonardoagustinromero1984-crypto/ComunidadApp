package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.authorization.AdminAccessPolicy
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.user.AccountStatus
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.viewmodel.PlatformAdminViewModel

@Composable
fun PlatformAdminScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlatformAdminViewModel = viewModel(factory = PlatformAdminViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Usuarios",
                showBackButton = true,
                onBackClick = {
                    if (uiState.selectedUserId != null) viewModel.clearSelection()
                    else onNavigateBack()
                }
            )
        }
    ) { padding ->
        when {
            !uiState.accessChecked -> {
                LoadingState(contentModifier = Modifier.padding(padding))
            }
            !uiState.accessAllowed -> {
                LaunchedEffect(Unit) { onNavigateBack() }
                Column(Modifier.padding(padding).padding(24.dp)) {
                    Text("No tenés permiso para administrar.")
                }
            }
            uiState.selectedUserId != null -> {
                AdminUserDetail(
                    modifier = Modifier.padding(padding),
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
            else -> {
                AdminUserSearch(
                    modifier = Modifier.padding(padding),
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }

    if (uiState.confirmAction != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirm,
            title = { Text("Confirmar acción") },
            text = {
                Text(
                    when (uiState.confirmAction) {
                        "status" ->
                            "¿Cambiar estado a ${uiState.pendingStatus?.let { AdminAccessPolicy.accountStatusLabel(it) }}?"
                        "assign" ->
                            "¿Asignar rol ${uiState.pendingRole?.let { AdminAccessPolicy.platformRoleLabel(it) }}?"
                        "revoke" ->
                            "¿Revocar rol ${uiState.pendingRole?.let { AdminAccessPolicy.platformRoleLabel(it) }}?"
                        else -> "¿Continuar?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPendingAction) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelConfirm) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun AdminUserSearch(
    modifier: Modifier,
    uiState: com.comunidapp.app.viewmodel.PlatformAdminUiState,
    viewModel: PlatformAdminViewModel
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar por nombre o usuario") },
            singleLine = true
        )
        if (uiState.isSearching) {
            CircularProgressIndicator(color = BrandOrange)
        }
        uiState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        uiState.results.forEach { user ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectUser(user.id) }
                    .padding(vertical = 8.dp)
            ) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        user.username?.let { "@$it" },
                        AdminAccessPolicy.accountStatusLabel(user.accountStatus),
                        user.email.takeIf { uiState.canViewPrivate }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AdminUserDetail(
    modifier: Modifier,
    uiState: com.comunidapp.app.viewmodel.PlatformAdminUiState,
    viewModel: PlatformAdminViewModel
) {
    val user = uiState.selectedUser
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(user?.displayName ?: "Usuario", style = MaterialTheme.typography.headlineSmall)
        user?.username?.let { Text("@$it") }
        Text("Estado: ${user?.accountStatus?.let { AdminAccessPolicy.accountStatusLabel(it) } ?: "-"}")
        if (uiState.canViewPrivate) {
            user?.email?.takeIf { it.isNotBlank() }?.let { email ->
                Text("Email: $email")
            }
        }
        user?.createdAtIso?.let { Text("Alta: $it") }

        OutlinedTextField(
            value = uiState.reasonCode,
            onValueChange = viewModel::onReasonCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Motivo") },
            singleLine = true
        )

        Text("Roles de plataforma", style = MaterialTheme.typography.titleMedium)
        uiState.roles.forEach { role ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(AdminAccessPolicy.platformRoleLabel(role.roleCode))
                if (uiState.canRevokeRoles) {
                    TextButton(onClick = { viewModel.requestRevokeRole(role.roleCode) }) {
                        Text("Revocar")
                    }
                }
            }
        }

        if (uiState.canAssignRoles) {
            Text("Asignar rol", style = MaterialTheme.typography.titleMedium)
            val assignable = if (uiState.actorIsSuperadmin) {
                listOf(
                    PlatformRoleCode.USER,
                    PlatformRoleCode.MODERATOR,
                    PlatformRoleCode.ADMIN,
                    PlatformRoleCode.SUPERADMIN
                )
            } else {
                listOf(PlatformRoleCode.USER, PlatformRoleCode.MODERATOR)
            }
            assignable.forEach { role ->
                OutlinedButton(
                    onClick = { viewModel.requestAssignRole(role) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(AdminAccessPolicy.platformRoleLabel(role))
                }
            }
        }

        if (uiState.canChangeStatus) {
            Text("Cambiar estado", style = MaterialTheme.typography.titleMedium)
            AccountStatus.entries.forEach { status ->
                OutlinedButton(
                    onClick = { viewModel.requestStatusChange(status) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(AdminAccessPolicy.accountStatusLabel(status))
                }
            }
        }

        if (uiState.canViewAudit) {
            Text("Historial de estado", style = MaterialTheme.typography.titleMedium)
            if (uiState.history.isEmpty()) {
                Text("Sin entradas")
            } else {
                uiState.history.forEach { entry ->
                    Text(
                        "${entry.changedAtEpochMs ?: "-"}: " +
                            "${entry.previousStatus} → ${entry.newStatus} " +
                            "(${entry.reasonCode})"
                    )
                }
            }
        }

        if (uiState.isWorking) {
            CircularProgressIndicator(color = BrandOrange)
        }
        uiState.message?.let {
            Text(it)
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::clearMessage) { Text("OK") }
        }
    }
}
