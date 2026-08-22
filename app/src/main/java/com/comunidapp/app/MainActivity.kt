package com.comunidapp.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.navigation.ComunidappNavGraph
import com.comunidapp.app.notifications.LeoverNotificationHelper
import com.comunidapp.app.notifications.NotificationChannelRegistry
import com.comunidapp.app.notifications.NotificationPendingNavigationStore
import com.comunidapp.app.notifications.PushTokenRegistrar
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.data.remote.supabase.SupabaseAuthConfig
import com.comunidapp.app.domain.auth.AuthDeepLinkKind
import com.comunidapp.app.domain.auth.AuthDeepLinkParser
import com.comunidapp.app.domain.auth.AuthLinkNoticeStore
import com.comunidapp.app.viewmodel.SessionState
import com.comunidapp.app.viewmodel.SessionViewModel
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    private var keepSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashScreen }
        super.onCreate(savedInstanceState)
        NotificationChannelRegistry.ensureChannels(this)
        handleAuthDeepLink(intent)
        captureNotificationDeepLink(intent)
        // POST_NOTIFICATIONS se pide en contexto (preferencias), no al iniciar la app.
        enableEdgeToEdge()
        setContent {
            ComunidappTheme {
                val sessionViewModel: SessionViewModel = viewModel()
                val sessionState by sessionViewModel.sessionState.collectAsState()
                keepSplashScreen = sessionState == SessionState.Loading
                LaunchedEffect(Unit) {
                    AuthLinkNoticeStore.notice.collect { notice ->
                        notice ?: return@collect
                        sessionViewModel.onAuthDeepLink(notice.kind, notice.userMessage)
                    }
                }
                LaunchedEffect(sessionState) {
                    if (sessionState == SessionState.LoggedIn) {
                        PushTokenRegistrar.syncCurrentToken()
                    } else if (sessionState == SessionState.LoggedOut) {
                        NotificationPendingNavigationStore.clear()
                    }
                }
                ComunidappNavGraph(sessionViewModel = sessionViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
        captureNotificationDeepLink(intent)
    }

    private fun captureNotificationDeepLink(intent: Intent?) {
        if (intent == null) return
        val notificationId = intent.getStringExtra(LeoverNotificationHelper.EXTRA_NOTIFICATION_ID)
            ?: return
        val route = NotificationDeepLinkRoute.fromString(
            intent.getStringExtra(LeoverNotificationHelper.EXTRA_DEEP_LINK_TYPE)
        ) ?: NotificationDeepLinkRoute.SAFE_HOME
        val link = NotificationDeepLink(
            routeType = route,
            resourceId = intent.getStringExtra(LeoverNotificationHelper.EXTRA_RESOURCE_ID),
            organizationId = intent.getStringExtra(LeoverNotificationHelper.EXTRA_ORGANIZATION_ID)
        )
        NotificationPendingNavigationStore.offer(notificationId, link)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        if (!AppConfigProvider.featureFlags().useSupabase || intent == null) return
        val data = intent.data ?: return
        if (data.scheme != SupabaseAuthConfig.SCHEME) return
        if (data.host != null && data.host != SupabaseAuthConfig.HOST) return
        val uri = data.toString()
        if (AuthDeepLinkParser.isForbiddenCallback(uri)) {
            AuthLinkNoticeStore.publish(
                AuthDeepLinkKind.LinkError,
                "No pudimos abrir ese enlace. Ingresá el código de verificación o reenviá uno nuevo."
            )
            return
        }
        val kind = AuthDeepLinkParser.consumeOnce(uri) ?: return
        val errorMessage = AuthDeepLinkParser.userMessageFor(uri)
        if (kind == AuthDeepLinkKind.SessionCallback) {
            com.comunidapp.app.domain.auth.GoogleAuthTrace.event("GOOGLE-CALLBACK")
        }
        if (kind == AuthDeepLinkKind.LinkError) {
            AuthLinkNoticeStore.publish(
                AuthDeepLinkKind.LinkError,
                errorMessage ?: "El enlace venció o ya fue utilizado."
            )
            return
        }
        val handled = runCatching { supabase.handleDeeplinks(intent) }
        if (handled.isFailure) {
            AuthLinkNoticeStore.publish(
                AuthDeepLinkKind.LinkError,
                AuthDeepLinkParser.messageForAuthCallbackError(
                    handled.exceptionOrNull()?.javaClass?.simpleName.orEmpty(),
                    ""
                )
            )
            return
        }
        AuthLinkNoticeStore.publish(kind)
        pendingDeepLinkKind = kind
    }

    companion object {
        @Volatile
        var pendingDeepLinkKind: com.comunidapp.app.domain.auth.AuthDeepLinkKind? = null
    }
}
