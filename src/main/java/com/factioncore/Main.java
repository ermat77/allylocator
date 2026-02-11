package com.factioncore;

import com.factioncore.commands.FactionCommand;
import com.factioncore.config.ClaimConfig;
import com.factioncore.config.GeneralConfig;
import com.factioncore.faction.FactionComponent;
import com.factioncore.faction.FactionManager;
import com.factioncore.faction.FactionSystem;
import com.factioncore.map.FactionWorldMapProvider;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.factioncore.systems.marker.FactionMarkerFilterSystem;
import com.factioncore.systems.protection.BreakBlockProtectionSystem;
import com.factioncore.systems.protection.DamageBlockProtectionSystem;
import com.factioncore.systems.protection.GlobalBreakBlockProtectionSystem;
import com.factioncore.systems.protection.GlobalDamageBlockProtectionSystem;
import com.factioncore.systems.protection.PlaceBlockProtectionSystem;
import com.factioncore.systems.protection.UseBlockProtectionSystem;
import com.factioncore.systems.interaction.BlockUseOpenUISystem;
import com.factioncore.systems.tick.ChunkBordersTickingSystem;
import com.factioncore.systems.tick.TitleTickingSystem;
import com.factioncore.systems.tick.WorldMapUpdateTickingSystem;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.PlayerNameCache;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import java.util.logging.Level;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.net.URI;
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
    private final FactionWorldMapProvider worldMapProvider = new FactionWorldMapProvider();
    /** Plugin data directory: same folder as the plugin JAR, then FactionCore (e.g. mods/FactionCore). */
    private final Path pluginDataPath;
    /** Cached DelayTp plugin instance (optional dependency). Null if DelayTp not present. */
    private Object delayTpApi;
    /** True after we have tried to resolve DelayTp on first use (lazy resolution). */
    private boolean delayTpLazyResolutionDone;

    /**
     * Creates a new Main plugin instance.
     * 
     * @param init The plugin initialization data
     */
    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
        pluginInstance = this;
        this.pluginDataPath = computePluginDataPath();

        // Configs in plugin data folder (e.g. mods/FactionCore/claim_config.json, config.json)
        try {
            Files.createDirectories(pluginDataPath);
        } catch (Exception e) {
            // Directory might already exist, ignore
        }
        this.claimConfig = new Config<>(pluginDataPath, "claim_config", ClaimConfig.CODEC);
        this.generalConfig = new Config<>(pluginDataPath, "config", GeneralConfig.CODEC);
    }

    /**
     * Resolves the plugin data path: directory containing the plugin JAR, then "FactionCore".
     * E.g. if the plugin is at mods/plugin.jar, returns mods/FactionCore.
     */
    private static Path computePluginDataPath() {
        try {
            URI location = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codeSourcePath = Paths.get(location);
            Path baseDir = codeSourcePath.getFileName() != null && codeSourcePath.toString().toLowerCase().endsWith(".jar")
                    ? codeSourcePath.getParent()
                    : Paths.get(System.getProperty("user.dir"));
            return baseDir != null ? baseDir.resolve("FactionCore") : Paths.get("FactionCore");
        } catch (Exception e) {
            return Paths.get(System.getProperty("user.dir", ".")).resolve("FactionCore");
        }
    }

    /**
     * Gets the plugin data directory (e.g. mods/FactionCore). Config and database are stored here.
     */
    public Path getPluginDataPath() {
        return pluginDataPath;
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
     * Gets the DelayTp plugin instance (optional dependency). Resolves lazily on first use so DelayTp
     * is found even if it loads after FactionCore. Null if DelayTp not present.
     * Use {@link com.factioncore.util.DelayTpHelper#requestDelayedTeleport} to call the API via reflection.
     */
    public Object getDelayTpApi() {
        if (delayTpApi == null && !delayTpLazyResolutionDone) {
            delayTpLazyResolutionDone = true;
            resolveDelayTp();
        }
        return delayTpApi;
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
            Path configPath = getPluginDataPath().resolve("config.json");
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
                getLogger().at(Level.INFO).log("GeneralConfig loaded from " + getPluginDataPath().resolve("config.json"));
            } catch (Exception e) {
                Throwable cause = e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;
                if (cause instanceof java.io.IOException) {
                    getLogger().at(Level.WARNING).withCause(e).log("Config file is invalid or corrupted (e.g. bad JSON). Replacing with default config.");
                    try {
                        Path recoveryPath = getPluginDataPath().resolve("config.json");
                        if (recoveryPath.toFile().exists()) {
                            Path backupPath = getPluginDataPath().resolve("config.json.bak");
                            Files.copy(recoveryPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                            getLogger().at(Level.INFO).log("Backed up bad config to " + backupPath.getFileName());
                        }
                        try (InputStream defaultStream = getClass().getClassLoader().getResourceAsStream("default_config.json")) {
                            if (defaultStream != null) {
                                Files.createDirectories(recoveryPath.getParent());
                                Files.copy(defaultStream, recoveryPath, StandardCopyOption.REPLACE_EXISTING);
                                this.generalConfig.load().join();
                                this.generalConfig.save().join();
                                getLogger().at(Level.INFO).log("GeneralConfig loaded from default config.");
                            } else {
                                throw new IllegalStateException("Default config not found in resources.", e);
                            }
                        }
                    } catch (Exception recoverEx) {
                        getLogger().at(Level.SEVERE).withCause(recoverEx).log("Failed to recover config. Please fix or remove config.json in FactionCore folder.");
                        throw new IllegalStateException("Failed to load or recover GeneralConfig.", recoverEx);
                    }
                } else {
                    getLogger().at(Level.WARNING).withCause(e).log("Failed to load/save GeneralConfig: " + e.getMessage());
                }
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
            
            // Log GeneralConfig values loaded from plugin data path
            logGeneralConfigValues();
            
            // Initialize FactionPermissionManager (after LuckPerms validation, before FactionManager)
            com.factioncore.permission.FactionPermissionManager.getInstance();
            getLogger().at(Level.INFO).log("FactionPermissionManager initialized");
            ConfigUtil.debug("FactionPermissionManager initialized");

            // Resolve optional DelayTp API (same pattern as LuckPerms: PluginManager + bridge classloader)
            resolveDelayTp();
            
            // Initialize FactionManager first (loads factions, allies, etc. from database)
            // so DB is opened and populated before we write claim_config.json in the same directory
            FactionManager.getInstance();
            getLogger().at(Level.INFO).log("FactionManager initialized");
            ConfigUtil.debug("FactionManager initialized");
            
            // Load ClaimConfig after FactionManager (creates claim_config.json with defaults if missing)
            try {
                this.claimConfig.load().join();
                this.claimConfig.save().join();
                getLogger().at(Level.INFO).log("ClaimConfig loaded from " + getPluginDataPath().resolve("claim_config.json"));
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Failed to load/save ClaimConfig: " + e.getMessage());
            }
            
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
            
            // Register protection ECS systems (order matches SimpleClaims: break then damage so both events are cancelled)
            getEntityStoreRegistry().registerSystem((ISystem) new BreakBlockProtectionSystem());
            getLogger().at(Level.INFO).log("BreakBlockProtectionSystem registered");
            ConfigUtil.debug("BreakBlockProtectionSystem registered");

            getEntityStoreRegistry().registerSystem((ISystem) new DamageBlockProtectionSystem());
            getLogger().at(Level.INFO).log("DamageBlockProtectionSystem registered");
            ConfigUtil.debug("DamageBlockProtectionSystem registered");
            
            getEntityStoreRegistry().registerSystem((ISystem) new PlaceBlockProtectionSystem());
            getLogger().at(Level.INFO).log("PlaceBlockProtectionSystem registered");
            ConfigUtil.debug("PlaceBlockProtectionSystem registered");
            
            getEntityStoreRegistry().registerSystem((ISystem) new UseBlockProtectionSystem());
            getLogger().at(Level.INFO).log("UseBlockProtectionSystem registered");
            ConfigUtil.debug("UseBlockProtectionSystem registered");

            getEntityStoreRegistry().registerSystem((ISystem) new BlockUseOpenUISystem());
            getLogger().at(Level.INFO).log("BlockUseOpenUISystem registered (F key on block opens UI)");
            ConfigUtil.debug("BlockUseOpenUISystem registered");

            // Global (world-level) break/damage protection: explosions and non-player breaks (SimpleClaims-style)
            getEntityStoreRegistry().registerSystem((ISystem) new GlobalDamageBlockProtectionSystem());
            getEntityStoreRegistry().registerSystem((ISystem) new GlobalBreakBlockProtectionSystem());

            // Tick systems (SimpleClaims-style: title, chunk border particles)
            getEntityStoreRegistry().registerSystem((ISystem) new TitleTickingSystem());
            getEntityStoreRegistry().registerSystem((ISystem) new ChunkBordersTickingSystem());

            getChunkStoreRegistry().registerSystem(new WorldMapUpdateTickingSystem());
            getLogger().at(Level.INFO).log("WorldMapUpdateTickingSystem registered");
            ConfigUtil.debug("WorldMapUpdateTickingSystem registered");
            
            // Register faction command
            AbstractCommand factionCommand = new FactionCommand();
            getCommandRegistry().registerCommand(factionCommand);
            getLogger().at(Level.INFO).log("Faction command registered: /f");
            ConfigUtil.debug("Faction command registered: /f");

            // /spawn is registered in start() so it runs after built-in TeleportPlugin.setup(),
            // ensuring our delayed /spawn overwrites the native one in CommandManager's map.

            // Register event handlers
            registerEvents();
            ConfigUtil.debug("Event handlers registered");
            
            // Register world map provider codec
            com.hypixel.hytale.server.core.universe.world.worldmap.provider.IWorldMapProvider.CODEC.register(
                FactionWorldMapProvider.ID, 
                FactionWorldMapProvider.class, 
                FactionWorldMapProvider.CODEC
            );
            getLogger().at(Level.INFO).log("FactionWorldMapProvider registered");
            ConfigUtil.debug("FactionWorldMapProvider registered");
            
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

        // Completely override native /spawn: remove the built-in command (OP-gated) then register ours.
        CommandManager cmdManager = CommandManager.get();
        java.util.Map<String, AbstractCommand> reg = cmdManager.getCommandRegistration();
        AbstractCommand removed = reg.remove("spawn");
        if (removed != null) {
            getLogger().at(Level.INFO).log("FactionCore: removed native /spawn command (was: %s)", removed.getClass().getName());
            ConfigUtil.debug("FactionCore: removed native /spawn from CommandManager");
        } else {
            ConfigUtil.debug("FactionCore: no native /spawn in CommandManager (already removed or not yet registered)");
        }
        getCommandRegistry().registerCommand(new com.factioncore.commands.SpawnCommand());
        getLogger().at(Level.INFO).log("Spawn command registered: /spawn (delayed, permission: factioncore.player.spawn)");
        ConfigUtil.debug("Spawn command registered: /spawn (delayed, permission: factioncore.player.spawn)");

        // Load player UUID -> displayName from universe/players JSON (fallback for offline name lookup)
        try {
            Path playersDir = Constants.UNIVERSE_PATH.resolve("players");
            int loaded = PlayerNameCache.loadFrom(playersDir);
            getLogger().at(Level.INFO).log("[FactionCore] Player name cache: loaded " + loaded + " entries from universe/players");
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("[FactionCore] Failed to load player name cache: " + e.getMessage());
        }

        // Register world map provider for existing worlds
        // Worlds load asynchronously, so we need to wait for them to be ready
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                getLogger().at(Level.WARNING).log("[FactionCore] Universe is null, cannot register world map providers");
                return;
            }
            
            // Wait for worlds to finish loading asynchronously
            universe.getUniverseReady().thenRun(() -> {
                try {
                    getLogger().at(Level.INFO).log("[FactionCore] Worlds are ready, registering world map providers for " + universe.getWorlds().size() + " worlds");
                    
                    for (World world : universe.getWorlds().values()) {
                        getLogger().at(Level.INFO).log("[FactionCore] Processing world: " + (world != null ? world.getName() : "null"));
                        tryRegisterWorldMapForWorld(world);
                    }
                } catch (Exception e) {
                    getLogger().at(Level.WARNING).withCause(e).log("[FactionCore] Failed to register world map providers after worlds loaded: " + e.getMessage());
                    ConfigUtil.debug("Failed to register world map providers after worlds loaded: " + e.getMessage(), e);
                }
            }).exceptionally(throwable -> {
                getLogger().at(Level.WARNING).withCause(throwable).log("[FactionCore] Error waiting for worlds to load: " + throwable.getMessage());
                ConfigUtil.debug("Error waiting for worlds to load: " + throwable.getMessage(), throwable);
                return null;
            });
            
            // Also try immediately in case worlds are already loaded
            if (universe.getWorlds().size() > 0) {
                getLogger().at(Level.INFO).log("[FactionCore] Worlds already available, registering immediately for " + universe.getWorlds().size() + " worlds");
                registerWorldMapProviders(universe);
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("[FactionCore] Failed to set up world map provider registration: " + e.getMessage());
            ConfigUtil.debug("Failed to set up world map provider registration: " + e.getMessage(), e);
        }
    }
    
    /**
     * Tries to set the faction world map generator for the given world.
     * Logs and returns false if WorldMapManager is null or registration fails.
     */
    private boolean tryRegisterWorldMapForWorld(World world) {
        if (world == null) return false;
        String worldName = world.getName();
        if (world.getWorldMapManager() == null) {
            getLogger().at(Level.WARNING).log("[FactionCore] WorldMapManager is null for world: " + worldName);
            return false;
        }
        try {
            getLogger().at(Level.INFO).log("[FactionCore] Setting generator for world: " + worldName);
            IWorldMap generator = worldMapProvider.getGenerator(world);
            if (generator != null) {
                world.getWorldMapManager().setGenerator(generator);
                getLogger().at(Level.INFO).log("[FactionCore] World map provider successfully registered for world: " + worldName);
                ConfigUtil.debug("World map provider registered for world: " + worldName);
                return true;
            }
            getLogger().at(Level.WARNING).log("[FactionCore] Generator is null for world: " + worldName);
            return false;
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("[FactionCore] Failed to register world map provider for world " + worldName + ": " + e.getMessage());
            ConfigUtil.debug("Failed to register world map provider for world " + worldName + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Helper method to register world map providers for all worlds in the universe.
     */
    private void registerWorldMapProviders(Universe universe) {
        for (World world : universe.getWorlds().values()) {
            getLogger().at(Level.INFO).log("[FactionCore] Processing world: " + (world != null ? world.getName() : "null"));
            tryRegisterWorldMapForWorld(world);
        }
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
     * from the plugin data path (e.g. mods/FactionCore/config.json).
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
                getLogger().at(Level.INFO).log("[CONFIG] Expected config file location: " + getPluginDataPath().resolve("config.json"));
            }
            
            // Also check the expected location directly
            try {
                Path expectedPath = getPluginDataPath().resolve("config.json");
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
     * Resolves optional DelayTp plugin via classloading (same pattern as LuckPerms).
     * Finds a ClassLoader that can load {@code com.delaytp.Main}, then calls {@code Main.get()}
     * to obtain the plugin instance. Does not depend on PluginManager or plugin name.
     * Caches the instance in delayTpApi for use in HomeFactionCommand.
     */
    private void resolveDelayTp() {
        this.delayTpApi = null;
        try {
            ConfigUtil.debug("resolveDelayTp: resolving DelayTp via classloader (Main.get())");
            ClassLoader loader = getDelayTpClassLoader();
            if (loader == null) {
                ConfigUtil.debug("resolveDelayTp: no classloader can load com.delaytp.Main");
                getLogger().at(Level.INFO).log("DelayTp not found – /f home will teleport immediately");
                return;
            }
            Class<?> mainClass = Class.forName("com.delaytp.Main", false, loader);
            java.lang.reflect.Method getMethod = mainClass.getMethod("get");
            Object instance = getMethod.invoke(null);
            if (instance != null) {
                this.delayTpApi = instance;
                getLogger().at(Level.INFO).log("DelayTp detected – delayed teleport available for /f home");
                ConfigUtil.debug("resolveDelayTp: DelayTp instance obtained via Main.get()");
            } else {
                ConfigUtil.debug("resolveDelayTp: Main.get() returned null (DelayTp not initialized yet?)");
                getLogger().at(Level.INFO).log("DelayTp not found – /f home will teleport immediately");
            }
        } catch (Exception e) {
            ConfigUtil.debug("resolveDelayTp: exception resolving DelayTp", e);
            getLogger().at(Level.FINE).log("DelayTp not available: " + e.getMessage());
            getLogger().at(Level.INFO).log("DelayTp not found – /f home will teleport immediately");
        }
    }

    /**
     * Gets a ClassLoader that can load com.delaytp.Main, or null if none.
     * Tries PluginBridgeClassLoader first, then context, system, and default (same order as LuckPerms).
     */
    private static ClassLoader getDelayTpClassLoader() {
        try {
            // PluginBridgeClassLoader (most reliable for plugin-to-plugin access)
            try {
                Class<?> pluginManagerClass = Class.forName("com.hypixel.hytale.server.core.plugin.PluginManager");
                java.lang.reflect.Method getMethod = pluginManagerClass.getMethod("get");
                Object pluginManager = getMethod.invoke(null);
                if (pluginManager != null) {
                    java.lang.reflect.Method getBridgeClassLoaderMethod = pluginManagerClass.getMethod("getBridgeClassLoader");
                    ClassLoader bridge = (ClassLoader) getBridgeClassLoaderMethod.invoke(pluginManager);
                    if (bridge != null) {
                        Class.forName("com.delaytp.Main", false, bridge);
                        return bridge;
                    }
                }
            } catch (ClassNotFoundException e) {
                // Continue
            } catch (Exception e) {
                // PluginManager not available or reflection failed
            }

            // Context classloader
            ClassLoader ctx = Thread.currentThread().getContextClassLoader();
            if (ctx != null) {
                try {
                    Class.forName("com.delaytp.Main", false, ctx);
                    return ctx;
                } catch (ClassNotFoundException e) {
                    // Continue
                }
            }

            // System classloader
            ClassLoader sys = ClassLoader.getSystemClassLoader();
            if (sys != null) {
                try {
                    Class.forName("com.delaytp.Main", false, sys);
                    return sys;
                } catch (ClassNotFoundException e) {
                    // Continue
                }
            }

            // Default (current class's classloader)
            try {
                Class.forName("com.delaytp.Main");
                return Main.class.getClassLoader();
            } catch (ClassNotFoundException e) {
                // Not found
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
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
            
            // Register AddWorldEvent to register world map provider for new worlds
            getEventRegistry().registerGlobal(AddWorldEvent.class, event -> {
                try {
                    if (event != null && event.getWorld() != null) {
                        World world = event.getWorld();
                        getLogger().at(Level.INFO).log("[FactionCore] AddWorldEvent received for world: " + world.getName());
                        tryRegisterWorldMapForWorld(world);
                    } else {
                        getLogger().at(Level.WARNING).log("[FactionCore] AddWorldEvent has null event or world");
                    }
                } catch (Exception e) {
                    getLogger().at(Level.WARNING).withCause(e).log("[FactionCore] Error handling AddWorldEvent: " + e.getMessage());
                    ConfigUtil.debug("AddWorldEvent handler failed: " + e.getMessage(), e);
                }
            });
            ConfigUtil.debug("AddWorldEvent handler registered");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error registering events: " + e.getMessage());
            ConfigUtil.debug("registerEvents() failed: " + e.getMessage(), e);
            throw e; // Re-throw to prevent plugin from loading without event handlers
        }
    }
}
