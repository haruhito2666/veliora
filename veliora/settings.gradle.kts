pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Sofuu Android SDK: GitHub Pages Maven repo (not Maven Central).
        maven("https://sofuu-runtime.github.io/sofuu/maven")
    }
}

rootProject.name = "Veliora"
include(":app")
