import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is opt-in. Set the four VELIORA_RELEASE_* values in the
// environment (or in ~/.gradle/gradle.properties) to produce a signed release
// build. They are intentionally not committed.
val releaseStoreFile = (project.findProperty("VELIORA_RELEASE_STORE_FILE") as String?)
    ?: System.getenv("VELIORA_RELEASE_STORE_FILE")
    ?: ""
val releaseStorePassword = (project.findProperty("VELIORA_RELEASE_STORE_PASSWORD") as String?)
    ?: System.getenv("VELIORA_RELEASE_STORE_PASSWORD")
    ?: ""
val releaseKeyAlias = (project.findProperty("VELIORA_RELEASE_KEY_ALIAS") as String?)
    ?: System.getenv("VELIORA_RELEASE_KEY_ALIAS")
    ?: ""
val releaseKeyPassword = (project.findProperty("VELIORA_RELEASE_KEY_PASSWORD") as String?)
    ?: System.getenv("VELIORA_RELEASE_KEY_PASSWORD")
    ?: ""
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isNotBlank() }

android {
    namespace = "com.veliora"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.veliora"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // API base URL. Override it for a device or production deployment:
        // -PVELIORA_API_BASE=https://api.your-domain.example
        val apiBase = (project.findProperty("VELIORA_API_BASE") as String?)
            ?: "http://10.0.2.2:8080"
        buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")

        // Clerk publishable key: -PVELIORA_CLERK_PUBLISHABLE_KEY=pk_test_...
        // or the same variable in the environment. A publishable key is not a
        // secret; never put a Clerk secret key (sk_...) in the app.
        val clerkKey = (project.findProperty("VELIORA_CLERK_PUBLISHABLE_KEY") as String?)
            ?: System.getenv("VELIORA_CLERK_PUBLISHABLE_KEY")
            ?: ""
        val escapedClerkKey = clerkKey.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$escapedClerkKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clerk.ui) {
        // Clerk's theming helper asks for a JetBrains Compose material3 alpha,
        // which drags in lifecycle 2.11/core 1.19 and requires compileSdk 37.
        // The app supplies stable material3 1.4.0 from the Compose BOM.
        exclude(group = "org.jetbrains.compose.material3", module = "material3")
    }
    debugImplementation(libs.androidx.ui.tooling)
}
