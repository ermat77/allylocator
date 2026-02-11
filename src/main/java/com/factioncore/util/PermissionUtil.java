package com.factioncore.util;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Utility class for checking permissions using LuckPerms API.
 * 
 * This plugin REQUIRES LuckPerms to be installed. All permissions are managed
 * exclusively through LuckPerms.
 * 
 * Based on LuckPerms API documentation:
 * - Uses LuckPermsProvider.get() to get the API instance
 * - Handles IllegalStateException if API is not loaded
 * - Uses cached permission data for efficient permission checks
 */
public class PermissionUtil {
    
    private static HytaleLogger LOGGER;
    
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
    
    // Player permission constants (use factioncore.player.* for default group)
    /**
     * Permission constant for creating factions.
     */
    public static final String PERM_CREATE_FACTION = "factioncore.player.create_faction";
    
    /**
     * Permission constant for viewing faction information.
     */
    public static final String PERM_SHOW_FACTION = "factioncore.player.show";

    /**
     * Permission constant for using /f prism (enter/leave prism world).
     */
    public static final String PERM_PRISM = "factioncore.player.prism";

    /**
     * Permission constant for claiming chunks.
     */
    public static final String PERM_CLAIM_CHUNK = "factioncore.player.claim";
    
    /**
     * Permission constant for unclaiming chunks.
     */
    public static final String PERM_UNCLAIM_CHUNK = "factioncore.player.unclaim";
    
    /**
     * Permission constant for viewing chunk claim information.
     */
    public static final String PERM_CLAIM_INFO = "factioncore.player.claiminfo";
    
    /**
     * Permission constant for renaming factions.
     */
    public static final String PERM_RENAME_FACTION = "factioncore.player.rename";
    
    /**
     * Permission constant for changing faction color.
     */
    public static final String PERM_CHANGE_COLOR = "factioncore.player.color";
    
    /**
     * Permission constant for disbanding factions.
     */
    public static final String PERM_DISBAND_FACTION = "factioncore.player.disband";
    
    /**
     * Permission constant for accessing faction settings.
     */
    public static final String PERM_SETTINGS = "factioncore.player.settings";
    
    /**
     * Permission constant for viewing faction members.
     */
    public static final String PERM_VIEW_MEMBERS = "factioncore.player.members";
    
    /**
     * Permission constant for using the /spawn command (delayed teleport to world spawn).
     * Included in factioncore.player.* so players with that group can use /spawn.
     */
    public static final String PERM_SPAWN = "factioncore.player.spawn";
    
    // Faction member permission constants (granted by faction owner)
    public static final String FACTION_PERM_RENAME = "ownfaction.rename";
    public static final String FACTION_PERM_CHANGE_COLOR = "ownfaction.change_color";
    public static final String FACTION_PERM_INVITE_MEMBERS = "ownfaction.invite_members";
    public static final String FACTION_PERM_KICK_MEMBERS = "ownfaction.kick_members";
    public static final String FACTION_PERM_MANAGE_ALLIES = "ownfaction.manage_allies";
    public static final String FACTION_PERM_MANAGE_ENEMIES = "ownfaction.manage_enemies";
    public static final String FACTION_PERM_CLAIM_CHUNK = "ownfaction.claim_chunk";
    public static final String FACTION_PERM_UNCLAIM_CHUNK = "ownfaction.unclaim_chunk";
    public static final String FACTION_PERM_SET_HOME = "ownfaction.set_home";
    public static final String FACTION_PERM_HOME = "ownfaction.home";

    /** Own-faction interaction permissions (context faction=faction-uuid). */
    public static final String FACTION_PERM_OPEN_CHEST = "factioncore.ownfaction.open_chest";
    public static final String FACTION_PERM_OPEN_DOOR = "factioncore.ownfaction.open_door";
    public static final String FACTION_PERM_OPEN_BENCH = "factioncore.ownfaction.open_bench";
    public static final String FACTION_PERM_USE_CHAIR = "factioncore.ownfaction.use_chair";
    public static final String FACTION_PERM_USE_PORTAL = "factioncore.ownfaction.use_portal";
    public static final String FACTION_PERM_PLACE_BLOCK = "factioncore.ownfaction.place_block";
    public static final String FACTION_PERM_BREAK_BLOCK = "factioncore.ownfaction.break_block";

    /** Ally-faction interaction permissions (context faction=faction-name). */
    public static final String ALLY_PERM_OPEN_CHEST = "factioncore.allyfaction.open_chest";
    public static final String ALLY_PERM_OPEN_DOOR = "factioncore.allyfaction.open_door";
    public static final String ALLY_PERM_OPEN_BENCH = "factioncore.allyfaction.open_bench";
    public static final String ALLY_PERM_USE_CHAIR = "factioncore.allyfaction.use_chair";
    public static final String ALLY_PERM_USE_PORTAL = "factioncore.allyfaction.use_portal";
    public static final String ALLY_PERM_PLACE_BLOCK = "factioncore.allyfaction.place_block";
    public static final String ALLY_PERM_BREAK_BLOCK = "factioncore.allyfaction.break_block";

    /** Permission key suffixes for mapping block types (e.g. "open_chest", "open_door"). */
    public static final String INTERACT_KEY_OPEN_CHEST = "open_chest";
    public static final String INTERACT_KEY_OPEN_DOOR = "open_door";
    public static final String INTERACT_KEY_OPEN_BENCH = "open_bench";
    public static final String INTERACT_KEY_USE_CHAIR = "use_chair";
    public static final String INTERACT_KEY_USE_PORTAL = "use_portal";
    public static final String INTERACT_KEY_PLACE_BLOCK = "place_block";
    public static final String INTERACT_KEY_BREAK_BLOCK = "break_block";

    // Admin permission constants (for actions on ANY faction)
    // Grouped under factioncore.admin.* for wildcard support
    public static final String PERM_ADMIN_RENAME = "factioncore.admin.rename";
    public static final String PERM_ADMIN_CHANGE_COLOR = "factioncore.admin.changeColor";
    public static final String PERM_ADMIN_DISBAND = "factioncore.admin.disband";
    public static final String PERM_ADMIN_ADD_MEMBER = "factioncore.admin.addMember";
    public static final String PERM_ADMIN_REMOVE_MEMBER = "factioncore.admin.removeMember";
    public static final String PERM_ADMIN_KICK_MEMBER = "factioncore.admin.kickMember";
    public static final String PERM_ADMIN_MANAGE_MEMBER_PERMISSIONS = "factioncore.admin.manageMemberPermissions";
    public static final String PERM_ADMIN_CLAIM_CHUNK = "factioncore.admin.claimChunk";
    public static final String PERM_ADMIN_UNCLAIM_CHUNK = "factioncore.admin.unclaimChunk";
    public static final String PERM_ADMIN_BYPASS_PROTECTION = "factioncore.admin.bypassProtection";
    /**
     * Permission to open any faction's UI as superadmin (/f admin &lt;name&gt;).
     */
    public static final String PERM_ADMIN_OPEN_FACTION = "factioncore.admin.openFaction";
    
    // Cache the classloader that can load LuckPerms classes
    private static ClassLoader luckPermsClassLoader = null;
    
    /**
     * Gets the classloader that can load LuckPerms classes, or null if not available.
     * Tries multiple classloaders in order of preference.
     */
    private static ClassLoader getLuckPermsClassLoader() {
        if (luckPermsClassLoader != null) {
            return luckPermsClassLoader;
        }
        
        try {
            // First, try using Hytale's PluginBridgeClassLoader (most reliable for plugin-to-plugin access)
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
                            luckPermsClassLoader = bridgeClassLoader;
                            return bridgeClassLoader;
                        } catch (ClassNotFoundException e) {
                            // Continue to next attempt
                        }
                    }
                }
            } catch (Exception e) {
                // PluginManager not available or reflection failed - continue to other methods
            }
            
            // Try context classloader
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                try {
                    Class.forName("net.luckperms.api.LuckPermsProvider", false, contextClassLoader);
                    luckPermsClassLoader = contextClassLoader;
                    return contextClassLoader;
                } catch (ClassNotFoundException e) {
                    // Continue to next attempt
                }
            }
            
            // Try system classloader
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            if (systemClassLoader != null) {
                try {
                    Class.forName("net.luckperms.api.LuckPermsProvider", false, systemClassLoader);
                    luckPermsClassLoader = systemClassLoader;
                    return systemClassLoader;
                } catch (ClassNotFoundException e) {
                    // Continue to next attempt
                }
            }
            
            // Try default classloader (current class's classloader)
            try {
                Class.forName("net.luckperms.api.LuckPermsProvider");
                luckPermsClassLoader = PermissionUtil.class.getClassLoader();
                return luckPermsClassLoader;
            } catch (ClassNotFoundException e) {
                // Not found
            }
        } catch (Exception e) {
            // Error getting classloader
        }
        
        return null;
    }
    
    /**
     * Checks if a player has a specific permission using LuckPerms.
     * 
     * @param playerUuid The UUID of the player
     * @param permission The permission node to check
     * @return true if the player has the permission, false otherwise.
     * @throws IllegalStateException if LuckPerms is not available (should not happen if startup check works)
     */
    public static boolean hasPermission(UUID playerUuid, String permission) {
        try {
            // Use reflection to access LuckPerms API classes
            // This allows the code to compile even if LuckPerms API is not on the classpath
            ClassLoader classLoader = getLuckPermsClassLoader();
            if (classLoader == null) {
                getLogger().at(Level.SEVERE).log("LuckPerms classloader is null - LuckPerms may not be installed!");
                return false;
            }
            
            Class<?> luckPermsProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider", false, classLoader);
            java.lang.reflect.Method getMethod = luckPermsProviderClass.getMethod("get");
            Object api = getMethod.invoke(null);
            
            // Get UserManager
            java.lang.reflect.Method getUserManagerMethod = api.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(api);
            
            // Try getUser() first (fast path for already-loaded users)
            java.lang.reflect.Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, playerUuid);
            
            // If user is not loaded, use loadUser() to load from storage
            if (user == null) {
                ConfigUtil.debug("Player " + playerUuid + " not in memory, loading from LuckPerms storage...");
                try {
                    // loadUser() returns CompletableFuture<User>
                    java.lang.reflect.Method loadUserMethod = userManager.getClass().getMethod("loadUser", UUID.class);
                    java.util.concurrent.CompletableFuture<?> userFuture = (java.util.concurrent.CompletableFuture<?>) loadUserMethod.invoke(userManager, playerUuid);
                    
                    // Wait for user to load (since we're already in async context, this is safe)
                    user = userFuture.join();
                    
                    if (user == null) {
                        ConfigUtil.debug("Player " + playerUuid + " not found in LuckPerms after loading");
                        return false;
                    }
                    ConfigUtil.debug("Player " + playerUuid + " loaded from LuckPerms storage");
                } catch (Exception loadException) {
                    ConfigUtil.debug("Error loading user from LuckPerms: " + loadException.getMessage());
                    return false;
                }
            }
            
            // Get cached data
            java.lang.reflect.Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            
            // Get permission data
            java.lang.reflect.Method getPermissionDataMethod = cachedData.getClass().getMethod("getPermissionData");
            Object permissionData = getPermissionDataMethod.invoke(cachedData);
            
            // Check permission - checkPermission returns a Tristate
            java.lang.reflect.Method checkPermissionMethod = permissionData.getClass().getMethod("checkPermission", String.class);
            Object tristate = checkPermissionMethod.invoke(permissionData, permission);
            
            // Convert Tristate to boolean - asBoolean() converts undefined to false
            java.lang.reflect.Method asBooleanMethod = tristate.getClass().getMethod("asBoolean");
            Boolean result = (Boolean) asBooleanMethod.invoke(tristate);
            
            ConfigUtil.debug("Player " + playerUuid + " permission check for " + permission + " (via LuckPerms): " + (result != null && result));
            
            return result != null && result;
            
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Check if the underlying exception is IllegalStateException (API not loaded)
            Throwable cause = e.getCause();
            if (cause instanceof IllegalStateException) {
                // LuckPerms API is not loaded
                getLogger().at(Level.SEVERE).log("LuckPerms API not loaded: " + cause.getMessage());
                return false;
            }
            // Other invocation errors
            getLogger().at(Level.SEVERE).log("Error invoking LuckPerms API: " + e.getMessage());
            if (cause != null) {
                getLogger().at(Level.SEVERE).log("Underlying cause: " + cause.getClass().getName() + ": " + cause.getMessage());
            }
            return false;
        } catch (Exception e) {
            // On any other error
            getLogger().at(Level.SEVERE).log("Error checking LuckPerms permission: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if a player has a specific permission OR a wildcard permission.
     * Used for admin permissions where both specific and wildcard (factioncore.admin.*) should be checked.
     * 
     * @param playerUuid The UUID of the player
     * @param permission The specific permission node to check
     * @param wildcardPermission The wildcard permission to also check (e.g., "factioncore.admin.*")
     * @return true if the player has either permission, false otherwise
     */
    private static boolean hasPermissionOrWildcard(UUID playerUuid, String permission, String wildcardPermission) {
        return hasPermission(playerUuid, permission) || hasPermission(playerUuid, wildcardPermission);
    }
    
    /**
     * Checks if a player has a permission with a specific context using LuckPerms.
     * This is used for faction member permissions with faction context.
     * 
     * @param playerUuid The UUID of the player
     * @param permission The permission node to check (e.g., "faction.rename")
     * @param contextKey The context key (e.g., "faction")
     * @param contextValue The context value (e.g., faction UUID as string)
     * @return true if the player has the permission with the context, false otherwise
     */
    public static boolean hasPermissionWithContext(UUID playerUuid, String permission, String contextKey, String contextValue) {
        try {
            // Use reflection to access LuckPerms API classes
            ClassLoader classLoader = getLuckPermsClassLoader();
            if (classLoader == null) {
                getLogger().at(Level.SEVERE).log("LuckPerms classloader is null - LuckPerms may not be installed!");
                return false;
            }
            
            Class<?> luckPermsProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider", false, classLoader);
            java.lang.reflect.Method getMethod = luckPermsProviderClass.getMethod("get");
            Object api = getMethod.invoke(null);
            
            // Get UserManager
            java.lang.reflect.Method getUserManagerMethod = api.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(api);
            
            // Try getUser() first (fast path for already-loaded users)
            java.lang.reflect.Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, playerUuid);
            
            // If user is not loaded, use loadUser() to load from storage
            if (user == null) {
                ConfigUtil.debug("Player " + playerUuid + " not in memory, loading from LuckPerms storage...");
                try {
                    java.lang.reflect.Method loadUserMethod = userManager.getClass().getMethod("loadUser", UUID.class);
                    java.util.concurrent.CompletableFuture<?> userFuture = (java.util.concurrent.CompletableFuture<?>) loadUserMethod.invoke(userManager, playerUuid);
                    user = userFuture.join();
                    
                    if (user == null) {
                        ConfigUtil.debug("Player " + playerUuid + " not found in LuckPerms after loading");
                        return false;
                    }
                    ConfigUtil.debug("Player " + playerUuid + " loaded from LuckPerms storage");
                } catch (Exception loadException) {
                    ConfigUtil.debug("Error loading user from LuckPerms: " + loadException.getMessage());
                    return false;
                }
            }
            
            // Get cached data
            java.lang.reflect.Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            
            // Get permission data
            java.lang.reflect.Method getPermissionDataMethod = cachedData.getClass().getMethod("getPermissionData");
            Object permissionData = getPermissionDataMethod.invoke(cachedData);
            
            // Create context set
            Class<?> contextSetClass = Class.forName("net.luckperms.api.context.ContextSet", false, classLoader);
            Class<?> contextKeyClass = Class.forName("net.luckperms.api.context.ContextKey", false, classLoader);
            Class<?> contextValueClass = Class.forName("net.luckperms.api.context.ContextValue", false, classLoader);
            
            java.lang.reflect.Method ofMethod = contextSetClass.getMethod("of", Object.class, Object.class);
            Object contextKeyObj = contextKeyClass.getMethod("of", String.class).invoke(null, contextKey);
            Object contextValueObj = contextValueClass.getMethod("of", String.class).invoke(null, contextValue);
            Object contextSet = ofMethod.invoke(null, contextKeyObj, contextValueObj);
            
            // Check permission with context - checkPermission returns a Tristate
            java.lang.reflect.Method checkPermissionMethod = permissionData.getClass().getMethod("checkPermission", String.class, contextSetClass);
            Object tristate = checkPermissionMethod.invoke(permissionData, permission, contextSet);
            
            // Convert Tristate to boolean - asBoolean() converts undefined to false
            java.lang.reflect.Method asBooleanMethod = tristate.getClass().getMethod("asBoolean");
            Boolean result = (Boolean) asBooleanMethod.invoke(tristate);
            
            ConfigUtil.debug("Player " + playerUuid + " permission check for " + permission + " with context " + contextKey + "=" + contextValue + " (via LuckPerms): " + (result != null && result));
            
            return result != null && result;
            
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Check if the underlying exception is IllegalStateException (API not loaded)
            Throwable cause = e.getCause();
            if (cause instanceof IllegalStateException) {
                // LuckPerms API is not loaded
                getLogger().at(Level.SEVERE).log("LuckPerms API not loaded: " + cause.getMessage());
                return false;
            }
            // Other invocation errors
            getLogger().at(Level.SEVERE).log("Error invoking LuckPerms API: " + e.getMessage());
            if (cause != null) {
                getLogger().at(Level.SEVERE).log("Underlying cause: " + cause.getClass().getName() + ": " + cause.getMessage());
            }
            return false;
        } catch (Exception e) {
            // On any other error
            getLogger().at(Level.SEVERE).log("Error checking LuckPerms permission with context: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if a player has permission to create factions.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.create_faction permission
     */
    public static boolean canCreateFaction(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_CREATE_FACTION);
    }
    
    /**
     * Checks if a player has permission to view faction information.
     * 
     * This permission is available to everyone by default.
     * 
     * @param playerUuid The UUID of the player
     * @return true by default (permission available to everyone)
     */
    public static boolean canShowFaction(UUID playerUuid) {
        // Available to everyone by default - always return true
        return true;
    }

    /**
     * Checks if a player has permission to use /f prism (enter/leave prism).
     *
     * @param playerUuid The UUID of the player
     * @return true if the player has permission
     */
    public static boolean canUsePrism(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_PRISM);
    }

    /**
     * Checks if a player has permission to claim chunks.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.claim permission
     */
    public static boolean canClaimChunk(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_CLAIM_CHUNK);
    }
    
    /**
     * Checks if a player has permission to unclaim chunks.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.unclaim permission
     */
    public static boolean canUnclaimChunk(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_UNCLAIM_CHUNK);
    }
    
    /**
     * Checks if a player has permission to view chunk claim information.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.claiminfo permission
     */
    public static boolean canViewClaimInfo(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_CLAIM_INFO);
    }
    
    /**
     * Checks if a player has permission to rename factions.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.rename permission
     */
    public static boolean canRenameFaction(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_RENAME_FACTION);
    }
    
    /**
     * Checks if a player has permission to change faction color.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.color permission
     */
    public static boolean canChangeColor(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_CHANGE_COLOR);
    }
    
    /**
     * Checks if a player has permission to disband factions.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.disband permission
     */
    public static boolean canDisbandFaction(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_DISBAND_FACTION);
    }
    
    /**
     * Checks if a player has permission to access faction settings.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.settings permission
     */
    public static boolean canAccessSettings(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_SETTINGS);
    }
    
    /**
     * Checks if a player has permission to view faction members.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has the factioncore.player.members permission
     */
    public static boolean canViewMembers(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_VIEW_MEMBERS);
    }
    
    /**
     * Checks if a player has permission to use the /spawn command.
     * Requires factioncore.player.spawn (included in factioncore.player.*).
     */
    public static boolean canUseSpawn(UUID playerUuid) {
        return hasPermission(playerUuid, PERM_SPAWN);
    }
    
    // Admin permission check methods (for actions on ANY faction)
    
    /**
     * Checks if a player has admin permission to rename any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.rename or factioncore.admin.* permission
     */
    public static boolean canRenameFactionAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_RENAME, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to change color of any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.changeColor or factioncore.admin.* permission
     */
    public static boolean canChangeFactionColorAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_CHANGE_COLOR, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to disband any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.disband or factioncore.admin.* permission
     */
    public static boolean canDisbandFactionAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_DISBAND, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to add members to any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.addMember or factioncore.admin.* permission
     */
    public static boolean canAddMemberAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_ADD_MEMBER, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to remove members from any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.removeMember or factioncore.admin.* permission
     */
    public static boolean canRemoveMemberAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_REMOVE_MEMBER, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to kick members from any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.kickMember or factioncore.admin.* permission
     */
    public static boolean canKickMemberAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_KICK_MEMBER, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to manage member permissions for any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.manageMemberPermissions or factioncore.admin.* permission
     */
    public static boolean canManageMemberPermissionsAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_MANAGE_MEMBER_PERMISSIONS, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to claim chunks for any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.claimChunk or factioncore.admin.* permission
     */
    public static boolean canClaimChunkAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_CLAIM_CHUNK, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to unclaim chunks for any faction.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.unclaimChunk or factioncore.admin.* permission
     */
    public static boolean canUnclaimChunkAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_UNCLAIM_CHUNK, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player has admin permission to bypass chunk protection.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.bypassProtection or factioncore.admin.* permission
     */
    public static boolean canBypassProtectionAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_BYPASS_PROTECTION, "factioncore.admin.*");
    }
    
    /**
     * Checks if a player can open any faction's UI as superadmin (/f admin &lt;name&gt;).
     *
     * @param playerUuid The UUID of the player
     * @return true if the player has factioncore.admin.openFaction or factioncore.admin.* permission
     */
    public static boolean canOpenFactionAdmin(UUID playerUuid) {
        return hasPermissionOrWildcard(playerUuid, PERM_ADMIN_OPEN_FACTION, "factioncore.admin.*");
    }
    
    // Faction member permission checks (checks owner, admin permissions, AND faction member permissions)
    
    /**
     * Checks if a player can rename a faction.
     * Checks: owner status, admin permission, or faction member permission.
     * 
     * @param playerUuid The UUID of the player
     * @param faction The faction to check permissions for
     * @return true if player is owner, has admin permission, or has been granted the faction.rename permission by owner
     */
    public static boolean canRenameFactionInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        // Owner always has permission
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        // Check admin permission (can rename any faction)
        if (canRenameFactionAdmin(playerUuid)) {
            return true;
        }
        // Check faction member permission (granted by owner) via FactionPermissionManager
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_RENAME, faction.getId());
    }
    
    /**
     * Checks if a player can change faction color.
     * Checks: owner status, admin permission, or faction member permission.
     * 
     * @param playerUuid The UUID of the player
     * @param faction The faction to check permissions for
     * @return true if player is owner, has admin permission, or has been granted the faction.change_color permission by owner
     */
    public static boolean canChangeColorInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        // Owner always has permission
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        // Check admin permission (can change color of any faction)
        if (canChangeFactionColorAdmin(playerUuid)) {
            return true;
        }
        // Check faction member permission (granted by owner) via FactionPermissionManager
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_CHANGE_COLOR, faction.getId());
    }
    
    /**
     * Checks if a player can invite members to a faction.
     * Checks: owner status, admin permission, or faction member permission.
     * 
     * @param playerUuid The UUID of the player
     * @param faction The faction to check permissions for
     * @return true if player is owner, has admin permission, or has faction member permission
     */
    public static boolean canInviteMembersInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        // Owner always has permission
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        // Check admin permission (can add members to any faction)
        if (canAddMemberAdmin(playerUuid)) {
            return true;
        }
        // Check faction member permission via FactionPermissionManager
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_INVITE_MEMBERS, faction.getId());
    }
    
    /**
     * Checks if a player can manage allies for a faction.
     * Checks: owner status, admin permission, or faction member permission.
     * 
     * @param playerUuid The UUID of the player
     * @param faction The faction to check permissions for
     * @return true if player is owner, has admin permission, or has faction member permission
     */
    public static boolean canManageAlliesInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        // Owner always has permission
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        // Check admin permission (can manage any faction)
        if (canManageMemberPermissionsAdmin(playerUuid)) {
            return true;
        }
        // Check faction member permission via FactionPermissionManager
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_MANAGE_ALLIES, faction.getId());
    }
    
    /**
     * Checks if a player can manage enemies for a faction.
     * Checks: owner status, admin permission, or faction member permission.
     * 
     * @param playerUuid The UUID of the player
     * @param faction The faction to check permissions for
     * @return true if player is owner, has admin permission, or has faction member permission
     */
    public static boolean canManageEnemiesInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        // Owner always has permission
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        // Check admin permission (can manage any faction)
        if (canManageMemberPermissionsAdmin(playerUuid)) {
            return true;
        }
        // Check faction member permission via FactionPermissionManager
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_MANAGE_ENEMIES, faction.getId());
    }
    
    /**
     * Checks if a player can kick members from a faction.
     * Checks: owner status, admin permission, or faction member permission.
     * 
     * @param playerUuid The UUID of the player
     * @param faction The faction to check permissions for
     * @return true if player is owner, has admin permission, or has faction member permission
     */
    public static boolean canKickMembersInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        // Owner always has permission
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        // Check admin permission (can kick members from any faction)
        if (canKickMemberAdmin(playerUuid)) {
            return true;
        }
        // Check faction member permission via FactionPermissionManager
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_KICK_MEMBERS, faction.getId());
    }
    
    /**
     * Checks if a player can claim chunks for a faction.
     * Checks: owner status, admin permission, or faction member permission.
     * 
     * @param playerUuid The UUID of the player
     * @param faction The faction to check permissions for
     * @return true if player is owner, has admin permission, or has been granted the faction.claim_chunk permission by owner
     */
    public static boolean canClaimChunkInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        // Owner always has permission
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        // Check admin permission (can claim chunks for any faction)
        if (canClaimChunkAdmin(playerUuid)) {
            return true;
        }
        // Check faction member permission (granted by owner) via FactionPermissionManager
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_CLAIM_CHUNK, faction.getId());
    }
    
    /**
     * Checks if a player can unclaim chunks for a faction.
     * Checks: owner status, admin permission, or faction member permission.
     * 
     * @param playerUuid The UUID of the player
     * @param faction The faction to check permissions for
     * @return true if player is owner, has admin permission, or has been granted the faction.unclaim_chunk permission by owner
     */
    public static boolean canUnclaimChunkInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        // Owner always has permission
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        // Check admin permission (can unclaim chunks for any faction)
        if (canUnclaimChunkAdmin(playerUuid)) {
            return true;
        }
        // Check faction member permission (granted by owner) via FactionPermissionManager
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_UNCLAIM_CHUNK, faction.getId());
    }
    
    /**
     * Checks if a player can set the faction home (/f sethome).
     * Checks: owner status, admin permission, or faction member permission.
     */
    public static boolean canSetHomeInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        if (canManageMemberPermissionsAdmin(playerUuid)) {
            return true;
        }
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_SET_HOME, faction.getId());
    }
    
    /**
     * Checks if a player can use faction home (/f home).
     * Checks: owner status, admin permission, or faction member permission.
     */
    public static boolean canUseHomeInFaction(UUID playerUuid, com.factioncore.faction.FactionInfo faction) {
        if (faction == null) {
            return false;
        }
        if (faction.isOwner(playerUuid)) {
            return true;
        }
        if (canManageMemberPermissionsAdmin(playerUuid)) {
            return true;
        }
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(playerUuid, FACTION_PERM_HOME, faction.getId());
    }
}
