plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kazumaproject.markdownhelperkeyboard"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.aaaaaisss.necokey"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.3.1"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                file("../vendor/sumire/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter"),
                file("../vendor/sumire/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/extensions"),
                file("../vendor/sumire/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/dictionary_override"),
            )
            assets.srcDir(file("../vendor/sumire/app/src/main/assets"))
            res.srcDir(file("../vendor/sumire/app/src/main/res"))
            // English/QWERTY conversion is intentionally omitted: necokey is Japanese-only.
        }
    }
}

dependencies {
    implementation(project(":sumireCustomKeyboard"))
    implementation(project(":sumireCore"))
    implementation(project(":zenz"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.google.dagger:hilt-android:2.50")
}
