package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.viewmodel.MyOrganizationsViewModel

@Composable
fun MyOrganizationsScreen(
    onNavigateBack: () -> Unit,
    onCreateOrganization: () -> Unit,
    onManageOrganization: (String) -> Unit,
    onEditOrganization: (String) -> Unit,
    onOpenPublic: (String) -> Unit,
    viewModel: MyOrganizationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Mis organizaciones",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LeoPrimaryButton(
                    text = "Crear organización",
                    onClick = onCreateOrganization,
                    modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm)
                )
                uiState.errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                if (uiState.organizations.isEmpty()) {
                    Text(
                        text = "Todavía no sos miembro de ninguna organización.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.organizations, key = { it.id.value }) { org ->
                            OrganizationListItem(
                                organization = org,
                                onManage = { onManageOrganization(org.id.value) },
                                onEdit = { onEditOrganization(org.id.value) },
                                onOpenPublic = { onOpenPublic(org.slug.value) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationListItem(
    organization: Organization,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onOpenPublic: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = organization.publicName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "@${organization.slug.value} · ${organization.type.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${organization.status.name} · ${organization.verificationStatus.name}",
            style = MaterialTheme.typography.labelMedium
        )
        Column(
            modifier = Modifier.padding(top = LeoDimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            LeoPrimaryButton(text = "Administrar", onClick = onManage)
            LeoOutlinedButton(text = "Editar perfil", onClick = onEdit)
            LeoOutlinedButton(text = "Ver perfil público", onClick = onOpenPublic)
        }
        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceCompact))
    }
}
