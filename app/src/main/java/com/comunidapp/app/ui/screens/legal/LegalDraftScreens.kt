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
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.ui.theme.BrandText

@Composable
fun TermsDraftScreen(onNavigateBack: () -> Unit) {
    LegalDraftScreen(
        title = "Términos",
        version = LegalDocumentConfig.terms.version,
        body = TERMS_DRAFT_BODY,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun PrivacyDraftScreen(onNavigateBack: () -> Unit) {
    LegalDraftScreen(
        title = "Privacidad",
        version = LegalDocumentConfig.privacy.version,
        body = PRIVACY_DRAFT_BODY,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun LegalDraftScreen(
    title: String,
    version: String,
    body: String,
    onNavigateBack: () -> Unit
) {
    VisualDirectionPilot {
    val visual = leoVisual()
    Scaffold(
        containerColor = visual.background,
        topBar = {
            LeoTopAppBar(
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = BrandText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Versión: $version",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandText
            )
        }
    }
    }
}

private const val TERMS_DRAFT_BODY =
    "Estos Términos describen el uso de LeoVer. Al continuar aceptás las condiciones " +
        "de esta versión. El texto jurídico definitivo se publica con cada release."

private const val PRIVACY_DRAFT_BODY =
    "Esta Política de privacidad describe cómo LeoVer trata los datos de cuenta. " +
        "La autenticación y el almacenamiento se procesan vía Supabase Auth y Postgres " +
        "según la configuración del proyecto."
