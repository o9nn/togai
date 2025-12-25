# Add project specific ProGuard rules here.

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep native library loader
-keep class org.ninelym.native.** { *; }

# Keep optimization classes
-keep class org.ninelym.optimization.** { *; }

# Keep quantum processor
-keep class org.ninelym.quantum.** { *; }

# Keep multimodal orchestrator
-keep class org.ninelym.evolution.** { *; }

# Keep cognitive engine
-keep class org.ninelym.cognitive.** { *; }

# React Native
-keep,allowobfuscation @interface com.facebook.proguard.annotations.DoNotStrip
-keep,allowobfuscation @interface com.facebook.proguard.annotations.KeepGettersAndSetters
-keep @com.facebook.proguard.annotations.DoNotStrip class *
-keepclassmembers class * {
    @com.facebook.proguard.annotations.DoNotStrip *;
}

# Hermes
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }
