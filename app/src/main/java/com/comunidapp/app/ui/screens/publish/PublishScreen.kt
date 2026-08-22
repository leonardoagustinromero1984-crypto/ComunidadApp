package com.comunidapp.app.ui.screens.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.publish.ContextPublishMatrix
import com.comunidapp.app.domain.publish.PublishAction
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2NavRow
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual

private data class PublishVisual(
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
    val onClick: () -> Unit
)

private data class PublishOption(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit,
    val iconTint: Color,
    val iconContainer: Color
)

/**
 * Hub Publicar — capacidades según [OperationalContext], no identidad humana.
 * No inventa PostTypes; solo presenta rutas ya cableadas.
 */
@Composable
fun PublishScreen(
    context: OperationalContext,
    onNavigateToGeneral: () -> Unit,
    onNavigateToReel: () -> Unit = {},
    onNavigateToStory: () -> Unit = {},
    showBackButton: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onNavigateToQuestion: () -> Unit = {},
    onNavigateToPromo: () -> Unit = {},
    onNavigateToAdoption: () -> Unit = {},
    onNavigateToLostFound: () -> Unit = {},
    onNavigateToFound: () -> Unit = {},
    onNavigateToUrgent: () -> Unit = {},
    onNavigateToFoster: () -> Unit = {},
    onNavigateToEvent: () -> Unit = {},
    onNavigateToDonation: () -> Unit = {},
    onNavigateToShelter: () -> Unit = {},
    onNavigateToProviderFicha: () -> Unit = {},
    onNavigateToCampaign: () -> Unit = {}
) {
    val visual = leoVisual()
    val options = ContextPublishMatrix.optionsFor(context).map { spec ->
        val (icon, tint, container, click) = when (spec.action) {
            PublishAction.SOCIAL_POST -> PublishVisual(Icons.Default.Image, visual.textPrimary, visual.borderSoft.copy(alpha = 0.45f), onNavigateToGeneral)
            PublishAction.REEL -> PublishVisual(Icons.Default.PlayCircle, visual.textPrimary, visual.borderSoft.copy(alpha = 0.45f), onNavigateToReel)
            PublishAction.STORY -> PublishVisual(Icons.Default.WatchLater, visual.accent, visual.accentSoft, onNavigateToStory)
            PublishAction.UPDATE_PROVIDER_FICHA -> PublishVisual(Icons.Default.Storefront, visual.primary, visual.primarySoft, onNavigateToProviderFicha)
            PublishAction.ORGANIZATION_NEED -> PublishVisual(Icons.Default.HomeWork, visual.primaryDark, visual.secondarySoft, onNavigateToShelter)
            PublishAction.CAMPAIGN -> PublishVisual(Icons.Default.Campaign, visual.primary, visual.primarySoft, onNavigateToCampaign)
            PublishAction.DONATION -> PublishVisual(Icons.Default.VolunteerActivism, visual.primary, visual.secondarySoft, onNavigateToDonation)
            PublishAction.EVENT -> PublishVisual(Icons.Default.Event, visual.accent, visual.accentSoft, onNavigateToEvent)
        }
        PublishOption(icon, spec.title, spec.description, click, tint, container)
    }

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LeoTopAppBar(
                title = "Publicar",
                subtitle = "Elegí el tipo de publicación",
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            options.forEach { option ->
                V2NavRow(
                    title = option.title,
                    description = option.description,
                    icon = option.icon,
                    onClick = option.onClick,
                    iconTint = option.iconTint,
                    iconContainer = option.iconContainer
                )
            }
        }
    }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBF8, widthDp = 390, name = "CreateContentSheetPreview")
@Composable
private fun CreateContentSheetPreview() {
    ComunidappTheme {
        PublishScreen(
            context = OperationalContext.Personal,
            onNavigateToGeneral = {},
            showBackButton = true
        )
    }
}
