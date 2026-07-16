package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.viewmodel.PublicOrganizationViewModel

@Composable
fun PublicOrganizationScreen(
    onNavigateBack: () -> Unit,
    viewModel: PublicOrganizationViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Organización",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.organization == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "No encontrada",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val org = uiState.organization!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = org.publicName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "@${org.slug}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${org.type.name} · ${org.verificationStatus.name}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    listOfNotNull(org.city, org.province, org.countryCode)
                        .takeIf { it.isNotEmpty() }
                        ?.let { parts ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = parts.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    org.description?.let { desc ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    org.publicEmail?.let { email ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Email: $email")
                    }
                    org.publicPhone?.let { phone ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Teléfono: $phone")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
