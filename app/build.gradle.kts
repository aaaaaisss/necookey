plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.aaaaaisss.necokey"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.aaaaaisss.necokey"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"
    }
}
dependencies {
    implementation(project(":sumireCustomKeyboard"))
}
