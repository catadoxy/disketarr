# Floppy Android client — R8/ProGuard rules for a Compose + Moshi + Retrofit + Coil app.

# Optional/compile-only annotations that are absent at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Moshi: generated JsonAdapter factories are referenced reflectively by name.
-keep class io.github.catadoxy.floppy.data.model.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# Retrofit: keep service interfaces and their method annotations.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault

# OkHttp
-dontwarn okhttp3.**
-keepnames class okhttp3.internal.* { *; }

# Compose
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# Coil
-keep class coil.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# Keep data classes used by Moshi reflection fallback.
-keepclassmembers class * {
    <init>(...);
}
