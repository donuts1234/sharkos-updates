import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

// One key for every APK that goes on the car: Android refuses an update signed with a different key.
// keystore.properties (gitignored) points at the .jks; without it builds fall back to the debug key.
val carKey = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.sharkos.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sharkos.launcher"
        minSdk = 29          // the Shark 6 head unit is Android 11 (API 30)
        targetSdk = 32       // kept <= 32 as Shark Hub does, so the head unit treats us as a known target
        versionCode = 6
        versionName = "0.1.5"
        // Where the car looks for updates (see README → Updates). Set once in gradle.properties.
        val updateUrl = (findProperty("sharkos.updateUrl") as String?)
            ?: "https://raw.githubusercontent.com/GITHUB_USER/sharkos-updates/main/latest.json"
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
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
        debug {
            if (car != null) signingConfig = car
        }
        release {
            // R8 shrinks the Compose runtime down to what we use: the light build for the car.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = car ?: signingConfigs.getByName("debug")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    // Deliberately small: Compose foundation only (no Material, no icon packs, no navigation library).
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    // Test-only: Robolectric runs the real activity on the JVM (startup + navigation smoke test).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
