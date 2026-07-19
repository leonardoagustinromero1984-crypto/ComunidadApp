package com.comunidapp.app.ui.screens.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.user.AccountStatus
import com.comunidapp.app.viewmodel.SessionViewModel

@Composable
fun AccountAccessBlockedScreen(
    accountStatus: AccountStatus,
    sessionViewModel: SessionViewModel
) {
    val (title, message) = when (accountStatus) {
        AccountStatus.SUSPENDED -> "Cuenta suspendida" to
            "Tu cuenta fue suspendida temporalmente. No podés acceder a LeoVer " +
                "hasta que se resuelva la situación. Si creés que es un error, contactá soporte."
        AccountStatus.BANNED -> "Cuenta bloqueada" to
            "Tu cuenta fue bloqueada de forma permanente. No podés acceder a LeoVer " +
                "con esta cuenta."
        else -> "Acceso restringido" to
            "Tu cuenta no tiene acceso a la aplicación en este momento."
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = { sessionViewModel.logout() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar sesión")
            }
        }
    }
}
