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
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}
