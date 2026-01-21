package com.factioncore.systems.protection;

import java.util.UUID;
import java.util.function.Predicate;

import com.factioncore.Main;
import com.factioncore.config.ClaimConfig;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.PermissionUtil;

/**
 * Service class for chunk protection logic.
 * Provides reusable methods for checking if players can interact in claimed chunks.
 */
public class ProtectionService {
    
    /**
     * Checks if a player is allowed to interact in a chunk.
     * 
     * @param playerUuid The player UUID
     * @param dimension The dimension name
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @param interactMethod Predicate to check if interaction type is enabled (e.g., FactionInfo protection settings)
     * @return true if interaction is allowed, false if blocked
     */
    public static boolean isAllowedToInteract(UUID playerUuid, String dimension, int blockX, int blockZ, Predicate<FactionInfo> interactMethod) {
        FactionManager manager = FactionManager.getInstance();
        
        // Check admin bypass permission
        if (PermissionUtil.canBypassProtectionAdmin(playerUuid)) {
            return true;
        }
        
        // Check creative mode bypass
        Main main = Main.get();
        if (main != null && main.getClaimConfig() != null) {
            ClaimConfig config = main.getClaimConfig().get();
            if (config.isCreativeModeBypassProtection()) {
                // TODO: Check if player is in creative mode
                // For now, skip this check
            }
        }
        
        // Get chunk info
        com.factioncore.claim.ChunkInfo chunkInfo = manager.getChunkRawCoords(dimension, blockX, blockZ);
        if (chunkInfo == null) {
            return true; // Not claimed, allow interaction
        }
        
        // Get faction that owns the chunk
        FactionInfo chunkFaction = manager.getFactionById(chunkInfo.getFactionOwner());
        if (chunkFaction == null || interactMethod.test(chunkFaction)) {
            return true; // No faction or interaction allowed by faction settings
        }
        
        // Check if player is in the same faction
        FactionInfo playerFaction = manager.getFactionFromPlayer(playerUuid);
        if (playerFaction == null) {
            return false; // Player not in any faction
        }
        
        return chunkInfo.getFactionOwner().equals(playerFaction.getId());
    }
    
    /**
     * Checks if block break is enabled for a faction.
     * 
     * @param faction The faction
     * @return true if block break is enabled
     */
    public static boolean isBlockBreakEnabled(FactionInfo faction) {
        Main main = Main.get();
        if (main != null && main.getClaimConfig() != null) {
            return main.getClaimConfig().get().isDefaultFactionBlockBreakEnabled();
        }
        return false; // Default: block break disabled
    }
    
    /**
     * Checks if block place is enabled for a faction.
     * 
     * @param faction The faction
     * @return true if block place is enabled
     */
    public static boolean isBlockPlaceEnabled(FactionInfo faction) {
        Main main = Main.get();
        if (main != null && main.getClaimConfig() != null) {
            return main.getClaimConfig().get().isDefaultFactionBlockPlaceEnabled();
        }
        return false; // Default: block place disabled
    }
    
    /**
     * Checks if block interact is enabled for a faction.
     * 
     * @param faction The faction
     * @return true if block interact is enabled
     */
    public static boolean isBlockInteractEnabled(FactionInfo faction) {
        Main main = Main.get();
        if (main != null && main.getClaimConfig() != null) {
            return main.getClaimConfig().get().isDefaultFactionBlockInteractEnabled();
        }
        return false; // Default: block interact disabled
    }
    
    /**
     * Checks if PVP is enabled for a faction.
     * 
     * @param faction The faction
     * @return true if PVP is enabled
     */
    public static boolean isPVPEnabled(FactionInfo faction) {
        Main main = Main.get();
        if (main != null && main.getClaimConfig() != null) {
            return main.getClaimConfig().get().isDefaultFactionPVPEnabled();
        }
        return false; // Default: PVP disabled
    }
}
