package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.organization.OrganizationContextMode
import com.comunidapp.app.domain.organization.OrganizationContextProvider
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.viewmodel.OrganizationManageViewModel

@Composable
fun OrganizationManageScreen(
    onNavigateBack: () -> Unit,
    onEditProfile: () -> Unit,
    onManageTeam: () -> Unit,
    onManageBranches: () -> Unit,
    viewModel: OrganizationManageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val contextState by OrganizationContextProvider.state.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = uiState.organization?.publicName ?: "Organización",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.errorMessage != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "@${uiState.organization?.slug?.value.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (contextState.mode == OrganizationContextMode.ORGANIZATION) {
                        "Contexto activo: organización"
                    } else {
                        "Contexto activo: perfil personal"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.activateContext()
                        onEditProfile()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Editar perfil institucional")
                }
                if (uiState.canManageMembers || uiState.canInvite) {
                    OutlinedButton(
                        onClick = onManageTeam,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Equipo e invitaciones")
                    }
                }
                if (uiState.canManageBranches) {
                    OutlinedButton(
                        onClick = onManageBranches,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Sucursales")
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.usePersonalContext() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Usar perfil personal")
                }
            }
        }
    }
}
