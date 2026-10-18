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

rootProject.name = "RoshanKhata"
include(":app")
include(":shared")
// The Windows app (KMP Phase 6): the shared code and screens in a desktop window.
include(":desktop")
