# SafeNight Radar release rules

# OSMDroid loads tile providers and overlays reflectively.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Play Services location internals.
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
