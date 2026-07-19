package com.comunidapp.app.ui.screens.profile

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.notifications.NotificationPermissionCoordinator
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.viewmodel.NotificationPreferencesUiState
import com.comunidapp.app.viewmodel.NotificationPreferencesViewModel
import com.comunidapp.app.viewmodel.PreferenceUiStatus
import java.time.DayOfWeek

@Composable
fun NotificationPreferencesScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationPreferencesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var showPermissionRationale by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionMessage = if (granted) {
            "Permiso de notificaciones concedido."
        } else {
            "Permiso no concedido. La bandeja in-app de LeoVer sigue disponible; podés cambiarlo desde Ajustes."
        }
        showPermissionRationale = false
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Preferencias de avisos",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            when (uiState.status) {
                PreferenceUiStatus.LOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .semantics { contentDescription = "Cargando preferencias" }
                    )
                    return@Column
                }
                PreferenceUiStatus.ERROR -> {
                    Text(
                        text = uiState.errorMessage ?: "No se pudieron cargar las preferencias.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::retry,
                        modifier = Modifier.semantics { contentDescription = "Reintentar cargar preferencias" }
                    ) {
                        Text("Reintentar")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                else -> Unit
            }

            Text(
                text = "Elegí qué categorías pueden enviarte avisos push. Las alertas críticas " +
                    "permanecen disponibles en la bandeja in-app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            PushPermissionSection(
                showRationale = showPermissionRationale,
                message = permissionMessage,
                canRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    activity != null &&
                    !NotificationPermissionCoordinator.isGranted(context),
                onShowRationale = { showPermissionRationale = true },
                onRequest = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onOpenSettings = {
                    NotificationPermissionCoordinator.openAppNotificationSettings(context)
                },
                onDismiss = { showPermissionRationale = false }
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("Categorías", style = MaterialTheme.typography.titleMedium)
            uiState.preferences.sortedBy { it.category.ordinal }.forEach { preference ->
                PreferenceCategoryRow(
                    label = viewModel.categoryLabel(preference.category),
                    category = preference.category,
                    pushEnabled = preference.pushEnabled,
                    inAppMandatory = viewModel.isInAppMandatory(preference.category),
                    onPushChange = { viewModel.setPushEnabled(preference.category, it) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            QuietHoursSection(uiState, viewModel)
            Spacer(modifier = Modifier.height(20.dp))
            MarketingSection(
                enabled = uiState.marketingConsent,
                onEnabledChange = viewModel::setMarketingConsent
            )
            Spacer(modifier = Modifier.height(16.dp))
            EmailComingSoonSection()

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = viewModel::save,
                enabled = uiState.status != PreferenceUiStatus.SAVING,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Guardar preferencias de notificaciones" }
            ) {
                if (uiState.status == PreferenceUiStatus.SAVING) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando…")
                } else {
                    Text("Guardar")
                }
            }
            if (uiState.status == PreferenceUiStatus.SUCCESS) {
                uiState.message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (uiState.status == PreferenceUiStatus.ERROR) {
                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PushPermissionSection(
    showRationale: Boolean,
    message: String?,
    canRequest: Boolean,
    onShowRationale: () -> Unit,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Text("Permiso del sistema", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = NotificationPermissionCoordinator.rationaleText(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (showRationale) {
        Text(
            "¿Querés recibir avisos push de LeoVer en este dispositivo?",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRequest,
                modifier = Modifier.semantics { contentDescription = "Confirmar permiso de notificaciones" }
            ) { Text("Permitir") }
            TextButton(onClick = onDismiss) { Text("Ahora no") }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onShowRationale,
                enabled = canRequest,
                modifier = Modifier.semantics { contentDescription = "Activar notificaciones push" }
            ) { Text("Activar push") }
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.semantics { contentDescription = "Abrir ajustes de notificaciones" }
            ) { Text("Abrir ajustes") }
        }
    }
    message?.let {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreferenceCategoryRow(
    label: String,
    category: NotificationCategory,
    pushEnabled: Boolean,
    inAppMandatory: Boolean,
    onPushChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (inAppMandatory) {
                Text(
                    "In-app obligatoria",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (category == NotificationCategory.SECURITY) {
                Text(
                    "Las alertas críticas siempre están en la bandeja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = pushEnabled,
            onCheckedChange = onPushChange,
            modifier = Modifier.semantics { contentDescription = "Notificaciones push: $label" }
        )
    }
}

@Composable
private fun QuietHoursSection(
    uiState: NotificationPreferencesUiState,
    viewModel: NotificationPreferencesViewModel
) {
    Text("Horario silencioso", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Las alertas de seguridad pueden exceptuar este horario según política.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Activar horario silencioso")
        Switch(
            checked = uiState.quietHoursEnabled,
            onCheckedChange = viewModel::setQuietHoursEnabled,
            modifier = Modifier.semantics { contentDescription = "Activar horario silencioso" }
        )
    }
    if (uiState.quietHoursEnabled) {
        OutlinedTextField(
            value = uiState.quietStart,
            onValueChange = viewModel::setQuietStart,
            label = { Text("Desde (HH:mm)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.quietEnd,
            onValueChange = viewModel::setQuietEnd,
            label = { Text("Hasta (HH:mm)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.timezoneId,
            onValueChange = viewModel::setTimezone,
            label = { Text("Zona horaria IANA") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Días", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in uiState.quietDays,
                    onClick = { viewModel.toggleQuietDay(day) },
                    label = { Text(day.shortLabel()) },
                    modifier = Modifier.semantics {
                        contentDescription = "Horario silencioso: ${day.displayLabel()}"
                    }
                )
            }
        }
    }
}

@Composable
private fun MarketingSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Marketing")
            Text(
                "Desactivado por defecto. Solo se activa con tu consentimiento explícito.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.semantics { contentDescription = "Consentimiento de marketing" }
        )
    }
}

@Composable
private fun EmailComingSoonSection() {
    Text(
        "Email: Próximamente",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "El canal de email aún no está disponible en LeoVer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.semantics { contentDescription = "Notificaciones por email, próximamente" }
    ) {
        Text("Email no disponible")
    }
}

private fun DayOfWeek.shortLabel(): String = when (this) {
    DayOfWeek.MONDAY -> "Lu"
    DayOfWeek.TUESDAY -> "Ma"
    DayOfWeek.WEDNESDAY -> "Mi"
    DayOfWeek.THURSDAY -> "Ju"
    DayOfWeek.FRIDAY -> "Vi"
    DayOfWeek.SATURDAY -> "Sá"
    DayOfWeek.SUNDAY -> "Do"
}

private fun DayOfWeek.displayLabel(): String = when (this) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}
