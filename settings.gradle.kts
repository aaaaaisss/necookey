pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Necookey"
include(":app")
include(":sumireCore")
include(":sumireCustomKeyboard")
include(":zenz")
project(":sumireCore").projectDir = file("vendor/sumire/core")
project(":sumireCustomKeyboard").projectDir = file("vendor/sumire/custom_keyboard")
