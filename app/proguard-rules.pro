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
