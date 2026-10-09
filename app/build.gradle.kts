plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.manoj.lofi4a"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.manoj.lofi4a"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // arm64 only by default to keep APK small; add others as needed
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
    
}
