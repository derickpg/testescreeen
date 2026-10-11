plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.automirrorlab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.automirrorlab"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }
}
