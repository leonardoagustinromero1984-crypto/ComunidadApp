package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comunidapp.app.R
import com.comunidapp.app.ui.components.ComunidappTopBar

@Composable
fun UserPublicProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Perfil",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${stringResource(R.string.public_profile_stub)}\nID: $userId",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
