package com.factioncore;

import com.factioncore.commands.FactionCommand;
import com.factioncore.config.ClaimConfig;
import com.factioncore.config.GeneralConfig;
import com.factioncore.faction.FactionComponent;
import com.factioncore.faction.FactionManager;
import com.factioncore.faction.FactionSystem;
import com.factioncore.systems.marker.FactionMarkerFilterSystem;
import com.factioncore.systems.protection.BreakBlockProtectionSystem;
import com.factioncore.systems.protection.PlaceBlockProtectionSystem;
import com.factioncore.systems.protection.UseBlockProtectionSystem;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.Constants;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import java.util.logging.Level;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;

/**
 * Main plugin class for FactionCore.
 * Provides faction management and ally location features.
 * Players will only see map markers for other players in their faction.
 */
public class Main extends JavaPlugin {

    private static Main pluginInstance;
    private FactionSystem factionSystem;
    private FactionMarkerFilterSystem markerFilterSystem;
    private ComponentType<EntityStore, FactionComponent> factionComponentType;
    private Config<ClaimConfig> claimConfig;
    private Config<GeneralConfig> generalConfig;

    /**
     * Creates a new Main plugin instance.
     * 
     * @param init The plugin initialization data
     */
    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
        pluginInstance = this;
        
        // Register configs (must be called before setup)
        this.claimConfig = withConfig(ClaimConfig.CODEC);
        
        // GeneralConfig should be in universe/FactionCore/ to match other data files
        // Use custom path instead of plugin data directory
        Path generalConfigPath = Constants.UNIVERSE_PATH.resolve("FactionCore");
        // Ensure directory exists
        try {
            Files.createDirectories(generalConfigPath);
        } catch (Exception e) {
            // Directory might already exist, ignore
        }
        this.generalConfig = new Config<>(generalConfigPath, "config", GeneralConfig.CODEC);
    }
    
    /**
     * Gets the plugin instance.
     * 
     * @return The Main plugin instance
     */
    public static Main get() {
        return pluginInstance;
    }
    
    /**
     * Gets the ComponentType for FactionComponent.
     * 
     * @return The ComponentType instance for FactionComponent
     */
    public ComponentType<EntityStore, FactionComponent> getFactionComponentType() {
        return factionComponentType;
    }
    
    /**
     * Gets the ClaimConfig.
     * 
     * @return The Config instance for ClaimConfig
     */
    public Config<ClaimConfig> getClaimConfig() {
        return claimConfig;
    }
    
    /**
     * Gets the GeneralConfig.
     * 
     * @return The Config instance for GeneralConfig
     */
    public Config<GeneralConfig> getGeneralConfig() {
        return generalConfig;
    }

    /**
     * Called during plugin setup phase.
     * Registers components, systems, commands, and events.
     */
    @Override
    protected void setup() {
        super.setup();
        
        try {
            // Check if config file exists, if not, copy default from resources
            Path configPath = Constants.UNIVERSE_PATH.resolve("FactionCore").resolve("config.json");
            File configFile = configPath.toFile();
            
            if (!configFile.exists()) {
                getLogger().at(Level.INFO).log("Config file not found, copying default config from resources...");
                try {
                    // Get default config from resources
                    InputStream defaultConfigStream = getClass().getClassLoader().getResourceAsStream("default_config.json");
                    if (defaultConfigStream != null) {
                        // Ensure directory exists
                        Files.createDirectories(configPath.getParent());
                        // Copy default config to target location
                        Files.copy(defaultConfigStream, configPath, StandardCopyOption.REPLACE_EXISTING);
                        defaultConfigStream.close();
                        getLogger().at(Level.INFO).log("Default config copied from resources to " + configPath.toAbsolutePath());
                    } else {
                        getLogger().at(Level.WARNING).log("Default config (default_config.json) not found in resources, will create with defaults");
                    }
                } catch (Exception e) {
                    getLogger().at(Level.WARNING).withCause(e).log("Failed to copy default config from resources: " + e.getMessage());
                }
            }
            
            // Load and save GeneralConfig FIRST (before any ConfigUtil.debug() calls)
            // This ensures the config file is created if it doesn't exist, and loaded if it does
            try {
                this.generalConfig.load().join();
                this.generalConfig.save().join(); // Save to ensure file exists with defaults if needed
                getLogger().at(Level.INFO).log("GeneralConfig loaded from universe/FactionCore/config.json");
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Failed to load/save GeneralConfig: " + e.getMessage());
            }
            
            // Now we can safely use ConfigUtil.debug() since config is loaded
            ConfigUtil.debug("Main.setup() called - starting plugin setup");
            
            // Check if LuckPerms is available (REQUIRED)
            if (!checkLuckPermsAvailable()) {
                getLogger().at(Level.SEVERE).log("FactionCore requires LuckPerms to be installed!");
                getLogger().at(Level.SEVERE).log("Please install LuckPerms and restart the server.");
                throw new IllegalStateException("FactionCore requires LuckPerms to be installed. Please install LuckPerms and restart the server.");
            }
            getLogger().at(Level.INFO).log("LuckPerms detected - permission system ready");
            ConfigUtil.debug("LuckPerms detected and available");
            
            // Initialize ConfigUtil (needed for debug logging)
            ConfigUtil.getInstance();
            ConfigUtil.debug("ConfigUtil initialized");
            
            // Log GeneralConfig values loaded from universe/FactionCore/config.json
            logGeneralConfigValues();
            
            // Initialize FactionPermissionManager (after LuckPerms validation, before FactionManager)
            com.factioncore.permission.FactionPermissionManager.getInstance();
            getLogger().at(Level.INFO).log("FactionPermissionManager initialized");
            ConfigUtil.debug("FactionPermissionManager initialized");
            
            // Initialize FactionManager (loads factions from disk)
            FactionManager.getInstance();
            getLogger().at(Level.INFO).log("FactionManager initialized");
            ConfigUtil.debug("FactionManager initialized");
            
            // Register FactionComponent
            this.factionComponentType = getEntityStoreRegistry().registerComponent(
                FactionComponent.class, 
                FactionComponent::new
            );
            getLogger().at(Level.INFO).log("FactionComponent registered");
            ConfigUtil.debug("FactionComponent registered");
            
            // Register faction ECS system
            this.factionSystem = new FactionSystem();
            getEntityStoreRegistry().registerSystem((ISystem) factionSystem);
            getLogger().at(Level.INFO).log("FactionSystem registered");
            ConfigUtil.debug("FactionSystem registered");
            
            // Register marker filter ECS system
            this.markerFilterSystem = new FactionMarkerFilterSystem();
            getEntityStoreRegistry().registerSystem((ISystem) markerFilterSystem);
            getLogger().at(Level.INFO).log("FactionMarkerFilterSystem registered");
            ConfigUtil.debug("FactionMarkerFilterSystem registered");
            
            // Register protection ECS systems
            getEntityStoreRegistry().registerSystem((ISystem) new BreakBlockProtectionSystem());
            getLogger().at(Level.INFO).log("BreakBlockProtectionSystem registered");
            ConfigUtil.debug("BreakBlockProtectionSystem registered");
            
            getEntityStoreRegistry().registerSystem((ISystem) new PlaceBlockProtectionSystem());
            getLogger().at(Level.INFO).log("PlaceBlockProtectionSystem registered");
            ConfigUtil.debug("PlaceBlockProtectionSystem registered");
            
            getEntityStoreRegistry().registerSystem((ISystem) new UseBlockProtectionSystem());
            getLogger().at(Level.INFO).log("UseBlockProtectionSystem registered");
            ConfigUtil.debug("UseBlockProtectionSystem registered");
            
            // Register faction command
            AbstractCommand factionCommand = new FactionCommand();
            getCommandRegistry().registerCommand(factionCommand);
            getLogger().at(Level.INFO).log("Faction command registered: /f");
            ConfigUtil.debug("Faction command registered: /f");
            
            // Register event handlers
            registerEvents();
            ConfigUtil.debug("Event handlers registered");
            
            getLogger().at(Level.INFO).log("FactionCore plugin setup complete! Players will only see markers for faction members.");
            ConfigUtil.debug("Main.setup() completed successfully");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error during plugin setup: " + e.getMessage());
            ConfigUtil.debug("Main.setup() failed: " + e.getMessage(), e);
            throw e; // Re-throw to prevent plugin from loading in an invalid state
        }
    }

    /**
     * Called after all plugins have been set up.
     * Suitable for starting background tasks or initializing plugin state.
     */
    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("FactionCore plugin started! Players will only see markers for faction members.");
        ConfigUtil.debug("Main.start() called - plugin started");
    }

    /**
     * Called when the server is shutting down.
     * Provides a place to clean up resources and save data.
     */
    @Override
    protected void shutdown() {
        ConfigUtil.debug("Main.shutdown() called - starting plugin shutdown");
        
        try {
            getLogger().at(Level.INFO).log("FactionCore plugin is shutting down...");
            
            // Save factions before shutdown
            try {
                FactionManager.getInstance().saveFactions();
                getLogger().at(Level.INFO).log("Factions saved to disk");
                ConfigUtil.debug("Factions saved to disk during shutdown");
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("Failed to save factions during shutdown: " + e.getMessage());
                ConfigUtil.debug("Failed to save factions during shutdown: " + e.getMessage(), e);
            }
            
            // Shutdown FactionPermissionManager
            try {
                com.factioncore.permission.FactionPermissionManager.getInstance().shutdown();
                ConfigUtil.debug("FactionPermissionManager shutdown completed");
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("Failed to shutdown FactionPermissionManager: " + e.getMessage());
                ConfigUtil.debug("Failed to shutdown FactionPermissionManager: " + e.getMessage(), e);
            }
            
            // Shutdown chunk saving thread
            try {
                FactionManager.getInstance().shutdown();
                ConfigUtil.debug("FactionManager shutdown completed");
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("Failed to shutdown FactionManager: " + e.getMessage());
                ConfigUtil.debug("Failed to shutdown FactionManager: " + e.getMessage(), e);
            }
            
            getLogger().at(Level.INFO).log("FactionCore plugin shutdown complete!");
            ConfigUtil.debug("Main.shutdown() completed");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error during plugin shutdown: " + e.getMessage());
            ConfigUtil.debug("Main.shutdown() failed: " + e.getMessage(), e);
        }
    }

    /**
     * Logs the GeneralConfig values loaded from the config file.
     * This verifies that debug, verboseDebug, and permissionsMode are correctly read
     * from universe/FactionCore/config.json.
     */
    private void logGeneralConfigValues() {
        try {
            // Get the config instance
            Config<GeneralConfig> configWrapper = this.generalConfig;
            if (configWrapper == null) {
                getLogger().at(Level.WARNING).log("[CONFIG] GeneralConfig wrapper is null - config may not be initialized");
                return;
            }
            
            // Try to get the actual file path using reflection
            Path actualConfigPath = null;
            try {
                Field pathField = Config.class.getDeclaredField("path");
                pathField.setAccessible(true);
                actualConfigPath = (Path) pathField.get(configWrapper);
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("[CONFIG] Could not retrieve config file path via reflection: " + e.getMessage());
            }
            
            // Log the actual config file path
            if (actualConfigPath != null) {
                getLogger().at(Level.INFO).log("[CONFIG] Actual config file path: " + actualConfigPath.toAbsolutePath());
                File configFile = actualConfigPath.toFile();
                getLogger().at(Level.INFO).log("[CONFIG] Config file exists: " + configFile.exists());
                if (configFile.exists()) {
                    getLogger().at(Level.INFO).log("[CONFIG] Config file size: " + configFile.length() + " bytes");
                }
            } else {
                getLogger().at(Level.INFO).log("[CONFIG] Expected config file location: universe/FactionCore/config.json");
            }
            
            // Also check the expected location directly
            try {
                Path expectedPath = Constants.UNIVERSE_PATH.resolve("FactionCore").resolve("config.json");
                File expectedFile = expectedPath.toFile();
                getLogger().at(Level.INFO).log("[CONFIG] Expected path exists: " + expectedFile.exists() + " at " + expectedPath.toAbsolutePath());
                if (expectedFile.exists()) {
                    // Read the file directly to see what's actually in it
                    String fileContent = new String(Files.readAllBytes(expectedPath));
                    getLogger().at(Level.INFO).log("[CONFIG] File content from expected path:\n" + fileContent);
                }
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("[CONFIG] Could not read expected config file: " + e.getMessage());
            }
            
            // Ensure config is loaded before accessing
            GeneralConfig config = null;
            try {
                // If config is null, try to load it
                if (configWrapper.get() == null) {
                    getLogger().at(Level.INFO).log("[CONFIG] Config not loaded yet, loading now...");
                    configWrapper.load().join();
                }
                config = configWrapper.get();
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("[CONFIG] Error loading config: " + e.getMessage());
                return;
            }
            
            if (config == null) {
                getLogger().at(Level.WARNING).log("[CONFIG] GeneralConfig instance is null after load attempt");
                return;
            }
            
            // Log the actual values loaded from the config file
            boolean debug = config.isDebug();
            boolean verboseDebug = config.isVerboseDebug();
            
            getLogger().at(Level.INFO).log("[CONFIG] GeneralConfig values loaded from Config object:");
            getLogger().at(Level.INFO).log("[CONFIG]   - Debug: " + debug);
            getLogger().at(Level.INFO).log("[CONFIG]   - VerboseDebug: " + verboseDebug);
            getLogger().at(Level.INFO).log("[CONFIG]   - MaxFactionNameLength: " + config.getMaxFactionNameLength());
            
            // Also log using debug() so it appears in debug logs too
            ConfigUtil.debug("GeneralConfig loaded - Debug: " + debug + ", VerboseDebug: " + verboseDebug);
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("[CONFIG] Error logging GeneralConfig values: " + e.getMessage());
        }
    }
    
    /**
     * Checks if LuckPerms is available on the server.
     * 
     * @return true if LuckPerms is available, false otherwise
     */
    private boolean checkLuckPermsAvailable() {
        try {
            // Try to get LuckPerms classloader
            ClassLoader classLoader = com.factioncore.util.PermissionUtil.class.getClassLoader();
            
            // Try multiple classloaders
            ClassLoader[] classLoaders = {
                classLoader,
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
            };
            
            // Also try PluginBridgeClassLoader
            try {
                Class<?> pluginManagerClass = Class.forName("com.hypixel.hytale.server.core.plugin.PluginManager");
                java.lang.reflect.Method getMethod = pluginManagerClass.getMethod("get");
                Object pluginManager = getMethod.invoke(null);
                if (pluginManager != null) {
                    java.lang.reflect.Method getBridgeClassLoaderMethod = pluginManagerClass.getMethod("getBridgeClassLoader");
                    ClassLoader bridgeClassLoader = (ClassLoader) getBridgeClassLoaderMethod.invoke(pluginManager);
                    if (bridgeClassLoader != null) {
                        try {
                            Class.forName("net.luckperms.api.LuckPermsProvider", false, bridgeClassLoader);
                            getLogger().at(Level.INFO).log("LuckPerms detected via PluginBridgeClassLoader");
                            return true;
                        } catch (ClassNotFoundException e) {
                            // Continue to other classloaders
                        }
                    }
                }
            } catch (Exception e) {
                // PluginManager not available, continue to other methods
            }
            
            // Try other classloaders
            for (ClassLoader loader : classLoaders) {
                if (loader != null) {
                    try {
                        Class.forName("net.luckperms.api.LuckPermsProvider", false, loader);
                        getLogger().at(Level.INFO).log("LuckPerms detected via " + loader.getClass().getName());
                        return true;
                    } catch (ClassNotFoundException e) {
                        // Continue to next classloader
                    }
                }
            }
            
            // Try default classloader
            try {
                Class.forName("net.luckperms.api.LuckPermsProvider");
                getLogger().at(Level.INFO).log("LuckPerms detected via default classloader");
                return true;
            } catch (ClassNotFoundException e) {
                // Not found
            }
            
            return false;
        } catch (Exception e) {
            getLogger().at(Level.WARNING).log("Error checking for LuckPerms: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Registers all event listeners.
     */
    private void registerEvents() {
        ConfigUtil.debug("registerEvents() called");
        
        try {
            // Register PlayerReadyEvent to apply marker filters immediately when player joins
            getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
                try {
                    ConfigUtil.debug("PlayerReadyEvent received");
                    
                    if (event == null) {
                        ConfigUtil.debug("PlayerReadyEvent: event is null, returning");
                        return;
                    }
                    
                    // Apply marker filter when player is ready
                    // The ECS system will handle ongoing updates, but we apply immediately here
                    com.factioncore.systems.marker.FactionMarkerFilterService.applyFilterToPlayer(event);
                    ConfigUtil.debug("PlayerReadyEvent: applied marker filter to player");
                } catch (Exception e) {
                    getLogger().at(Level.WARNING).log("Error handling PlayerReadyEvent: " + e.getMessage());
                    ConfigUtil.debug("PlayerReadyEvent handler failed: " + e.getMessage(), e);
                }
            });
            ConfigUtil.debug("PlayerReadyEvent handler registered");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error registering events: " + e.getMessage());
            ConfigUtil.debug("registerEvents() failed: " + e.getMessage(), e);
            throw e; // Re-throw to prevent plugin from loading without event handlers
        }
    }
}
