plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.eisenhowertodo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.eisenhowertodo"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "1.3.0-beta1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
