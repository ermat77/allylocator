# ProGuard rules for AllyLocator plugin obfuscation

# Keep the main plugin class and its public methods
-keep class com.allylocator.Main {
    public <init>(...);
    public void setup();
    public void start();
    public void shutdown();
}

# Keep all classes in the plugin package (you can be more selective if needed)
-keep class com.allylocator.** { *; }

# Keep classes that implement interfaces (needed for Hytale plugin system)
-keep class * implements com.hypixel.hytale.server.core.plugin.JavaPlugin {
    <init>(...);
}

# Keep event listener methods (they're called via reflection)
-keepclassmembers class com.allylocator.listeners.** {
    public void *(com.hypixel.hytale.server.core.event.**);
}

# Keep marker provider implementation
-keep class com.allylocator.systems.PartyFilteredPlayerIconMarkerProvider {
    public static final com.allylocator.systems.PartyFilteredPlayerIconMarkerProvider INSTANCE;
    public void update(...);
}

# Keep PartyService public methods (may be called via reflection)
-keep class com.allylocator.systems.PartyService {
    public static *;
}

# Keep DisableOtherPlayersMarkersSystem public methods
-keep class com.allylocator.systems.DisableOtherPlayersMarkersSystem {
    public <init>(...);
    public void applyFilter(...);
    public void shutdown();
    public static *;
}

# Keep PlayerListener public methods
-keep class com.allylocator.listeners.PlayerListener {
    public <init>(...);
    public void onPlayerConnect(...);
    public void onPlayerReady(...);
}

# Obfuscation options (obfuscation is enabled by default in ProGuard)
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively

# Remove logging (optional - makes code smaller but removes debug info)
# -assumenosideeffects class java.util.logging.Logger {
#     public *** log(...);
# }

# Keep line numbers for better stack traces (optional - remove for more obfuscation)
-keepattributes SourceFile,LineNumberTable

# Keep generic signatures (needed for reflection)
-keepattributes Signature

# Keep exceptions for stack traces
-keepattributes Exceptions

# Remove unused code
-dontpreverify
-repackageclasses ''
-allowaccessmodification
