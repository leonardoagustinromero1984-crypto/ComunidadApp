package com.comunidapp.app.domain.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService

/**
 * Launch the already-built Google OAuth URL.
 * Same ACTION_VIEW mechanism that physically worked; the only change is
 * pinning the Android default browser so Gmail cannot take the HTTPS intent.
 */
object LeoVerGoogleSignIn {
    data class LaunchResult(
        val packageName: String,
        val customTabs: Boolean
    )

    private val MAIL_PACKAGES = setOf(
        "com.google.android.gm",
        "com.google.android.gm.lite",
        "com.google.android.apps.gmail",
        "com.samsung.android.email.provider",
        "com.samsung.android.email.ui",
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "org.kman.AquaMail",
        "ch.protonmail.android",
        "me.bluemail.mail",
        "com.fsck.k9"
    )

    fun openAuthorizeUrl(context: Context, url: String): LaunchResult? {
        val uri = Uri.parse(url)
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            GoogleAuthTrace.event("GOOGLE-ERROR=BAD_URL")
            return null
        }

        val defaultBrowser = defaultBrowserPackage(context)
        GoogleAuthTrace.event("GOOGLE-DEFAULT-BROWSER=${defaultBrowser ?: "null"}")

        if (defaultBrowser != null) {
            return launchToPackage(context, uri, defaultBrowser)
        }

        val browsers = installedBrowserPackages(context)
        when {
            browsers.isEmpty() -> {
                GoogleAuthTrace.event("GOOGLE-ERROR=NO_BROWSER")
                return null
            }
            browsers.size == 1 -> return launchToPackage(context, uri, browsers.first())
            else -> return launchBrowserChooser(context, uri, browsers)
        }
    }

    private fun launchToPackage(context: Context, uri: Uri, packageName: String): LaunchResult? {
        val customTabs = supportsCustomTabs(context, packageName)
        GoogleAuthTrace.event(
            "GOOGLE-LAUNCH-MODE=${if (customTabs) "CUSTOM_TAB" else "EXPLICIT_BROWSER"}"
        )
        GoogleAuthTrace.event("GOOGLE-FINAL-PACKAGE=$packageName")
        return try {
            if (customTabs) {
                launchCustomTab(context, uri, packageName)
            } else {
                launchExplicitView(context, uri, packageName)
            }
            GoogleAuthTrace.event("GOOGLE-LAUNCH")
            LaunchResult(packageName, customTabs)
        } catch (_: ActivityNotFoundException) {
            if (customTabs) {
                try {
                    launchExplicitView(context, uri, packageName)
                    GoogleAuthTrace.event("GOOGLE-LAUNCH-MODE=EXPLICIT_BROWSER")
                    GoogleAuthTrace.event("GOOGLE-LAUNCH")
                    return LaunchResult(packageName, customTabs = false)
                } catch (_: ActivityNotFoundException) {
                    GoogleAuthTrace.event("GOOGLE-ERROR=LAUNCH")
                    return null
                }
            }
            GoogleAuthTrace.event("GOOGLE-ERROR=LAUNCH")
            null
        }
    }

    private fun launchCustomTab(context: Context, uri: Uri, packageName: String) {
        val tabs = CustomTabsIntent.Builder()
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setBookmarksButtonEnabled(false)
            .setDownloadButtonEnabled(false)
            .build()
        tabs.intent.setPackage(packageName)
        if (context !is Activity) {
            tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        tabs.launchUrl(context, uri)
    }

    private fun launchExplicitView(context: Context, uri: Uri, packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(packageName)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    private fun launchBrowserChooser(
        context: Context,
        uri: Uri,
        packages: List<String>
    ): LaunchResult? {
        val intents = packages.map { pkg ->
            Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(pkg)
            }
        }
        val chooser = Intent.createChooser(intents.first(), "Elegí un navegador").apply {
            if (intents.size > 1) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
            }
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        GoogleAuthTrace.event("GOOGLE-LAUNCH-MODE=EXPLICIT_BROWSER")
        GoogleAuthTrace.event("GOOGLE-FINAL-PACKAGE=${packages.joinToString(",")}")
        return try {
            context.startActivity(chooser)
            GoogleAuthTrace.event("GOOGLE-LAUNCH")
            LaunchResult(packages.first(), customTabs = false)
        } catch (_: ActivityNotFoundException) {
            GoogleAuthTrace.event("GOOGLE-ERROR=LAUNCH")
            null
        }
    }

    private fun defaultBrowserPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        val pkg = resolved.activityInfo?.packageName ?: return null
        val cls = resolved.activityInfo?.name
        if (isResolver(pkg, cls) || !isBrowserPackage(pkg)) return null
        return pkg
    }

    private fun installedBrowserPackages(context: Context): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        val flags = if (Build.VERSION.SDK_INT >= 23) PackageManager.MATCH_ALL else 0
        return context.packageManager.queryIntentActivities(intent, flags)
            .mapNotNull { it.activityInfo?.packageName }
            .filter(::isBrowserPackage)
            .distinct()
    }

    private fun supportsCustomTabs(context: Context, packageName: String): Boolean {
        val service = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(packageName)
        return context.packageManager.queryIntentServices(service, 0).isNotEmpty()
    }

    private fun isResolver(packageName: String, className: String?): Boolean {
        val pkg = packageName.lowercase()
        val cls = className.orEmpty()
        return pkg == "android" ||
            pkg.contains("intentresolver") ||
            cls.contains("ResolverActivity")
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg in MAIL_PACKAGES) return false
        if (pkg.endsWith(".mail") || pkg.contains(".email") || pkg.contains("mailbox")) return false
        return true
    }
}

fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
