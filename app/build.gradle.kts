plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.cafeteria.gestion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cafeteria.gestion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        fun configValue(name: String): String = providers.gradleProperty(name).orElse("").get()
        fun quotedConfig(name: String) = "\"${configValue(name).replace("\\", "\\\\").replace("\"", "\\\"")}\""
        buildConfigField("String", "API_BASE_URL", quotedConfig("API_BASE_URL"))
        buildConfigField("String", "FIREBASE_API_KEY", quotedConfig("FIREBASE_API_KEY"))
        buildConfigField("String", "FIREBASE_APP_ID", quotedConfig("FIREBASE_APP_ID"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quotedConfig("FIREBASE_PROJECT_ID"))
        buildConfigField("String", "FIREBASE_SENDER_ID", quotedConfig("FIREBASE_SENDER_ID"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.firebase:firebase-messaging:24.1.0")

    testImplementation("junit:junit:4.13.2")
}
