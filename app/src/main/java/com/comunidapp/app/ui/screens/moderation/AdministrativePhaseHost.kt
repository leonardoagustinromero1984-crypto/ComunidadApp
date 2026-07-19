package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase

@Composable
fun AdministrativePhaseHost(
    title: String,
    phase: AdministrativeScreenPhase,
    onNavigateBack: () -> Unit,
    emptyTitle: String = "Sin resultados",
    emptyMessage: String? = null,
    errorMessage: String = "No pudimos cargar la información.",
    onRetry: (() -> Unit)? = null,
    accessDeniedMessage: String = "No tenés permiso para ver esta sección de LeoVer.",
    content: @Composable (contentModifier: Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = title,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (phase) {
            AdministrativeScreenPhase.Loading,
            AdministrativeScreenPhase.Submitting -> LoadingState(contentModifier)
            AdministrativeScreenPhase.AccessDenied -> EmptyState(
                title = "Acceso denegado",
                message = accessDeniedMessage,
                contentModifier = contentModifier
            )
            AdministrativeScreenPhase.Empty -> EmptyState(
                title = emptyTitle,
                message = emptyMessage,
                actionLabel = if (onRetry != null) "Reintentar" else null,
                onAction = onRetry,
                contentModifier = contentModifier
            )
            AdministrativeScreenPhase.Error -> ErrorState(
                message = errorMessage,
                onRetry = onRetry,
                contentModifier = contentModifier
            )
            AdministrativeScreenPhase.Content -> content(contentModifier)
        }
    }
}

@Composable
fun AccessDeniedLabel(message: String = "No tenés permiso.") {
    Text(text = message)
}
