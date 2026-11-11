# Media3 / ExoPlayer keeps renderer + extractor classes reachable only by reflection.
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }

# The YouTube player bridges Kotlin <-> JS through @JavascriptInterface methods.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
