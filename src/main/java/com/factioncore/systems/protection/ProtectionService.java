package com.factioncore.systems.protection;

import java.util.UUID;
import java.util.function.Predicate;

import com.factioncore.Main;
import com.factioncore.config.ClaimConfig;
import com.factioncore.diplomacy.AllySettings;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.factioncore.prism.PrismAttackService;
import com.factioncore.util.PermissionUtil;

/**
 * Service class for chunk protection logic.
 * Provides reusable methods for checking if players can interact in claimed chunks.
 */
public class ProtectionService {
    
    /**
     * Checks if a player is allowed to interact in a chunk.
     * <p>
     * This overload does not consider ally settings; allies are always denied. Use the String overload
     * with the appropriate INTERACT_KEY_* constant from {@link PermissionUtil} for
     * ally-aware checks.
     *
     * @param playerUuid The player UUID
     * @param dimension The dimension name
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @param interactMethod Predicate to check if interaction type is enabled (e.g., FactionInfo protection settings)
     * @return true if interaction is allowed, false if blocked
     * @deprecated Use {@link #isAllowedToInteract(UUID, String, int, int, String)} with the appropriate INTERACT_KEY_* for ally-aware checks.
     */
    @Deprecated
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
     * Canonical entry point for all interaction types (same-faction and ally).
     * Checks if a player is allowed to interact in a chunk using a permission key (e.g. open_chest, open_door, break_block).
     * Same-faction: owner always allowed; else checks factioncore.ownfaction.&lt;key&gt; with context faction=faction-uuid.
     * Ally: if player's faction is ally of chunk owner, checks in-memory AllySettings (chunk owner's settings for that ally) via {@link #isAllyAllowedForPermissionKey}.
     * For new interaction types: add the key constant in {@link com.factioncore.util.PermissionUtil} and a case in {@link #isAllyAllowedForPermissionKey}.
     *
     * @param playerUuid The player UUID
     * @param dimension The dimension name
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @param interactPermissionKey Permission key suffix (e.g. "open_chest", "open_door", "break_block")
     * @return true if interaction is allowed, false if blocked
     */
    public static boolean isAllowedToInteract(UUID playerUuid, String dimension, int blockX, int blockZ, String interactPermissionKey) {
        FactionManager manager = FactionManager.getInstance();
        boolean isPlaceBlock = PermissionUtil.INTERACT_KEY_PLACE_BLOCK.equals(interactPermissionKey);
        boolean isBreakBlock = PermissionUtil.INTERACT_KEY_BREAK_BLOCK.equals(interactPermissionKey);
        boolean isDebugKey = isPlaceBlock || isBreakBlock;
        String logPrefix = isPlaceBlock ? "[Protection place_block] " : isBreakBlock ? "[Protection break_block] " : "[Protection] ";
        if (isDebugKey) {
            ConfigUtil.debug(logPrefix + "dim=" + dimension + " block=(" + blockX + "," + blockZ + ") player=" + playerUuid + (isBreakBlock ? " key=break_block" : ""));
        } else {
            ConfigUtil.verboseDebug("[Protection] isAllowedToInteract dim=" + dimension + " block=(" + blockX + "," + blockZ + ") player=" + playerUuid + " key=" + interactPermissionKey);
        }

        if (playerUuid != null && PermissionUtil.canBypassProtectionAdmin(playerUuid)) {
            if (isDebugKey) ConfigUtil.debug(logPrefix + "ALLOW: admin bypass");
            else ConfigUtil.verboseDebug("[Protection] ALLOW: admin bypass");
            return true;
        }

        com.factioncore.claim.ChunkInfo chunkInfo = manager.getChunkRawCoords(dimension, blockX, blockZ);
        if (chunkInfo == null) {
            if (isDebugKey) ConfigUtil.debug(logPrefix + "ALLOW: chunk not claimed");
            else ConfigUtil.verboseDebug("[Protection] ALLOW: chunk not claimed");
            return true;
        }

        if (playerUuid == null) {
            if (isDebugKey) ConfigUtil.debug(logPrefix + "DENY: no player (global event)");
            else ConfigUtil.verboseDebug("[Protection] DENY: global event (no player) in claimed chunk");
            return false;
        }

        FactionInfo chunkFaction = manager.getFactionById(chunkInfo.getFactionOwner());
        if (chunkFaction == null) {
            if (isDebugKey) ConfigUtil.debug(logPrefix + "ALLOW: chunk faction missing");
            else ConfigUtil.verboseDebug("[Protection] ALLOW: chunk faction missing");
            return true;
        }

        FactionInfo playerFaction = manager.getFactionFromPlayer(playerUuid);
        if (playerFaction == null) {
            if (isDebugKey) ConfigUtil.debug(logPrefix + "DENY: player not in any faction (chunk owned by " + chunkFaction.getName() + ")");
            else ConfigUtil.verboseDebug("[Protection] DENY: player not in any faction");
            return false;
        }

        String ownPerm = "factioncore.ownfaction." + interactPermissionKey;

        if (chunkInfo.getFactionOwner().equals(playerFaction.getId())) {
            // Same faction: owner always allowed; else check member permission
            if (chunkFaction.isOwner(playerUuid)) {
                if (isDebugKey) ConfigUtil.debug(logPrefix + "ALLOW: owner of " + chunkFaction.getName());
                else ConfigUtil.verboseDebug("[Protection] ALLOW: owner");
                return true;
            }
            boolean has = com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, ownPerm, chunkFaction.getId());
            if (isDebugKey) ConfigUtil.debug(logPrefix + "same faction member hasPerm=" + has + " => " + (has ? "ALLOW" : "DENY"));
            else ConfigUtil.verboseDebug("[Protection] same faction member hasPerm=" + has + " => " + (has ? "ALLOW" : "DENY"));
            return has;
        }

        // Different faction: allow only if ally and chunk owner's ally settings grant this permission (in-memory, no DB/LuckPerms call)
        if (!manager.getAllies(chunkFaction.getId()).contains(playerFaction.getId())) {
            if (isDebugKey) ConfigUtil.debug(logPrefix + "DENY: not ally (chunk=" + chunkFaction.getName() + " playerFaction=" + playerFaction.getName() + ")");
            else ConfigUtil.verboseDebug("[Protection] DENY: not ally");
            return false;
        }
        AllySettings settings = manager.getAllySettings(chunkFaction.getId(), playerFaction.getId());
        boolean hasAlly = isAllyAllowedForPermissionKey(settings, interactPermissionKey);
        if (isPlaceBlock) {
            String mapKey = chunkFaction.getId().toString() + "_" + playerFaction.getId().toString();
            boolean keyInMap = manager.hasAllySettingsEntry(chunkFaction.getId(), playerFaction.getId());
            ConfigUtil.debug("[Protection place_block] ally: chunkOwner=" + chunkFaction.getName() + " playerFaction=" + playerFaction.getName()
                + " => " + (hasAlly ? "ALLOW" : "DENY"));
            ConfigUtil.debug("[Protection place_block] ally_settings key=\"" + mapKey + "\" (chunkOwnerId_playerFactionId, both UUIDs) keyInMap=" + keyInMap
                + " (if false, no entry for " + chunkFaction.getName() + "_" + playerFaction.getName() + "; check that " + chunkFaction.getName() + " opened Ally settings for " + playerFaction.getName() + ")");
            ConfigUtil.debug("[Protection place_block] ally_settings state: shareMarkers=" + (settings != null && settings.isSharePlayerMarkers())
                + " allowInteractions=" + (settings != null && settings.isAllowInteractionsInClaimedChunks())
                + " openChest=" + (settings != null && settings.isAllowOpenChest())
                + " openDoor=" + (settings != null && settings.isAllowOpenDoor())
                + " openBench=" + (settings != null && settings.isAllowOpenBench())
                + " useChair=" + (settings != null && settings.isAllowUseChair())
                + " usePortal=" + (settings != null && settings.isAllowUsePortal())
                + " placeBlock=" + (settings != null && settings.isAllowPlaceBlock())
                + " breakBlock=" + (settings != null && settings.isAllowBreakBlock()));
            if (!hasAlly) {
                ConfigUtil.debug("[Protection place_block] To allow: " + chunkFaction.getName() + " must open Diplomacy -> ally " + playerFaction.getName() + " -> Ally settings -> check Place block (not the other way around).");
            }
        } else if (isBreakBlock) {
            ConfigUtil.debug("[Protection break_block] ally: chunkOwner=" + chunkFaction.getName() + " playerFaction=" + playerFaction.getName()
                + " => " + (hasAlly ? "ALLOW" : "DENY"));
            if (!hasAlly) {
                ConfigUtil.debug("[Protection break_block] To allow: " + chunkFaction.getName() + " must open Diplomacy -> ally " + playerFaction.getName() + " -> Ally settings -> check Break block.");
            }
        } else {
            ConfigUtil.verboseDebug("[Protection] ally hasPerm=" + hasAlly + " => " + (hasAlly ? "ALLOW" : "DENY"));
        }
        return hasAlly;
    }

    /**
     * Maps interactPermissionKey to the corresponding AllySettings getter (in-memory, no DB call).
     */
    private static boolean isAllyAllowedForPermissionKey(AllySettings settings, String interactPermissionKey) {
        if (settings == null) return false;
        switch (interactPermissionKey) {
            case PermissionUtil.INTERACT_KEY_OPEN_CHEST: return settings.isAllowOpenChest();
            case PermissionUtil.INTERACT_KEY_OPEN_DOOR: return settings.isAllowOpenDoor();
            case PermissionUtil.INTERACT_KEY_OPEN_BENCH: return settings.isAllowOpenBench();
            case PermissionUtil.INTERACT_KEY_USE_CHAIR: return settings.isAllowUseChair();
            case PermissionUtil.INTERACT_KEY_USE_PORTAL: return settings.isAllowUsePortal();
            case PermissionUtil.INTERACT_KEY_PLACE_BLOCK: return settings.isAllowPlaceBlock();
            case PermissionUtil.INTERACT_KEY_BREAK_BLOCK: return settings.isAllowBreakBlock();
            default: return false;
        }
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

    /**
     * Returns true if block breaking is disabled in the given world (e.g. prism attack worlds).
     * When true, break/damage protection systems should cancel break events in that world.
     *
     * @param worldName World name (dimension name)
     * @return true if block break is disabled in this world
     */
    public static boolean isBlockBreakDisabledInWorld(String worldName) {
        return PrismAttackService.isAttackWorldName(worldName);
    }

    /**
     * Returns true if block placing is disabled in the given world (e.g. prism attack worlds).
     * When true, place block protection system should cancel place events in that world.
     *
     * @param worldName World name (dimension name)
     * @return true if block place is disabled in this world
     */
    public static boolean isBlockPlaceDisabledInWorld(String worldName) {
        return PrismAttackService.isAttackWorldName(worldName);
    }
}
