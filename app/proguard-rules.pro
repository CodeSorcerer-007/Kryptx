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
-keep class com.kryptx.app.core.model.** { *; }

# Security & Biometrics
-keep class androidx.biometric.** { *; }
-keep class * extends androidx.biometric.BiometricPrompt$AuthenticationCallback { *; }
-keep class com.kryptx.app.core.crypto.** { *; }
-keep class com.kryptx.app.core.security.** { *; }

# Autofill Service & Field Detection
-keep class com.kryptx.app.system.autofill.** { *; }
-keep class * extends android.service.autofill.AutofillService { *; }

# Compose Runtime & UI
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ZXing QR Generator & Scanner
-keep class com.google.zxing.** { *; }

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Prevent stripping of cryptographic algorithms
-keepclassmembers class javax.crypto.** { *; }
-keepclassmembers class java.security.** { *; }
