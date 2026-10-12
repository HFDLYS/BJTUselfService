import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "team.bjtuss.bjtuselfservice.kmp"
    compileSdk = 36

    defaultConfig {
        applicationId = "team.bjtuss.bjtuselfservice.kmp"
        minSdk = 28
        targetSdk = 35
        versionCode = 32
        versionName = "1.8.4-Beta-SheetFix-Widget"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Non-Android Gradle tasks still configure this project. Only Android builds
    // need the keystore to exist; desktop/iOS packaging must not depend on it.
    val androidBuildRequested = gradle.startParameter.taskNames.any { taskName ->
        taskName.contains(":androidApp:", ignoreCase = true) ||
            taskName.substringAfterLast(':').let { task ->
                task.startsWith("assemble", ignoreCase = true) ||
                    task.startsWith("bundle", ignoreCase = true) ||
                    task.contains("Android", ignoreCase = true)
            }
    }
    // One upload keystore for local and CI, so APKs can overlay-install.
    val uploadKeystore = File(
        providers.environmentVariable("BJTU_ANDROID_KEYSTORE")
            .orElse(
                providers.provider {
                    File(System.getProperty("user.home"), ".android/bjtu-kmp-upload.keystore")
                        .absolutePath
                },
            )
            .get(),
    )
    if (androidBuildRequested) {
        require(uploadKeystore.isFile) {
            "Missing Android upload keystore at ${uploadKeystore.absolutePath}. " +
                "Copy the shared key to that path, or set BJTU_ANDROID_KEYSTORE."
        }
    }

    // 签名口令只允许显式注入（环境变量或 ~/.gradle/gradle.properties），
    // 不内置弱口令默认值：keystore 一旦泄露，默认口令等于直接交出签名能力。
    fun requiredSigningSecret(envName: String, propertyName: String): String {
        val fromEnv = providers.environmentVariable(envName).orNull?.takeIf(String::isNotBlank)
        val fromProperty = providers.gradleProperty(propertyName).orNull?.takeIf(String::isNotBlank)
        return fromEnv ?: fromProperty ?: error(
            "Missing Android signing credential: set $envName in the environment, or " +
                "$propertyName in ~/.gradle/gradle.properties (never in the repo's gradle.properties). " +
                "CI provides these as GitHub Secrets.",
        )
    }

    signingConfigs {
        create("shared") {
            storeFile = uploadKeystore
            if (androidBuildRequested) {
                storePassword = requiredSigningSecret("BJTU_ANDROID_STORE_PASSWORD", "bjtuAndroidStorePassword")
                keyAlias = requiredSigningSecret("BJTU_ANDROID_KEY_ALIAS", "bjtuAndroidKeyAlias")
                keyPassword = requiredSigningSecret("BJTU_ANDROID_KEY_PASSWORD", "bjtuAndroidKeyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    // Align with the original author's v1.7.0 APK: release builds default to
    // arm64 only. A verifier can opt into an emulator ABI without changing
    // the published default, for example BJTU_ANDROID_ABIS=arm64-v8a,x86_64.
    val requestedAbis = providers.environmentVariable("BJTU_ANDROID_ABIS")
        .orNull
        ?.split(',', ';', ' ')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }
        ?: listOf("arm64-v8a")
    splits {
        abi {
            isEnable = true
            reset()
            include(*requestedAbis.toTypedArray())
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
