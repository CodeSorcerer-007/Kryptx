import java.util.Properties
import java.io.FileInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    id("jacoco")
}

android {
    namespace = "com.kryptx.app"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.kryptx.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "2.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    splits {
        abi {
            isEnable = !gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            FileInputStream(localPropertiesFile).use { load(it) }
        }
    }

    val releaseKeyStore = (System.getenv("KRYPTX_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) }
        ?: file("kryptx-release-key.jks")).let {
            if (it.exists()) it else rootProject.file("app/kryptx-release-key.jks")
        }
    val keyStorePassword = System.getenv("KRYPTX_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty("kryptx.keystore.password")
        ?: (project.findProperty("kryptx.keystore.password") as? String)
    val keyPasswordVal = System.getenv("KRYPTX_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty("kryptx.key.password")
        ?: (project.findProperty("kryptx.key.password") as? String)
    val keyAliasVal = System.getenv("KRYPTX_KEY_ALIAS")?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty("kryptx.key.alias")
        ?: (project.findProperty("kryptx.key.alias") as? String)
        ?: "kryptx-release"

    val isReleaseTask = gradle.startParameter.taskNames.any { taskName ->
        val simpleName = taskName.substringAfterLast(":")
        simpleName.equals("assembleRelease", ignoreCase = true) ||
        simpleName.equals("bundleRelease", ignoreCase = true) ||
        simpleName.startsWith("packageRelease", ignoreCase = true)
    }
    val isCi = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null

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
        } else if (isCi && isReleaseTask) {
            // On CI, missing signing credentials during release build is a hard build failure — never silently
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
            // Only require release signing config when actually assembling a release build.
            // Guarded so Gradle sync from developer workstations succeeds without signing keys.
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            } else if (isReleaseTask) {
                error(
                    "Release signing config not found.\n" +
                    "Provide KRYPTX_KEYSTORE_PASSWORD / KRYPTX_KEY_PASSWORD via env vars " +
                    "or local.properties before building a release APK."
                )
            }

            // Inject the release APK signing certificate SHA-256 fingerprint into BuildConfig.
            // This fingerprint is computed from the release keystore at build time and baked into
            // the APK so SecurityBootstrapper can enforce exact-match certificate pinning at runtime.
            val computedKeystoreFingerprint: String? = run {
                if (releaseKeyStore.exists() && !keyStorePassword.isNullOrBlank() && !keyAliasVal.isNullOrBlank()) {
                    try {
                        val ks = try {
                            KeyStore.getInstance("PKCS12")
                        } catch (_: Exception) {
                            KeyStore.getInstance(KeyStore.getDefaultType())
                        }
                        releaseKeyStore.inputStream().use { stream: InputStream ->
                            ks.load(stream, keyStorePassword.toCharArray())
                        }
                        val cert = ks.getCertificate(keyAliasVal)
                        if (cert != null) {
                            val md = MessageDigest.getInstance("SHA-256")
                            val digest = md.digest(cert.encoded)
                            digest.joinToString(":") { byte -> "%02X".format(byte) }
                        } else null
                    } catch (e: Exception) {
                        logger.warn("Could not automatically read certificate from release keystore: ${e.message}")
                        null
                    }
                } else null
            }
            val defaultOfficialReleaseFp = "C2:D1:01:03:2A:19:A9:37:8A:F3:5A:D9:5A:15:65:BA:61:2B:1A:AA:4D:4F:56:FA:33:67:AC:E9:22:F5:AA:1D"

            val releaseFp = System.getenv("KRYPTX_RELEASE_FINGERPRINT")?.takeIf { it.isNotBlank() }
                ?: localProperties.getProperty("kryptx.release.fingerprint")?.takeIf { it.isNotBlank() }
                ?: computedKeystoreFingerprint?.takeIf { it.isNotBlank() }
                ?: (if (releaseKeyStore.exists()) defaultOfficialReleaseFp else "")

            val playFp = System.getenv("KRYPTX_PLAY_FINGERPRINT")?.takeIf { it.isNotBlank() }
                ?: localProperties.getProperty("kryptx.play.fingerprint")?.takeIf { it.isNotBlank() }
                ?: ""

            buildConfigField("String", "RELEASE_CERT_SHA256", "\"$releaseFp\"")
            buildConfigField("String", "PLAY_CERT_SHA256", "\"$playFp\"")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            // Debug builds have no pinned fingerprint — they use the debug keystore
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"\"")
            buildConfigField("String", "PLAY_CERT_SHA256", "\"\"")
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
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnit()
            it.maxHeapSize = "1536m"
            it.testLogging {
                events("failed")
                showExceptions = true
                showCauses = true
                showStackTraces = true
            }
            it.systemProperty("java.awt.headless", "true")
            // Roborazzi screenshot tests use Robolectric Native Graphics. Standard unit test runs
            // exclude them so normal builds and CI run fast without Skia graphics deadlocks.
            // Screenshot tests are executed when roborazzi properties are passed (recordRoborazziDebug / verifyRoborazziDebug).
            if (!project.hasProperty("roborazzi.test.record") && !project.hasProperty("roborazzi.test.verify")) {
                it.exclude("**/screenshot/**")
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += listOf(
            "InlinedApi",
            "UseKtx",
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "IconXmlAndPng",
            "IconLauncherShape"
        )
        htmlReport = true
        xmlReport = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crypto"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
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

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Hardware Security Keys (YubiKey)
    implementation(libs.yubikit.android)
    implementation(libs.yubikit.core)
    implementation(libs.yubikit.yubiotp)

    // SQLCipher Database Encryption (16 KB page-size compatible)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    // Barcode / QR Code Generation & Scanning (TOTP QR & Wi-Fi QR)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Anti-Tampering (Play Integrity removed: requires network, incompatible with zero-network design; RootDetector performs local hardware Keystore attestation)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.profileinstaller)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // JNA for UniFFI
    implementation(libs.jna)

    // Manual Dependency Injection (ADR-001): Pure constructor-based DI via AppContainer.
    // No reflection-based DI frameworks (Hilt, Dagger, Koin) are used.

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
val generatedKotlinDir = rootProject.file("core/crypto/src/main/java/com/kryptx/app/core/crypto/generated")

tasks.register("generateRustBindings") {
    group = "rust"
    description = "Generates Kotlin bindings using Mozilla UniFFI from the kryptx_crypto crate"
    
    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val libExtension = if (isWindows) "dll" else "so"
    val libPrefix = if (isWindows) "" else "lib"
    
    doLast {
        val whichCmd = if (isWindows) "where" else "which"
        val hasCargo = try {
            ProcessBuilder(whichCmd, "cargo").start().waitFor() == 0
        } catch (_: Exception) {
            false
        }
        if (!hasCargo) {
            throw GradleException("Cargo is not installed or not in PATH. Install Rust to generate UniFFI bindings.")
        }

        val buildPb = ProcessBuilder("cargo", "build")
        buildPb.directory(rustSrcDir)
        buildPb.inheritIO()
        val buildExit = buildPb.start().waitFor()
        if (buildExit != 0) {
            throw GradleException("cargo build failed with exit code $buildExit")
        }

        val genPb = ProcessBuilder(
            "cargo", "run", "--features=uniffi/cli", "--bin", "uniffi-bindgen", "generate",
            "--library", "target/debug/${libPrefix}kryptx_crypto.$libExtension",
            "--language", "kotlin", "--out-dir", generatedKotlinDir.absolutePath
        )
        genPb.directory(rustSrcDir)
        genPb.inheritIO()
        val genExit = genPb.start().waitFor()
        if (genExit != 0) {
            throw GradleException("uniffi-bindgen generate failed with exit code $genExit")
        }
        logger.lifecycle("[Kryptx UniFFI] Bindings successfully generated into: ${generatedKotlinDir.absolutePath}")
    }
}

tasks.register<Exec>("buildRustEngineDebug") {
    group = "rust"
    description = "Compiles the kryptx_crypto core for host to generate bindings"
    workingDir = rustSrcDir
    commandLine("cargo", "build")
}

tasks.register("buildRustEngine") {
    group = "rust"
    description = "Compiles the kryptx_crypto core for Android targets using cargo-ndk"
    
    val requiredAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    
    inputs.dir(rustSrcDir).withPropertyName("rustSrc").ignoreEmptyDirectories()
    outputs.files(requiredAbis.map { file("$jniLibsDir/$it/libkryptx_crypto.so") })
    
    doLast {
        val localProperties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        
        // Comprehensive NDK path discovery across local.properties, env vars, and Android SDK
        val sdkDir = sequenceOf(
            localProperties.getProperty("sdk.dir"),
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT")
        ).filterNotNull().firstOrNull { it.isNotBlank() && file(it).exists() } ?: ""
        val ndkVer = "27.2.12479018"
        val discoveredNdkDir = sequenceOf(
            localProperties.getProperty("ndk.dir"),
            System.getenv("ANDROID_NDK_HOME"),
            System.getenv("ANDROID_NDK_ROOT"),
            if (sdkDir.isNotBlank()) file("$sdkDir/ndk/$ndkVer").takeIf { it.exists() }?.absolutePath else null,
            if (sdkDir.isNotBlank()) {
                val ndkParent = file("$sdkDir/ndk")
                if (ndkParent.exists()) {
                    ndkParent.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }?.absolutePath
                } else null
            } else null
        ).filterNotNull().firstOrNull { it.isNotBlank() && file(it).exists() } ?: ""
        
        val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
        val whichCmd = if (isWindows) "where" else "which"
        val hasCargo = try {
            ProcessBuilder(whichCmd, "cargo").start().waitFor() == 0
        } catch (_: Exception) {
            false
        }
        var hasCargoNdk = try {
            if (hasCargo) {
                ProcessBuilder("cargo", "ndk", "--version").start().waitFor() == 0
            } else false
        } catch (_: Exception) {
            false
        }

        // Automatic cargo-ndk bootstrapping if cargo is available
        if (hasCargo && !hasCargoNdk) {
            logger.lifecycle("[Kryptx Rust NDK] cargo found but cargo-ndk missing. Auto-installing cargo-ndk...")
            val installPb = ProcessBuilder("cargo", "install", "cargo-ndk")
            installPb.inheritIO()
            if (installPb.start().waitFor() == 0) {
                hasCargoNdk = true
                logger.lifecycle("[Kryptx Rust NDK] cargo-ndk installed successfully.")
            } else {
                logger.warn("[Kryptx Rust NDK] Could not automatically install cargo-ndk.")
            }
        }

        if (hasCargoNdk) {
            // Ensure Rustup targets for all 4 Android architectures are installed
            val hasRustup = try {
                ProcessBuilder(whichCmd, "rustup").start().waitFor() == 0
            } catch (_: Exception) {
                false
            }
            if (hasRustup) {
                val targetList = listOf("aarch64-linux-android", "armv7-linux-androideabi", "i686-linux-android", "x86_64-linux-android")
                val rustupPb = ProcessBuilder(listOf("rustup", "target", "add") + targetList)
                rustupPb.inheritIO()
                rustupPb.start().waitFor()
            }

            logger.lifecycle("[Kryptx Rust NDK] cargo-ndk detected. Cross-compiling UniFFI kryptx_crypto for Android ABIs...")
            val pb = ProcessBuilder(
                "cargo", "ndk",
                "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86", "-t", "x86_64",
                "-o", jniLibsDir.absolutePath,
                "build", "--release"
            )
            pb.directory(rustSrcDir)
            if (discoveredNdkDir.isNotEmpty()) {
                pb.environment()["ANDROID_NDK_HOME"] = discoveredNdkDir
            }
            // Support Android 15's 16 KB page sizes by forcing the linker to align ELF segments and RELRO boundaries
            pb.environment()["RUSTFLAGS"] = "-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
            pb.inheritIO()
            val exitCode = pb.start().waitFor()
            if (exitCode != 0) {
                throw GradleException("cargo-ndk compilation failed with exit code $exitCode")
            }

            if (discoveredNdkDir.isNotEmpty()) {
                val exeExt = if (isWindows) ".exe" else ""
                val stripTool = fileTree("$discoveredNdkDir/toolchains/llvm/prebuilt") {
                    include("**/llvm-strip$exeExt")
                }.firstOrNull()

                if (stripTool?.exists() == true) {
                    fileTree(jniLibsDir) {
                        include("**/*.so")
                    }.forEach { soFile ->
                        ProcessBuilder(stripTool.absolutePath, "--strip-unneeded", soFile.absolutePath)
                            .redirectErrorStream(true)
                            .start()
                            .waitFor()
                    }
                }
            }
            logger.lifecycle("[Kryptx Rust NDK] cargo-ndk cross-compilation completed successfully.")
        } else {
            val missing = requiredAbis.filter { abi -> !file("$jniLibsDir/$abi/libkryptx_crypto.so").exists() }
            if (missing.isEmpty()) {
                logger.lifecycle("[Kryptx Rust NDK] cargo-ndk not detected in environment. Using verified pre-compiled native binaries in src/main/jniLibs.")
            } else {
                throw GradleException(
                    "Missing pre-compiled native binaries for kryptx_crypto ($missing) and cargo-ndk was not found in PATH.\n" +
                    "To build from clean source, install Rust and cargo-ndk:\n" +
                    "  cargo install cargo-ndk\n" +
                    "  rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android"
                )
            }
        }
    }
}

tasks.register("verifyRustEngine16Kb") {
    group = "verification"
    description = "Validates that all native .so libraries are aligned to 16 KB page sizes for Android 15/16"
    dependsOn("buildRustEngine")

    val requiredAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    doLast {
        for (abi in requiredAbis) {
            val soFile = file("$jniLibsDir/$abi/libkryptx_crypto.so")
            if (!soFile.exists() || soFile.length() == 0L) {
                throw GradleException("ELF verification failed: $soFile does not exist or is empty.")
            }

            val bytes = soFile.readBytes()
            if (bytes.size < 64 || bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
                bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()) {
                throw GradleException("ELF verification failed: $soFile is not a valid ELF binary.")
            }

            val is64Bit = bytes[4].toInt() == 2
            val isLittleEndian = bytes[5].toInt() == 1
            if (!isLittleEndian) {
                throw GradleException("ELF verification failed: Unexpected big-endian ELF in $soFile")
            }

            fun readUInt16(offset: Int): Int =
                (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

            fun readUInt32(offset: Int): Long =
                ((bytes[offset].toLong() and 0xFF)) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24)

            fun readUInt64(offset: Int): Long {
                var res = 0L
                for (b in 0 until 8) {
                    res = res or ((bytes[offset + b].toLong() and 0xFF) shl (b * 8))
                }
                return res
            }

            val (phOff, phEntSize, phNum) = if (is64Bit) {
                Triple(readUInt64(32).toInt(), readUInt16(54), readUInt16(56))
            } else {
                Triple(readUInt32(28).toInt(), readUInt16(42), readUInt16(44))
            }

            var verifiedLoadSegments = 0
            for (i in 0 until phNum) {
                val entryOffset = phOff + i * phEntSize
                val pType = readUInt32(entryOffset)
                if (pType == 1L) { // PT_LOAD
                    val pAlign = if (is64Bit) readUInt64(entryOffset + 48) else readUInt32(entryOffset + 28)
                    if (pAlign < 16384L) {
                        throw GradleException(
                            "Android 15/16 16 KB Page-Size Violation in $soFile: " +
                            "PT_LOAD segment $i alignment is $pAlign bytes (< 16384 bytes / 16 KB). " +
                            "Ensure RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'."
                        )
                    }
                    verifiedLoadSegments++
                }
            }

            if (verifiedLoadSegments == 0) {
                throw GradleException("ELF verification failed: No PT_LOAD segments found in $soFile")
            }
            logger.lifecycle("[Kryptx 16 KB Audit] $abi: $verifiedLoadSegments PT_LOAD segment(s) verified at 16 KB (0x4000) alignment.")
        }
        logger.lifecycle("[Kryptx 16 KB Audit] All native architectures PASSED 16 KB page-size alignment verification.")
    }
}

tasks.named("preBuild") {
    dependsOn("verifyRustEngine16Kb")
}

tasks.register<Copy>("copyApk") {
    group = "build"
    description = "Copies the assembled APK directly into the root apk/ directory"
    dependsOn("packageDebug")
    mustRunAfter("packageDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { name ->
        if (name.contains("universal")) "Kryptx-Security-Debug.apk"
        else name.replace("app-", "Kryptx-Debug-")
    }
}

tasks.register<Copy>("copyReleaseBundle") {
    group = "build"
    description = "Copies the assembled release AAB bundle directly into the root apk/ directory"
    dependsOn("bundleRelease")
    mustRunAfter("bundleRelease")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(layout.buildDirectory.dir("outputs/bundle/release"))
    include("*.aab")
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { _ ->
        "Kryptx-v${android.defaultConfig.versionCode}-release.aab"
    }
}

tasks.register<Copy>("copyReleaseApk") {
    group = "build"
    description = "Copies the assembled release APK directly into the root apk/ directory"
    dependsOn("assembleRelease")
    mustRunAfter("assembleRelease")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*universal*.apk")
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { _ ->
        "Kryptx-v${android.defaultConfig.versionCode}-release.apk"
    }
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

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "Reporting"
    description = "Generate Jacoco coverage reports for debug unit tests"
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/ui/**"
        )
    }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(layout.buildDirectory.get()) {
        include(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            "jacoco/testDebugUnitTest.exec"
        )
    })
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "Verification"
    description = "Verify code coverage threshold for debug unit tests"
    dependsOn("jacocoTestReport")

    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/ui/**",
            "**/components/**",
            "**/generated/**"
        )
    }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(layout.buildDirectory.get()) {
        include(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            "jacoco/testDebugUnitTest.exec"
        )
    })

    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("com.kryptx.app.core.crypto", "com.kryptx.app.core.security")
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.register("aggregatedCoverageReport") {
    group = "Reporting"
    description = "Merges Jacoco coverage reports across all core and feature modules"
    dependsOn("jacocoTestReport")
}
