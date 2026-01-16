package com.allylocator.systems;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.util.PositionUtil;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Custom marker provider that filters player markers to show only party/faction members.
 * 
 * This replaces the default PlayerIconMarkerProvider and adds party/faction-based filtering.
 * Based on the decompiled PlayerIconMarkerProvider from HytaleServer.jar.
 */
public class PartyFilteredPlayerIconMarkerProvider implements WorldMapManager.MarkerProvider {
    
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    public static final PartyFilteredPlayerIconMarkerProvider INSTANCE = new PartyFilteredPlayerIconMarkerProvider();
    
    private PartyFilteredPlayerIconMarkerProvider() {
    }
    
    @Override
    public void update(@Nonnull World world, @Nonnull GameplayConfig gameplayConfig, 
                      @Nonnull WorldMapTracker tracker, int chunkViewRadius, 
                      int playerChunkX, int playerChunkZ) {
        WorldMapConfig worldMapConfig = gameplayConfig.getWorldMapConfig();
        if (!worldMapConfig.isDisplayPlayers()) {
            return;
        }
        
        if (!tracker.shouldUpdatePlayerMarkers()) {
            return;
        }
        
        Player player = tracker.getPlayer();
        if (player == null) {
            return;
        }
        
        UUID viewingPlayerUuid = player.getUuid();
        String viewingPlayerName = player.getDisplayName();
        
        // Get the viewing player's party/faction using PartyService (supports both Hyfaction and SimpleClaims)
        Object viewingPlayerParty = PartyService.getPartyFromPlayer(viewingPlayerUuid);
        
        int chunkViewRadiusSq = chunkViewRadius * chunkViewRadius;
        Iterator<PlayerRef> playerRefs = world.getPlayerRefs().iterator();
        
        List<String> markersShown = new ArrayList<>();
        List<String> markersHidden = new ArrayList<>();
        
        while (playerRefs.hasNext()) {
            PlayerRef otherPlayer = playerRefs.next();
            UUID otherPlayerUuid = otherPlayer.getUuid();
            String otherPlayerName = otherPlayer.getUsername();
            String markerId = "Player-" + otherPlayerUuid.toString();
            
            // Skip self
            if (otherPlayerUuid.equals(viewingPlayerUuid)) {
                continue;
            }
            
            // Filter by party/faction membership
            if (viewingPlayerParty == null) {
                // If viewing player has no party/faction or no plugin is available, hide all other players
                continue;
            }
            
            if (!PartyService.isOwnerOrMember(viewingPlayerParty, otherPlayerUuid)) {
                // If other player is not in the same party/faction, hide them
                continue;
            }
            
            // Check distance
            Transform otherPlayerTransform = otherPlayer.getTransform();
            Vector3d otherPos = otherPlayerTransform.getPosition();
            int otherChunkX = (int)otherPos.x >> 5;
            int otherChunkZ = (int)otherPos.z >> 5;
            int chunkDiffX = otherChunkX - playerChunkX;
            int chunkDiffZ = otherChunkZ - playerChunkZ;
            int chunkDistSq = chunkDiffX * chunkDiffX + chunkDiffZ * chunkDiffZ;
            
            if (chunkDistSq > chunkViewRadiusSq) {
                continue;
            }
            
            // Send marker for this party/faction member
            tracker.trySendMarker(
                chunkViewRadius, 
                playerChunkX, 
                playerChunkZ, 
                otherPos, 
                otherPlayer.getHeadRotation().getYaw(), 
                markerId, 
                "Player: " + (otherPlayerName != null ? otherPlayerName : otherPlayerUuid.toString()), 
                otherPlayer, 
                (id, name, op) -> {
                    return new MapMarker(
                        id, 
                        name, 
                        "Player.png", 
                        PositionUtil.toTransformPacket(op.getTransform()), 
                        (ContextMenuItem[])null
                    );
                }
            );
        }
        
        tracker.resetPlayerMarkersUpdateTimer();
    }
    
    /**
     * Gets a player's name from their UUID.
     * 
     * @param playerUuid The player's UUID
     * @return The player's username, or null if not found
     */
    private String getPlayerName(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        try {
            com.hypixel.hytale.server.core.universe.PlayerRef playerRef = 
                com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(playerUuid);
            return playerRef != null ? playerRef.getUsername() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
