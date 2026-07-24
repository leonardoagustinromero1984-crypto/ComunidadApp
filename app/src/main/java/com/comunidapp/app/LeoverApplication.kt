package com.comunidapp.app

import android.app.Application
import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.notifications.LeoverNotificationHelper
import com.comunidapp.app.notifications.PushTokenRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LeoverApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        val config = AppConfigProvider.get()
        AppLog.info(
            "LeoverApp",
            "startup env=${config.environment} mock=${config.isMockMode} version=${config.appVersionName}"
        )
        config.missingConfigMessage?.let { AppLog.warning("LeoverApp", it) }
        if (config.isDebug) {
            com.comunidapp.app.core.config.AuthConfigDiagnostics.logSafe("startup")
        }
        LeoverNotificationHelper.ensureChannel(this)
        appScope.launch {
            PushTokenRegistrar.syncCurrentToken()
        }
    }

    companion object {
        lateinit var instance: LeoverApplication
            private set
    }
}
