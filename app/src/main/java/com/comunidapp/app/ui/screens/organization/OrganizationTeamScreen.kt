package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.viewmodel.OrganizationTeamViewModel

@Composable
fun OrganizationTeamScreen(
    onNavigateBack: () -> Unit,
    onLeftOrganization: () -> Unit,
    onClosedOrganization: () -> Unit,
    viewModel: OrganizationTeamViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf<OrganizationMembership?>(null) }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Salir de la organización") },
            text = { Text("¿Confirmás que querés dejar de ser miembro?") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveOrganization(onLeftOrganization)
                }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancelar") }
            }
        )
    }
    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("Cerrar organización") },
            text = { Text("Esta acción bloquea la administración operativa. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseDialog = false
                    viewModel.closeOrganization(onClosedOrganization)
                }) { Text("Cerrar") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) { Text("Cancelar") }
            }
        )
    }
    transferTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { transferTarget = null },
            title = { Text("Transferir ownership") },
            text = { Text("¿Transferir ownership al usuario ${target.userId}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.transferOwnership(target.userId)
                    transferTarget = null
                }) { Text("Transferir") }
            },
            dismissButton = {
                TextButton(onClick = { transferTarget = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Equipo",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.errorMessage?.let { msg ->
                    item {
                        Text(text = msg, color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.successMessage?.let { msg ->
                    item {
                        Text(text = msg, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (uiState.canInvite) {
                    item {
                        InviteMemberSection(
                            email = uiState.inviteEmail,
                            role = uiState.inviteRole,
                            isInviting = uiState.isInviting,
                            onEmailChange = viewModel::onInviteEmailChange,
                            onRoleChange = viewModel::onInviteRoleChange,
                            onInvite = viewModel::inviteMember
                        )
                    }
                }
                item {
                    Text(
                        text = "Miembros",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (uiState.members.isEmpty()) {
                    item {
                        Text(
                            text = "Sin miembros visibles o sin permiso.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(uiState.members, key = { it.id }) { member ->
                        MemberCard(
                            member = member,
                            canManageRoles = uiState.canManageRoles,
                            canRemove = uiState.canRemove,
                            canTransfer = uiState.canTransferOwnership,
                            ownerCount = uiState.ownerCount,
                            onChangeRole = { viewModel.changeRole(member.userId, it) },
                            onSuspend = { viewModel.suspendMember(member.userId) },
                            onRemove = { viewModel.removeMember(member.userId) },
                            onTransfer = { transferTarget = member }
                        )
                    }
                }
                if (uiState.invitations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invitaciones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(uiState.invitations, key = { it.id }) { invitation ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "${invitation.invitedRole.name} · ${invitation.status.name}",
                                    fontWeight = FontWeight.Medium
                                )
                                invitation.targetEmailHint?.let {
                                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (uiState.canManageMembers &&
                                    invitation.status.name == "PENDING"
                                ) {
                                    TextButton(onClick = { viewModel.revokeInvitation(invitation.id) }) {
                                        Text("Revocar")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showLeaveDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Salir de la organización") }
                    if (uiState.canTransferOwnership) {
                        OutlinedButton(
                            onClick = { showCloseDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cerrar organización") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteMemberSection(
    email: String,
    role: OrganizationRoleCode,
    isInviting: Boolean,
    onEmailChange: (String) -> Unit,
    onRoleChange: (OrganizationRoleCode) -> Unit,
    onInvite: () -> Unit
) {
    val roles = listOf(
        OrganizationRoleCode.ADMIN,
        OrganizationRoleCode.MANAGER,
        OrganizationRoleCode.MEMBER,
        OrganizationRoleCode.VIEWER
    )
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Invitar miembro", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(text = "Rol: ${role.name}", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                roles.forEach { r ->
                    TextButton(onClick = { onRoleChange(r) }) {
                        Text(r.name)
                    }
                }
            }
            Button(
                onClick = onInvite,
                enabled = !isInviting,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isInviting) "Enviando…" else "Invitar") }
        }
    }
}

@Composable
private fun MemberCard(
    member: OrganizationMembership,
    canManageRoles: Boolean,
    canRemove: Boolean,
    canTransfer: Boolean,
    ownerCount: Int,
    onChangeRole: (OrganizationRoleCode) -> Unit,
    onSuspend: () -> Unit,
    onRemove: () -> Unit,
    onTransfer: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = member.userId, fontWeight = FontWeight.Medium)
            Text(
                text = "${member.role.name} · ${member.status.name}",
                style = MaterialTheme.typography.bodySmall
            )
            if (canManageRoles && member.role != OrganizationRoleCode.OWNER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onChangeRole(OrganizationRoleCode.MEMBER) }) {
                        Text("Miembro")
                    }
                    TextButton(onClick = { onChangeRole(OrganizationRoleCode.VIEWER) }) {
                        Text("Viewer")
                    }
                }
            }
            if (canRemove && !(member.role == OrganizationRoleCode.OWNER && ownerCount <= 1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSuspend) { Text("Suspender") }
                    TextButton(onClick = onRemove) { Text("Remover") }
                }
            }
            if (canTransfer && member.role != OrganizationRoleCode.OWNER) {
                TextButton(onClick = onTransfer) { Text("Transferir ownership") }
            }
        }
    }
}
