# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ru.ubt.familyshield.**$$serializer { *; }
-keepclassmembers class ru.ubt.familyshield.** { *** Companion; }
-keepclasseswithmembers class ru.ubt.familyshield.** { kotlinx.serialization.KSerializer serializer(...); }
# Models are serialized by name.
-keep @kotlinx.serialization.Serializable class ru.ubt.familyshield.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- RuStore SDK (reflection-based init providers, services, AIDL) ---
-keep class ru.rustore.sdk.** { *; }
-keep class com.vk.push.** { *; }
-dontwarn ru.rustore.sdk.**
-dontwarn com.vk.push.**

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- App services referenced from the manifest ---
-keep class ru.ubt.familyshield.ShieldApp
-keep class ru.ubt.familyshield.**.service.** { *; }
-keep class ru.ubt.familyshield.ui.MainActivity
-keep class ru.ubt.familyshield.ui.QrScanActivity
-keep class ru.ubt.familyshield.protect.ui.** { *; }

# Keep enum names: policies and alerts are serialized by enum name.
-keepclassmembers enum ru.ubt.familyshield.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
