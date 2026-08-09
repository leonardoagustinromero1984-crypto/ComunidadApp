package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.NeutralBorder
import kotlinx.coroutines.launch

data class ProfileMenuActions(
    val onMyPets: () -> Unit = {},
    val onMyApplications: () -> Unit = {},
    val onReceivedApplications: () -> Unit = {},
    val onMyOrganizations: () -> Unit = {},
    val onMessages: () -> Unit = {},
    val onMyPosts: () -> Unit = {},
    val onMyReels: () -> Unit = {},
    val onMyStories: () -> Unit = {},
    val onSaved: () -> Unit = {},
    val onDrafts: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onPrivacy: () -> Unit = {},
    val onSupport: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onModeration: (() -> Unit)? = null,
    val onCases: (() -> Unit)? = null,
    val onAppealsStaff: (() -> Unit)? = null,
    val onVerification: (() -> Unit)? = null,
    val onSupportStaff: (() -> Unit)? = null,
    val onAudit: (() -> Unit)? = null,
    val onObservability: (() -> Unit)? = null,
    val onPlatformAdmin: (() -> Unit)? = null,
    val onMyAppeals: () -> Unit = {},
    val onFriendRequests: () -> Unit = {},
    val onTutorial: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
    onDismiss: () -> Unit,
    actions: ProfileMenuActions
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun runAfterHide(block: () -> Unit) {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
            block()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandCream
    ) {
        ProfileMenuContent(
            actions = actions,
            onItemClick = { block -> runAfterHide(block) }
        )
    }
}

@Composable
fun ProfileMenuContent(
    actions: ProfileMenuActions,
    onItemClick: (() -> Unit) -> Unit = { it() },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LeoDimens.SpaceLg)
    ) {
        Text(
            text = "Menú",
            style = LeoSectionTitle,
            color = BrandText,
            modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm)
        )

        MenuGroup("Contenido") {
            MenuRow("Mis publicaciones", Icons.Default.PostAdd) { onItemClick(actions.onMyPosts) }
            MenuRow("Mis reels", Icons.Default.PlayCircle) { onItemClick(actions.onMyReels) }
            MenuRow("Mis historias", Icons.Default.WatchLater) { onItemClick(actions.onMyStories) }
            MenuRow("Borradores", Icons.Default.Drafts, "Privado") { onItemClick(actions.onDrafts) }
            MenuRow("Guardados", Icons.Default.Bookmark, "Privado") { onItemClick(actions.onSaved) }
        }
        MenuGroup("Gestión") {
            MenuRow("Mis mascotas", Icons.Default.Pets) { onItemClick(actions.onMyPets) }
            MenuRow("Mis postulaciones", Icons.AutoMirrored.Filled.Assignment) {
                onItemClick(actions.onMyApplications)
            }
            MenuRow("Solicitudes recibidas", Icons.Default.Inbox) {
                onItemClick(actions.onReceivedApplications)
            }
            MenuRow("Mis organizaciones", Icons.Default.Business) {
                onItemClick(actions.onMyOrganizations)
            }
        }
        MenuGroup("Actividad") {
            MenuRow("Mensajes", Icons.AutoMirrored.Filled.Chat) { onItemClick(actions.onMessages) }
            MenuRow("Notificaciones", Icons.Default.Notifications) {
                onItemClick(actions.onNotifications)
            }
            MenuRow("Solicitudes de amistad", Icons.Default.People) {
                onItemClick(actions.onFriendRequests)
            }
            MenuRow("Mis apelaciones", Icons.Default.Gavel) { onItemClick(actions.onMyAppeals) }
        }
        MenuGroup("Cuenta") {
            MenuRow("Configuración", Icons.Default.Settings) { onItemClick(actions.onSettings) }
            MenuRow("Privacidad", Icons.Default.Lock) { onItemClick(actions.onPrivacy) }
            MenuRow("Soporte", Icons.Default.SupportAgent) { onItemClick(actions.onSupport) }
            MenuRow("Tutorial", Icons.AutoMirrored.Filled.HelpOutline) {
                onItemClick(actions.onTutorial)
            }
            MenuRow("Cerrar sesión", Icons.AutoMirrored.Filled.Logout) {
                onItemClick(actions.onLogout)
            }
        }

        val staff = listOfNotNull(
            actions.onModeration?.let { "Moderación" to it },
            actions.onCases?.let { "Casos" to it },
            actions.onAppealsStaff?.let { "Apelaciones (staff)" to it },
            actions.onVerification?.let { "Verificación" to it },
            actions.onSupportStaff?.let { "Soporte (staff)" to it },
            actions.onAudit?.let { "Auditoría" to it },
            actions.onObservability?.let { "Observabilidad" to it },
            actions.onPlatformAdmin?.let { "Admin plataforma" to it }
        )
        if (staff.isNotEmpty()) {
            MenuGroup("Staff") {
                staff.forEach { (label, action) ->
                    MenuRow(label, Icons.Default.Shield) { onItemClick(action) }
                }
            }
        }
    }
}

@Composable
private fun MenuGroup(title: String, content: @Composable () -> Unit) {
    Text(
        text = title.uppercase(),
        style = LeoCaption,
        color = MutedText,
        modifier = Modifier.padding(
            start = LeoDimens.SpaceMd,
            end = LeoDimens.SpaceMd,
            top = LeoDimens.SpaceMd,
            bottom = LeoDimens.SpaceSm
        )
    )
    content()
    HorizontalDivider(color = NeutralBorder, modifier = Modifier.padding(vertical = LeoDimens.SpaceSm))
}

@Composable
private fun MenuRow(
    title: String,
    icon: ImageVector,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrandText, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(LeoDimens.SpaceCompact))
        Text(text = title, style = LeoCaption, color = BrandText, modifier = Modifier.weight(1f))
        if (badge != null) {
            Text(text = badge, style = LeoCaption, color = MutedText)
        }
    }
}
