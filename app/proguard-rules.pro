# NullFlow — ProGuard/R8 rules (release build)

# Keep VPN service (referenced from AndroidManifest)
-keep class com.codezmr.nullflow.vpn.FocusVpnService { *; }

# Keep Tile service
-keep class com.codezmr.nullflow.tile.FocusTileService { *; }

# Keep Room entities (reflection-based)
-keep class com.codezmr.nullflow.data.** { *; }

# Keep MainActivity
-keep class com.codezmr.nullflow.MainActivity { *; }

# Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** {
    @androidx.compose.runtime.Composable <methods>;
}

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Material
-keep class com.google.android.material.** { *; }

# R8 full mode: don't warn about Compose reflection
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
