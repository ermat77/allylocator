package com.allylocator.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.task.TaskRegistry;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * System that filters player markers on the world map to show only party/faction members.
 * 
 * Uses the "replace provider" approach (like MarkerModule):
 * - Replaces default player marker providers with PartyFilteredPlayerIconMarkerProvider
 * - The custom provider only sends markers for players in the same party/faction
 * - Hides the player's own marker and all non-party/faction players
 * 
 * Approach:
 * 1. Replace player marker providers with PartyFilteredPlayerIconMarkerProvider
 *    - This provider filters to show only party/faction members (not self)
 * 2. Disable teleporting to markers
 * 3. Periodically check and replace providers (in case they get re-added)
 * 
 * Provider keys to replace: Based on WorldMapManager registration:
 * - "playerIcons" (PlayerIconMarkerProvider.INSTANCE)
 * - "playerMarkers" (PlayerMarkersProvider.INSTANCE)
 * - "players" (common variation)
 * 
 */
public class DisableOtherPlayersMarkersSystem {

    private final HytaleLogger logger;
    private final TaskRegistry taskRegistry;
    
    // Provider keys to replace with filtered provider (player markers)
    // From decompiled HytaleServer.jar WorldMapManager.class:
    // - "playerIcons" (PlayerIconMarkerProvider.INSTANCE)
    // - "playerMarkers" (PlayerMarkersProvider.INSTANCE)
    // Also includes "players" as used by working plugin
    private static final String[] PLAYER_PROVIDER_KEYS = {
        "playerMarkers",      // PlayerMarkersProvider.INSTANCE (from WorldMapManager)
        "playerIcons",        // PlayerIconMarkerProvider.INSTANCE (from WorldMapManager)
        "players"             // Used by working plugin
    };
    
    // Track which worlds we're monitoring (using WeakHashMap like original plugin)
    private final Set<World> monitoredWorlds = Collections.newSetFromMap(new WeakHashMap<>());
    
    // Store reference to scheduled task for potential manual cancellation
    @SuppressWarnings("unchecked")
    private final ScheduledFuture<Void> scheduledTask;

    /**
     * Creates a new DisableOtherPlayersMarkersSystem instance.
     * 
     * @param logger The plugin logger instance
     * @param taskRegistry The task registry for automatic cleanup
     * @throws IllegalArgumentException if logger or taskRegistry is null
     */
    public DisableOtherPlayersMarkersSystem(HytaleLogger logger, TaskRegistry taskRegistry) {
        if (logger == null) {
            throw new IllegalArgumentException("Logger cannot be null");
        }
        if (taskRegistry == null) {
            throw new IllegalArgumentException("TaskRegistry cannot be null");
        }
        
        this.logger = logger;
        this.taskRegistry = taskRegistry;
        
        logger.at(Level.INFO).log("DisableOtherPlayersMarkersSystem initialized! Hiding non-party/faction players from the map...");
        
        // Periodically disable providers (in case they get re-added)
        // Matches original plugin: scheduleAtFixedRate with 5L initial delay, 30L interval
        // Register with TaskRegistry for automatic cleanup on plugin disable
        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> task = (ScheduledFuture<Void>) HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                // Replace providers for all monitored worlds (in case they get re-added)
                for (World world : this.monitoredWorlds) {
                    if (world != null) {
                        this.replacePlayerMarkerProviders(world);
                    }
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Error in marker provider poller: " + e.getMessage());
                logger.at(Level.FINE).log("Stack trace: ", e);
            }
        }, 5L, 30L, TimeUnit.SECONDS);
        
        this.scheduledTask = task;
        taskRegistry.registerTask(task);
    }
    
    /**
     * Applies filter to a specific player to hide all markers including their own.
     * 
     * @param player The player to apply the filter to
     * @param world The world the player is in
     */
    public void applyFilter(Player player, World world) {
        if (player == null) {
            logger.at(Level.WARNING).log("Cannot apply filter: player is null");
            return;
        }
        
        if (world != null) {
            this.monitoredWorlds.add(world);
        }
        
        // Replace providers with our filtered provider (shows only party/faction members)
        this.replacePlayerMarkerProviders(world);
        
        try {
            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker == null) {
                return;
            }
            
            if (world != null) {
                tracker.setAllowTeleportToMarkers(world, false);
            }
        } catch (Exception e) {
            logger.at(Level.SEVERE).log("Error applying filter: " + e.getMessage());
            logger.at(Level.FINE).log("Stack trace: ", e);
        }
    }
    
    /**
     * Extracts player UUID from a player marker ID.
     * Player marker IDs follow the format: "Player-{UUID}"
     * 
     * @param markerId The marker ID (e.g., "Player-825d797e-b50c-40cf-a138-4c071b13fa8b")
     * @return The player UUID, or null if the marker ID doesn't match the expected format
     */
    public static UUID extractPlayerUuidFromMarkerId(String markerId) {
        if (markerId == null || !markerId.startsWith("Player-")) {
            return null;
        }
        try {
            String uuidString = markerId.substring(7); // Remove "Player-" prefix
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            return null; // Invalid UUID format
        }
    }
    
    /**
     * Gets a PlayerRef from a player UUID.
     * 
     * @param playerUuid The player's UUID
     * @return The PlayerRef, or null if the player is not found
     */
    public static PlayerRef getPlayerRefFromUuid(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        return Universe.get().getPlayer(playerUuid);
    }
    
    /**
     * Logs all player markers found in the world, including player UUIDs and names.
     * 
     * @param world The world to scan for player markers
     */
    private void logAllPlayerMarkers(World world) {
        try {
            if (world == null) {
                return;
            }
            
            logger.at(Level.FINE).log("=== Scanning for player markers in world: " + world.getName() + " ===");
            
            int markerCount = 0;
            
            // Iterate over all players in the world
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                if (playerRef == null) {
                    continue;
                }
                
                UUID playerUuid = playerRef.getUuid();
                String playerName = playerRef.getUsername();
                
                if (playerUuid != null) {
                    markerCount++;
                    logger.at(Level.FINE).log(String.format(
                        "Found player marker [%d]: UUID=%s, Name=%s", 
                        markerCount, 
                        playerUuid.toString(), 
                        playerName != null ? playerName : "Unknown"
                    ));
                }
            }
            
            logger.at(Level.FINE).log("=== Total player markers found: " + markerCount + " ===");
            
            // Also check WorldMapManager for any stored marker references
            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager != null) {
                // Try to get points of interest which might include player markers
                try {
                    Map<String, ?> pointsOfInterest = mapManager.getPointsOfInterest();
                    if (pointsOfInterest != null && !pointsOfInterest.isEmpty()) {
                        logger.at(Level.FINE).log("WorldMapManager has " + pointsOfInterest.size() + " points of interest");
                        
                        // Check for player markers in POI map
                        for (Map.Entry<String, ?> entry : pointsOfInterest.entrySet()) {
                            String markerId = entry.getKey();
                            UUID extractedUuid = extractPlayerUuidFromMarkerId(markerId);
                            if (extractedUuid != null) {
                                PlayerRef playerRef = getPlayerRefFromUuid(extractedUuid);
                                String playerName = playerRef != null ? playerRef.getUsername() : "Unknown";
                                logger.at(Level.FINE).log(String.format(
                                    "Found player marker in POI: MarkerID=%s, UUID=%s, Name=%s",
                                    markerId,
                                    extractedUuid.toString(),
                                    playerName
                                ));
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.at(Level.FINE).log("Could not access points of interest: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.at(Level.WARNING).log("Error logging player markers: " + e.getMessage());
            logger.at(Level.FINE).log("Stack trace: ", e);
        }
    }
    
    /**
     * Replaces player marker providers with PartyFilteredPlayerIconMarkerProvider.
     * This uses the "replace provider" approach - the custom provider only sends
     * markers for players in the same party/faction (excluding self).
     * 
     * @param world The world to replace providers in
     */
    private void replacePlayerMarkerProviders(World world) {
        try {
            if (world == null) {
                return;
            }
            
            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) {
                return;
            }
            
            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) {
                return;
            }
            
            // Replace player marker providers with our filtered provider (using replace approach)
            // This prevents default providers from sending markers, but our provider
            // will handle showing only party/faction members (excluding self)
            for (String key : Arrays.asList(PLAYER_PROVIDER_KEYS)) {
                if (key != null) {
                    // Check if provider exists and is not already our custom provider
                    WorldMapManager.MarkerProvider existingProvider = providers.get(key);
                    if (existingProvider != null && existingProvider != PartyFilteredPlayerIconMarkerProvider.INSTANCE) {
                        // Replace with our filtered provider that only shows party/faction members
                        providers.put(key, PartyFilteredPlayerIconMarkerProvider.INSTANCE);
                    } else if (existingProvider == null) {
                        // Provider doesn't exist yet, add our custom provider
                        providers.put(key, PartyFilteredPlayerIconMarkerProvider.INSTANCE);
                    }
                }
            }
        } catch (Exception e) {
            logger.at(Level.SEVERE).log("Error replacing provider: " + e.getMessage());
            logger.at(Level.FINE).log("Stack trace: ", e);
        }
    }
    
    /**
     * Shuts down the system and cleans up resources.
     * Note: TaskRegistry automatically cancels registered tasks, but this method
     * provides explicit cleanup if needed.
     */
    public void shutdown() {
        try {
            if (scheduledTask != null && !scheduledTask.isDone()) {
                scheduledTask.cancel(false); // false = don't interrupt if running
                logger.at(Level.INFO).log("Cancelled scheduled marker provider cleanup task");
            }
            monitoredWorlds.clear();
            logger.at(Level.INFO).log("DisableOtherPlayersMarkersSystem shutdown complete");
        } catch (Exception e) {
            logger.at(Level.WARNING).log("Error during shutdown: " + e.getMessage());
            logger.at(Level.FINE).log("Stack trace: ", e);
        }
    }
}
