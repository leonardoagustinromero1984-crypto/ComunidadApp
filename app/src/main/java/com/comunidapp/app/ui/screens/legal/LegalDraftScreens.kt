package com.comunidapp.app.ui.screens.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import com.comunidapp.app.ui.components.ComunidappTopBar

@Composable
fun TermsDraftScreen(onNavigateBack: () -> Unit) {
    LegalDraftScreen(
        title = "Términos",
        version = LegalDocumentConfig.terms.version,
        draftLabel = LegalDocumentConfig.terms.draftLabel,
        body = TERMS_DRAFT_BODY,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun PrivacyDraftScreen(onNavigateBack: () -> Unit) {
    LegalDraftScreen(
        title = "Privacidad",
        version = LegalDocumentConfig.privacy.version,
        draftLabel = LegalDocumentConfig.privacy.draftLabel,
        body = PRIVACY_DRAFT_BODY,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun LegalDraftScreen(
    title: String,
    version: String,
    draftLabel: String?,
    body: String,
    onNavigateBack: () -> Unit
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = draftLabel ?: "BORRADOR — NO PUBLICABLE",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Versión: $version",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "La revisión legal formal es requisito de lanzamiento, no de compilación debug.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val TERMS_DRAFT_BODY =
    "Este documento es un BORRADOR interno de Términos de uso de Leover. " +
        "No constituye texto jurídico definitivo ni está publicado en un dominio externo. " +
        "Al continuar en debug aceptás usar la app en modo de desarrollo según esta versión de borrador."

private const val PRIVACY_DRAFT_BODY =
    "Este documento es un BORRADOR interno de Política de privacidad de Leover. " +
        "No inventa URLs públicas ni compromete un tratamiento legal final. " +
        "Describe de forma provisional que los datos de cuenta se procesan vía Supabase Auth/Postgres " +
        "según la configuración del proyecto. Revisión legal requerida antes de release."
