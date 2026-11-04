# Proguard rules for Aternos AI Console Bridge
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.aternos.bridge.bridge.AternosNativeBridge { *; }
