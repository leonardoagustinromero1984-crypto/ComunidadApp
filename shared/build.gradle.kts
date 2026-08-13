@file:OptIn(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi::class)

import java.net.URI
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // AGP 9 Android-KMP library target
    android {
        namespace = "com.comunidapp.shared"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        withHostTest {}
    }

    iosArm64().binaries.framework {
        baseName = "LeoVerShared"
        isStatic = true
    }

    // KT-86501: storage-kt iosSimulatorArm64 fails native compiler cache
    // (IrTypeAliasSymbolImpl already bound for kotlinx.datetime/Instant).
    iosSimulatorArm64().binaries.framework {
        baseName = "LeoVerShared"
        isStatic = true
        @Suppress("DEPRECATION")
        disableNativeCache(
            version = DisableCacheInKotlinVersion.`2_3_20`,
            reason = "Workaround for KT-86501 triggered while linking storage-kt on iosSimulatorArm64",
            issueUrl = URI("https://youtrack.jetbrains.com/issue/KT-86501")
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.storage)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
