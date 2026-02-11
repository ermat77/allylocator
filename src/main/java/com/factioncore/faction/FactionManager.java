package com.factioncore.faction;

import com.hypixel.hytale.logger.HytaleLogger;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.PermissionUtil;
import com.factioncore.Main;
import com.factioncore.config.ClaimConfig;
import com.factioncore.config.GeneralConfig;
import com.factioncore.claim.ChunkInfo;
import com.factioncore.claim.tracking.ModifiedTracking;
import com.factioncore.database.DatabaseManager;
import com.factioncore.diplomacy.AllySettings;
import com.factioncore.diplomacy.PendingAllyRequest;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.math.util.ChunkUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Singleton manager for faction data and persistence.
 * 
 * This class manages all factions, handles SQLite persistence via DatabaseManager,
 * and provides methods to create, retrieve, and manage factions.
 * 
 * CRITICAL: All runtime queries use in-memory maps (ConcurrentHashMap) for fast lookups.
 * DatabaseManager is ONLY used for persistence (save periodically, load on startup).
 * 
 * Following Hytale best practices, this is a singleton that manages all faction data.
 * Data is stored in the plugin data folder (e.g. mods/FactionCore) next to the plugin JAR.
 */
public class FactionManager {
    
    private static final FactionManager INSTANCE = new FactionManager();
    private static HytaleLogger LOGGER;
    
    // In-memory storage (PRIMARY storage during gameplay)
    // Storage for factions: factionId -> FactionInfo
    private final Map<UUID, FactionInfo> factions;
    
    // Quick lookup: playerId -> factionId
    private final Map<UUID, UUID> playerToFaction;
    
    // Chunk claiming system (in-memory)
    private final Map<String, Map<String, ChunkInfo>> chunks; // dimension -> (chunk key -> ChunkInfo)
    private final Map<UUID, Integer> factionClaimCounts;

    // World map cache invalidation queue (world name -> chunk indices to clear)
    private final Map<String, LongSet> mapUpdateQueue;
    private final Set<String> worldsNeedingUpdates;

    // Invite system (in-memory)
    private final Map<UUID, List<com.factioncore.invite.PendingInvite>> pendingInvites; // player UUID -> list of invites
    
    // Diplomacy (in-memory): factionId -> set of ally/enemy faction IDs; pending requests; ally settings
    private final Map<UUID, Set<UUID>> factionAllies;
    private final Map<UUID, Set<UUID>> factionEnemies;
    private final List<PendingAllyRequest> pendingAllyRequests;
    private final Map<String, AllySettings> allySettings; // key: factionId_allyFactionId
    /** Member permissions: key factionId_memberUuid -> set of permission strings (e.g. factioncore.ownfaction.place_block). */
    private final Map<String, Set<String>> memberPermissions;
    
    // Database manager (ONLY for persistence)
    private final DatabaseManager databaseManager;
    
    // Async initialization
    private final CompletableFuture<Void> initializationFuture;
    
    // Periodic saving thread
    private volatile boolean isDirty;
    private final AtomicBoolean savingThreadRunning;
    private ExecutorService savingExecutor;
    
    /**
     * Private constructor for singleton pattern.
     * Initializes in-memory maps and starts async loading from database.
     */
    private FactionManager() {
        // Initialize in-memory maps (PRIMARY storage during gameplay)
        this.factions = new ConcurrentHashMap<>();
        this.playerToFaction = new ConcurrentHashMap<>();
        this.chunks = new ConcurrentHashMap<>();
        this.factionClaimCounts = new ConcurrentHashMap<>();
        this.mapUpdateQueue = new ConcurrentHashMap<>();
        this.worldsNeedingUpdates = ConcurrentHashMap.newKeySet();
        this.pendingInvites = new ConcurrentHashMap<>();
        this.factionAllies = new ConcurrentHashMap<>();
        this.factionEnemies = new ConcurrentHashMap<>();
        this.pendingAllyRequests = Collections.synchronizedList(new ArrayList<>());
        this.allySettings = new ConcurrentHashMap<>();
        this.memberPermissions = new ConcurrentHashMap<>();

        // Initialize DatabaseManager (non-blocking - just creates instance)
        this.databaseManager = new DatabaseManager(getLogger());
        
        // Initialize saving thread
        this.isDirty = false;
        this.savingThreadRunning = new AtomicBoolean(false);
        this.savingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FactionCore-DatabaseSaver");
            t.setDaemon(true);
            return t;
        });
        
        // Start async loading from database
        this.initializationFuture = initializeAsync();
        
        // Start periodic saving thread
        startAsyncSaving();
    }
    
    /**
     * Gets the singleton instance of FactionManager.
     * Waits for async initialization to complete before returning.
     * 
     * @return The FactionManager instance
     */
    public static FactionManager getInstance() {
        // Wait for initialization to complete (acceptable during plugin setup)
        INSTANCE.initializationFuture.join();
        return INSTANCE;
    }
    
    /**
     * Initializes data asynchronously from database.
     * Loads all data into in-memory maps.
     * 
     * @return CompletableFuture that completes when all data is loaded
     */
    private CompletableFuture<Void> initializeAsync() {
        ConfigUtil.debug("FactionManager: Starting async initialization from database");
        
        return CompletableFuture.allOf(
            databaseManager.loadFactionsAsync().thenAccept(this::populateFactions),
            databaseManager.loadClaimsAsync().thenAccept(this::populateClaims),
            databaseManager.loadPendingInvitesAsync().thenAccept(this::populateInvites),
            databaseManager.loadFactionAlliesAsync().thenAccept(this::populateAllies),
            databaseManager.loadFactionEnemiesAsync().thenAccept(this::populateEnemies),
            databaseManager.loadPendingAllyRequestsAsync().thenAccept(this::populatePendingAllyRequests),
            databaseManager.loadAllySettingsAsync().thenAccept(this::populateAllySettings),
            databaseManager.loadMemberPermissionsAsync().thenAccept(this::populateMemberPermissions)
        ).thenRun(() -> {
            ConfigUtil.debug("FactionManager: Async initialization completed");
            getLogger().at(Level.INFO).log("FactionManager initialized: " + factions.size() + " factions, " + 
                chunks.values().stream().mapToInt(Map::size).sum() + " claims loaded");
        });
    }
    
    /**
     * Populates factions map from loaded data.
     */
    private void populateFactions(Map<UUID, FactionInfo> loadedFactions) {
        ConfigUtil.debug("populateFactions: loading " + loadedFactions.size() + " factions");
        this.factions.putAll(loadedFactions);
        
        // Rebuild playerToFaction map
        for (FactionInfo faction : loadedFactions.values()) {
            if (faction.getOwner() != null) {
                playerToFaction.put(faction.getOwner(), faction.getId());
            }
            for (UUID member : faction.getMembers()) {
                playerToFaction.put(member, faction.getId());
            }
        }
        ConfigUtil.debug("populateFactions: populated " + factions.size() + " factions, " + 
            playerToFaction.size() + " player mappings");
    }
    
    /**
     * Populates claims map from loaded data.
     */
    private void populateClaims(Map<String, Map<String, ChunkInfo>> loadedClaims) {
        ConfigUtil.debug("populateClaims: loading claims for " + loadedClaims.size() + " dimensions");
        this.chunks.putAll(loadedClaims);
        
        // Initialize claim counts
        for (Map<String, ChunkInfo> dimensionChunks : loadedClaims.values()) {
            for (ChunkInfo chunk : dimensionChunks.values()) {
                factionClaimCounts.merge(chunk.getFactionOwner(), 1, (a, b) -> a + b);
            }
        }
        ConfigUtil.debug("populateClaims: populated " + chunks.values().stream().mapToInt(Map::size).sum() + " claims");
    }
    
    /**
     * Populates pending invites map from loaded data.
     */
    private void populateInvites(Map<UUID, List<com.factioncore.invite.PendingInvite>> loadedInvites) {
        ConfigUtil.debug("populateInvites: loading invites for " + loadedInvites.size() + " players");
        this.pendingInvites.putAll(loadedInvites);
        ConfigUtil.debug("populateInvites: populated " + pendingInvites.size() + " player invite lists");
    }
    
    /**
     * Populates faction allies from loaded data.
     */
    private void populateAllies(Map<UUID, Set<UUID>> loadedAllies) {
        ConfigUtil.debug("populateAllies: loading allies for " + loadedAllies.size() + " factions");
        for (Map.Entry<UUID, Set<UUID>> e : loadedAllies.entrySet()) {
            factionAllies.put(e.getKey(), ConcurrentHashMap.newKeySet());
            factionAllies.get(e.getKey()).addAll(e.getValue());
        }
    }
    
    /**
     * Populates faction enemies from loaded data.
     */
    private void populateEnemies(Map<UUID, Set<UUID>> loadedEnemies) {
        ConfigUtil.debug("populateEnemies: loading enemies for " + loadedEnemies.size() + " factions");
        for (Map.Entry<UUID, Set<UUID>> e : loadedEnemies.entrySet()) {
            factionEnemies.put(e.getKey(), ConcurrentHashMap.newKeySet());
            factionEnemies.get(e.getKey()).addAll(e.getValue());
        }
    }
    
    /**
     * Populates pending ally requests from loaded data.
     */
    private void populatePendingAllyRequests(List<PendingAllyRequest> loaded) {
        ConfigUtil.debug("populatePendingAllyRequests: loading " + loaded.size() + " requests");
        pendingAllyRequests.clear();
        pendingAllyRequests.addAll(loaded);
    }
    
    /**
     * Populates ally settings from loaded data.
     */
    private void populateAllySettings(Map<String, AllySettings> loaded) {
        ConfigUtil.debug("populateAllySettings: loading " + loaded.size() + " settings");
        allySettings.putAll(loaded);
    }
    
    /**
     * Populates member permissions map from loaded data.
     */
    private void populateMemberPermissions(Map<String, Set<String>> loaded) {
        ConfigUtil.debug("populateMemberPermissions: loading " + loaded.size() + " entries");
        for (Map.Entry<String, Set<String>> e : loaded.entrySet()) {
            memberPermissions.put(e.getKey(), ConcurrentHashMap.newKeySet());
            memberPermissions.get(e.getKey()).addAll(e.getValue());
        }
    }
    
    /**
     * Gets the logger instance, initializing it lazily if needed.
     * 
     * @return The HytaleLogger instance (never null)
     */
    private static HytaleLogger getLogger() {
        if (LOGGER == null) {
            try {
                LOGGER = HytaleLogger.forEnclosingClass();
            } catch (Exception e) {
                // Fallback if forEnclosingClass fails
            }
            // If still null, use alternative initialization
            if (LOGGER == null) {
                LOGGER = HytaleLogger.getLogger().getSubLogger("FactionManager");
            }
        }
        return LOGGER;
    }
    
    /**
     * Gets the maximum allowed length for faction names from config.
     * 
     * @return The maximum faction name length in characters (default: 20)
     */
    public static int getMaxFactionNameLength() {
        Main main = Main.get();
        if (main != null && main.getGeneralConfig() != null) {
            try {
                GeneralConfig config = main.getGeneralConfig().get();
                return config.getMaxFactionNameLength();
            } catch (Exception e) {
                // Config not loaded or error accessing, use default
                return 20;
            }
        }
        return 20; // Default fallback
    }
    
    /**
     * Creates a new faction with the given name and owner.
     * 
     * @param name The faction name
     * @param ownerUuid The UUID of the faction owner
     * @return The created FactionInfo, or null if creation failed
     */
    public FactionInfo createFaction(String name, UUID ownerUuid) {
        ConfigUtil.debug("createFaction called: name=" + name + ", ownerUuid=" + ownerUuid);
        
        if (name == null || name.trim().isEmpty()) {
            getLogger().at(Level.WARNING).log("Cannot create faction: name is empty");
            ConfigUtil.debug("createFaction failed: name is empty");
            return null;
        }
        
        String trimmedName = name.trim();
        int maxLength = getMaxFactionNameLength();
        if (trimmedName.length() > maxLength) {
            getLogger().at(Level.WARNING).log("Cannot create faction: name '" + name + "' exceeds maximum length of " + maxLength + " characters");
            ConfigUtil.debug("createFaction failed: name '" + name + "' exceeds maximum length");
            return null;
        }
        
        if (ownerUuid == null) {
            getLogger().at(Level.WARNING).log("Cannot create faction: owner UUID is null");
            ConfigUtil.debug("createFaction failed: owner UUID is null");
            return null;
        }
        
        // Check permission (business logic layer enforcement)
        if (!PermissionUtil.canCreateFaction(ownerUuid)) {
            getLogger().at(Level.WARNING).log("Cannot create faction: player " + ownerUuid + " does not have permission");
            ConfigUtil.debug("createFaction failed: player " + ownerUuid + " does not have permission");
            return null;
        }
        
        // Check if player is already in a faction
        if (playerToFaction.containsKey(ownerUuid)) {
            getLogger().at(Level.WARNING).log("Cannot create faction: player " + ownerUuid + " is already in a faction");
            ConfigUtil.debug("createFaction failed: player " + ownerUuid + " is already in a faction");
            return null;
        }
        
        // Check if faction name already exists
        for (FactionInfo existing : factions.values()) {
            if (existing.getName().equalsIgnoreCase(trimmedName)) {
                getLogger().at(Level.WARNING).log("Cannot create faction: name '" + name + "' already exists");
                ConfigUtil.debug("createFaction failed: name '" + name + "' already exists");
                return null;
            }
        }
        
        // Create new faction
        UUID factionId = UUID.randomUUID();
        ConfigUtil.debug("Creating new faction with ID: " + factionId);
        
        FactionInfo faction = new FactionInfo(
            factionId,
            ownerUuid,
            trimmedName,
            "",
            new UUID[0],
            0xFFFFFF // Default white color
        );
        
        // Add owner as member (they're already the owner, but we track them separately)
        // Actually, in our design, owner is separate from members
        // But we should add them to the playerToFaction map
        
        factions.put(factionId, faction);
        playerToFaction.put(ownerUuid, factionId);
        ConfigUtil.debug("Added faction to maps: factions.size()=" + factions.size() + ", playerToFaction.size()=" + playerToFaction.size());
        
        // Clean up pending invites for the owner (they now have a faction)
        clearPendingInvitesForPlayer(ownerUuid);
        
        // Save to database (queued, async)
        databaseManager.saveFaction(faction);
        
        // Update marker filters for the new faction owner
        com.factioncore.systems.marker.FactionMarkerFilterService.updateFiltersForFaction(faction);
        ConfigUtil.debug("Updated marker filters for new faction");
        
        getLogger().at(Level.INFO).log("Created faction: " + name + " (ID: " + factionId + ")");
        ConfigUtil.debug("createFaction completed successfully: " + name + " (ID: " + factionId + ")");
        
        return faction;
    }
    
    /**
     * Gets the faction a player belongs to.
     * 
     * @param playerUuid The player's UUID
     * @return The FactionInfo, or null if player is not in a faction
     */
    public FactionInfo getFactionFromPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        
        UUID factionId = playerToFaction.get(playerUuid);
        if (factionId == null) {
            return null;
        }
        
        return factions.get(factionId);
    }
    
    /**
     * Gets a faction by its ID.
     * 
     * @param factionId The faction UUID
     * @return The FactionInfo, or null if not found
     */
    public FactionInfo getFactionById(UUID factionId) {
        if (factionId == null) {
            return null;
        }
        return factions.get(factionId);
    }
    
    /**
     * Sets the faction home location. Persisted by the periodic save.
     * 
     * @param factionId The faction ID
     * @param dimension World/dimension name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return true if the faction existed and home was set
     */
    public boolean setFactionHome(UUID factionId, String dimension, double x, double y, double z) {
        if (factionId == null) {
            return false;
        }
        FactionInfo faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        faction.setHome(dimension, x, y, z);
        markDirty();
        return true;
    }
    
    /**
     * Gets a faction by its name (case-insensitive).
     * 
     * @param name The faction name
     * @return The FactionInfo, or null if not found
     */
    public FactionInfo getFactionByName(String name) {
        ConfigUtil.debug("getFactionByName called: name=" + name);
        
        if (name == null || name.trim().isEmpty()) {
            ConfigUtil.debug("getFactionByName: name is null or empty, returning null");
            return null;
        }
        
        String searchName = name.trim().toLowerCase();
        ConfigUtil.debug("getFactionByName: searching for name (lowercase)=" + searchName);
        
        for (FactionInfo faction : factions.values()) {
            if (faction.getName().toLowerCase().equals(searchName)) {
                ConfigUtil.debug("getFactionByName: found faction=" + faction.getName() + " (ID: " + faction.getId() + ")");
                return faction;
            }
        }
        
        ConfigUtil.debug("getFactionByName: no faction found with name=" + name);
        return null;
    }
    
    /**
     * Adds a player to a faction.
     * 
     * @param playerUuid The player's UUID
     * @param factionId The faction UUID
     * @return true if successful, false otherwise
     */
    public boolean addPlayerToFaction(UUID playerUuid, UUID factionId) {
        ConfigUtil.debug("addPlayerToFaction called: playerUuid=" + playerUuid + ", factionId=" + factionId);
        
        if (playerUuid == null || factionId == null) {
            ConfigUtil.debug("addPlayerToFaction failed: null parameters");
            return false;
        }
        
        FactionInfo faction = factions.get(factionId);
        if (faction == null) {
            ConfigUtil.debug("addPlayerToFaction failed: faction not found with ID=" + factionId);
            return false;
        }
        
        // Check if player is already in a faction
        if (playerToFaction.containsKey(playerUuid)) {
            ConfigUtil.debug("addPlayerToFaction failed: player " + playerUuid + " is already in a faction");
            return false;
        }
        
        faction.addMember(playerUuid);
        playerToFaction.put(playerUuid, factionId);
        ConfigUtil.debug("addPlayerToFaction: added player " + playerUuid + " to faction " + faction.getName());

        // Apply default member interact permissions from config (factioncore.ownfaction.* with context faction=factionId)
        applyDefaultMemberInteractPermissions(playerUuid, factionId);
        
        // Clean up pending invites for the player (they now have a faction)
        clearPendingInvitesForPlayer(playerUuid);
        
        // Save to database (queued, async)
        databaseManager.saveFaction(faction);
        
        // Update marker filters for the faction
        com.factioncore.systems.marker.FactionMarkerFilterService.updateFiltersForFaction(faction);
        ConfigUtil.debug("addPlayerToFaction: updated marker filters");
        
        return true;
    }

    /**
     * Applies default member interaction permissions from ClaimConfig to a new member.
     */
    private void applyDefaultMemberInteractPermissions(UUID memberUuid, UUID factionId) {
        com.factioncore.permission.FactionPermissionManager fpm = com.factioncore.permission.FactionPermissionManager.getInstance();
        ClaimConfig config = null;
        if (Main.get() != null && Main.get().getClaimConfig() != null) {
            config = Main.get().getClaimConfig().get();
        }
        if (config == null) return;
        grantOrRevoke(fpm, memberUuid, factionId, PermissionUtil.FACTION_PERM_OPEN_CHEST, config.isDefaultMemberCanInteractWithChest());
        grantOrRevoke(fpm, memberUuid, factionId, PermissionUtil.FACTION_PERM_OPEN_DOOR, config.isDefaultMemberCanInteractWithDoor());
        grantOrRevoke(fpm, memberUuid, factionId, PermissionUtil.FACTION_PERM_OPEN_BENCH, config.isDefaultMemberCanInteractWithBench());
        grantOrRevoke(fpm, memberUuid, factionId, PermissionUtil.FACTION_PERM_USE_CHAIR, config.isDefaultMemberCanInteractWithChair());
        grantOrRevoke(fpm, memberUuid, factionId, PermissionUtil.FACTION_PERM_USE_PORTAL, config.isDefaultMemberCanInteractWithPortal());
        grantOrRevoke(fpm, memberUuid, factionId, PermissionUtil.FACTION_PERM_PLACE_BLOCK, config.isDefaultMemberCanPlaceBlock());
        grantOrRevoke(fpm, memberUuid, factionId, PermissionUtil.FACTION_PERM_BREAK_BLOCK, config.isDefaultMemberCanBreakBlock());
    }

    private void grantOrRevoke(com.factioncore.permission.FactionPermissionManager fpm, UUID memberUuid, UUID factionId, String permission, boolean grant) {
        if (grant) {
            fpm.grantPermission(memberUuid, permission, factionId);
        } else {
            fpm.revokePermission(memberUuid, permission, factionId);
        }
    }
    
    /**
     * Removes a player from their faction.
     * 
     * @param playerUuid The player's UUID
     * @return true if successful, false otherwise
     */
    public boolean removePlayerFromFaction(UUID playerUuid) {
        ConfigUtil.debug("removePlayerFromFaction called: playerUuid=" + playerUuid);
        
        if (playerUuid == null) {
            ConfigUtil.debug("removePlayerFromFaction failed: playerUuid is null");
            return false;
        }
        
        UUID factionId = playerToFaction.remove(playerUuid);
        ConfigUtil.debug("removePlayerFromFaction: removed from playerToFaction map, factionId=" + factionId);
        
        if (factionId == null) {
            ConfigUtil.debug("removePlayerFromFaction failed: player was not in any faction");
            return false;
        }
        
        FactionInfo faction = factions.get(factionId);
        if (faction != null) {
            // Revoke all own-faction permissions for this player (cleanup)
            com.factioncore.permission.FactionPermissionManager.getInstance().revokeAllFactionPermissions(playerUuid, factionId);
            
            // If player is the owner, disband the faction
            if (faction.isOwner(playerUuid)) {
                ConfigUtil.debug("removePlayerFromFaction: player is owner, disbanding faction");
                disbandFaction(factionId, playerUuid);
            } else {
                // Just remove as member
                ConfigUtil.debug("removePlayerFromFaction: removing player as member");
                faction.removeMember(playerUuid);
                
                // Save to database (queued, async)
                databaseManager.saveFaction(faction);
                
                // Update marker filters for remaining faction members
                com.factioncore.systems.marker.FactionMarkerFilterService.updateFiltersForFaction(faction);
                ConfigUtil.debug("removePlayerFromFaction: updated marker filters");
            }
        } else {
            ConfigUtil.debug("removePlayerFromFaction: faction not found with ID=" + factionId);
        }
        
        return true;
    }
    
    /**
     * Disbands a faction, removing all members.
     * 
     * @param factionId The faction UUID
     */
    /**
     * Disbands a faction. Checks permissions: player must be owner or have factioncore.admin.disband permission.
     * 
     * @param factionId The UUID of the faction to disband
     * @param playerUuid The UUID of the player attempting to disband
     * @return true if the faction was successfully disbanded, false if permission denied or faction not found
     */
    public boolean disbandFaction(UUID factionId, UUID playerUuid) {
        ConfigUtil.debug("disbandFaction called: factionId=" + factionId + ", playerUuid=" + playerUuid);
        
        if (factionId == null) {
            ConfigUtil.debug("disbandFaction: factionId is null, returning");
            return false;
        }
        
        if (playerUuid == null) {
            ConfigUtil.debug("disbandFaction: playerUuid is null, returning");
            return false;
        }
        
        FactionInfo faction = factions.get(factionId);
        if (faction == null) {
            ConfigUtil.debug("disbandFaction: faction not found with ID=" + factionId);
            return false;
        }
        
        // Check permissions: must be owner OR have admin disband permission
        boolean isOwner = faction.isOwner(playerUuid);
        boolean hasAdminPermission = com.factioncore.util.PermissionUtil.canDisbandFactionAdmin(playerUuid);
        
        if (!isOwner && !hasAdminPermission) {
            ConfigUtil.debug("disbandFaction: permission denied - player " + playerUuid + " is not owner and doesn't have admin permission");
            getLogger().at(Level.WARNING).log("Player " + playerUuid + " attempted to disband faction " + faction.getName() + " without permission");
            return false;
        }
        
        ConfigUtil.debug("disbandFaction: permission granted (isOwner=" + isOwner + ", hasAdminPermission=" + hasAdminPermission + ")");
        
        ConfigUtil.debug("disbandFaction: removing faction " + faction.getName() + " with " + faction.getMembers().length + " members");
        
        // Revoke all faction permissions for all members (cleanup)
        for (UUID member : faction.getMembers()) {
            com.factioncore.permission.FactionPermissionManager.getInstance().revokeAllFactionPermissions(member, factionId);
        }
        // Also revoke for owner
        if (faction.getOwner() != null) {
            com.factioncore.permission.FactionPermissionManager.getInstance().revokeAllFactionPermissions(faction.getOwner(), factionId);
        }
        
        // Remove all members from playerToFaction map
        for (UUID member : faction.getMembers()) {
            playerToFaction.remove(member);
        }
        // Remove owner
        playerToFaction.remove(faction.getOwner());
        ConfigUtil.debug("disbandFaction: removed all members from playerToFaction map");
        
        // Remove faction from map
        factions.remove(factionId);
        
        // Clean up diplomacy data for this faction
        factionAllies.remove(factionId);
        factionEnemies.remove(factionId);
        for (UUID otherId : new HashSet<>(factionAllies.keySet())) {
            Set<UUID> allies = factionAllies.get(otherId);
            if (allies != null) {
                allies.remove(factionId);
                if (allies.isEmpty()) factionAllies.remove(otherId);
            }
        }
        synchronized (pendingAllyRequests) {
            pendingAllyRequests.removeIf(req ->
                req.getRequesterFactionId().equals(factionId) || req.getTargetFactionId().equals(factionId));
        }
        allySettings.keySet().removeIf(key -> key.startsWith(factionId.toString() + "_") || key.endsWith("_" + factionId.toString()));
        
        // Delete from database (queued, async)
        databaseManager.deleteFaction(factionId);
        
        getLogger().at(Level.INFO).log("Disbanded faction: " + faction.getName() + " (ID: " + factionId + ") by player " + playerUuid);
        ConfigUtil.debug("disbandFaction completed: " + faction.getName() + " (ID: " + factionId + ")");
        return true;
    }
    
    /**
     * Disbands a faction without permission check (for internal use only).
     * Use disbandFaction(UUID, UUID) for normal operations.
     * 
     * @param factionId The UUID of the faction to disband
     * @deprecated Use disbandFaction(UUID, UUID) instead
     */
    @Deprecated
    public void disbandFaction(UUID factionId) {
        ConfigUtil.debug("disbandFaction (deprecated) called: factionId=" + factionId);
        
        if (factionId == null) {
            ConfigUtil.debug("disbandFaction: factionId is null, returning");
            return;
        }
        
        FactionInfo faction = factions.remove(factionId);
        if (faction != null) {
            ConfigUtil.debug("disbandFaction: removing faction " + faction.getName() + " with " + faction.getMembers().length + " members");
            
            // Revoke all faction permissions for all members (cleanup)
            for (UUID member : faction.getMembers()) {
                com.factioncore.permission.FactionPermissionManager.getInstance().revokeAllFactionPermissions(member, factionId);
            }
            // Also revoke for owner
            if (faction.getOwner() != null) {
                com.factioncore.permission.FactionPermissionManager.getInstance().revokeAllFactionPermissions(faction.getOwner(), factionId);
            }
            
            // Remove all members from playerToFaction map
            for (UUID member : faction.getMembers()) {
                playerToFaction.remove(member);
            }
            // Remove owner
            playerToFaction.remove(faction.getOwner());
            ConfigUtil.debug("disbandFaction: removed all members from playerToFaction map");
            
            // Clean up diplomacy data for this faction
            factionAllies.remove(factionId);
            factionEnemies.remove(factionId);
            for (UUID otherId : new HashSet<>(factionAllies.keySet())) {
                Set<UUID> allies = factionAllies.get(otherId);
                if (allies != null) {
                    allies.remove(factionId);
                    if (allies.isEmpty()) factionAllies.remove(otherId);
                }
            }
            synchronized (pendingAllyRequests) {
                pendingAllyRequests.removeIf(req ->
                    req.getRequesterFactionId().equals(factionId) || req.getTargetFactionId().equals(factionId));
            }
            allySettings.keySet().removeIf(key -> key.startsWith(factionId.toString() + "_") || key.endsWith("_" + factionId.toString()));
            
            // Delete from database (queued, async)
            databaseManager.deleteFaction(factionId);
            
            getLogger().at(Level.INFO).log("Disbanded faction: " + faction.getName() + " (ID: " + factionId + ")");
            ConfigUtil.debug("disbandFaction completed: " + faction.getName() + " (ID: " + factionId + ")");
        } else {
            ConfigUtil.debug("disbandFaction: faction not found with ID=" + factionId);
        }
    }
    
    /**
     * Gets all factions.
     * 
     * @return A map of factionId -> FactionInfo
     */
    public Map<UUID, FactionInfo> getAllFactions() {
        ConfigUtil.debug("getAllFactions called: returning " + factions.size() + " factions");
        return new HashMap<>(factions);
    }
    
    /**
     * Saves a single faction to the database.
     * Queues the save operation (async, non-blocking).
     * 
     * @param faction The faction to save
     */
    public void saveFaction(FactionInfo faction) {
        if (faction == null) {
            return;
        }
        ConfigUtil.debug("saveFaction called: saving faction " + faction.getName());
        databaseManager.saveFaction(faction);
        markDirty();
    }
    
    /**
     * Saves all factions to the database.
     * This method queues saves for all factions (used for periodic saves).
     */
    public void saveFactions() {
        ConfigUtil.debug("saveFactions called: saving " + factions.size() + " factions");
        
        // Queue save for each faction
        for (FactionInfo faction : factions.values()) {
            databaseManager.saveFaction(faction);
        }
        
        markDirty();
        ConfigUtil.debug("saveFactions completed: queued " + factions.size() + " faction saves");
    }
    
    // ========== Diplomacy Methods ==========
    
    /**
     * Gets the set of enemy faction IDs for a faction (one-way: we declared them enemy).
     */
    public Set<UUID> getEnemies(UUID factionId) {
        if (factionId == null) return Collections.emptySet();
        Set<UUID> set = factionEnemies.get(factionId);
        return set != null ? new HashSet<>(set) : Collections.emptySet();
    }
    
    /**
     * Gets the set of ally faction IDs for a faction (symmetric).
     */
    public Set<UUID> getAllies(UUID factionId) {
        if (factionId == null) return Collections.emptySet();
        Set<UUID> set = factionAllies.get(factionId);
        return set != null ? new HashSet<>(set) : Collections.emptySet();
    }
    
    /**
     * Gets all pending ally requests involving the given faction (as requester or target).
     * Caller can derive state: awaitingResponseFromOtherFaction when we are requester, awaitingResponseFromYourFaction when we are target.
     */
    public List<PendingAllyRequest> getPendingAllyRequestsForFaction(UUID factionId) {
        if (factionId == null) return Collections.emptyList();
        synchronized (pendingAllyRequests) {
            List<PendingAllyRequest> result = new ArrayList<>();
            for (PendingAllyRequest req : pendingAllyRequests) {
                if (req.getRequesterFactionId().equals(factionId) || req.getTargetFactionId().equals(factionId)) {
                    result.add(req);
                }
            }
            return result;
        }
    }
    
    /**
     * Sends an ally request from one faction to another. Returns false if already allies, already pending, or invalid.
     */
    public boolean sendAllyRequest(UUID fromFactionId, UUID toFactionId) {
        if (fromFactionId == null || toFactionId == null || fromFactionId.equals(toFactionId)) return false;
        if (getAllies(fromFactionId).contains(toFactionId)) return false;
        synchronized (pendingAllyRequests) {
            for (PendingAllyRequest req : pendingAllyRequests) {
                if ((req.getRequesterFactionId().equals(fromFactionId) && req.getTargetFactionId().equals(toFactionId))
                    || (req.getRequesterFactionId().equals(toFactionId) && req.getTargetFactionId().equals(fromFactionId))) {
                    return false;
                }
            }
            pendingAllyRequests.add(new PendingAllyRequest(fromFactionId, toFactionId, LocalDateTime.now().toString()));
        }
        markDirty();
        return true;
    }
    
    /**
     * Accepts an ally request. Acceptor is the target faction; requester is the faction that sent the request. Adds each other as allies and removes the request.
     */
    public boolean acceptAllyRequest(UUID acceptorFactionId, UUID requesterFactionId) {
        if (acceptorFactionId == null || requesterFactionId == null) return false;
        PendingAllyRequest toRemove = null;
        synchronized (pendingAllyRequests) {
            for (PendingAllyRequest req : pendingAllyRequests) {
                if (req.getRequesterFactionId().equals(requesterFactionId) && req.getTargetFactionId().equals(acceptorFactionId)) {
                    toRemove = req;
                    break;
                }
            }
            if (toRemove == null) return false;
            pendingAllyRequests.remove(toRemove);
        }
        factionAllies.computeIfAbsent(acceptorFactionId, k -> ConcurrentHashMap.newKeySet()).add(requesterFactionId);
        factionAllies.computeIfAbsent(requesterFactionId, k -> ConcurrentHashMap.newKeySet()).add(acceptorFactionId);
        markDirty();
        return true;
    }

    /**
     * Removes an ally (breaks alliance both ways). Revokes ally permissions for all members of both factions.
     */
    public boolean removeAlly(UUID factionId, UUID allyFactionId) {
        if (factionId == null || allyFactionId == null) return false;
        Set<UUID> ourAllies = factionAllies.get(factionId);
        Set<UUID> theirAllies = factionAllies.get(allyFactionId);
        if (ourAllies == null || !ourAllies.remove(allyFactionId)) return false;
        if (theirAllies != null) theirAllies.remove(factionId);
        if (ourAllies.isEmpty()) factionAllies.remove(factionId);
        if (theirAllies != null && theirAllies.isEmpty()) factionAllies.remove(allyFactionId);
        allySettings.remove(factionId.toString() + "_" + allyFactionId.toString());
        allySettings.remove(allyFactionId.toString() + "_" + factionId.toString());
        markDirty();
        return true;
    }
    
    /**
     * Declines an ally request (target faction declines the request from requester).
     */
    public boolean declineAllyRequest(UUID targetFactionId, UUID requesterFactionId) {
        if (targetFactionId == null || requesterFactionId == null) return false;
        boolean removed;
        synchronized (pendingAllyRequests) {
            removed = pendingAllyRequests.removeIf(req ->
                req.getRequesterFactionId().equals(requesterFactionId) && req.getTargetFactionId().equals(targetFactionId));
        }
        if (removed) markDirty();
        return removed;
    }
    
    /**
     * Cancels an ally request (requester cancels the request they sent to target).
     */
    public boolean cancelAllyRequest(UUID requesterFactionId, UUID targetFactionId) {
        if (requesterFactionId == null || targetFactionId == null) return false;
        synchronized (pendingAllyRequests) {
            boolean removed = pendingAllyRequests.removeIf(req ->
                req.getRequesterFactionId().equals(requesterFactionId) && req.getTargetFactionId().equals(targetFactionId));
            if (removed) markDirty();
            return removed;
        }
    }
    
    /**
     * Adds an enemy (one-way: our faction declares the other as enemy).
     */
    public void addEnemy(UUID factionId, UUID enemyFactionId) {
        if (factionId == null || enemyFactionId == null || factionId.equals(enemyFactionId)) return;
        factionEnemies.computeIfAbsent(factionId, k -> ConcurrentHashMap.newKeySet()).add(enemyFactionId);
        markDirty();
    }
    
    /**
     * Removes an enemy (one-way).
     */
    public void removeEnemy(UUID factionId, UUID enemyFactionId) {
        if (factionId == null || enemyFactionId == null) return;
        Set<UUID> set = factionEnemies.get(factionId);
        if (set != null) {
            set.remove(enemyFactionId);
            if (set.isEmpty()) factionEnemies.remove(factionId);
            markDirty();
        }
    }
    
    /**
     * Gets ally settings for (our faction, ally faction). Returns default (false, false) if not set.
     */
    /**
     * Returns faction IDs that have the given faction as ally (so we can grant ally perms to new members of that faction).
     */
    public Set<UUID> getFactionIdsThatHaveAsAlly(UUID allyFactionId) {
        if (allyFactionId == null) return Set.of();
        String suffix = "_" + allyFactionId.toString();
        Set<UUID> out = new HashSet<>();
        for (String key : allySettings.keySet()) {
            if (!key.endsWith(suffix)) continue;
            String ourIdStr = key.substring(0, key.length() - suffix.length());
            try {
                out.add(UUID.fromString(ourIdStr));
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    public AllySettings getAllySettings(UUID factionId, UUID allyFactionId) {
        if (factionId == null || allyFactionId == null) return new AllySettings();
        String key = factionId.toString() + "_" + allyFactionId.toString();
        AllySettings s = allySettings.get(key);
        return s != null ? s : new AllySettings();
    }

    /**
     * Returns true if the in-memory ally settings map has an entry for (factionId, allyFactionId).
     * Useful for debugging (distinguish "key missing" from "key present but allowPlaceBlock=false").
     */
    public boolean hasAllySettingsEntry(UUID factionId, UUID allyFactionId) {
        if (factionId == null || allyFactionId == null) return false;
        String key = factionId.toString() + "_" + allyFactionId.toString();
        return allySettings.containsKey(key);
    }
    
    /**
     * Sets ally settings for (our faction, ally faction).
     * Refreshes map marker filters for the ally faction so they see/stop seeing our members
     * when "Share faction members' PlayerMarkers" is toggled.
     */
    public void setAllySettings(UUID factionId, UUID allyFactionId, AllySettings settings) {
        if (factionId == null || allyFactionId == null || settings == null) return;
        String key = factionId.toString() + "_" + allyFactionId.toString();
        allySettings.put(key, new AllySettings(
                settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(),
                settings.isAllowOpenChest(), settings.isAllowOpenDoor(), settings.isAllowOpenBench(),
                settings.isAllowUseChair(), settings.isAllowUsePortal(),
                settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()));
        markDirty();
        FactionInfo allyFaction = getFactionById(allyFactionId);
        if (allyFaction != null) {
            com.factioncore.systems.marker.FactionMarkerFilterService.updateFiltersForFaction(allyFaction);
        }
    }
    
    // ========== Member Permissions (in-memory, persisted via saveAllMemberPermissions) ==========
    
    /**
     * Gets member permissions for (factionId, memberUuid). Returns a copy of the set, or empty set if absent.
     */
    public Set<String> getMemberPermissions(UUID factionId, UUID memberUuid) {
        if (factionId == null || memberUuid == null) return new HashSet<>();
        String key = factionId.toString() + "_" + memberUuid.toString();
        Set<String> perms = memberPermissions.get(key);
        return perms != null ? new HashSet<>(perms) : new HashSet<>();
    }
    
    /**
     * Sets member permissions for (factionId, memberUuid). Replaces existing set.
     */
    public void setMemberPermissions(UUID factionId, UUID memberUuid, Set<String> permissions) {
        if (factionId == null || memberUuid == null) return;
        String key = factionId.toString() + "_" + memberUuid.toString();
        if (permissions == null || permissions.isEmpty()) {
            memberPermissions.remove(key);
        } else {
            Set<String> copy = ConcurrentHashMap.newKeySet();
            copy.addAll(permissions);
            memberPermissions.put(key, copy);
        }
        markDirty();
    }
    
    /**
     * Returns true if the member has the given permission in the faction.
     */
    public boolean hasMemberPermission(UUID factionId, UUID memberUuid, String permission) {
        if (factionId == null || memberUuid == null || permission == null) return false;
        return getMemberPermissions(factionId, memberUuid).contains(permission);
    }
    
    /**
     * Returns true if the in-memory member permissions map has an entry for (factionId, memberUuid).
     */
    public boolean hasMemberPermissionsEntry(UUID factionId, UUID memberUuid) {
        if (factionId == null || memberUuid == null) return false;
        String key = factionId.toString() + "_" + memberUuid.toString();
        return memberPermissions.containsKey(key);
    }
    
    // ========== Chunk Claiming Methods ==========
    
    /**
     * Starts the async saving thread for periodic database saves.
     * Saves all dirty data every 5 seconds.
     */
    private void startAsyncSaving() {
        if (savingThreadRunning.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                while (savingThreadRunning.get()) {
                    try {
                        if (isDirty) {
                            isDirty = false;
                            ConfigUtil.debug("Periodic save: saving all data to database...");
                            
                            // Save all factions
                            saveFactions();
                            
                            // Save all claims
                            for (Map.Entry<String, Map<String, ChunkInfo>> entry : chunks.entrySet()) {
                                String dimension = entry.getKey();
                                for (ChunkInfo chunk : entry.getValue().values()) {
                                    databaseManager.saveClaim(dimension, chunk);
                                }
                            }
                            
                            // Save all pending invites
                            for (List<com.factioncore.invite.PendingInvite> playerInvites : pendingInvites.values()) {
                                for (com.factioncore.invite.PendingInvite invite : playerInvites) {
                                    databaseManager.savePendingInvite(invite);
                                }
                            }
                            
                            // Save diplomacy data
                            databaseManager.saveAllFactionAllies(factionAllies);
                            databaseManager.saveAllFactionEnemies(factionEnemies);
                            databaseManager.saveAllPendingAllyRequests(new ArrayList<>(pendingAllyRequests));
                            databaseManager.saveAllAllySettings(allySettings);
                            databaseManager.saveAllMemberPermissions(memberPermissions);
                            
                            ConfigUtil.debug("Periodic save: finished queuing all data saves");
                        }
                        
                        Thread.sleep(5000); // Save every 5 seconds when dirty
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        getLogger().at(Level.SEVERE).log("Error in saving thread: " + e.getMessage());
                        ConfigUtil.debug("Error in saving thread: " + e.getMessage(), e);
                    }
                }
            }, savingExecutor);
        }
    }
    
    /**
     * Marks data as dirty, triggering a periodic save.
     */
    public void markDirty() {
        this.isDirty = true;
    }
    
    /**
     * Gets a chunk by dimension and chunk coordinates.
     * Uses in-memory map for fast lookup (O(1)).
     * 
     * @param dimension The dimension name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return The ChunkInfo, or null if not claimed
     */
    public ChunkInfo getChunk(String dimension, int chunkX, int chunkZ) {
        Map<String, ChunkInfo> dimensionChunks = chunks.get(dimension);
        if (dimensionChunks == null) {
            return null;
        }
        String formattedChunk = ChunkInfo.formatCoordinates(chunkX, chunkZ);
        return dimensionChunks.get(formattedChunk);
    }
    
    /**
     * Gets a chunk by dimension and block coordinates (converts to chunk coordinates).
     * 
     * @param dimension The dimension name
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @return The ChunkInfo, or null if not claimed
     */
    public ChunkInfo getChunkRawCoords(String dimension, int blockX, int blockZ) {
        return getChunk(dimension, ChunkUtil.chunkCoordinate(blockX), ChunkUtil.chunkCoordinate(blockZ));
    }
    
    /**
     * Claims a chunk for a faction.
     * 
     * @param dimension The dimension name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param faction The faction claiming the chunk
     * @param owner The player owner
     * @param playerRef The PlayerRef
     * @return The created ChunkInfo
     */
    public ChunkInfo claimChunkBy(String dimension, int chunkX, int chunkZ, FactionInfo faction, Player owner, PlayerRef playerRef) {
        ConfigUtil.debug("claimChunkBy called: dimension=" + dimension + ", chunkX=" + chunkX + ", chunkZ=" + chunkZ + ", faction=" + faction.getName());
        
        ChunkInfo chunkInfo = new ChunkInfo(faction.getId(), chunkX, chunkZ);
        chunkInfo.setCreatedTracked(new ModifiedTracking(
            playerRef.getUuid(),
            owner.getDisplayName(),
            LocalDateTime.now().toString()
        ));
        
        // Update in-memory map
        Map<String, ChunkInfo> dimensionChunks = chunks.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        dimensionChunks.put(ChunkInfo.formatCoordinates(chunkX, chunkZ), chunkInfo);
        
        // Update claim count
        factionClaimCounts.merge(faction.getId(), 1, (a, b) -> a + b);
        
        // Queue save to database
        databaseManager.saveClaim(dimension, chunkInfo);
        markDirty();
        
        ConfigUtil.debug("claimChunkBy completed: claimed chunk for faction " + faction.getName());
        return chunkInfo;
    }
    
    /**
     * Claims a chunk by block coordinates (converts to chunk coordinates).
     * 
     * @param dimension The dimension name
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @param faction The faction claiming the chunk
     * @param owner The player owner
     * @param playerRef The PlayerRef
     * @return The created ChunkInfo
     */
    public ChunkInfo claimChunkByRawCoords(String dimension, int blockX, int blockZ, FactionInfo faction, Player owner, PlayerRef playerRef) {
        return claimChunkBy(dimension, ChunkUtil.chunkCoordinate(blockX), ChunkUtil.chunkCoordinate(blockZ), faction, owner, playerRef);
    }
    
    /**
     * Unclaims a chunk.
     * 
     * @param dimension The dimension name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    public void unclaim(String dimension, int chunkX, int chunkZ) {
        ConfigUtil.debug("unclaim called: dimension=" + dimension + ", chunkX=" + chunkX + ", chunkZ=" + chunkZ);
        
        Map<String, ChunkInfo> chunkMap = chunks.get(dimension);
        if (chunkMap != null) {
            ChunkInfo removed = chunkMap.remove(ChunkInfo.formatCoordinates(chunkX, chunkZ));
            if (removed != null) {
                // Update claim count
                factionClaimCounts.computeIfPresent(removed.getFactionOwner(), (k, v) -> (v > 1) ? v - 1 : null);
                
                // Queue delete from database
                databaseManager.deleteClaim(dimension, chunkX, chunkZ);
                markDirty();
                
                ConfigUtil.debug("unclaim: removed chunk, updated claim count");
            }
        }
    }
    
    /**
     * Unclaims a chunk by block coordinates (converts to chunk coordinates).
     * 
     * @param dimension The dimension name
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     */
    public void unclaimRawCoords(String dimension, int blockX, int blockZ) {
        unclaim(dimension, ChunkUtil.chunkCoordinate(blockX), ChunkUtil.chunkCoordinate(blockZ));
    }

    /**
     * Queues world map cache invalidation for the given chunk and its neighbors.
     * Processed by WorldMapUpdateTickingSystem using the public API
     * (WorldMapManager.clearImagesInChunks + WorldMapTracker.clearChunks).
     *
     * @param world  the world
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     */
    public void queueMapUpdate(World world, int chunkX, int chunkZ) {
        if (world == null) return;
        queueMapUpdate(world.getName(), chunkX, chunkZ);
    }

    /**
     * Queues world map cache invalidation for the given chunk and its neighbors by dimension name.
     * Use when no World reference is available (e.g. when invalidating all chunks for a faction after rename).
     *
     * @param dimension world/dimension name (key used by WorldMapUpdateTickingSystem)
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     */
    public void queueMapUpdate(String dimension, int chunkX, int chunkZ) {
        if (dimension == null || dimension.isEmpty()) return;
        mapUpdateQueue.computeIfAbsent(dimension, k -> new LongOpenHashSet()).add(ChunkUtil.indexChunk(chunkX, chunkZ));
        mapUpdateQueue.get(dimension).add(ChunkUtil.indexChunk(chunkX + 1, chunkZ));
        mapUpdateQueue.get(dimension).add(ChunkUtil.indexChunk(chunkX - 1, chunkZ));
        mapUpdateQueue.get(dimension).add(ChunkUtil.indexChunk(chunkX, chunkZ + 1));
        mapUpdateQueue.get(dimension).add(ChunkUtil.indexChunk(chunkX, chunkZ - 1));
        worldsNeedingUpdates.add(dimension);
    }

    /**
     * Queues world map cache invalidation for every chunk claimed by the given faction.
     * Call when faction data displayed on the map changes (e.g. faction name or color)
     * so cached chunk images are cleared and regenerated with the new label.
     *
     * @param faction the faction whose claimed chunks should be invalidated
     */
    public void queueMapUpdateForFaction(FactionInfo faction) {
        if (faction == null) return;
        UUID factionId = faction.getId();
        for (Map.Entry<String, Map<String, ChunkInfo>> entry : chunks.entrySet()) {
            String dimension = entry.getKey();
            for (ChunkInfo chunk : entry.getValue().values()) {
                if (chunk.getFactionOwner().equals(factionId)) {
                    queueMapUpdate(dimension, chunk.getChunkX(), chunk.getChunkZ());
                }
            }
        }
    }

    public Map<String, LongSet> getMapUpdateQueue() {
        return mapUpdateQueue;
    }

    public Set<String> getWorldsNeedingUpdates() {
        return worldsNeedingUpdates;
    }

    /**
     * Checks if a faction has enough claims left.
     * 
     * @param faction The faction to check
     * @return true if faction can claim more chunks
     */
    public boolean hasEnoughClaimsLeft(FactionInfo faction) {
        int maxAmount = getMaxClaimAmount(faction);
        int currentAmount = factionClaimCounts.getOrDefault(faction.getId(), 0);
        boolean hasEnough = currentAmount < maxAmount;
        ConfigUtil.debug("hasEnoughClaimsLeft: faction=" + faction.getName() + ", current=" + currentAmount + ", max=" + maxAmount + ", hasEnough=" + hasEnough);
        return hasEnough;
    }
    
    /**
     * Gets the maximum claim amount for a faction (from config).
     * 
     * @param faction The faction
     * @return The maximum claim amount
     */
    private int getMaxClaimAmount(FactionInfo faction) {
        return faction.getMaxClaimAmount();
    }
    
    /**
     * Gets the amount of claims a faction has.
     * 
     * @param faction The faction
     * @return The number of claimed chunks
     */
    public int getAmountOfClaims(FactionInfo faction) {
        return factionClaimCounts.getOrDefault(faction.getId(), 0);
    }
    
    /**
     * Checks if claiming is allowed in a dimension.
     * 
     * @param world The world to check
     * @return true if claiming is allowed
     */
    public boolean canClaimInDimension(World world) {
        if (world.getWorldConfig().isDeleteOnRemove()) {
            return false;
        }
        if (world.getName().contains("Gaia_Temple")) {
            return false;
        }
        
        Main main = Main.get();
        if (main != null && main.getClaimConfig() != null) {
            String[] blacklist = main.getClaimConfig().get().getWorldNameBlacklistForClaiming();
            if (blacklist != null) {
                for (String blacklisted : blacklist) {
                    if (world.getName().equals(blacklisted)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Checks if a player is allowed to interact in a chunk.
     * 
     * @param playerUUID The player UUID
     * @param dimension The dimension name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param interactMethod Predicate to check if interaction is allowed (e.g., FactionInfo::isBlockBreakEnabled)
     * @return true if interaction is allowed
     */
    public boolean isAllowedToInteract(UUID playerUUID, String dimension, int chunkX, int chunkZ, Predicate<FactionInfo> interactMethod) {
        // Get chunk from in-memory map (O(1) lookup)
        ChunkInfo chunkInfo = getChunk(dimension, chunkX, chunkZ);
        if (chunkInfo == null) {
            return true; // Not claimed, allow interaction
        }
        
        // Get faction from in-memory map (O(1) lookup)
        FactionInfo chunkFaction = getFactionById(chunkInfo.getFactionOwner());
        if (chunkFaction == null || interactMethod.test(chunkFaction)) {
            return true; // No faction or interaction allowed
        }
        
        // Check if player is in the same faction (in-memory lookup)
        UUID playerFactionId = playerToFaction.get(playerUUID);
        if (playerFactionId == null) {
            return false; // Player not in any faction
        }
        
        return chunkInfo.getFactionOwner().equals(playerFactionId);
    }
    
    /**
     * Gets all chunks.
     * Returns in-memory map for fast access.
     * 
     * @return Map of dimension -> (chunk key -> ChunkInfo)
     */
    public Map<String, Map<String, ChunkInfo>> getChunks() {
        return chunks;
    }
    
    // ========== Invite Management Methods ==========
    
    /**
     * Invites a player to a faction.
     * 
     * @param playerUuid The UUID of the player to invite
     * @param factionId The UUID of the faction
     * @param inviterName The name of the player who sent the invite
     * @return true if successful, false otherwise
     */
    public boolean invitePlayerToFaction(UUID playerUuid, UUID factionId, String inviterName) {
        ConfigUtil.debug("invitePlayerToFaction called: playerUuid=" + playerUuid + ", factionId=" + factionId + ", inviterName=" + inviterName);
        
        if (playerUuid == null || factionId == null) {
            ConfigUtil.debug("invitePlayerToFaction failed: null parameters");
            return false;
        }
        
        FactionInfo faction = factions.get(factionId);
        if (faction == null) {
            ConfigUtil.debug("invitePlayerToFaction failed: faction not found with ID=" + factionId);
            return false;
        }
        
        // Check if player is already in a faction
        if (playerToFaction.containsKey(playerUuid)) {
            ConfigUtil.debug("invitePlayerToFaction failed: player " + playerUuid + " is already in a faction");
            return false;
        }
        
        // Check if player already has an invite from this faction (in-memory lookup)
        List<com.factioncore.invite.PendingInvite> existingInvites = 
            pendingInvites.getOrDefault(playerUuid, new ArrayList<>());
        for (com.factioncore.invite.PendingInvite invite : existingInvites) {
            if (invite.getFactionId().equals(factionId)) {
                ConfigUtil.debug("invitePlayerToFaction failed: player already has invite from this faction");
                return false;
            }
        }
        
        // Create and add invite to in-memory map
        com.factioncore.invite.PendingInvite invite = new com.factioncore.invite.PendingInvite(
            playerUuid,
            factionId,
            inviterName != null ? inviterName : "Unknown",
            java.time.LocalDateTime.now().toString()
        );
        
        pendingInvites.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(invite);
        
        // Queue save to database
        databaseManager.savePendingInvite(invite);
        markDirty();
        
        ConfigUtil.debug("invitePlayerToFaction: added invite for player " + playerUuid + " to faction " + faction.getName());
        return true;
    }
    
    /**
     * Clears all pending invites for a player.
     * Called when a player joins or creates a faction.
     * 
     * @param playerUuid The player UUID
     */
    public void clearPendingInvitesForPlayer(UUID playerUuid) {
        ConfigUtil.debug("clearPendingInvitesForPlayer called: playerUuid=" + playerUuid);
        
        if (playerUuid == null) {
            return;
        }
        
        List<com.factioncore.invite.PendingInvite> invites = 
            pendingInvites.remove(playerUuid);
        if (invites != null && !invites.isEmpty()) {
            // Queue delete from database
            databaseManager.deletePendingInvitesForPlayer(playerUuid);
            markDirty();
            ConfigUtil.debug("clearPendingInvitesForPlayer: removed " + invites.size() + " invites for player " + playerUuid);
        }
    }
    
    /**
     * Gets pending invites for a player.
     * Uses in-memory map for fast lookup (O(1)).
     * 
     * @param playerUuid The player UUID
     * @return List of pending invites
     */
    public List<com.factioncore.invite.PendingInvite> getPendingInvitesForPlayer(UUID playerUuid) {
        return new ArrayList<>(pendingInvites.getOrDefault(playerUuid, new ArrayList<>()));
    }
    
    /**
     * Gets all pending invites for a faction.
     * 
     * @param factionId The faction UUID
     * @return List of pending invites for this faction
     */
    public List<com.factioncore.invite.PendingInvite> getPendingInvitesForFaction(UUID factionId) {
        ConfigUtil.debug("getPendingInvitesForFaction called: factionId=" + factionId);
        
        if (factionId == null) {
            return new ArrayList<>();
        }
        
        List<com.factioncore.invite.PendingInvite> factionInvites = new ArrayList<>();
        
        // Search in-memory map
        for (List<com.factioncore.invite.PendingInvite> playerInvites : pendingInvites.values()) {
            for (com.factioncore.invite.PendingInvite invite : playerInvites) {
                if (invite.getFactionId().equals(factionId)) {
                    factionInvites.add(invite);
                }
            }
        }
        
        ConfigUtil.debug("getPendingInvitesForFaction: found " + factionInvites.size() + " invites for faction " + factionId);
        return factionInvites;
    }
    
    /**
     * Cancels a pending invite.
     * 
     * @param playerUuid The UUID of the invited player
     * @param factionId The UUID of the faction
     * @return true if invite was found and removed, false otherwise
     */
    public boolean cancelPendingInvite(UUID playerUuid, UUID factionId) {
        ConfigUtil.debug("cancelPendingInvite called: playerUuid=" + playerUuid + ", factionId=" + factionId);
        
        if (playerUuid == null || factionId == null) {
            ConfigUtil.debug("cancelPendingInvite failed: null parameters");
            return false;
        }
        
        List<com.factioncore.invite.PendingInvite> invites = 
            pendingInvites.get(playerUuid);
        
        if (invites == null) {
            ConfigUtil.debug("cancelPendingInvite: invite not found");
            return false;
        }
        
        boolean found = invites.removeIf(invite -> invite.getFactionId().equals(factionId));
        
        if (found) {
            // Remove empty list
            if (invites.isEmpty()) {
                pendingInvites.remove(playerUuid);
            }
            
            // Queue delete from database
            databaseManager.deletePendingInvite(playerUuid, factionId);
            markDirty();
            
            ConfigUtil.debug("cancelPendingInvite: removed invite for player " + playerUuid + " from faction " + factionId);
        } else {
            ConfigUtil.debug("cancelPendingInvite: invite not found");
        }
        
        return found;
    }
    
    /**
     * Shuts down the saving thread and database manager (called on plugin shutdown).
     */
    public void shutdown() {
        savingThreadRunning.set(false);
        if (savingExecutor != null) {
            savingExecutor.shutdown();
        }
        
        // Final save of all data
        if (isDirty) {
            try {
                ConfigUtil.debug("Final save on shutdown: saving all data to database...");
                
                // Save all factions
                saveFactions();
                
                // Save all claims
                for (Map.Entry<String, Map<String, ChunkInfo>> entry : chunks.entrySet()) {
                    String dimension = entry.getKey();
                    for (ChunkInfo chunk : entry.getValue().values()) {
                        databaseManager.saveClaim(dimension, chunk);
                    }
                }
                
                // Save all pending invites
                for (List<com.factioncore.invite.PendingInvite> playerInvites : pendingInvites.values()) {
                    for (com.factioncore.invite.PendingInvite invite : playerInvites) {
                        databaseManager.savePendingInvite(invite);
                    }
                }
                
                ConfigUtil.debug("Final save on shutdown: queued all data saves");
            } catch (Exception e) {
                getLogger().at(Level.SEVERE).log("Failed to save data on shutdown: " + e.getMessage());
            }
        }
        
        // Shutdown database manager
        databaseManager.shutdown();
    }
}
