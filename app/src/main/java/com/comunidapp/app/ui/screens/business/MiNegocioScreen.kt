package com.comunidapp.app.ui.screens.business

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.CommunityCategory
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.ProfileViewModel

@Composable
fun MiNegocioScreen(
    onNavigateToEditProfile: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user
    val accountType = user?.accountType ?: AccountType.SHOP
    val title = RolePermissions.businessPanelTitle(accountType)
    val sampleListing = MockData.communityListings.firstOrNull { listing ->
        when (accountType) {
            AccountType.VET -> listing.category == CommunityCategory.VET
            AccountType.SHOP -> listing.category == CommunityCategory.SHOP
            AccountType.TRAINER -> listing.category == CommunityCategory.TRAINER
            AccountType.WALKER -> listing.category == CommunityCategory.WALKER
            else -> false
        }
    }

    Scaffold(
        topBar = { ComunidappTopBar(title = title) }
    ) { padding ->
        if (user == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Iniciá sesión para ver tu negocio")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PetImage(
                            imageUrl = user.profileImageUrl ?: sampleListing?.photoUrl,
                            modifier = Modifier.size(88.dp),
                            contentDescription = user.name
                        )
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = accountType.toDisplayName(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        user.locationText?.let { location ->
                            Text(
                                text = "📍 $location",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        user.bio?.let { bio ->
                            Text(
                                text = bio,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = onNavigateToEditProfile,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Editar ficha", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            item {
                BusinessInfoCard(
                    title = "Vista en Comunidad",
                    description = sampleListing?.description
                        ?: "Tu negocio aparece en el directorio de Comunidad para que otros usuarios te encuentren.",
                    icon = { Icon(Icons.Default.Storefront, contentDescription = null) }
                )
            }
            item {
                BusinessInfoCard(
                    title = "Publicidad en el feed",
                    description = "Usá Publicar → Publicidad / promo para llegar a dueños de mascotas en tu zona.",
                    icon = { Icon(Icons.Default.Insights, contentDescription = null) }
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Próximamente",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Horarios, catálogo, turnos y mensajes de clientes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessInfoCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
