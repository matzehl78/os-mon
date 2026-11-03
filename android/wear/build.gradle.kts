// Wear-OS-App (Samsung Galaxy Watch, Pixel Watch u. a.). Gleiche applicationId wie die Handy-App,
// damit der Wearable Data Layer beide verbindet. Gemeinsamer Code aus :app (reines Kotlin + Zeichnung):
// engine/, figure/core/, wearsync/ – siehe sourceSets unten.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.osmon.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.osmon"     // muss zur Handy-App passen (Data Layer)
        minSdk = 30
        targetSdk = 34
        // Uhr-App: gleiche Paket-ID wie das Handy, daher eigener Bereich für versionCode (Google Play verlangt verschiedene)
        versionCode = 100_000 + (System.getenv("OSMON_VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = System.getenv("OSMON_VERSION_NAME") ?: "0.1.0"
    }

    sourceSets["main"].java.srcDirs(
        "src/main/java",
        "../app/src/main/java/com/example/osmon/engine",
        "../app/src/main/java/com/example/osmon/figure/core",
        "../app/src/main/java/com/example/osmon/wearsync"
    )

    // Signierung für den Release nur, wenn der Keystore per Umgebung kommt (Workflow „Android Release“)
    val ks = System.getenv("OSMON_KEYSTORE")
    if (ks != null) signingConfigs.create("release") {
        storeFile = file(ks)
        storePassword = System.getenv("OSMON_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("OSMON_KEY_ALIAS")
        keyPassword = System.getenv("OSMON_KEY_PASSWORD")
    }

    buildTypes {
        release {
            if (ks != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.complications.source)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
