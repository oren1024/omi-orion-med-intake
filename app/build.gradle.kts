plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.orion.omi"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.orion.omi"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // A fixed test key kept in the repo so every build is signed the same way
    // and a new APK installs over the old one without losing data.
    // Test builds only. Keep the repository private.
    signingConfigs {
        create("omi") {
            storeFile = file("omi-debug.keystore")
            storePassword = "omi-debug"
            keyAlias = "omi"
            keyPassword = "omi-debug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("omi")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
