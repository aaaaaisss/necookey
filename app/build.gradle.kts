plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
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

    packaging {
        jniLibs {
            excludes += setOf("lib/x86/**", "lib/x86_64/**")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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
                file("src/main/java"),
                file("../vendor/sumire/app/src/main/java"),
            )
            res.srcDir(file("../vendor/sumire/app/src/main/res"))
            assets.srcDir(file("../vendor/sumire/app/src/main/assets"))
        }
    }
}

dependencies {
    implementation(project(":sumireCustomKeyboard"))
    implementation(project(":sumireCore"))
    implementation(project(":necokeyZenz"))

    // Keyboard support modules used by Sumire's FlickKeyboardView.
    implementation(project(":sumireSymbolKeyboard"))
    implementation(project(":sumireGojuonKeyboard"))
    implementation(project(":sumireQwertyKeyboard"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.emoji2:emoji2:1.5.0")
    implementation("androidx.window:window:1.4.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("de.psdev.licensesdialog:licensesdialog:2.1.0")
    implementation("com.afollestad.material-dialogs:core:3.3.0")
    implementation("com.afollestad.material-dialogs:color:3.3.0")
}
