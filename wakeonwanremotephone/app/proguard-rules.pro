# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Gson / Retrofit model safety (R8 is currently disabled, rules are future-proofing) ---
-keepattributes Signature
-keepattributes *Annotation*

# Gson uses the field names (mirrored by @SerializedName) to populate models.
-keep class com.example.wakeonwanremotephone.DeviceProfile { *; }
-keep class com.example.wakeonwanremotephone.GithubRelease { *; }
-keep class com.example.wakeonwanremotephone.GithubAsset { *; }

# Retrofit reads method/parameter annotations from the service interface.
-keep interface com.example.wakeonwanremotephone.GithubService { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}