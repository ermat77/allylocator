package com.factioncore.systems.protection;

import java.util.UUID;
import java.util.function.Predicate;

import com.factioncore.Main;
import com.factioncore.config.ClaimConfig;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
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
        ConfigUtil.verboseDebug("[Protection] isAllowedToInteract dim=" + dimension + " block=(" + blockX + "," + blockZ + ") player=" + playerUuid);

        // Admin bypass only for non-null player (global events have null player → deny in claimed chunks)
        if (playerUuid != null && PermissionUtil.canBypassProtectionAdmin(playerUuid)) {
            ConfigUtil.verboseDebug("[Protection] ALLOW: admin bypass (factioncore.admin.bypassProtection or factioncore.admin.*)");
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
            ConfigUtil.verboseDebug("[Protection] ALLOW: chunk not claimed");
            return true; // Not claimed, allow interaction
        }
        ConfigUtil.verboseDebug("[Protection] chunk claimed by factionId=" + chunkInfo.getFactionOwner());

        // Global events (explosions, etc.) have no player → deny in claimed chunks
        if (playerUuid == null) {
            ConfigUtil.verboseDebug("[Protection] DENY: global event (no player) in claimed chunk");
            return false;
        }
        
        // Get faction that owns the chunk
        FactionInfo chunkFaction = manager.getFactionById(chunkInfo.getFactionOwner());
        if (chunkFaction == null) {
            ConfigUtil.verboseDebug("[Protection] ALLOW: chunk faction missing (orphan claim)");
            return true; // Chunk has no valid faction, allow (should not happen)
        }
        
        // Check if player is in the same faction as the chunk owner
        FactionInfo playerFaction = manager.getFactionFromPlayer(playerUuid);
        if (playerFaction == null) {
            ConfigUtil.verboseDebug("[Protection] DENY: player not in any faction, chunk owned by " + chunkFaction.getName());
            return false; // Player not in any faction → cannot interact in others' claims
        }
        
        if (!chunkInfo.getFactionOwner().equals(playerFaction.getId())) {
            ConfigUtil.verboseDebug("[Protection] DENY: different faction (chunk=" + chunkFaction.getName() + " playerFaction=" + playerFaction.getName() + ")");
            return false; // Different faction → deny
        }
        
        // Player is in the same faction that owns the chunk: allow only if this interaction type is enabled for the faction
        boolean interactionEnabled = interactMethod.test(chunkFaction);
        ConfigUtil.verboseDebug("[Protection] same faction=" + chunkFaction.getName() + " interactionEnabled=" + interactionEnabled + " => " + (interactionEnabled ? "ALLOW" : "DENY"));
        return interactionEnabled;
    }

    /**
     * Checks if a player is allowed to interact in a chunk using a permission key (e.g. open_chest, open_door).
     * Same-faction: checks factioncore.ownfaction.&lt;key&gt; with context faction=faction-uuid.
     * Ally: if player's faction is ally of chunk owner, checks factioncore.allyfaction.&lt;key&gt; with context faction=chunk-owner-name.
     *
     * @param playerUuid The player UUID
     * @param dimension The dimension name
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @param interactPermissionKey Permission key suffix (e.g. "open_chest", "open_door")
     * @return true if interaction is allowed, false if blocked
     */
    public static boolean isAllowedToInteract(UUID playerUuid, String dimension, int blockX, int blockZ, String interactPermissionKey) {
        FactionManager manager = FactionManager.getInstance();
        ConfigUtil.verboseDebug("[Protection] isAllowedToInteract dim=" + dimension + " block=(" + blockX + "," + blockZ + ") player=" + playerUuid + " key=" + interactPermissionKey);

        if (playerUuid != null && PermissionUtil.canBypassProtectionAdmin(playerUuid)) {
            ConfigUtil.verboseDebug("[Protection] ALLOW: admin bypass");
            return true;
        }

        com.factioncore.claim.ChunkInfo chunkInfo = manager.getChunkRawCoords(dimension, blockX, blockZ);
        if (chunkInfo == null) {
            ConfigUtil.verboseDebug("[Protection] ALLOW: chunk not claimed");
            return true;
        }

        if (playerUuid == null) {
            ConfigUtil.verboseDebug("[Protection] DENY: global event (no player) in claimed chunk");
            return false;
        }

        FactionInfo chunkFaction = manager.getFactionById(chunkInfo.getFactionOwner());
        if (chunkFaction == null) {
            ConfigUtil.verboseDebug("[Protection] ALLOW: chunk faction missing");
            return true;
        }

        FactionInfo playerFaction = manager.getFactionFromPlayer(playerUuid);
        if (playerFaction == null) {
            ConfigUtil.verboseDebug("[Protection] DENY: player not in any faction");
            return false;
        }

        String ownPerm = "factioncore.ownfaction." + interactPermissionKey;
        String allyPerm = "factioncore.allyfaction." + interactPermissionKey;

        if (chunkInfo.getFactionOwner().equals(playerFaction.getId())) {
            // Same faction: owner always allowed; else check member permission
            if (chunkFaction.isOwner(playerUuid)) {
                ConfigUtil.verboseDebug("[Protection] ALLOW: owner");
                return true;
            }
            boolean has = com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, ownPerm, chunkFaction.getId());
            ConfigUtil.verboseDebug("[Protection] same faction member hasPerm=" + has + " => " + (has ? "ALLOW" : "DENY"));
            return has;
        }

        // Different faction: allow only if ally and has ally permission with context faction=chunkOwnerName
        if (!manager.getAllies(chunkFaction.getId()).contains(playerFaction.getId())) {
            ConfigUtil.verboseDebug("[Protection] DENY: not ally");
            return false;
        }
        String chunkOwnerName = chunkFaction.getName();
        if (chunkOwnerName == null) chunkOwnerName = chunkFaction.getId().toString();
        boolean hasAlly = PermissionUtil.hasPermissionWithContext(playerUuid, allyPerm, "faction", chunkOwnerName);
        ConfigUtil.verboseDebug("[Protection] ally hasPerm=" + hasAlly + " => " + (hasAlly ? "ALLOW" : "DENY"));
        return hasAlly;
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

    /**
     * Returns true if the block type should ignore break/damage/interact restriction (SimpleClaims-style).
     * If the block type id (lowercase) contains any configured substring, protection is skipped for that block.
     *
     * @param blockTypeIdLowercase Block type id, typically from event.getBlockType().getId().toLowerCase(Locale.ROOT)
     * @return true if this block type is in the ignore list
     */
    public static boolean isBlockIgnored(String blockTypeIdLowercase) {
        if (blockTypeIdLowercase == null) return false;
        Main main = Main.get();
        if (main == null || main.getClaimConfig() == null) return false;
        String[] list = main.getClaimConfig().get().getBlocksThatIgnoreRestriction();
        if (list == null) return false;
        for (String entry : list) {
            if (entry != null && blockTypeIdLowercase.contains(entry.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
