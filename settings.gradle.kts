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
include(":zenz")
project(":sumireCore").projectDir = file("vendor/sumire/core")
