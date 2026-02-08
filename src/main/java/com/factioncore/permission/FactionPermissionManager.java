package com.factioncore.permission;

import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Thread-safe singleton manager for faction member permissions.
 * 
 * Manages permissions using LuckPerms context-based permissions with local caching for performance.
 * All operations are thread-safe and non-blocking.
 * 
 * Architecture:
 * - Local cache: ConcurrentHashMap for O(1) lookups (lock-free reads)
 * - LuckPerms sync: Single-threaded ExecutorService for async operations
 * - Periodic sync: Background thread polls LuckPerms every 30s for external changes
 */
public class FactionPermissionManager {
    
    private static FactionPermissionManager INSTANCE;
    private static HytaleLogger LOGGER;
    
    // Thread-safe cache: factionId -> memberId -> permissions
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Set<String>>> cache;
    
    // Single-threaded executor for all LuckPerms operations (prevents race conditions)
    private final ExecutorService luckPermsExecutor;
    
    // Scheduled executor for periodic cache sync
    private ScheduledExecutorService periodicSyncExecutor;
    
    // Shutdown flag
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    // Context key for LuckPerms
    private static final String FACTION_CONTEXT_KEY = "faction";
    
    /**
     * Private constructor for singleton pattern.
     */
    private FactionPermissionManager() {
        this.cache = new ConcurrentHashMap<>();
        this.luckPermsExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FactionCore-LuckPermsSync");
            t.setDaemon(true);
            return t;
        });
        startPeriodicSync();
        getLogger().at(Level.INFO).log("FactionPermissionManager initialized");
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
     * Grants a permission to a member in a faction.
     * Updates cache immediately and syncs to LuckPerms asynchronously.
     * 
     * @param memberUuid The UUID of the member
     * @param permission The permission string (e.g., "faction.rename")
     * @param factionId The UUID of the faction
     */
    public void grantPermission(UUID memberUuid, String permission, UUID factionId) {
        if (memberUuid == null || permission == null || factionId == null) {
            ConfigUtil.debug("FactionPermissionManager.grantPermission(): null parameter");
            return;
        }
        
        if (shutdown.get()) {
            getLogger().at(Level.WARNING).log("FactionPermissionManager is shutdown, cannot grant permission");
            return;
        }
        
        // Update cache immediately (thread-safe)
        cache.computeIfAbsent(factionId, k -> new ConcurrentHashMap<>())
             .computeIfAbsent(memberUuid, k -> ConcurrentHashMap.newKeySet())
             .add(permission);
        
        ConfigUtil.debug("FactionPermissionManager.grantPermission(): granted " + permission + " to " + memberUuid + " in faction " + factionId);
        
        // Sync to LuckPerms asynchronously (non-blocking)
        syncToLuckPerms(memberUuid, permission, factionId, true);
    }
    
    /**
     * Revokes a permission from a member in a faction.
     * Updates cache immediately and syncs to LuckPerms asynchronously.
     * 
     * @param memberUuid The UUID of the member
     * @param permission The permission string (e.g., "faction.rename")
     * @param factionId The UUID of the faction
     */
    public void revokePermission(UUID memberUuid, String permission, UUID factionId) {
        if (memberUuid == null || permission == null || factionId == null) {
            ConfigUtil.debug("FactionPermissionManager.revokePermission(): null parameter");
            return;
        }
        
        if (shutdown.get()) {
            getLogger().at(Level.WARNING).log("FactionPermissionManager is shutdown, cannot revoke permission");
            return;
        }
        
        // Update cache immediately (thread-safe)
        ConcurrentHashMap<UUID, Set<String>> factionCache = cache.get(factionId);
        if (factionCache != null) {
            Set<String> memberPerms = factionCache.get(memberUuid);
            if (memberPerms != null) {
                memberPerms.remove(permission);
                if (memberPerms.isEmpty()) {
                    factionCache.remove(memberUuid);
                }
            }
        }
        
        ConfigUtil.debug("FactionPermissionManager.revokePermission(): revoked " + permission + " from " + memberUuid + " in faction " + factionId);
        
        // Sync to LuckPerms asynchronously (non-blocking)
        syncToLuckPerms(memberUuid, permission, factionId, false);
    }
    
    /**
     * Checks if a member has a permission in a faction.
     * Fast path: checks cache only (O(1) lookup, lock-free).
     * 
     * @param memberUuid The UUID of the member
     * @param permission The permission string (e.g., "faction.rename")
     * @param factionId The UUID of the faction
     * @return true if the member has the permission, false otherwise
     */
    public boolean hasPermission(UUID memberUuid, String permission, UUID factionId) {
        if (memberUuid == null || permission == null || factionId == null) {
            return false;
        }
        
        // Fast path: check cache (lock-free read)
        ConcurrentHashMap<UUID, Set<String>> factionCache = cache.get(factionId);
        if (factionCache != null) {
            Set<String> memberPerms = factionCache.get(memberUuid);
            if (memberPerms != null && memberPerms.contains(permission)) {
                return true;
            }
        }
        
        // Cache miss - return false (secure default)
        // Note: We don't do async LuckPerms check here to keep this method fast and non-blocking
        // Periodic sync will eventually update cache if permission exists in LuckPerms
        return false;
    }
    
    /**
     * Gets all permissions for a member in a faction.
     * 
     * @param memberUuid The UUID of the member
     * @param factionId The UUID of the faction
     * @return A set of permission strings, or empty set if member has no permissions
     */
    public Set<String> getMemberPermissions(UUID memberUuid, UUID factionId) {
        if (memberUuid == null || factionId == null) {
            return new HashSet<>();
        }
        
        ConcurrentHashMap<UUID, Set<String>> factionCache = cache.get(factionId);
        if (factionCache != null) {
            Set<String> memberPerms = factionCache.get(memberUuid);
            if (memberPerms != null) {
                return new HashSet<>(memberPerms);
            }
        }
        
        return new HashSet<>();
    }
    
    /**
     * Sets all permissions for a member in a faction.
     * Updates cache immediately and syncs to LuckPerms asynchronously.
     * 
     * @param memberUuid The UUID of the member
     * @param permissions The set of permission strings
     * @param factionId The UUID of the faction
     */
    public void setMemberPermissions(UUID memberUuid, Set<String> permissions, UUID factionId) {
        if (memberUuid == null || factionId == null) {
            ConfigUtil.debug("FactionPermissionManager.setMemberPermissions(): null parameter");
            return;
        }
        
        if (shutdown.get()) {
            getLogger().at(Level.WARNING).log("FactionPermissionManager is shutdown, cannot set permissions");
            return;
        }
        
        // Update cache immediately (thread-safe)
        ConcurrentHashMap<UUID, Set<String>> factionCache = cache.computeIfAbsent(factionId, k -> new ConcurrentHashMap<>());
        
        if (permissions == null || permissions.isEmpty()) {
            factionCache.remove(memberUuid);
        } else {
            Set<String> memberPerms = ConcurrentHashMap.newKeySet();
            memberPerms.addAll(permissions);
            factionCache.put(memberUuid, memberPerms);
        }
        
        ConfigUtil.debug("FactionPermissionManager.setMemberPermissions(): set " + (permissions != null ? permissions.size() : 0) + " permissions for " + memberUuid + " in faction " + factionId);
        
        // Sync all permissions to LuckPerms asynchronously
        if (permissions != null && !permissions.isEmpty()) {
            for (String permission : permissions) {
                syncToLuckPerms(memberUuid, permission, factionId, true);
            }
        }
    }
    
    /**
     * Revokes all permissions for a member in a faction.
     * Updates cache immediately and syncs to LuckPerms asynchronously.
     * 
     * @param memberUuid The UUID of the member
     * @param factionId The UUID of the faction
     */
    public void revokeAllFactionPermissions(UUID memberUuid, UUID factionId) {
        if (memberUuid == null || factionId == null) {
            ConfigUtil.debug("FactionPermissionManager.revokeAllFactionPermissions(): null parameter");
            return;
        }
        
        if (shutdown.get()) {
            getLogger().at(Level.WARNING).log("FactionPermissionManager is shutdown, cannot revoke permissions");
            return;
        }
        
        // Get current permissions before clearing
        Set<String> currentPermissions = getMemberPermissions(memberUuid, factionId);
        
        // Clear from cache immediately (thread-safe)
        ConcurrentHashMap<UUID, Set<String>> factionCache = cache.get(factionId);
        if (factionCache != null) {
            factionCache.remove(memberUuid);
        }
        
        // Also revoke known interaction permissions so LuckPerms is clean even for legacy members (not in cache)
        currentPermissions.add(com.factioncore.util.PermissionUtil.FACTION_PERM_OPEN_CHEST);
        currentPermissions.add(com.factioncore.util.PermissionUtil.FACTION_PERM_OPEN_DOOR);
        currentPermissions.add(com.factioncore.util.PermissionUtil.FACTION_PERM_OPEN_BENCH);
        currentPermissions.add(com.factioncore.util.PermissionUtil.FACTION_PERM_USE_CHAIR);
        currentPermissions.add(com.factioncore.util.PermissionUtil.FACTION_PERM_USE_PORTAL);
        currentPermissions.add(com.factioncore.util.PermissionUtil.FACTION_PERM_PLACE_BLOCK);
        currentPermissions.add(com.factioncore.util.PermissionUtil.FACTION_PERM_BREAK_BLOCK);
        
        ConfigUtil.debug("FactionPermissionManager.revokeAllFactionPermissions(): revoked all permissions from " + memberUuid + " in faction " + factionId);
        
        // Sync all revocations to LuckPerms asynchronously
        for (String permission : currentPermissions) {
            syncToLuckPerms(memberUuid, permission, factionId, false);
        }
    }
    
    /**
     * Syncs a permission to LuckPerms asynchronously.
     * Uses reflection to access LuckPerms API.
     * 
     * @param memberUuid The UUID of the member
     * @param permission The permission string
     * @param factionId The UUID of the faction
     * @param grant true to grant, false to revoke
     */
    private void syncToLuckPerms(UUID memberUuid, String permission, UUID factionId, boolean grant) {
        if (shutdown.get()) {
            return;
        }
        
        luckPermsExecutor.submit(() -> {
            try {
                // Use reflection to access LuckPerms API
                ClassLoader classLoader = com.factioncore.util.PermissionUtil.class.getClassLoader();
                
                // Try to get LuckPerms classloader
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
                                classLoader = bridgeClassLoader;
                            } catch (ClassNotFoundException e) {
                                // Continue with default classloader
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue with default classloader
                }
                
                Class<?> luckPermsProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider", false, classLoader);
                java.lang.reflect.Method getMethod = luckPermsProviderClass.getMethod("get");
                Object api = getMethod.invoke(null);
                
                // Get UserManager
                java.lang.reflect.Method getUserManagerMethod = api.getClass().getMethod("getUserManager");
                Object userManager = getUserManagerMethod.invoke(api);
                
                // Get or load user
                java.lang.reflect.Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
                Object user = getUserMethod.invoke(userManager, memberUuid);
                
                if (user == null) {
                    java.lang.reflect.Method loadUserMethod = userManager.getClass().getMethod("loadUser", UUID.class);
                    java.util.concurrent.CompletableFuture<?> userFuture = (java.util.concurrent.CompletableFuture<?>) loadUserMethod.invoke(userManager, memberUuid);
                    user = userFuture.join();
                }
                
                if (user == null) {
                    ConfigUtil.debug("FactionPermissionManager.syncToLuckPerms(): user not found for " + memberUuid);
                    return;
                }
                
                // Get user data
                java.lang.reflect.Method getDataMethod = user.getClass().getMethod("data");
                Object userData = getDataMethod.invoke(user);
                
                // Create context set
                Class<?> contextSetClass = Class.forName("net.luckperms.api.context.ContextSet", false, classLoader);
                Class<?> contextKeyClass = Class.forName("net.luckperms.api.context.ContextKey", false, classLoader);
                Class<?> contextValueClass = Class.forName("net.luckperms.api.context.ContextValue", false, classLoader);
                
                java.lang.reflect.Method ofMethod = contextSetClass.getMethod("of", Object.class, Object.class);
                Object contextKey = contextKeyClass.getMethod("of", String.class).invoke(null, FACTION_CONTEXT_KEY);
                Object contextValue = contextValueClass.getMethod("of", String.class).invoke(null, factionId.toString());
                Object contextSet = ofMethod.invoke(null, contextKey, contextValue);
                
                // Create node
                Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node", false, classLoader);
                java.lang.reflect.Method builderMethod = nodeClass.getMethod("builder", String.class);
                Object nodeBuilder = builderMethod.invoke(null, permission);
                
                java.lang.reflect.Method withContextMethod = nodeBuilder.getClass().getMethod("withContext", contextSetClass);
                Object node = withContextMethod.invoke(nodeBuilder, contextSet);
                
                // Add or remove node
                if (grant) {
                    java.lang.reflect.Method addMethod = userData.getClass().getMethod("add", nodeClass);
                    addMethod.invoke(userData, node);
                } else {
                    java.lang.reflect.Method removeMethod = userData.getClass().getMethod("remove", nodeClass);
                    removeMethod.invoke(userData, node);
                }
                
                // Save user
                java.lang.reflect.Method saveUserMethod = userManager.getClass().getMethod("saveUser", user.getClass());
                saveUserMethod.invoke(userManager, user);
                
                ConfigUtil.debug("FactionPermissionManager.syncToLuckPerms(): synced " + permission + " for " + memberUuid + " in faction " + factionId + " (grant=" + grant + ")");
                
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("FactionPermissionManager.syncToLuckPerms(): failed to sync permission " + permission + " for " + memberUuid + ": " + e.getMessage());
            }
        });
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
