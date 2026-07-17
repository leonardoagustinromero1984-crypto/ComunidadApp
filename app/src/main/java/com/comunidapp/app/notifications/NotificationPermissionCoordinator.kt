package com.comunidapp.app.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Coordina POST_NOTIFICATIONS en contexto (nunca automático al iniciar la app).
 * La bandeja in-app sigue disponible si push está denegado.
 */
object NotificationPermissionCoordinator {

    enum class PermissionState {
        GRANTED,
        NOT_REQUIRED,
        DENIED_CAN_ASK,
        DENIED_PERMANENT
    }

    fun currentState(activity: Activity): PermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return PermissionState.NOT_REQUIRED
        }
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return PermissionState.GRANTED
        val canAsk = ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        )
        return if (canAsk) PermissionState.DENIED_CAN_ASK else PermissionState.DENIED_PERMANENT
    }

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun rationaleText(): String =
        "LeoVer puede avisarte de invitaciones, seguridad y actividad importante. " +
            "La bandeja in-app funciona aunque deniegues el permiso de notificaciones del sistema."

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    /** In-app nunca se bloquea por denegación de push. */
    fun blocksAppUsageWhenDenied(): Boolean = false
}
