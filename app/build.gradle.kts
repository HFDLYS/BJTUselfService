plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "team.bjtuss.bjtuselfservice"
    compileSdk = 34

    defaultConfig {
        applicationId = "team.bjtuss.bjtuselfservice"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "v1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.pytorch:pytorch_android:2.1.0")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("com.github.jeziellago:compose-markdown:0.5.6")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.github.stoyan-vuchev:squircle-shape:2.0.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("io.github.oleksandrbalan:lazytable:1.8.0")
    implementation("androidx.webkit:webkit:1.5.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.0")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("com.squareup.moshi:moshi-kotlin:1.13.0")
    implementation("com.kizitonwose.calendar:compose:2.6.0")
    implementation ("com.google.accompanist:accompanist-swiperefresh:0.30.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2") // 核心库
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2") // JSON 支持
    implementation ("com.airbnb.android:lottie-compose:4.0.0")
}
kapt {
    correctErrorTypes = true
}