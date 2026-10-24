plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.pauze.auralis"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pauze.auralis"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.tv:tv-material:1.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
