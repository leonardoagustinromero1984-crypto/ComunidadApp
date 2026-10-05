package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.auth.AuthMethodKind
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoVerSettingsRow
import com.comunidapp.app.ui.components.leo.LeoVerSettingsSection
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.viewmodel.ProfileViewModel

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onEditProfile: () -> Unit,
    onPrivacy: () -> Unit,
    onLegalPrivacy: () -> Unit = onPrivacy,
    onAccountSecurity: () -> Unit,
    onAddFunction: () -> Unit,
    onNotificationPreferences: () -> Unit,
    onHelpTutorials: () -> Unit,
    onSupport: () -> Unit,
    onTerms: () -> Unit,
    onLogout: () -> Unit,
    spotlightAddFunction: Boolean = false,
    onAdministration: (() -> Unit)? = null,
    onModeration: (() -> Unit)? = null,
    onCases: (() -> Unit)? = null,
    onAppealsStaff: (() -> Unit)? = null,
    onVerification: (() -> Unit)? = null,
    onSupportStaff: (() -> Unit)? = null,
    onAudit: (() -> Unit)? = null,
    onObservability: (() -> Unit)? = null,
    onPlatformAdmin: (() -> Unit)? = null,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val guide = spotlightAddFunction
    val blocked: () -> Unit = {}
    var linkedMethods by remember { mutableStateOf(emptyList<AuthMethodKind>()) }
    LaunchedEffect(Unit) {
        linkedMethods = AuthProvider.repository.linkedAuthMethods()
            .filter { it != AuthMethodKind.APPLE }
    }
    VisualDirectionPilot {
        val visual = leoVisual()
        Scaffold(
            containerColor = visual.background,
            topBar = {
                LeoTopAppBar(
                    title = "Configuración",
                    showBackButton = true,
                    onBackClick = if (guide) blocked else onNavigateBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceMd)
            ) {
                if (guide) {
                    androidx.compose.material3.Text(
                        text = "Desde acá agregás funciones. Tocá solo «Agregar función o perfil».",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                }
                LeoVerSettingsSection(title = "Cuenta") {
                    LeoVerSettingsRow(
                        title = "Datos personales",
                        subtitle = "Nombre, foto y ubicación",
                        icon = Icons.Default.Person,
                        onClick = if (guide) blocked else onEditProfile
                    )
                    LeoVerSettingsRow(
                        title = "Privacidad",
                        subtitle = "Quién puede ver tu perfil",
                        icon = Icons.Default.Lock,
                        onClick = if (guide) blocked else onPrivacy
                    )
                }
                LeoVerSettingsSection(title = "LeoVer") {
                    LeoVerSettingsRow(
                        title = "Agregar función o perfil",
                        subtitle = "Activá una nueva forma de usar LeoVer",
                        icon = Icons.Default.Add,
                        onClick = onAddFunction
                    )
                }
                LeoVerSettingsSection(title = "Preferencias") {
                    LeoVerSettingsRow(
                        title = "Notificaciones",
                        subtitle = "Avisos y permisos",
                        icon = Icons.Default.Notifications,
                        onClick = if (guide) blocked else onNotificationPreferences
                    )
                }
                LeoVerSettingsSection(title = "Ayuda") {
                    LeoVerSettingsRow(
                        title = "Tutoriales",
                        subtitle = "Volvé a ver las guías",
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        onClick = if (guide) blocked else onHelpTutorials
                    )
                    LeoVerSettingsRow(
                        title = "Ayuda / soporte",
                        subtitle = "Tickets y consultas",
                        icon = Icons.Default.SupportAgent,
                        onClick = if (guide) blocked else onSupport
                    )
                }
                if (linkedMethods.isNotEmpty()) {
                    LeoVerSettingsSection(title = "Inicio de sesión") {
                        linkedMethods.forEach { method ->
                            LeoVerSettingsRow(
                                title = when (method) {
                                    AuthMethodKind.EMAIL_PASSWORD_OTP -> "Email"
                                    AuthMethodKind.GOOGLE -> "Google"
                                    AuthMethodKind.APPLE -> "Apple"
                                },
                                subtitle = "Método vinculado. No se puede desvincular desde acá.",
                                icon = when (method) {
                                    AuthMethodKind.GOOGLE -> Icons.Default.AccountCircle
                                    else -> Icons.Default.Email
                                }
                            )
                        }
                    }
                }
                LeoVerSettingsSection(title = "Seguridad") {
                    LeoVerSettingsRow(
                        title = "Contraseña y cuenta",
                        subtitle = "Cambiar contraseña o eliminar cuenta",
                        icon = Icons.Default.Shield,
                        onClick = if (guide) blocked else onAccountSecurity
                    )
                }
                LeoVerSettingsSection(title = "Legal") {
                    LeoVerSettingsRow(
                        title = "Términos",
                        icon = Icons.Default.Gavel,
                        onClick = if (guide) blocked else onTerms
                    )
                    LeoVerSettingsRow(
                        title = "Privacidad",
                        icon = Icons.Default.Policy,
                        onClick = if (guide) blocked else onLegalPrivacy
                    )
                }
                if (uiState.canEnterAdministration) {
                    onAdministration?.let { openAdmin ->
                        LeoVerSettingsSection(title = "Plataforma") {
                            LeoVerSettingsRow(
                                title = "Administración",
                                icon = Icons.Default.Shield,
                                onClick = if (guide) blocked else openAdmin
                            )
                        }
                    }
                }
                LeoVerSettingsSection(title = "Sesión") {
                    LeoVerSettingsRow(
                        title = "Cerrar sesión",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = if (guide) blocked else onLogout
                    )
                }
            }
        }
    }
}
