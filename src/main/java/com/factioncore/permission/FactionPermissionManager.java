package com.factioncore.permission;

import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Thread-safe singleton manager for faction member permissions.
 * Delegates to FactionManager's in-memory member permissions map (DB-backed, no LuckPerms).
 */
public class FactionPermissionManager {
    
    private static FactionPermissionManager INSTANCE;
    private static HytaleLogger LOGGER;
    
    // Shutdown flag (for graceful shutdown of any remaining executor)
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    // Single-threaded executor (kept for shutdown; no longer used for LuckPerms)
    private final ExecutorService luckPermsExecutor;
    
    // Scheduled executor for periodic sync (placeholder, no-op)
    private ScheduledExecutorService periodicSyncExecutor;
    
    private FactionPermissionManager() {
        this.luckPermsExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FactionCore-PermissionManager");
            t.setDaemon(true);
            return t;
        });
        startPeriodicSync();
        getLogger().at(Level.INFO).log("FactionPermissionManager initialized (delegates to FactionManager)");
    }
    
    /**
     * Gets the singleton instance.
     * 
     * @return The FactionPermissionManager instance
     */
    public static synchronized FactionPermissionManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FactionPermissionManager();
        }
        return INSTANCE;
    }
    
    /**
     * Gets the logger instance, initializing it lazily if needed.
     * 
     * @return The HytaleLogger instance (never null)
     */
    private static HytaleLogger getLogger() {
        if (LOGGER == null) {
            LOGGER = HytaleLogger.forEnclosingClass();
        }
        return LOGGER;
    }
    
    /**
     * Grants a permission to a member in a faction. Delegates to FactionManager (in-memory map, DB-backed).
     */
    public void grantPermission(UUID memberUuid, String permission, UUID factionId) {
        if (memberUuid == null || permission == null || factionId == null) {
            ConfigUtil.debug("FactionPermissionManager.grantPermission(): null parameter");
            return;
        }
        Set<String> perms = new HashSet<>(FactionManager.getInstance().getMemberPermissions(factionId, memberUuid));
        perms.add(permission);
        FactionManager.getInstance().setMemberPermissions(factionId, memberUuid, perms);
        ConfigUtil.debug("FactionPermissionManager.grantPermission(): granted " + permission + " to " + memberUuid + " in faction " + factionId);
    }
    
    /**
     * Revokes a permission from a member in a faction. Delegates to FactionManager.
     */
    public void revokePermission(UUID memberUuid, String permission, UUID factionId) {
        if (memberUuid == null || permission == null || factionId == null) {
            ConfigUtil.debug("FactionPermissionManager.revokePermission(): null parameter");
            return;
        }
        Set<String> perms = new HashSet<>(FactionManager.getInstance().getMemberPermissions(factionId, memberUuid));
        perms.remove(permission);
        FactionManager.getInstance().setMemberPermissions(factionId, memberUuid, perms);
        ConfigUtil.debug("FactionPermissionManager.revokePermission(): revoked " + permission + " from " + memberUuid + " in faction " + factionId);
    }
    
    /**
     * Checks if a member has a permission in a faction. Delegates to FactionManager.
     */
    public boolean hasPermission(UUID memberUuid, String permission, UUID factionId) {
        if (memberUuid == null || permission == null || factionId == null) {
            return false;
        }
        return FactionManager.getInstance().hasMemberPermission(factionId, memberUuid, permission);
    }
    
    /**
     * Gets all permissions for a member in a faction. Delegates to FactionManager.
     */
    public Set<String> getMemberPermissions(UUID memberUuid, UUID factionId) {
        if (memberUuid == null || factionId == null) {
            return new HashSet<>();
        }
        return FactionManager.getInstance().getMemberPermissions(factionId, memberUuid);
    }
    
    /**
     * Sets all permissions for a member in a faction. Delegates to FactionManager (in-memory map, DB-backed).
     */
    public void setMemberPermissions(UUID memberUuid, Set<String> permissions, UUID factionId) {
        if (memberUuid == null || factionId == null) {
            ConfigUtil.debug("FactionPermissionManager.setMemberPermissions(): null parameter");
            return;
        }
        FactionManager.getInstance().setMemberPermissions(factionId, memberUuid, permissions != null ? permissions : Collections.emptySet());
        ConfigUtil.debug("FactionPermissionManager.setMemberPermissions(): set " + (permissions != null ? permissions.size() : 0) + " permissions for " + memberUuid + " in faction " + factionId);
    }
    
    /**
     * Revokes all permissions for a member in a faction. Delegates to FactionManager.
     */
    public void revokeAllFactionPermissions(UUID memberUuid, UUID factionId) {
        if (memberUuid == null || factionId == null) {
            ConfigUtil.debug("FactionPermissionManager.revokeAllFactionPermissions(): null parameter");
            return;
        }
        FactionManager.getInstance().setMemberPermissions(factionId, memberUuid, Collections.emptySet());
        ConfigUtil.debug("FactionPermissionManager.revokeAllFactionPermissions(): revoked all permissions from " + memberUuid + " in faction " + factionId);
    }
    
    
    /**
     * Starts the periodic cache sync thread.
     * Polls LuckPerms every 30 seconds to detect external changes.
     */
    private void startPeriodicSync() {
        periodicSyncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FactionCore-PermissionSync");
            t.setDaemon(true);
            return t;
        });
        
        periodicSyncExecutor.scheduleAtFixedRate(this::periodicCacheSync, 30, 30, TimeUnit.SECONDS);
        ConfigUtil.debug("FactionPermissionManager: Started periodic cache sync (every 30 seconds)");
    }
    
    /**
     * Periodic cache sync: polls LuckPerms for permission changes.
     * This detects changes made externally (via LuckPerms web editor or commands).
     */
    private void periodicCacheSync() {
        if (shutdown.get()) {
            return;
        }
        
        try {
            // TODO: Implement periodic sync to check LuckPerms for changes
            // For now, this is a placeholder - the cache is the source of truth
            // In the future, we could poll LuckPerms and update cache if permissions changed externally
            ConfigUtil.debug("FactionPermissionManager.periodicCacheSync(): running (placeholder)");
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("FactionPermissionManager.periodicCacheSync(): error: " + e.getMessage());
        }
    }
    
    /**
     * Shuts down the manager and all executors.
     * Should be called on plugin disable.
     */
    public void shutdown() {
        if (shutdown.getAndSet(true)) {
            return; // Already shutdown
        }
        
        getLogger().at(Level.INFO).log("FactionPermissionManager: Shutting down...");
        
        // Shutdown periodic sync
        if (periodicSyncExecutor != null) {
            periodicSyncExecutor.shutdown();
            try {
                if (!periodicSyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    periodicSyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                periodicSyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Shutdown LuckPerms executor
        luckPermsExecutor.shutdown();
        try {
            if (!luckPermsExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                luckPermsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            luckPermsExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        getLogger().at(Level.INFO).log("FactionPermissionManager: Shutdown complete");
    }
}
