plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.aaaaaisss.necokey.zenz"
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    defaultConfig {
        minSdk = 29
        targetSdk = 36
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
