package com.factioncore.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Constants;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Utility class for managing Hytale's default permissions.json file.
 * 
 * This class reads and parses the permissions.json file located at the root of the
 * Hytale server directory. It provides permission checking functionality as a
 * fallback when LuckPerms is not available.
 * 
 * Permissions.json structure:
 * {
 *   "users": {
 *     "uuid": ["group1", "group2"]
 *   },
 *   "groups": {
 *     "groupName": ["permission1", "permission2", "*"]
 *   }
 * }
 */
public class HytalePermissionManager {
    
    private static HytaleLogger LOGGER;
    private static HytalePermissionManager INSTANCE;
    
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
                LOGGER = HytaleLogger.getLogger().getSubLogger("HytalePermissionManager");
            }
        }
        return LOGGER;
    }
    
    private final Path permissionsPath;
    private final File permissionsFile;
    private final Gson gson;
    
    private Map<UUID, List<String>> userGroups;
    private Map<String, List<String>> groupPermissions;
    private long lastModified;
    private boolean initialized = false;
    
    /**
     * Private constructor for singleton pattern.
     */
    private HytalePermissionManager() {
        this.gson = new GsonBuilder().create();
        
        // permissions.json is in the run directory (same level as universe/)
        // Constants.UNIVERSE_PATH points to run/universe (or just universe if relative)
        // We need to get the parent to reach run/, then look for permissions.json
        Path universePath = Constants.UNIVERSE_PATH;
        
        // Convert to absolute path first to ensure proper parent resolution
        Path absoluteUniversePath = universePath.toAbsolutePath();
        Path runDir = absoluteUniversePath.getParent(); // Go up from run/universe/ to run/
        
        // If parent is null, the universe path might be at root level
        // In that case, try to resolve permissions.json relative to universe's parent
        if (runDir == null) {
            // Fallback: try relative path resolution
            runDir = universePath.getParent();
            if (runDir == null) {
                // Last resort: permissions.json should be in the same directory as universe
                // This handles the case where universe is at the root
                runDir = universePath;
            }
        }
        
        this.permissionsPath = runDir.resolve("permissions.json");
        this.permissionsFile = this.permissionsPath.toFile();
        
        // Log the resolved path for debugging
        ConfigUtil.debug("HytalePermissionManager: Constants.UNIVERSE_PATH = " + universePath);
        ConfigUtil.debug("HytalePermissionManager: Absolute universe path = " + absoluteUniversePath);
        ConfigUtil.debug("HytalePermissionManager: Resolved run directory = " + runDir);
        ConfigUtil.debug("HytalePermissionManager: permissions.json path = " + this.permissionsPath.toAbsolutePath());
        ConfigUtil.debug("HytalePermissionManager: permissions.json exists = " + this.permissionsFile.exists());
        
        this.userGroups = new HashMap<>();
        this.groupPermissions = new HashMap<>();
        this.lastModified = 0;
        
        // Load permissions on initialization
        loadPermissions();
    }
    
    /**
     * Gets the singleton instance of HytalePermissionManager.
     * 
     * @return The HytalePermissionManager instance
     */
    public static synchronized HytalePermissionManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HytalePermissionManager();
        }
        return INSTANCE;
    }
    
    /**
     * Loads permissions from the permissions.json file.
     * Reloads automatically if the file has been modified.
     */
    public void loadPermissions() {
        if (!permissionsFile.exists()) {
            ConfigUtil.debug("permissions.json not found at " + permissionsPath + ", using empty permissions");
            userGroups.clear();
            groupPermissions.clear();
            initialized = true;
            return;
        }
        
        // Check if file has been modified
        long currentModified = permissionsFile.lastModified();
        if (initialized && currentModified == lastModified) {
            return; // No need to reload
        }
        
        lastModified = currentModified;
        
        try (FileReader reader = new FileReader(permissionsFile)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            
            if (root == null) {
                ConfigUtil.debug("permissions.json is null or empty");
                userGroups.clear();
                groupPermissions.clear();
                initialized = true;
                return;
            }
            
            // Parse users
            userGroups.clear();
            if (root.has("users") && root.get("users").isJsonObject()) {
                JsonObject users = root.getAsJsonObject("users");
                for (Map.Entry<String, JsonElement> entry : users.entrySet()) {
                    try {
                        UUID userUuid = UUID.fromString(entry.getKey());
                        List<String> groups = new ArrayList<>();
                        
                        JsonElement userValue = entry.getValue();
                        
                        // Handle nested object structure: {"uuid": {"groups": ["group1", "group2"]}}
                        if (userValue.isJsonObject()) {
                            JsonObject userObj = userValue.getAsJsonObject();
                            if (userObj.has("groups") && userObj.get("groups").isJsonArray()) {
                                JsonArray groupsArray = userObj.getAsJsonArray("groups");
                                for (JsonElement groupElement : groupsArray) {
                                    if (groupElement.isJsonPrimitive()) {
                                        groups.add(groupElement.getAsString());
                                    }
                                }
                            }
                        }
                        // Handle direct array structure: {"uuid": ["group1", "group2"]}
                        else if (userValue.isJsonArray()) {
                            JsonArray groupsArray = userValue.getAsJsonArray();
                            for (JsonElement groupElement : groupsArray) {
                                if (groupElement.isJsonPrimitive()) {
                                    groups.add(groupElement.getAsString());
                                }
                            }
                        }
                        
                        ConfigUtil.debug("HytalePermissionManager: parsed user " + userUuid + " with groups: " + groups);
                        userGroups.put(userUuid, groups);
                    } catch (IllegalArgumentException e) {
                        ConfigUtil.debug("Invalid UUID in permissions.json: " + entry.getKey());
                    }
                }
            }
            
            // Parse groups
            groupPermissions.clear();
            if (root.has("groups") && root.get("groups").isJsonObject()) {
                JsonObject groups = root.getAsJsonObject("groups");
                for (Map.Entry<String, JsonElement> entry : groups.entrySet()) {
                    String groupName = entry.getKey();
                    List<String> permissions = new ArrayList<>();
                    
                    if (entry.getValue().isJsonArray()) {
                        JsonArray permissionsArray = entry.getValue().getAsJsonArray();
                        for (JsonElement permElement : permissionsArray) {
                            if (permElement.isJsonPrimitive()) {
                                permissions.add(permElement.getAsString());
                            }
                        }
                    }
                    
                    groupPermissions.put(groupName, permissions);
                }
            }
            
            initialized = true;
            ConfigUtil.debug("Loaded permissions.json: " + userGroups.size() + " users, " + groupPermissions.size() + " groups");
            
        } catch (IOException e) {
            getLogger().at(java.util.logging.Level.WARNING).log("Failed to read permissions.json: " + e.getMessage());
            ConfigUtil.debug("Failed to read permissions.json: " + e.getMessage(), e);
            userGroups.clear();
            groupPermissions.clear();
            initialized = true;
        } catch (Exception e) {
            getLogger().at(java.util.logging.Level.WARNING).log("Error parsing permissions.json: " + e.getMessage());
            ConfigUtil.debug("Error parsing permissions.json: " + e.getMessage(), e);
            userGroups.clear();
            groupPermissions.clear();
            initialized = true;
        }
    }
    
    /**
     * Checks if a player has a specific permission.
     * 
     * @param playerUuid The UUID of the player
     * @param permission The permission node to check (e.g., "factioncore.create_faction")
     * @return true if the player has the permission, false otherwise
     */
    public boolean hasPermission(UUID playerUuid, String permission) {
        if (!initialized) {
            loadPermissions();
        }
        
        // Reload if file has been modified
        if (permissionsFile.exists() && permissionsFile.lastModified() != lastModified) {
            loadPermissions();
        }
        
        if (playerUuid == null || permission == null || permission.isEmpty()) {
            ConfigUtil.debug("HytalePermissionManager.hasPermission(): playerUuid or permission is null/empty");
            return false;
        }
        
        ConfigUtil.debug("HytalePermissionManager.hasPermission(): checking permission " + permission + " for player " + playerUuid);
        ConfigUtil.debug("HytalePermissionManager.hasPermission(): userGroups map size = " + userGroups.size());
        ConfigUtil.debug("HytalePermissionManager.hasPermission(): groupPermissions map size = " + groupPermissions.size());
        
        // Get player's groups
        List<String> groups = userGroups.get(playerUuid);
        ConfigUtil.debug("HytalePermissionManager.hasPermission(): player groups = " + (groups != null ? groups.toString() : "null"));
        
        if (groups == null || groups.isEmpty()) {
            // Player not in permissions.json, check if they're in Default group
            // Default group typically has no permissions, so return false
            ConfigUtil.debug("HytalePermissionManager.hasPermission(): player not found in userGroups map");
            // Debug: show all UUIDs in the map
            ConfigUtil.debug("HytalePermissionManager.hasPermission(): all UUIDs in map = " + userGroups.keySet());
            return false;
        }
        
        // Check each group for the permission
        for (String groupName : groups) {
            ConfigUtil.debug("HytalePermissionManager.hasPermission(): checking group " + groupName);
            List<String> permissions = groupPermissions.get(groupName);
            ConfigUtil.debug("HytalePermissionManager.hasPermission(): group " + groupName + " permissions = " + (permissions != null ? permissions.toString() : "null"));
            
            if (permissions == null) {
                ConfigUtil.debug("HytalePermissionManager.hasPermission(): group " + groupName + " not found in groupPermissions map");
                continue;
            }
            
            // Check if group has "*" (all permissions) or the specific permission
            for (String perm : permissions) {
                if ("*".equals(perm)) {
                    // Group has all permissions
                    ConfigUtil.debug("HytalePermissionManager.hasPermission(): group " + groupName + " has wildcard *, granting permission");
                    return true;
                }
                if (permission.equals(perm)) {
                    // Group has the specific permission
                    ConfigUtil.debug("HytalePermissionManager.hasPermission(): group " + groupName + " has exact permission match, granting");
                    return true;
                }
                // Check for wildcard permissions (e.g., "factioncore.*" matches "factioncore.create_faction")
                if (perm.endsWith(".*") && permission.startsWith(perm.substring(0, perm.length() - 2))) {
                    ConfigUtil.debug("HytalePermissionManager.hasPermission(): group " + groupName + " has wildcard permission " + perm + " matching " + permission);
                    return true;
                }
            }
        }
        
        ConfigUtil.debug("HytalePermissionManager.hasPermission(): no matching permission found, denying");
        return false;
    }
    
    /**
     * Checks if a player is in the OP group (which typically has "*" permission).
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player is in the OP group, false otherwise
     */
    public boolean isOp(UUID playerUuid) {
        if (!initialized) {
            loadPermissions();
        }
        
        List<String> groups = userGroups.get(playerUuid);
        return groups != null && groups.contains("OP");
    }
    
    /**
     * Gets all groups a player belongs to.
     * 
     * @param playerUuid The UUID of the player
     * @return List of group names, or empty list if player is not in permissions.json
     */
    public List<String> getPlayerGroups(UUID playerUuid) {
        if (!initialized) {
            loadPermissions();
        }
        
        List<String> groups = userGroups.get(playerUuid);
        return groups != null ? new ArrayList<>(groups) : new ArrayList<>();
    }
    
    /**
     * Forces a reload of the permissions.json file.
     */
    public void reload() {
        initialized = false;
        loadPermissions();
    }
}
