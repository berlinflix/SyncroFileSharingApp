# kotlinx.serialization ships its own rules; keep the protocol models' generated serializers explicitly too.
-keepclassmembers class com.syncro.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.syncro.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.syncro.core.**$$serializer { *; }

# JCA providers are looked up reflectively by algorithm name.
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
