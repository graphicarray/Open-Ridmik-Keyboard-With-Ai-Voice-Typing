plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.graphicarray.voicetyper"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.graphicarray.voicetyper"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.7"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
