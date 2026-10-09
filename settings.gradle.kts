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
include(":necokeyZenz")
include(":sumireFlexbox")
include(":sumireTenkey")
include(":sumireSymbolKeyboard")
include(":sumireGojuonKeyboard")
include(":sumireQwertyKeyboard")
include(":sumireZenz")
project(":sumireCore").projectDir = file("vendor/sumire/core")
project(":sumireCustomKeyboard").projectDir = file("vendor/sumire/custom_keyboard")
project(":sumireFlexbox").projectDir = file("vendor/sumire/flexbox")
project(":sumireTenkey").projectDir = file("vendor/sumire/tenkey")
project(":sumireSymbolKeyboard").projectDir = file("vendor/sumire/symbol_keyboard")
project(":sumireGojuonKeyboard").projectDir = file("vendor/sumire/gojuon_keyboard")
project(":sumireQwertyKeyboard").projectDir = file("vendor/sumire/qwerty_keyboard")
project(":sumireZenz").projectDir = file("vendor/sumire/zenz")
project(":necokeyZenz").projectDir = file("zenz")
