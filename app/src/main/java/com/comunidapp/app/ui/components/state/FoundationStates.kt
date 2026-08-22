package com.comunidapp.app.ui.components.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoBody
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens

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
        CircularProgressIndicator(color = BrandOrange)
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
            style = LeoCardTitle,
            color = BrandText,
            textAlign = TextAlign.Center
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(LeoDimens.SpaceS))
            Text(
                text = message,
                style = LeoBody,
                color = BrandTextSecondary,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(LeoDimens.SpaceL))
            LeoPrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth(0.6f))
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
            style = LeoCardTitle,
            color = BrandText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(LeoDimens.SpaceS))
        Text(
            text = message,
            style = LeoBody,
            color = BrandTextSecondary,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(Modifier.height(LeoDimens.SpaceL))
            LeoPrimaryButton(text = retryLabel, onClick = onRetry, modifier = Modifier.fillMaxWidth(0.6f))
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
