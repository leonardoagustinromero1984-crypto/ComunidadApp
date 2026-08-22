package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.organization.OrgInvitePolicy
import com.comunidapp.app.domain.organization.authorization.MembershipDisplay
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.viewmodel.OrgInvitationAcceptViewModel
import com.comunidapp.app.viewmodel.headline
import com.comunidapp.app.viewmodel.roleLabel

@Composable
fun OrgInvitationAcceptScreen(
    invitationId: String,
    onNavigateBack: () -> Unit,
    onAccepted: (organizationId: String) -> Unit,
    viewModel: OrgInvitationAcceptViewModel = viewModel(
        factory = OrgInvitationAcceptViewModel.factory(invitationId)
    )
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(ui.accepted, ui.invitation) {
        if (ui.accepted) {
            val orgId = ui.invitation?.organizationId?.value.orEmpty()
            if (orgId.isNotBlank()) onAccepted(orgId) else onNavigateBack()
        }
    }
    LaunchedEffect(ui.rejected) {
        if (ui.rejected) onNavigateBack()
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Invitación",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val invitation = ui.invitation
            if (ui.loading) {
                Text("Cargando invitación…")
            } else if (invitation == null) {
                Text(ui.error ?: "Esta invitación ya no está pendiente.")
            } else {
                Text(invitation.headline(), fontWeight = FontWeight.SemiBold)
                Text("Organización: ${invitation.organizationName ?: "LeoVer"}")
                invitation.organizationCapability?.let { Text("Categoría: $it") }
                Text("Rol: ${invitation.roleLabel()}")
                invitation.inviterName?.let { Text("${it} te invitó como ${invitation.roleLabel()}.") }
                if (invitation.permissionCodes.isNotEmpty() &&
                    invitation.roleLabel() == MembershipDisplay.MEMBER_VISIBLE
                ) {
                    Text("Permisos propuestos:")
                    invitation.permissionCodes.forEach { code ->
                        val label = OrgInvitePolicy.MEMBER_PERMISSION_OPTIONS
                            .firstOrNull { it.catalogCode == code }?.visibleLabel ?: code
                        Text("· $label")
                    }
                }
                ui.error?.let { Text(it) }
                Button(
                    onClick = viewModel::accept,
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Aceptar invitación") }
                OutlinedButton(
                    onClick = viewModel::reject,
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Rechazar") }
            }
        }
    }
}
