# ProGuard rules for Share-to-Inbox
# Focused on security: obfuscate everything, no logging

# Keep the main entry points
-keep class com.shareinbox.MainActivity { *; }
-keep class com.shareinbox.SetupActivity { *; }
-keep class com.shareinbox.ShareReceiverActivity { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }

# Remove all logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Security: Obfuscate crypto classes heavily
-repackageclasses 'x'
-allowaccessmodification
-optimizationpasses 5
