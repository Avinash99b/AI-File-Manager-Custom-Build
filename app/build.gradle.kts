plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android") version "2.60" apply true

}

android {
    namespace = "com.aviansh.aifilemanager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    packaging {
        resources {
            excludes.add("/META-INF/INDEX.LIST")
            excludes.add("/META-INF/DEPENDENCIES")
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

    // AndroidX & Core
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // ViewModel & Navigation
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Retrofit & Gson
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.13.1")

    // Hilt (Aligning runtime with your version 2.60 plugin)
    implementation("com.google.dagger:hilt-android:2.60")
    ksp("com.google.dagger:hilt-compiler:2.60")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // --- GOOGLE GEMINI SDK CLEANUP ---
    // This is the clean, correct declaration for the standard Google AI client.
    // Gradle will automatically bring in the correct matching version of 'common' and 'ktor'.
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // ---------------------------------

    // Debug Tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit & Android Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}


chaquopy {
}