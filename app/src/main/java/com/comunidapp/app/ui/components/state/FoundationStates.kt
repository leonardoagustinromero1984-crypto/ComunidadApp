package com.comunidapp.app.ui.components.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.theme.ComunidappTheme

@Composable
fun LoadingState(
    contentModifier: Modifier = Modifier,
    contentDescription: String = "Cargando"
) {
    Box(
        modifier = contentModifier
            .fillMaxSize()
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyState(
    title: String,
    contentModifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = contentModifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { contentDescription = title },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAction, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    contentModifier: Modifier = Modifier,
    title: String = "Algo salió mal",
    retryLabel: String = "Reintentar",
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = contentModifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { contentDescription = title },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text(retryLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingStatePreview() {
    ComunidappTheme { LoadingState() }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    ComunidappTheme {
        EmptyState(
            title = "Sin resultados",
            message = "Todavía no hay contenido para mostrar.",
            actionLabel = "Actualizar",
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    ComunidappTheme {
        ErrorState(
            message = "No pudimos cargar la información.",
            onRetry = {}
        )
    }
}
