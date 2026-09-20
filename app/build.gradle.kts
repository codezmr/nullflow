plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.codezmr.nullflow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.codezmr.nullflow"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        getByName("debug").also {
            // Reuse debug key for release (GitHub distribution only)
        }
        create("release") {
            storeFile = file("../local.keystore")
            storePassword = System.getenv("KEYSTORE_PASS") ?: "android"
            keyAlias = "nullflow"
            keyPassword = System.getenv("KEY_PASS") ?: "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

// Name the output APK "NullFlow.apk" (legacy API -
// androidComponents.outputFileName does NOT exist in AGP 8.5.2)
android.applicationVariants.all {
    outputs.forEach { output ->
        (output as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
            .outputFileName = "NullFlow.apk"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Material Components - required for com.google.android.material.bottomsheet
    // .BottomSheetDialog, which TileService.showDialog() needs to host the
    // Compose Focus Panel as a native system overlay.
    implementation("com.google.android.material:material:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Room (persistence for focus profiles, blocked apps, sessions)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
