# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.extractor.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rainplayer.**$$serializer { *; }
-keepclassmembers class com.rainplayer.** {
    *** Companion;
}
-keepclasseswithmembers class com.rainplayer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
