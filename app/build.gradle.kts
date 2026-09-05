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
        versionCode = 9
        versionName = "2.2.0"

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
        } else if (System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null) {
            // On CI, missing signing credentials is a hard build failure — never silently
            // fall back to the debug keystore in production/release builds.
            error(
                "Release signing credentials are missing on CI.\n" +
                "Set KRYPTX_KEYSTORE_PASSWORD, KRYPTX_KEY_PASSWORD, and KRYPTX_KEY_ALIAS " +
                "as environment variables, or add them to local.properties."
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            // Hard-fail if no release signing config is available. Shipping a release APK
            // signed with the debug keystore is a supply-chain footgun.
            val releaseSigningConfig = signingConfigs.findByName("release")
                ?: error(
                    "Release signing config not found.\n" +
                    "Provide KRYPTX_KEYSTORE_PASSWORD / KRYPTX_KEY_PASSWORD via env vars " +
                    "or local.properties before building a release APK."
                )
            signingConfig = releaseSigningConfig
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

    // Anti-Tampering
    implementation("com.google.android.play:integrity:1.3.0")

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

    // JNA for UniFFI
    implementation("net.java.dev.jna:jna:5.14.0@aar")

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
// GRADUATED TO PRODUCTION: The Rust kryptx_crypto crate (Argon2id + XChaCha20-Poly1305 +
// AES-256-GCM + Linux mlock JNI) and UniFFI Kotlin bindings in kryptx_crypto.kt are active.
// Native binaries are compiled for all 4 Android ABIs (arm64-v8a, armeabi-v7a, x86, x86_64)
// and packaged in src/main/jniLibs.
//
// NativeCryptoEngineWrapper routes cryptographic operations directly through bare-metal Rust
// on Android with automatic fallback to BouncyCastle on host test environments.

val rustSrcDir = file("src/main/rust/kryptx_crypto")
val jniLibsDir = file("src/main/jniLibs")
val generatedKotlinDir = file("src/main/java/com/kryptx/app/core/crypto/generated")

tasks.register<Exec>("generateRustBindings") {
    group = "rust"
    description = "Generates Kotlin bindings using Mozilla UniFFI from the kryptx_crypto crate"
    workingDir = rustSrcDir
    
    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val libExtension = if (isWindows) "dll" else "so"
    val libPrefix = if (isWindows) "" else "lib"
    
    commandLine(
        "cargo", "run", "--features=uniffi/cli", "--bin", "uniffi-bindgen", "generate", 
        "--library", "target/debug/${libPrefix}kryptx_crypto.$libExtension", 
        "--language", "kotlin", "--out-dir", generatedKotlinDir.absolutePath
    )
    
    dependsOn("buildRustEngineDebug")
}

tasks.register<Exec>("buildRustEngineDebug") {
    group = "rust"
    description = "Compiles the kryptx_crypto core for host to generate bindings"
    workingDir = rustSrcDir
    commandLine("cargo", "build")
}

tasks.register<Exec>("buildRustEngine") {
    group = "rust"
    description = "Compiles the kryptx_crypto core for Android targets using cargo-ndk"
    workingDir = rustSrcDir
    
    val localProperties = Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }
    val ndkDir = localProperties.getProperty("ndk.dir") ?: System.getenv("ANDROID_NDK_HOME") ?: ""
    if (ndkDir.isNotEmpty()) {
        environment("ANDROID_NDK_HOME", ndkDir)
    }
    
    // Support Android 15's 16 KB page sizes by forcing the linker to align ELF segments
    environment("RUSTFLAGS", "-C link-arg=-Wl,-z,max-page-size=16384")
    
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86", "-t", "x86_64", 
        "-o", jniLibsDir.absolutePath, "build", "--release"
    )
}

tasks.register<Copy>("copyApk") {
    group = "build"
    description = "Copies the assembled APK directly into the root apk/ directory"
    dependsOn("packageDebug")
    mustRunAfter("packageDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "Kryptx-Security-Debug.apk" }
}

afterEvaluate {
    tasks.named("packageReleaseBundle").configure {
        doLast {
            println("PACKAGE BUNDLE FINISHED. Outputs:")
            outputs.files.forEach { println(" - $it (exists=${it.exists()})") }
        }
    }

    tasks.named("signReleaseBundle").configure {
        doLast {
            println("SIGN BUNDLE FINISHED. Outputs:")
            outputs.files.forEach { println(" - $it (exists=${it.exists()})") }
        }
    }
}
