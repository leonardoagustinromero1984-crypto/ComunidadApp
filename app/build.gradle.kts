plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    jacoco
}

import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun prop(key: String): String =
    localProperties.getProperty(key).orEmpty().trim()

fun escapeBc(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

fun firstNonBlank(vararg keys: String): String =
    keys.map { prop(it) }.firstOrNull { it.isNotBlank() }.orEmpty()

// LOCAL (dev / emulador) — SUPABASE_URL + SUPABASE_ANON_KEY
val localUrl = prop("SUPABASE_URL")
val localKey = prop("SUPABASE_ANON_KEY")

// STAGING (remoto LeoVer) — never service_role; prefer publishable, fallback anon legacy
val stagingUrl = prop("SUPABASE_STAGING_URL")
val stagingKey = firstNonBlank(
    "SUPABASE_STAGING_PUBLISHABLE_KEY",
    "SUPABASE_STAGING_ANON_KEY"
)

// PRODUCTION (futuro) — placeholders only until production exists
val productionUrl = prop("SUPABASE_PRODUCTION_URL")
val productionKey = firstNonBlank(
    "SUPABASE_PRODUCTION_PUBLISHABLE_KEY",
    "SUPABASE_PRODUCTION_ANON_KEY"
)

fun isForbiddenLocalHost(url: String): Boolean {
    val u = url.lowercase()
    return u.contains("localhost") ||
        u.contains("127.0.0.1") ||
        u.contains("10.0.2.2") ||
        u.startsWith("http://")
}

fun isRemoteHttpsSupabaseUrl(url: String): Boolean =
    url.isNotBlank() &&
        url.startsWith("https://", ignoreCase = true) &&
        !isForbiddenLocalHost(url)

// Custom Auth domains — empty/inactive until DNS + Supabase Custom Domain are live.
// Do not default these to true: auth-staging.leover.com.ar is currently NXDOMAIN.
val stagingAuthUrl = prop("SUPABASE_STAGING_AUTH_URL")
val stagingAuthActive = prop("SUPABASE_STAGING_AUTH_ACTIVE").equals("true", ignoreCase = true) &&
    isRemoteHttpsSupabaseUrl(stagingAuthUrl)
val productionAuthUrl = prop("SUPABASE_PRODUCTION_AUTH_URL")
val productionAuthActive = prop("SUPABASE_PRODUCTION_AUTH_ACTIVE").equals("true", ignoreCase = true) &&
    isRemoteHttpsSupabaseUrl(productionAuthUrl)

/**
 * localDebug APK must not embed emulator-only hosts (10.0.2.2 / localhost / cleartext).
 * Prefer SUPABASE_URL when it is remote HTTPS; otherwise fall back to staging credentials.
 */
data class ResolvedLocalSupabase(
    val url: String,
    val key: String,
    val enabled: Boolean,
    val source: String
)

fun resolveLocalSupabase(): ResolvedLocalSupabase {
    if (isRemoteHttpsSupabaseUrl(localUrl) && localKey.isNotBlank()) {
        if (localKey.contains("service_role", ignoreCase = true)) {
            throw GradleException("service_role is forbidden in Android local credentials.")
        }
        return ResolvedLocalSupabase(localUrl, localKey, true, "SUPABASE_URL")
    }
    if (isRemoteHttpsSupabaseUrl(stagingUrl) && stagingKey.isNotBlank()) {
        if (stagingKey.contains("service_role", ignoreCase = true)) {
            throw GradleException("service_role is forbidden in Android staging credentials.")
        }
        logger.warn(
            "local flavor: SUPABASE_URL missing or points to localhost/http/10.0.2.2; " +
                "using SUPABASE_STAGING_* for localDebug (physical APK / cleartext-safe)."
        )
        return ResolvedLocalSupabase(stagingUrl, stagingKey, true, "STAGING_FALLBACK")
    }
    logger.warn(
        "local flavor: no usable remote Supabase credentials; SUPABASE_ENABLED=false (mock mode)."
    )
    return ResolvedLocalSupabase("", "", false, "NONE")
}

val resolvedLocal = resolveLocalSupabase()
val resolvedLocalUrl = resolvedLocal.url
val resolvedLocalKey = resolvedLocal.key
val resolvedLocalEnabled = resolvedLocal.enabled
val resolvedLocalSource = resolvedLocal.source
val mapsApiKey = prop("MAPS_API_KEY").ifBlank { "MAPS_API_KEY_MISSING" }

android {
    namespace = "com.comunidapp.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.comunidapp.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"${escapeBc(if (mapsApiKey == "MAPS_API_KEY_MISSING") "" else mapsApiKey)}\"")
    }

    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            resValue("string", "app_name", "LeoVer Local")
            buildConfigField("Boolean", "SUPABASE_ENABLED", resolvedLocalEnabled.toString())
            buildConfigField("String", "SUPABASE_URL", "\"${escapeBc(resolvedLocalUrl)}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${escapeBc(resolvedLocalKey)}\"")
            buildConfigField("String", "LEOVER_ENV", "\"local\"")
            buildConfigField("String", "SUPABASE_CREDENTIAL_SOURCE", "\"${escapeBc(resolvedLocalSource)}\"")
            buildConfigField("String", "AUTH_DOMAIN", "\"${escapeBc(stagingAuthUrl)}\"")
            buildConfigField("Boolean", "AUTH_CUSTOM_DOMAIN_ACTIVE", stagingAuthActive.toString())
            buildConfigField("Boolean", "KLIPY_ENABLED", "false")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "LeoVer Staging")
            val enabled = stagingUrl.isNotBlank() && stagingKey.isNotBlank()
            buildConfigField("Boolean", "SUPABASE_ENABLED", enabled.toString())
            buildConfigField("String", "SUPABASE_URL", "\"${escapeBc(stagingUrl)}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${escapeBc(stagingKey)}\"")
            buildConfigField("String", "LEOVER_ENV", "\"staging\"")
            buildConfigField("String", "SUPABASE_CREDENTIAL_SOURCE", "\"STAGING\"")
            buildConfigField("String", "AUTH_DOMAIN", "\"${escapeBc(stagingAuthUrl)}\"")
            buildConfigField("Boolean", "AUTH_CUSTOM_DOMAIN_ACTIVE", stagingAuthActive.toString())
            buildConfigField("Boolean", "KLIPY_ENABLED", "false")
        }
        create("production") {
            dimension = "environment"
            // No suffix — future production id
            resValue("string", "app_name", "LeoVer")
            val enabled = productionUrl.isNotBlank() && productionKey.isNotBlank()
            buildConfigField("Boolean", "SUPABASE_ENABLED", enabled.toString())
            buildConfigField("String", "SUPABASE_URL", "\"${escapeBc(productionUrl)}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${escapeBc(productionKey)}\"")
            buildConfigField("String", "LEOVER_ENV", "\"production\"")
            buildConfigField("String", "SUPABASE_CREDENTIAL_SOURCE", "\"PRODUCTION\"")
            buildConfigField("String", "AUTH_DOMAIN", "\"${escapeBc(productionAuthUrl)}\"")
            buildConfigField("Boolean", "AUTH_CUSTOM_DOMAIN_ACTIVE", productionAuthActive.toString())
            buildConfigField("Boolean", "KLIPY_ENABLED", "false")
        }
    }

    buildTypes {
        debug {
            // Off by default on low-RAM machines; enable with -PenableUnitTestCoverage=true
            // before jacocoTestReport / full coverage runs.
            enableUnitTestCoverage = project.hasProperty("enableUnitTestCoverage")
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

// Solo construir staging/production cuando la tarea lo pide (evita que
// assembleDebug/testDebugUnitTest fallen sin credenciales staging).
androidComponents {
    beforeVariants { variant ->
        val envFlavor = variant.productFlavors
            .firstOrNull { it.first == "environment" }
            ?.second
            ?: return@beforeVariants
        val tasks = gradle.startParameter.taskNames.joinToString(" ").lowercase()
        val enableStaging = tasks.contains("staging") ||
            project.hasProperty("enableStagingBuild")
        val enableProduction = tasks.contains("production") ||
            project.hasProperty("enableProductionBuild")
        when (envFlavor) {
            "staging" -> variant.enable = enableStaging
            "production" -> variant.enable = enableProduction
        }
    }
}

// Staging assemble must use HTTPS remote — never local hosts / cleartext.
tasks.configureEach {
    val n = name
    if (n.startsWith("assembleLocal") || n.startsWith("bundleLocal") ||
        n.startsWith("packageLocal") || n.startsWith("installLocal")
    ) {
        doFirst {
            if (resolvedLocalEnabled) {
                if (!isRemoteHttpsSupabaseUrl(resolvedLocalUrl)) {
                    throw GradleException(
                        "localDebug Supabase URL must be remote HTTPS (not localhost / 10.0.2.2 / http)."
                    )
                }
                if (resolvedLocalKey.contains("service_role", ignoreCase = true)) {
                    throw GradleException("service_role is forbidden in Android local credentials.")
                }
            }
        }
    }
    if (n.startsWith("assembleStaging") || n.startsWith("bundleStaging") ||
        n.startsWith("packageStaging") || n.startsWith("installStaging")
    ) {
        doFirst {
            if (stagingUrl.isBlank() || stagingKey.isBlank()) {
                throw GradleException(
                    "Staging credentials missing. Set SUPABASE_STAGING_URL and " +
                        "SUPABASE_STAGING_PUBLISHABLE_KEY (or SUPABASE_STAGING_ANON_KEY) in local.properties."
                )
            }
            if (!stagingUrl.startsWith("https://")) {
                throw GradleException("SUPABASE_STAGING_URL must use HTTPS.")
            }
            if (isForbiddenLocalHost(stagingUrl)) {
                throw GradleException(
                    "SUPABASE_STAGING_URL must not use localhost / 127.0.0.1 / 10.0.2.2 / http."
                )
            }
            if (stagingKey.contains("service_role", ignoreCase = true)) {
                throw GradleException("service_role is forbidden in Android staging credentials.")
            }
        }
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates JaCoCo coverage report for unit tests (informative baseline)."
    dependsOn("testLocalDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/com/android/**"
    )
    val debugTree = fileTree(layout.buildDirectory.dir("intermediates/javac/localDebug")) {
        exclude(fileFilter)
    }
    val kotlinTreeLegacy = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/localDebug")) {
        exclude(fileFilter)
    }
    val kotlinTreeAgp = fileTree(
        layout.buildDirectory.dir(
            "intermediates/built_in_kotlinc/localDebug/compileLocalDebugKotlin/classes"
        )
    ) {
        exclude(fileFilter)
    }
    classDirectories.setFrom(files(debugTree, kotlinTreeLegacy, kotlinTreeAgp))
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "**/*.exec",
                "**/jacoco/testLocalDebugUnitTest.exec",
                "**/unit_test_code_coverage/**/*.exec",
                "**/coverage_exec/**/*.ec"
            )
        }
    )
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.github.yalantis:ucrop:2.2.10")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val copyLocalDebugApk = tasks.register<Copy>("copyLocalDebugApk") {
    from(layout.buildDirectory.file("outputs/apk/local/debug/app-local-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "LeoVer-local-debug.apk" }
}

val copyStagingDebugApk = tasks.register<Copy>("copyStagingDebugApk") {
    from(layout.buildDirectory.file("outputs/apk/staging/debug/app-staging-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "LeoVer-M08-Staging-debug.apk" }
}

afterEvaluate {
    tasks.findByName("assembleLocalDebug")?.finalizedBy(copyLocalDebugApk)
    tasks.findByName("assembleStagingDebug")?.finalizedBy(copyStagingDebugApk)

    val gateTaskNames = setOf("uiRegressionGate", "checkLeoVerUiContract")
    val requestedGate = gradle.startParameter.taskNames.any { requested ->
        gateTaskNames.any { requested == it || requested.endsWith(":$it") }
    }
    if (requestedGate) {
        tasks.named<Test>("testLocalDebugUnitTest").configure {
            filter {
                includeTestsMatching("com.comunidapp.app.ui.UiRegressionGateTest")
                includeTestsMatching("com.comunidapp.app.ui.LeoVerDesignSystemContractTest")
                includeTestsMatching("com.comunidapp.app.ui.LeoVerUx07ContractTest")
                includeTestsMatching("com.comunidapp.app.ui.LeoVerMapPolicyGateTest")
                includeTestsMatching("com.comunidapp.app.ui.PersonaBottomSurfacesTest")
                includeTestsMatching("com.comunidapp.app.ui.VisualDirectionV2PilotTest")
                includeTestsMatching("com.comunidapp.app.ui.Ux05SocialHomeCommunityProfileSettingsTest")
                includeTestsMatching("com.comunidapp.app.data.model.CommunityCanonicalUiTest")
                includeTestsMatching("com.comunidapp.app.domain.ux.CanonicalUiErrorMapperTest")
                includeTestsMatching("com.comunidapp.app.domain.auth.Auth05SignupOtpOnlyGuardsTest")
                includeTestsMatching("com.comunidapp.app.domain.onboarding.onb02.Onb02TutorialRoutingTest")
                includeTestsMatching("com.comunidapp.app.domain.foster.FosterDirectPlacementTest")
                includeTestsMatching("com.comunidapp.app.domain.auth.LeoVerAuth06ContractTest")
                includeTestsMatching("com.comunidapp.app.domain.onboarding.onb03.LeoVerOnb03ContractTest")
                includeTestsMatching("com.comunidapp.app.domain.social.LeoVerOrgSocialQaContractTest")
                includeTestsMatching("com.comunidapp.app.domain.social.LeoVerPreQaFinalContractTest")
                includeTestsMatching("com.comunidapp.app.domain.vitacora.import.LeoVerVitacoraImportContractTest")
                includeTestsMatching("com.comunidapp.app.domain.onboarding.LeoVerAuthOnbRecoveryContractTest")
            }
            filter.isFailOnNoMatchingTests = true
        }
    }
}

tasks.register("checkLeoVerUiContract") {
    group = "verification"
    description = "LeoVer UI contract + free-only map gate (no emulator)."
    dependsOn("uiRegressionGate")
}

tasks.register("uiRegressionGate") {
    group = "verification"
    description = "LeoVer UI-01 static UI regression gate (no emulator, no screenshots)."
    dependsOn("testLocalDebugUnitTest")
}
