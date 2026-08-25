# kotlinx.serialization keeps its generated serializers via companion objects;
# without these the release build fails to parse the API payloads at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.cmac.opscommand.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.cmac.opscommand.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cmac.opscommand.data.**$$serializer { *; }

# OkHttp / Okio ship with these harmless optional references.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**
