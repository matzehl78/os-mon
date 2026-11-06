plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.osmon"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.osmon"     // vor der Veröffentlichung auf eine eigene Domain ändern
        minSdk = 29
        targetSdk = 35
        versionCode = System.getenv("OSMON_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("OSMON_VERSION_NAME") ?: "0.1.0"
    }

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
    sourceSets["main"].java.srcDir("../family")
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.health.connect)
    implementation(libs.play.services.wearable)          // Wear-OS-Uhr (PhoneWearBridge.kt)
    implementation(libs.billing.ktx)                     // Premium als Einmalkauf (Premium.kt)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.org.json)                    // echtes org.json für WearPayloadTest
}
