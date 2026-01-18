# ProGuard rules for RealFaction plugin obfuscation

# Keep the main plugin class and its public methods
-keep class com.realfaction.Main {
    public <init>(...);
    public void setup();
    public void start();
    public void shutdown();
}

# Keep all classes in the plugin package (you can be more selective if needed)
-keep class com.realfaction.** { *; }

# Keep classes that implement interfaces (needed for Hytale plugin system)
-keep class * implements com.hypixel.hytale.server.core.plugin.JavaPlugin {
    <init>(...);
}

# Keep event listener methods (they're called via reflection)
-keepclassmembers class com.realfaction.** {
    public void *(com.hypixel.hytale.server.core.event.**);
}

# Keep ECS systems (they're registered and called by Hytale)
-keep class com.realfaction.faction.FactionSystem {
    public <init>(...);
    public void tick(...);
}

-keep class com.realfaction.systems.marker.FactionMarkerFilterSystem {
    public <init>(...);
    public void tick(...);
}

# Keep service classes that may be called via reflection
-keep class com.realfaction.systems.marker.FactionMarkerFilterService {
    public static *;
}

# Keep manager classes
-keep class com.realfaction.faction.FactionManager {
    public static *;
    public <init>(...);
}

# Keep command classes
-keep class com.realfaction.commands.** {
    public <init>(...);
    public * execute*(...);
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

# Keep stackmap frames (required for Java 7+ bytecode verification)
-keepattributes StackMapTable

# Remove unused code
# Note: -dontpreverify is removed - ProGuard will add stackmap frames for modern JVMs
-repackageclasses ''
-allowaccessmodification
