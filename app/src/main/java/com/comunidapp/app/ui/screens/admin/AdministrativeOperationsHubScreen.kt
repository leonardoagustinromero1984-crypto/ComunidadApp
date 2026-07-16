package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.components.ComunidappTopBar

@Composable
fun AdministrativeOperationsHubScreen(
    onNavigateBack: () -> Unit,
    canModeration: Boolean,
    canAppeals: Boolean,
    canVerification: Boolean,
    canSupportStaff: Boolean,
    canAudit: Boolean,
    onModeration: () -> Unit,
    onCases: () -> Unit,
    onAppeals: () -> Unit,
    onVerification: () -> Unit,
    onSupportStaff: () -> Unit,
    onAudit: () -> Unit
) {
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Operaciones LeoVer",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canModeration) {
                OutlinedButton(onClick = onModeration, modifier = Modifier.fillMaxWidth()) {
                    Text("Moderación")
                }
                OutlinedButton(onClick = onCases, modifier = Modifier.fillMaxWidth()) {
                    Text("Casos")
                }
            }
            if (canAppeals) {
                OutlinedButton(onClick = onAppeals, modifier = Modifier.fillMaxWidth()) {
                    Text("Apelaciones")
                }
            }
            if (canVerification) {
                OutlinedButton(onClick = onVerification, modifier = Modifier.fillMaxWidth()) {
                    Text("Verificación")
                }
            }
            if (canSupportStaff) {
                OutlinedButton(onClick = onSupportStaff, modifier = Modifier.fillMaxWidth()) {
                    Text("Soporte staff")
                }
            }
            if (canAudit) {
                OutlinedButton(onClick = onAudit, modifier = Modifier.fillMaxWidth()) {
                    Text("Auditoría")
                }
            }
        }
    }
}
