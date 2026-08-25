import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kryptx.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kryptx.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            FileInputStream(localPropertiesFile).use { load(it) }
        }
    }

    val releaseKeyStore = file("kryptx-release-key.jks")
    val keyStorePassword = System.getenv("KRYPTX_KEYSTORE_PASSWORD")
        ?: localProperties.getProperty("kryptx.keystore.password")
        ?: (project.findProperty("kryptx.keystore.password") as? String)
    val keyPasswordVal = System.getenv("KRYPTX_KEY_PASSWORD")
        ?: localProperties.getProperty("kryptx.key.password")
        ?: (project.findProperty("kryptx.key.password") as? String)
    val keyAliasVal = System.getenv("KRYPTX_KEY_ALIAS")
        ?: localProperties.getProperty("kryptx.key.alias")
        ?: (project.findProperty("kryptx.key.alias") as? String)
        ?: "kryptx-release"

    signingConfigs {
        if (releaseKeyStore.exists() && keyStorePassword != null && keyPasswordVal != null) {
            create("release") {
                storeFile = releaseKeyStore
                storePassword = keyStorePassword
                keyAlias = keyAliasVal
                keyPassword = keyPasswordVal
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnit()
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose UI & Material 3 Expressive
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Security & Cryptography
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.bouncycastle.bcprov)

    // DataStore & Serialization
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Hardware Security Keys (YubiKey)
    implementation("com.yubico.yubikit:android:2.4.0")
    implementation("com.yubico.yubikit:core:2.4.0")
    implementation("com.yubico.yubikit:yubiotp:2.4.0")

    // SQLCipher Database Encryption
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Barcode / QR Code Generation & Scanning (TOTP QR & Wi-Fi QR)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    // Instrumentation Testing
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// ==========================================
// Rust Cryptographic Engine & UniFFI Binding
// ==========================================
tasks.register<Exec>("generateRustBindings") {
    group = "rust"
    description = "Generates Kotlin bindings using Mozilla UniFFI from the kryptx_crypto crate"
    
    val rustProjectDir = file("src/main/rust/kryptx_crypto")
    val outDir = file("src/main/java")
    
    workingDir = rustProjectDir
    // In uniffi 0.32, we use `cargo run --bin uniffi-bindgen` directly.
    commandLine(
        "cargo", "run", "--features=uniffi/cli", "--bin", "uniffi-bindgen", "generate",
        "--library", "../../jniLibs/arm64-v8a/libkryptx_crypto.so", // Requires build step first in real pipeline
        "--language", "kotlin",
        "--out-dir", outDir.absolutePath
    )
}

tasks.register<Exec>("buildRustEngine") {
    group = "rust"
    description = "Compiles the kryptx_crypto core for Android targets"
    
    val rustProjectDir = file("src/main/rust/kryptx_crypto")
    workingDir = rustProjectDir
    
    // In a fully configured Android NDK environment, this would iterate over:
    // aarch64-linux-android, x86_64-linux-android, etc.
    // using `cargo build --target <target> --release`.
    commandLine("cargo", "build", "--release", "-j", "1")
}

// Ensure Rust code compiles before Android builds
tasks.whenTaskAdded {
    if (name == "javaPreCompileDebug" || name == "javaPreCompileRelease") {
        dependsOn("buildRustEngine")
    }
}
