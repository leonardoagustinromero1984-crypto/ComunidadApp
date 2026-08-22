package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.context.ContextHumanLabels
import com.comunidapp.app.domain.context.ContextIdentityMapping
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.v2.V2NavRow
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoPageTitle
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.MutedText
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
    val active by OperationalContextProvider.active.collectAsState()
    val refuge = ContextIdentityMapping.isRefugeNav(active)

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    ContextHumanLabels.homeBrandLine(active),
                    style = LeoCaption,
                    color = MutedText
                )
                Text(
                    if (refuge) "Refugio" else "Organización",
                    style = LeoPageTitle,
                    color = BrandText
                )
                V2NavRow(
                    title = if (refuge) "Perfil del refugio" else "Editar perfil institucional",
                    description = "Nombre, contacto y datos públicos",
                    icon = Icons.Default.Storefront,
                    onClick = {
                        viewModel.activateContext()
                        onEditProfile()
                    }
                )
                if (uiState.canManageMembers || uiState.canInvite) {
                    V2NavRow(
                        title = "Equipo e invitaciones",
                        description = "Quién opera esta organización",
                        icon = Icons.Default.Groups,
                        onClick = onManageTeam
                    )
                }
                if (uiState.canManageBranches) {
                    V2NavRow(
                        title = "Sucursales",
                        description = "Sedes",
                        icon = Icons.Default.HomeWork,
                        onClick = onManageBranches
                    )
                }
            }
        }
    }
}
