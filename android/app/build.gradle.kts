plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.docusnap.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.docusnap.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "26.2.7"



        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Gemini API key can be overridden in local.properties or BuildConfig
        buildConfigField("String", "GEMINI_API_KEY", "\"AIzaSyMockKeyForDev_ReplaceWithYourKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xskip-metadata-version-check",
            "-Xskip-prerelease-check"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Enforce 16 KB page-size alignment for native libraries (Google Play API 35/36 requirement)
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Force all Kotlin stdlib modules to match Kotlin compiler 2.0.21 to prevent metadata 2.2.0 binary mismatch
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21",
            "org.jetbrains.kotlin:kotlin-reflect:2.0.21"
        )
    }
}

dependencies {
    // Core AndroidX & Lifecycle (Target SDK 36 Ready)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose BOM & UI (Compatible with Kotlin 2.0.21)
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Google Play Billing Library 8.x (Required by Google Play Console >= 8.0.0)
    implementation("com.android.billingclient:billing:8.3.0")
    implementation("com.android.billingclient:billing-ktx:8.3.0")

    // Coil Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room Database with SQLite FTS5 (Stable 2.6.1 for Android Studio & KSP)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Jetpack WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Google ML Kit Text Recognition (Bundled on-device OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Google Generative AI Client SDK (Gemini)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Firebase (BOM)
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.7.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Kotlin Coroutines & JSON
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.json:json:20240303")

    // Google Mobile Ads (AdMob) SDK (Target SDK 36 Ready)
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
