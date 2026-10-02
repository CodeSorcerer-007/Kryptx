# Kryptx Production ProGuard / R8 Configuration
# Keep core serialization data models
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

# Kotlinx Serialization
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers @kotlinx.serialization.Serializable class com.kryptx.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}

# Security & Biometrics
-keep class * extends androidx.biometric.BiometricPrompt$AuthenticationCallback { *; }
-keep class com.kryptx.app.core.crypto.generated.** { *; }
-keep class com.kryptx.app.core.security.** { *; }

# Mozilla UniFFI & JNA Native Bindings
-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Callback { *; }
-dontwarn java.awt.**

# SQLCipher
-keep class net.zetetic.** { *; }
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.**

# Jetpack Security Crypto & Google Tink
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# CrashDefense Shield
-keep class com.kryptx.app.core.security.CrashDefense** { *; }

# Autofill Service
-keep class * extends android.service.autofill.AutofillService { *; }

# Compose UI
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# CameraX
-dontwarn androidx.camera.**

# Prevent stripping of cryptographic algorithms
-keepclassmembers class javax.crypto.** { *; }
-keepclassmembers class java.security.** { *; }

# Strip all Android Log.* calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Suppress compile-only annotation warnings from Tink, Guava, and YubiKit
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

