package com.factioncore.permission;

import com.factioncore.diplomacy.AllySettings;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Syncs ally interaction permissions to LuckPerms with context faction=faction-name.
 * When our faction allows an ally faction to open chests etc., each member of the ally
 * gets factioncore.allyfaction.open_chest (etc.) with context faction=ourFactionName.
 */
public class AllyPermissionService {

    private static AllyPermissionService INSTANCE;
    private static HytaleLogger LOGGER;
    private static final String FACTION_CONTEXT_KEY = "faction";

    private final ExecutorService luckPermsExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FactionCore-AllyLuckPermsSync");
        t.setDaemon(true);
        return t;
    });

    private AllyPermissionService() {
        getLogger().at(Level.INFO).log("AllyPermissionService initialized");
    }

    public static synchronized AllyPermissionService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AllyPermissionService();
        }
        return INSTANCE;
    }

    private static HytaleLogger getLogger() {
        if (LOGGER == null) {
            LOGGER = HytaleLogger.forEnclosingClass();
        }
        return LOGGER;
    }

    /**
     * Syncs ally permissions for all members of the ally faction when our faction's ally settings are saved.
     * For each member of allyFactionId, sets or removes factioncore.allyfaction.* nodes with context faction=ourFactionName.
     */
    public void syncAllyFactionMembers(UUID ourFactionId, UUID allyFactionId, AllySettings settings) {
        FactionManager manager = FactionManager.getInstance();
        FactionInfo ourFaction = manager.getFactionById(ourFactionId);
        FactionInfo allyFaction = manager.getFactionById(allyFactionId);
        if (ourFaction == null || allyFaction == null) return;
        String ourFactionName = ourFaction.getName() != null ? ourFaction.getName() : ourFactionId.toString();

        Set<UUID> memberUuids = new HashSet<>();
        if (allyFaction.getOwner() != null) memberUuids.add(allyFaction.getOwner());
        memberUuids.addAll(Arrays.asList(allyFaction.getMembers()));

        for (UUID memberUuid : memberUuids) {
            syncAllyPermissionsForUser(memberUuid, ourFactionName, settings);
        }
    }

    /**
     * Revokes all ally permissions for a user who left a faction.
     * For every faction that had leftFactionId as ally, revokes factioncore.allyfaction.* with context faction=that faction's name.
     */
    public void revokeAllAllyPermissionsForUser(UUID memberUuid, UUID leftFactionId) {
        FactionManager manager = FactionManager.getInstance();
        Set<UUID> ourFactionIds = manager.getFactionIdsThatHaveAsAlly(leftFactionId);
        AllySettings allFalse = new AllySettings(false, false, false, false, false, false, false, false, false);
        for (UUID ourFactionId : ourFactionIds) {
            FactionInfo ourFaction = manager.getFactionById(ourFactionId);
            if (ourFaction == null) continue;
            String ourFactionName = ourFaction.getName() != null ? ourFaction.getName() : ourFactionId.toString();
            syncAllyPermissionsForUser(memberUuid, ourFactionName, allFalse);
        }
    }

    /**
     * Revokes ally permissions for all members of exAllyFactionId when our faction breaks the alliance.
     * Removes factioncore.allyfaction.* with context faction=ourFactionName for each member of exAllyFactionId.
     */
    public void revokeAllyPermissionsForFaction(UUID ourFactionId, UUID exAllyFactionId) {
        FactionManager manager = FactionManager.getInstance();
        FactionInfo ourFaction = manager.getFactionById(ourFactionId);
        FactionInfo exAllyFaction = manager.getFactionById(exAllyFactionId);
        if (ourFaction == null || exAllyFaction == null) return;
        String ourFactionName = ourFaction.getName() != null ? ourFaction.getName() : ourFactionId.toString();
        AllySettings allFalse = new AllySettings(false, false, false, false, false, false, false, false, false);
        Set<UUID> memberUuids = new HashSet<>();
        if (exAllyFaction.getOwner() != null) memberUuids.add(exAllyFaction.getOwner());
        memberUuids.addAll(Arrays.asList(exAllyFaction.getMembers()));
        for (UUID memberUuid : memberUuids) {
            syncAllyPermissionsForUser(memberUuid, ourFactionName, allFalse);
        }
    }

    /**
     * Applies ally permissions to a single user (e.g. when they join a faction).
     * Called for each faction that has joinedFactionId as ally; grants this user the permissions that faction allows for the ally.
     */
    public void applyAllyPermissionsFromFactionsToUser(UUID memberUuid, UUID joinedFactionId) {
        FactionManager manager = FactionManager.getInstance();
        FactionInfo joinedFaction = manager.getFactionById(joinedFactionId);
        if (joinedFaction == null) return;

        // Find all factions that have joinedFactionId as ally
        Set<UUID> ourFactionIds = manager.getFactionIdsThatHaveAsAlly(joinedFactionId);
        for (UUID ourFactionId : ourFactionIds) {
            AllySettings settings = manager.getAllySettings(ourFactionId, joinedFactionId);
            FactionInfo ourFaction = manager.getFactionById(ourFactionId);
            if (ourFaction == null) continue;
            String ourFactionName = ourFaction.getName() != null ? ourFaction.getName() : ourFactionId.toString();
            syncAllyPermissionsForUser(memberUuid, ourFactionName, settings);
        }
    }

    private void syncAllyPermissionsForUser(UUID memberUuid, String factionName, AllySettings settings) {
        if (memberUuid == null || factionName == null || settings == null) return;
        luckPermsExecutor.submit(() -> {
            syncOne(memberUuid, PermissionUtil.ALLY_PERM_OPEN_CHEST, factionName, settings.isAllowOpenChest());
            syncOne(memberUuid, PermissionUtil.ALLY_PERM_OPEN_DOOR, factionName, settings.isAllowOpenDoor());
            syncOne(memberUuid, PermissionUtil.ALLY_PERM_OPEN_BENCH, factionName, settings.isAllowOpenBench());
            syncOne(memberUuid, PermissionUtil.ALLY_PERM_USE_CHAIR, factionName, settings.isAllowUseChair());
            syncOne(memberUuid, PermissionUtil.ALLY_PERM_USE_PORTAL, factionName, settings.isAllowUsePortal());
            syncOne(memberUuid, PermissionUtil.ALLY_PERM_PLACE_BLOCK, factionName, settings.isAllowPlaceBlock());
            syncOne(memberUuid, PermissionUtil.ALLY_PERM_BREAK_BLOCK, factionName, settings.isAllowBreakBlock());
        });
    }

    private void syncOne(UUID memberUuid, String permission, String factionName, boolean grant) {
        try {
            ClassLoader classLoader = PermissionUtil.class.getClassLoader();
            try {
                Class<?> pm = Class.forName("com.hypixel.hytale.server.core.plugin.PluginManager");
                Object pluginManager = pm.getMethod("get").invoke(null);
                if (pluginManager != null) {
                    ClassLoader bridge = (ClassLoader) pm.getMethod("getBridgeClassLoader").invoke(pluginManager);
                    if (bridge != null) {
                        Class.forName("net.luckperms.api.LuckPermsProvider", false, bridge);
                        classLoader = bridge;
                    }
                }
            } catch (Exception e) { /* use default */ }

            Class<?> luckPermsProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider", false, classLoader);
            Object api = luckPermsProviderClass.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, memberUuid);
            if (user == null) {
                java.util.concurrent.CompletableFuture<?> fut = (java.util.concurrent.CompletableFuture<?>) userManager.getClass().getMethod("loadUser", UUID.class).invoke(userManager, memberUuid);
                user = fut.join();
            }
            if (user == null) return;

            Object userData = user.getClass().getMethod("data").invoke(user);
            Class<?> contextSetClass = Class.forName("net.luckperms.api.context.ContextSet", false, classLoader);
            Class<?> contextKeyClass = Class.forName("net.luckperms.api.context.ContextKey", false, classLoader);
            Class<?> contextValueClass = Class.forName("net.luckperms.api.context.ContextValue", false, classLoader);
            Object contextKey = contextKeyClass.getMethod("of", String.class).invoke(null, FACTION_CONTEXT_KEY);
            Object contextValue = contextValueClass.getMethod("of", String.class).invoke(null, factionName);
            Object contextSet = contextSetClass.getMethod("of", Object.class, Object.class).invoke(null, contextKey, contextValue);

            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node", false, classLoader);
            Object nodeBuilder = nodeClass.getMethod("builder", String.class).invoke(null, permission);
            Object node = nodeBuilder.getClass().getMethod("withContext", contextSetClass).invoke(nodeBuilder, contextSet);

            if (grant) {
                userData.getClass().getMethod("add", nodeClass).invoke(userData, node);
            } else {
                userData.getClass().getMethod("remove", nodeClass).invoke(userData, node);
            }
            userManager.getClass().getMethod("saveUser", user.getClass()).invoke(userManager, user);
            ConfigUtil.debug("AllyPermissionService: synced " + permission + " for " + memberUuid + " context faction=" + factionName + " grant=" + grant);
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("AllyPermissionService.syncOne failed: " + e.getMessage());
        }
    }
}
