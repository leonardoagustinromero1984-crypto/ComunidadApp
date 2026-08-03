package com.comunidapp.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.local.OnboardingProvider
import com.comunidapp.app.domain.onboarding.ContextualHelpId

/**
 * Ayuda de primera visita: se muestra una vez, es cerrable y no bloquea acciones críticas.
 */
@Composable
fun ContextualFirstVisitHelp(
    helpId: ContextualHelpId,
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    var visible by remember(helpId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(helpId) {
        visible = !OnboardingProvider.repository().isContextualHelpSeen(helpId)
    }

    if (!visible) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Ayuda contextual: $message" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Primera visita",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            IconButton(
                onClick = {
                    visible = false
                    scope.launch {
                        OnboardingProvider.repository().markContextualHelpSeen(helpId)
                    }
                    onDismiss()
                },
                modifier = Modifier.semantics {
                    contentDescription = "Cerrar ayuda contextual"
                }
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
        }
    }
}

object ContextualHelpMessages {
    const val PET_PASSPORT =
        "Acá vas a encontrar la identidad y el historial de tu mascota."
    const val ALERTS =
        "La ubicación pública será aproximada. Podrás compartir más información únicamente con personas autorizadas."
    const val ADOPTIONS =
        "Los estados permiten saber en qué etapa se encuentra cada postulación."
    const val SHELTERS =
        "El perfil público no muestra integrantes, datos privados ni información operativa interna."
}
