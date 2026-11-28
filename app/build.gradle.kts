plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kawaiimusic.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.kawaiimusic.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    // Release signing is configured from environment variables so the GitHub
    // workflow can inject a persistent keystore (see .github/workflows).
    val ksPath = System.getenv("KAWAI_KEYSTORE_PATH")
    signingConfigs {
        if (ksPath != null) {
            create("kawai") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KAWAI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KAWAI_KEY_ALIAS") ?: "kawai-music"
                keyPassword = System.getenv("KAWAI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (ksPath != null) signingConfig = signingConfigs.getByName("kawai")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/INDEX.LIST",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Liquid-glass backdrop blur / refraction (same library SimpMusic uses).
    implementation("io.github.kyant0:backdrop:2.0.1")

    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-datasource:$media3")

    // Resolves playable stream URLs (signature / throttling-parameter decoding).
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.1")
}
