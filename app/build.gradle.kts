plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android") version "2.57" apply false


}

android {
    namespace = "com.aviansh.aifilemanager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aviansh.aifilemanager"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))

    // AndroidX
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Gson
    implementation("com.google.code.gson:gson:2.13.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57")
    ksp("com.google.dagger:hilt-compiler:2.57")
    // Hilt Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Unit Tests
    testImplementation("junit:junit:4.13.2")

    // Android Tests
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Source: https://mvnrepository.com/artifact/com.google.ai.client.generativeai/generativeai
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    // Source: https://mvnrepository.com/artifact/com.google.ai.client.generativeai/common
    runtimeOnly("com.google.ai.client.generativeai:common:0.10.0")
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")


    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))

    // Add the dependency for the Firebase AI Logic library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-ai")

    // Source: https://mvnrepository.com/artifact/com.google.genai/google-genai
    implementation("com.google.genai:google-genai:1.60.0")
}

chaquopy {
}