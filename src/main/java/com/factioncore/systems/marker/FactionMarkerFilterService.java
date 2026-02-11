package com.factioncore.systems.marker;

import com.factioncore.diplomacy.AllySettings;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;

import java.util.UUID;

/**
 * Service class for applying faction-based marker filters to players.
 * 
 * This follows Hytale's pattern of having a service class that contains
 * the actual filtering logic, which is called by ECS systems and event handlers.
 * 
 * Uses WorldMapTracker.setPlayerMapFilter() to set a predicate that determines
 * which player markers should be visible.
 */
public class FactionMarkerFilterService {
    
    /**
     * Safely gets the Universe instance, checking if it's initialized and ready.
     * Returns null if Universe is not available or not ready.
     * 
     * @return The Universe instance, or null if not available/ready
     */
    private static com.hypixel.hytale.server.core.universe.Universe getUniverseSafely() {
        try {
            com.hypixel.hytale.server.core.universe.Universe universe = 
                com.hypixel.hytale.server.core.universe.Universe.get();
            
            if (universe == null) {
                return null;
            }
            
            // Check if universe is ready
            try {
                java.util.concurrent.CompletableFuture<Void> readyFuture = universe.getUniverseReady();
                if (readyFuture == null || !readyFuture.isDone()) {
                    return null;
                }
            } catch (Exception e) {
                // Universe became null or error accessing it
                return null;
            }
            
            return universe;
        } catch (Exception e) {
            // Any error getting Universe means it's not available
            return null;
        }
    }
    
    /**
     * Applies faction-based marker filter to a player.
     * Shows markers for: same-faction members, and ally faction members when that ally
     * has "Share faction members' PlayerMarkers on the map" enabled for the viewer's faction
     * (same pattern as {@link com.factioncore.systems.protection.ProtectionService} ally checks).
     *
     * @param player The player to apply the filter to
     * @param playerRef The PlayerRef for the player
     */
    public static void applyFilterToPlayer(Player player, PlayerRef playerRef) {
        ConfigUtil.verboseDebug("FactionMarkerFilterService.applyFilterToPlayer() called");
        
        if (player == null || playerRef == null) {
            ConfigUtil.verboseDebug("FactionMarkerFilterService.applyFilterToPlayer(): player or playerRef is null, returning");
            return;
        }
        
        WorldMapTracker tracker = player.getWorldMapTracker();
        if (tracker == null) {
            ConfigUtil.verboseDebug("FactionMarkerFilterService.applyFilterToPlayer(): tracker is null, returning");
            return;
        }
        
        UUID viewerUuid = playerRef.getUuid();
        ConfigUtil.verboseDebug("FactionMarkerFilterService.applyFilterToPlayer(): viewerUuid=" + viewerUuid);
        
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo viewerFaction = factionManager.getFactionFromPlayer(viewerUuid);
        ConfigUtil.verboseDebug("FactionMarkerFilterService.applyFilterToPlayer(): viewerFaction=" + 
                       (viewerFaction != null ? viewerFaction.getName() : "null"));
        
        // Set the filter predicate.
        // Hytale API: predicate returns TRUE = hide marker, FALSE = show marker.
        // (PlayerIconMarkerProvider: if filter.test(other) is true → skip; false → add marker.)
        tracker.setPlayerMapFilter(otherPlayerRef -> {
            if (otherPlayerRef == null) {
                return true;  // hide
            }
            UUID otherPlayerUuid = otherPlayerRef.getUuid();
            // Always hide self (provider also skips self; this is defensive)
            if (viewerUuid.equals(otherPlayerUuid)) {
                return true;  // hide
            }
            // If viewer has no faction, hide all other players
            if (viewerFaction == null) {
                return true;  // hide
            }
            // Same faction: show
            if (viewerFaction.isOwnerOrMember(otherPlayerUuid)) {
                return false;  // show
            }
            // Ally: show only if the other's faction has "share markers" enabled for viewer's faction
            // (AllySettings are from the faction that owns the setting: otherFaction shares with viewerFaction)
            FactionInfo otherFaction = factionManager.getFactionFromPlayer(otherPlayerUuid);
            if (otherFaction == null) {
                return true;  // hide
            }
            if (!factionManager.getAllies(viewerFaction.getId()).contains(otherFaction.getId())) {
                return true;  // hide (not ally)
            }
            AllySettings settings = factionManager.getAllySettings(otherFaction.getId(), viewerFaction.getId());
            boolean showAlly = settings != null && settings.isSharePlayerMarkers();
            return !showAlly;  // hide when not sharing, show when sharing
        });
        
        ConfigUtil.verboseDebug("FactionMarkerFilterService.applyFilterToPlayer(): filter applied successfully");
    }
    
    /**
     * Applies faction-based marker filter from a PlayerReadyEvent.
     * 
     * @param event The PlayerReadyEvent
     */
    public static void applyFilterToPlayer(PlayerReadyEvent event) {
        ConfigUtil.debug("FactionMarkerFilterService.applyFilterToPlayer(PlayerReadyEvent) called");
        
        if (event == null) {
            ConfigUtil.debug("FactionMarkerFilterService.applyFilterToPlayer(PlayerReadyEvent): event is null, returning");
            return;
        }
        
        Player player = event.getPlayer();
        if (player == null) {
            ConfigUtil.debug("FactionMarkerFilterService.applyFilterToPlayer(PlayerReadyEvent): player is null, returning");
            return;
        }
        
        // Get PlayerRef from player (deprecated but still functional)
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) {
            ConfigUtil.debug("FactionMarkerFilterService.applyFilterToPlayer(PlayerReadyEvent): playerRef is null, returning");
            return;
        }
        
        ConfigUtil.debug("FactionMarkerFilterService.applyFilterToPlayer(PlayerReadyEvent): delegating to applyFilterToPlayer(Player, PlayerRef)");
        applyFilterToPlayer(player, playerRef);
    }
    
    /**
     * Updates marker filters for all members of a faction.
     * Called when faction membership changes.
     * 
     * @param faction The faction whose members need filter updates
     */
    public static void updateFiltersForFaction(FactionInfo faction) {
        ConfigUtil.debug("FactionMarkerFilterService.updateFiltersForFaction() called: faction=" + 
                       (faction != null ? faction.getName() : "null"));
        
        if (faction == null) {
            ConfigUtil.debug("FactionMarkerFilterService.updateFiltersForFaction(): faction is null, returning");
            return;
        }
        
        // Safely get Universe - it may be null during server startup
        com.hypixel.hytale.server.core.universe.Universe universe = getUniverseSafely();
        if (universe == null) {
            ConfigUtil.debug("FactionMarkerFilterService.updateFiltersForFaction(): universe is not available or not ready yet, returning");
            return;
        }
        
        // Update filters for all faction members
        UUID[] members = faction.getMembers();
        UUID owner = faction.getOwner();
        ConfigUtil.debug("FactionMarkerFilterService.updateFiltersForFaction(): owner=" + owner + 
                       ", members=" + (members != null ? members.length : 0));
        
        // Check owner
        if (owner != null) {
            ConfigUtil.debug("FactionMarkerFilterService.updateFiltersForFaction(): updating filter for owner " + owner);
            updateFilterForPlayer(owner);
        }
        
        // Check members
        if (members != null) {
            for (UUID memberUuid : members) {
                if (memberUuid != null && !memberUuid.equals(owner)) {
                    ConfigUtil.debug("FactionMarkerFilterService.updateFiltersForFaction(): updating filter for member " + memberUuid);
                    updateFilterForPlayer(memberUuid);
                }
            }
        }
        
        ConfigUtil.debug("FactionMarkerFilterService.updateFiltersForFaction(): completed");
    }
    
    /**
     * Updates the marker filter for a specific player.
     * 
     * @param playerUuid The UUID of the player
     */
    private static void updateFilterForPlayer(UUID playerUuid) {
        ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer() called: playerUuid=" + playerUuid);
        
        if (playerUuid == null) {
            ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer(): playerUuid is null, returning");
            return;
        }
        
        // Safely get Universe - it may be null during server startup
        com.hypixel.hytale.server.core.universe.Universe universe = getUniverseSafely();
        if (universe == null) {
            ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer(): universe is not available or not ready yet, returning");
            return;
        }
        
        PlayerRef playerRef = universe.getPlayer(playerUuid);
        if (playerRef == null) {
            ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer(): playerRef not found for " + playerUuid);
            return;
        }
        
        // Get the player entity from the world
        java.util.Map<String, com.hypixel.hytale.server.core.universe.world.World> worlds = universe.getWorlds();
        if (worlds == null) {
            ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer(): worlds is null, returning");
            return;
        }
        
        ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer(): searching " + worlds.size() + " worlds");
        
        for (com.hypixel.hytale.server.core.universe.world.World world : worlds.values()) {
            if (world == null) {
                continue;
            }
            
            // Use PlayerRefs from world (returns Collection, convert to list for iteration)
            java.util.Collection<PlayerRef> playerRefsCollection = world.getPlayerRefs();
            if (playerRefsCollection == null || playerRefsCollection.isEmpty()) {
                continue;
            }
            
            for (PlayerRef targetPlayerRef : playerRefsCollection) {
                if (targetPlayerRef != null && playerUuid.equals(targetPlayerRef.getUuid())) {
                    ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer(): found player in world");
                    
                    // Get the player entity from the world's entity store
                    com.hypixel.hytale.server.core.universe.world.storage.EntityStore entityStore = world.getEntityStore();
                    if (entityStore != null) {
                        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref = 
                            targetPlayerRef.getReference();
                        if (ref != null && ref.isValid()) {
                            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = 
                                ref.getStore();
                            if (store != null) {
                                Player targetPlayer = store.getComponent(ref, Player.getComponentType());
                                if (targetPlayer != null) {
                                    ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer(): applying filter");
                                    applyFilterToPlayer(targetPlayer, targetPlayerRef);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        ConfigUtil.debug("FactionMarkerFilterService.updateFilterForPlayer(): player not found in any world");
    }
}
