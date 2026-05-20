# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class org.json.** { *; }
-keepclassmembers class * {
    @org.json.* <fields>;
}