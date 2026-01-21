package com.factioncore.faction;

import com.hypixel.hytale.logger.HytaleLogger;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.PermissionUtil;
import com.factioncore.config.GeneralConfig;
import com.factioncore.claim.ChunkInfo;
import com.factioncore.claim.tracking.ModifiedTracking;
import com.factioncore.database.DatabaseManager;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.factioncore.Main;

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
 * Data is stored in the universe directory (not mods/) to keep it with world/server data.
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
    
    // Invite system (in-memory)
    private final Map<UUID, List<com.factioncore.invite.PendingInvite>> pendingInvites; // player UUID -> list of invites
    
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
        this.pendingInvites = new ConcurrentHashMap<>();
        
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
            databaseManager.loadPendingInvitesAsync().thenAccept(this::populateInvites)
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
        ConfigUtil.verboseDebug("getFactionFromPlayer called: playerUuid=" + playerUuid);
        
        if (playerUuid == null) {
            ConfigUtil.verboseDebug("getFactionFromPlayer: playerUuid is null, returning null");
            return null;
        }
        
        UUID factionId = playerToFaction.get(playerUuid);
        ConfigUtil.verboseDebug("getFactionFromPlayer: playerToFaction lookup result=" + factionId);
        
        if (factionId == null) {
            ConfigUtil.verboseDebug("getFactionFromPlayer: no faction found for player " + playerUuid);
            return null;
        }
        
        FactionInfo faction = factions.get(factionId);
        ConfigUtil.verboseDebug("getFactionFromPlayer: faction lookup result=" + (faction != null ? faction.getName() : "null"));
        
        return faction;
    }
    
    /**
     * Gets a faction by its ID.
     * 
     * @param factionId The faction UUID
     * @return The FactionInfo, or null if not found
     */
    public FactionInfo getFactionById(UUID factionId) {
        ConfigUtil.debug("getFactionById called: factionId=" + factionId);
        
        if (factionId == null) {
            ConfigUtil.debug("getFactionById: factionId is null, returning null");
            return null;
        }
        
        FactionInfo faction = factions.get(factionId);
        ConfigUtil.debug("getFactionById: result=" + (faction != null ? faction.getName() : "null"));
        
        return faction;
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
            // Revoke all faction permissions for this player (cleanup)
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
        Main main = Main.get();
        if (main != null && main.getClaimConfig() != null) {
            return main.getClaimConfig().get().getDefaultFactionClaimsAmount();
        }
        return 25; // Default
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
