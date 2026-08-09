package com.comunidapp.app.ui.screens.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.ui.components.leo.LeoFeatureCard
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoPageTitle
import com.comunidapp.app.ui.theme.MutedText

data class PublishOption(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

/**
 * Creador social rápido (hub Publicar).
 * Casos estructurados (adopción, perdidos, tránsito, eventos) se crean desde Sumate.
 */
@Composable
fun PublishScreen(
    accountType: AccountType,
    onNavigateToGeneral: () -> Unit,
    onNavigateToReel: () -> Unit = {},
    onNavigateToStory: () -> Unit = {},
    showBackButton: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onNavigateToQuestion: () -> Unit = {},
    onNavigateToPromo: () -> Unit = {},
    onNavigateToAdoption: () -> Unit = {},
    onNavigateToLostFound: () -> Unit = {},
    onNavigateToUrgent: () -> Unit = {},
    onNavigateToFoster: () -> Unit = {},
    onNavigateToEvent: () -> Unit = {},
    onNavigateToDonation: () -> Unit = {},
    onNavigateToShelter: () -> Unit = {}
) {
    @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
    val preservedStructuredRoutes = remember(accountType) {
        listOf(
            onNavigateToQuestion,
            onNavigateToPromo,
            onNavigateToAdoption,
            onNavigateToLostFound,
            onNavigateToUrgent,
            onNavigateToFoster,
            onNavigateToEvent,
            onNavigateToDonation,
            onNavigateToShelter
        )
    }

    val socialOptions = listOf(
        PublishOption(
            icon = Icons.Default.Image,
            title = "Publicación",
            description = "Foto, video o texto · descripción · mascota opcional · ubicación opcional",
            onClick = onNavigateToGeneral
        ),
        PublishOption(
            icon = Icons.Default.PlayCircle,
            title = "Reel",
            description = "Video corto vertical para el feed y la pestaña Reels",
            onClick = onNavigateToReel
        ),
        PublishOption(
            icon = Icons.Default.WatchLater,
            title = "Historia",
            description = "Imagen o video efímero · vigente 24 horas",
            onClick = onNavigateToStory
        )
    )

    Scaffold(
        containerColor = BrandCream,
        topBar = {
            LeoTopAppBar(
                title = "Publicar",
                subtitle = "Creá contenido social en segundos",
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
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            Text(text = "Crear ahora", style = LeoPageTitle, color = BrandText)
            Text(
                text = "Elegí el formato. Los casos de ayuda se publican desde Sumate.",
                style = LeoCaption,
                color = MutedText
            )
            socialOptions.forEach { option ->
                LeoFeatureCard(
                    title = option.title,
                    description = option.description,
                    icon = option.icon,
                    onClick = option.onClick,
                    containerColor = BrandOrangeContainer,
                    iconTint = BrandOrangeSoft
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "CreateContentSheetPreview")
@Composable
private fun CreateContentSheetPreview() {
    ComunidappTheme {
        PublishScreen(
            accountType = AccountType.PERSON,
            onNavigateToGeneral = {},
            showBackButton = true
        )
    }
}
