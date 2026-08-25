import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Read deployment config from local.properties first (gitignored, per-machine),
// falling back to gradle.properties / -P flags / environment variables.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun cfg(key: String): String =
    localProps.getProperty(key)
        ?: project.findProperty(key) as String?
        ?: System.getenv(key)
        ?: ""

android {
    namespace = "com.cmac.opscommand"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cmac.opscommand"
        // Google TV / Android TV devices in the field are all API 26+; 26 also
        // unlocks variable-font support for the Orbitron/Rajdhani HUD faces.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "CMAC_SERVER_URL", "\"${cfg("CMAC_SERVER_URL")}\"")
        buildConfigField("String", "MAPBOX_TOKEN", "\"${cfg("CMAC_MAPBOX_TOKEN")}\"")
    }

    buildTypes {
        debug {
            // The dashboard is a LAN appliance; the Express server it talks to is
            // plain http. Cleartext is allowed via the network-security-config.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    // Deliberately NO material3 / tv-material: every surface in this dashboard is
    // custom-drawn to match the web app's HUD design pixel-for-pixel, so the
    // Material component layer would be dead weight in the APK.
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // HTTP + Server-Sent Events (the dashboard's live channel)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Static dispatch-map imagery
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Unit tests for the map projection / outlier maths (pure JVM, no device).
dependencies {
    testImplementation("junit:junit:4.13.2")
}
