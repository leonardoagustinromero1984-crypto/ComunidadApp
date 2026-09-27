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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.domain.organization.authorization.MembershipDisplay
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
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
            text = { Text("¿Transferir la administración a este miembro?") },
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                contentPadding = PaddingValues(LeoDimens.SpaceMd)
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
                            query = uiState.personQuery,
                            hits = uiState.personHits,
                            selected = uiState.selectedPerson,
                            role = uiState.inviteRole,
                            selectedPermissions = uiState.selectedPermissions,
                            isInviting = uiState.isInviting,
                            onQueryChange = viewModel::onPersonQueryChange,
                            onSelectPerson = viewModel::selectPerson,
                            onRoleChange = viewModel::onInviteRoleChange,
                            onTogglePermission = viewModel::togglePermission,
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
                        MemberRow(
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
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(vertical = LeoDimens.SpaceCompact)) {
                                Text(
                                    text = "${MembershipDisplay.visibleRole(invitation.invitedRole)} · ${invitation.status.name}",
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
                            LeoHairline()
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    LeoOutlinedButton(
                        text = "Salir de la organización",
                        onClick = { showLeaveDialog = true }
                    )
                    if (uiState.canTransferOwnership) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LeoOutlinedButton(
                            text = "Cerrar organización",
                            onClick = { showCloseDialog = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteMemberSection(
    query: String,
    hits: List<com.comunidapp.app.domain.organization.PersonSearchHit>,
    selected: com.comunidapp.app.domain.organization.PersonSearchHit?,
    role: OrganizationRoleCode,
    selectedPermissions: Set<String>,
    isInviting: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectPerson: (com.comunidapp.app.domain.organization.PersonSearchHit) -> Unit,
    onRoleChange: (OrganizationRoleCode) -> Unit,
    onTogglePermission: (String) -> Unit,
    onInvite: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Invitar persona", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Buscar por nombre o @usuario") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        hits.forEach { hit ->
            TextButton(onClick = { onSelectPerson(hit) }, modifier = Modifier.fillMaxWidth()) {
                Text("${hit.displayName}  @${hit.username}")
            }
        }
        selected?.let {
            Text(
                text = "Seleccionada: ${it.displayName}  @${it.username}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(text = "Rol: ${MembershipDisplay.visibleRole(role)}", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(OrganizationRoleCode.ADMIN, OrganizationRoleCode.MEMBER).forEach { r ->
                TextButton(onClick = { onRoleChange(r) }) {
                    Text(MembershipDisplay.visibleRole(r))
                }
            }
        }
        if (role == OrganizationRoleCode.MEMBER) {
            Text("Permisos", fontWeight = FontWeight.SemiBold)
            com.comunidapp.app.domain.organization.OrgInvitePolicy.MEMBER_PERMISSION_OPTIONS.forEach { option ->
                TextButton(onClick = { onTogglePermission(option.catalogCode) }) {
                    val mark = if (option.catalogCode in selectedPermissions) "[x]" else "[ ]"
                    Text("$mark ${option.visibleLabel}")
                }
            }
        }
        LeoPrimaryButton(
            text = if (isInviting) "Enviando…" else "Enviar invitación",
            onClick = onInvite,
            enabled = !isInviting
        )
        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceCompact))
    }
}

@Composable
private fun MemberRow(
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = LeoDimens.SpaceCompact)) {
            Text(text = MembershipDisplay.visibleRole(member.role), fontWeight = FontWeight.Medium)
            Text(
                text = "${MembershipDisplay.visibleRole(member.role)} · ${member.status.name}",
                style = MaterialTheme.typography.bodySmall
            )
            if (canManageRoles && member.role != OrganizationRoleCode.OWNER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onChangeRole(OrganizationRoleCode.MEMBER) }) {
                        Text(MembershipDisplay.MEMBER_VISIBLE)
                    }
                    TextButton(onClick = { onChangeRole(OrganizationRoleCode.ADMIN) }) {
                        Text(MembershipDisplay.ADMINISTRATOR_VISIBLE)
                    }
                }
            }
            if (canRemove && !(MembershipDisplay.isAdministrator(member.role) && ownerCount <= 1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSuspend) { Text("Suspender") }
                    TextButton(onClick = onRemove) { Text("Remover") }
                }
            }
            if (canTransfer && member.role != OrganizationRoleCode.OWNER) {
                TextButton(onClick = onTransfer) { Text("Transferir administración") }
            }
        }
        LeoHairline()
    }
}
