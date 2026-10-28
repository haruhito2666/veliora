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

        // The Sofuu AAR ships arm64-v8a and x86_64. Do not install on a
        // 32-bit-only device where libsofuu.so cannot load.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // The app runs the Veliora pipeline inside libsofuu, so there is no
        // remote API base. These values configure the embedded runtime.
        val clerkIssuer = (project.findProperty("VELIORA_CLERK_ISSUER") as String?)
            ?: System.getenv("VELIORA_CLERK_ISSUER")
            ?: System.getenv("CLERK_ISSUER")
            ?: "https://clerk.veliora-app.rikuhana.com"
        val clerkJwks = (project.findProperty("VELIORA_CLERK_JWKS_URL") as String?)
            ?: System.getenv("VELIORA_CLERK_JWKS_URL")
            ?: System.getenv("CLERK_JWKS_URL")
            ?: "$clerkIssuer/.well-known/jwks.json"
        val clerkAuthorizedParties = (project.findProperty("VELIORA_CLERK_AUTHORIZED_PARTIES") as String?)
            ?: System.getenv("VELIORA_CLERK_AUTHORIZED_PARTIES")
            ?: System.getenv("CLERK_AUTHORIZED_PARTIES")
            ?: ""
        val embeddedMode = (project.findProperty("VELIORA_EMBEDDED_MODE") as String?)
            ?: System.getenv("VELIORA_EMBEDDED_MODE")
            ?: "scripted"
        val embeddedStepDelay = ((project.findProperty("VELIORA_STEP_DELAY_MS") as String?)
            ?: System.getenv("VELIORA_STEP_DELAY_MS")
            ?: "25").toIntOrNull() ?: 25
        val embeddedProvider = (project.findProperty("VELIORA_PROVIDER") as String?)
            ?: System.getenv("VELIORA_PROVIDER")
            ?: ""
        val embeddedModel = (project.findProperty("VELIORA_MODEL") as String?)
            ?: System.getenv("VELIORA_MODEL")
            ?: ""
        val embeddedBaseUrl = (project.findProperty("VELIORA_BASE_URL") as String?)
            ?: System.getenv("VELIORA_BASE_URL")
            ?: ""

        fun quoted(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        buildConfigField("String", "CLERK_ISSUER", quoted(clerkIssuer))
        buildConfigField("String", "CLERK_JWKS_URL", quoted(clerkJwks))
        buildConfigField("String", "CLERK_AUTHORIZED_PARTIES", quoted(clerkAuthorizedParties))
        buildConfigField("String", "EMBEDDED_MODE", quoted(embeddedMode))
        buildConfigField("int", "EMBEDDED_STEP_DELAY_MS", embeddedStepDelay.toString())
        buildConfigField("String", "EMBEDDED_PROVIDER", quoted(embeddedProvider))
        buildConfigField("String", "EMBEDDED_MODEL", quoted(embeddedModel))
        buildConfigField("String", "EMBEDDED_BASE_URL", quoted(embeddedBaseUrl))

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
    implementation("com.sofuu:sofuu-android:0.2.0")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clerk.api)
    debugImplementation(libs.androidx.ui.tooling)
}
