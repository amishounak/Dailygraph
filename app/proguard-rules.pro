# Keep Room entities
-keep class com.diary.app.data.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Gson / JSON
-keepattributes Signature
-keepattributes *Annotation*
