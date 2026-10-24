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
        // Motor web de Firefox (GeckoView).
        maven("https://maven.mozilla.org/maven2/")
    }
}
rootProject.name = "LauncherAmarillo"
include(":app")
