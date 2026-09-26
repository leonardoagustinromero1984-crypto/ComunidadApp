package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.People
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
import com.comunidapp.app.data.repository.AdminDashboardSummary
import com.comunidapp.app.ui.components.leo.LeoCard
import com.comunidapp.app.ui.components.leo.LeoSettingsRow
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.viewmodel.AdminHubViewModel

@Composable
fun AdminHubScreen(
    onNavigateBack: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToModeration: () -> Unit,
    onNavigateToStaff: () -> Unit = {},
    onNavigateToCatalogs: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
    exclusiveAdminSession: Boolean = false,
    onLogout: () -> Unit = {},
    viewModel: AdminHubViewModel = viewModel(factory = AdminHubViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Administración",
                showBackButton = !exclusiveAdminSession,
                onBackClick = onNavigateBack,
                actions = {
                    if (exclusiveAdminSession) {
                        TextButton(onClick = onLogout) {
                            Text("Cerrar sesión")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            !uiState.accessChecked -> {
                LoadingState(contentModifier = Modifier.padding(padding))
            }
            !uiState.accessAllowed -> {
                LaunchedEffect(Unit) {
                    if (exclusiveAdminSession) onLogout() else onNavigateBack()
                }
                Column(Modifier.padding(padding).padding(24.dp)) {
                    Text("No tenés permiso para administrar.")
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Gestión de la plataforma LeoVer",
                        style = LeoCaption,
                        color = BrandTextSecondary
                    )
                    uiState.roleLabel?.let { label ->
                        Text(
                            label,
                            style = LeoCardTitle,
                            color = BrandText
                        )
                    }
                    AdminSummarySection(
                        summary = uiState.summary,
                        canSeeUsers = uiState.canSeeUsers,
                        canSeeModeration = uiState.canSeeModeration
                    )
                    if (uiState.canSeeUsers) {
                        LeoSettingsRow(
                            title = "Usuarios",
                            description = "Buscar cuentas, estado y roles de plataforma",
                            icon = Icons.Default.People,
                            onClick = onNavigateToUsers
                        )
                    }
                    if (uiState.canSeeStaff) {
                        LeoSettingsRow(
                            title = "Personal administrativo",
                            description = "Cuentas técnicas internas de LeoVer",
                            icon = Icons.Default.Badge,
                            onClick = onNavigateToStaff
                        )
                    }
                    if (uiState.canSeeModeration) {
                        LeoSettingsRow(
                            title = "Moderación",
                            description = "Revisar denuncias y marcar su estado",
                            icon = Icons.Default.Flag,
                            onClick = onNavigateToModeration
                        )
                    }
                    if (uiState.canSeeSupport) {
                        LeoSettingsRow(
                            title = "Soporte",
                            description = "Casos, tickets e historial de atención",
                            icon = Icons.Default.Email,
                            onClick = onNavigateToSupport
                        )
                    }
                    if (uiState.canSeeCatalogs) {
                        LeoSettingsRow(
                            title = "Catálogos",
                            description = "Especies, razas y datos maestros",
                            icon = Icons.Default.Category,
                            onClick = onNavigateToCatalogs
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSummarySection(
    summary: AdminDashboardSummary?,
    canSeeUsers: Boolean,
    canSeeModeration: Boolean
) {
    if (summary == null) return
    val items = listOfNotNull(
        summary.users?.takeIf { canSeeUsers }?.let { "Usuarios" to it.toString() },
        summary.organizations?.takeIf { canSeeUsers }?.let { "Organizaciones" to it.toString() },
        summary.openReports?.takeIf { canSeeModeration }?.let {
            "Reportes pendientes" to it.toString()
        }
    )
    if (items.isEmpty()) return
    LeoCard {
        Text("Resumen", style = LeoSectionTitle, color = BrandText)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.forEach { (label, value) ->
                Column {
                    Text(value, style = LeoSectionTitle, color = BrandText)
                    Text(
                        label,
                        style = LeoCaption,
                        color = BrandTextSecondary
                    )
                }
            }
        }
    }
}
