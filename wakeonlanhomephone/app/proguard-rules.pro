# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- Gson models -----------------------------------------------------------
# Field names are the persisted/wire format. Keep them even if minification is
# enabled later; @SerializedName is the authoritative name.
-keep class com.example.wakeonlanhomephone.DeviceProfile { *; }
-keep class com.example.wakeonlanhomephone.MqttDevice { *; }
-keep class com.example.wakeonlanhomephone.GithubRelease { *; }
-keep class com.example.wakeonlanhomephone.GithubAsset { *; }
-keep interface com.example.wakeonlanhomephone.GithubService { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Retrofit --------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
