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
    }
}
rootProject.name = "Alis"
include(":app")
include(":core:model")
include(":core:discovery")
include(":core:upnp")
include(":core:akis")
include(":core:data")
include(":core:player")
include(":core:ui")
