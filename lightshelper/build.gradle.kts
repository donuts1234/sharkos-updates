import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Same key as SharkOS: the two apps trust each other through a signature permission.
val carKey = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.sharkos.lights"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sharkos.lights"
        minSdk = 28
        // Android 9 rules, as Sharkware's Bluetooth helper: the Shark 6 is kinder to these with Bluetooth
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (!carKey.isEmpty()) {
            create("car") {
                storeFile = rootProject.file(carKey.getProperty("storeFile"))
                storePassword = carKey.getProperty("storePassword")
                keyAlias = carKey.getProperty("keyAlias")
                keyPassword = carKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        val car = signingConfigs.findByName("car")
        debug { if (car != null) signingConfig = car }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = car ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Never goes on Google Play; the old target is deliberate (see defaultConfig)
    lint { disable += "ExpiredTargetSdkVersion" }
}
