package com.factioncore.commands;

import com.factioncore.Main;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.DelayTpHelper;
import com.factioncore.util.I18n;
import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Override of the native /spawn command that adds a delay (and cancel-on-damage) like /f home.
 * <p>
 * The server's CommandManager uses a single map by command name; the built-in TeleportPlugin
 * registers "spawn" first, then FactionCore registers this command with the same name "spawn",
 * so this implementation replaces the native one when the plugin loads.
 * <p>
 * Destination is the world's configured spawn (same as native), from
 * {@code world.getWorldConfig().getSpawnProvider().getSpawnPoint(world, playerUuid)}, with a
 * fallback to (0, 100, 0) if no spawn provider is set. Players with * permission bypass the delay.
 */
public class SpawnCommand extends AbstractAsyncCommand {

    private static final double DEFAULT_SPAWN_X = 0;
    private static final double DEFAULT_SPAWN_Y = 100;
    private static final double DEFAULT_SPAWN_Z = 0;

    public SpawnCommand() {
        super("spawn", "Teleport to spawn");
        setPermissionGroup(GameMode.Adventure);
    }

    /**
     * Do not use the framework's auto-generated permission (e.g. "spawn" which is OP-gated).
     * We use our own check for factioncore.player.spawn in executeAsync instead.
     */
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();

        if (!(sender instanceof Player)) {
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.players_only")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();

        if (ref == null || !ref.isValid()) {
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.must_be_in_world")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                commandContext.sendMessage(Message.raw(I18n.get("chat.command.no_player_ref")).color(Color.RED));
                return;
            }

            UUID playerUuid = playerRef.getUuid();
            if (!PermissionUtil.canUseSpawn(playerUuid)) {
                commandContext.sendMessage(Message.raw(I18n.get("chat.spawn.no_permission")).color(Color.RED));
                return;
            }
            World targetWorld = world;
            Transform transform = resolveSpawnTransform(world, playerUuid);

            // Players with * permission bypass delayed teleport (same as /f home)
            boolean bypassDelay = PermissionUtil.hasPermission(playerUuid, "*");
            boolean delayed = false;
            if (!bypassDelay) {
                Object delayTpApi = Main.get().getDelayTpApi();
                ConfigUtil.debug("SpawnCommand: getDelayTpApi()=" + (delayTpApi != null ? delayTpApi.getClass().getName() : "null"));
                delayed = DelayTpHelper.requestDelayedTeleport(delayTpApi, "spawn", ref, store, targetWorld, transform);
                ConfigUtil.debug("SpawnCommand: requestDelayedTeleport(spawn) returned delayed=" + delayed);
            } else {
                ConfigUtil.debug("SpawnCommand: player has * permission, bypassing delayed teleport");
            }

            if (delayed) {
                player.sendMessage(Message.raw(I18n.get("chat.spawn.delayed")).color(Color.GREEN));
                ConfigUtil.debug("SpawnCommand: delayed teleport scheduled for player " + playerUuid);
            } else {
                Teleport teleport = Teleport.createForPlayer(targetWorld, transform);
                store.addComponent(ref, Teleport.getComponentType(), teleport);
                player.sendMessage(Message.raw(I18n.get("chat.spawn.teleported")).color(Color.GREEN));
                ConfigUtil.debug("SpawnCommand: teleported player " + playerUuid + " to spawn");
            }
        }, (Executor) world);
    }

    /**
     * Resolves spawn transform from the world's spawn provider (same as native /spawn).
     * Falls back to (0, 100, 0) if the world has no spawn provider configured.
     */
    private static Transform resolveSpawnTransform(World world, UUID playerUuid) {
        if (world == null) {
            return new Transform(DEFAULT_SPAWN_X, DEFAULT_SPAWN_Y, DEFAULT_SPAWN_Z);
        }
        ISpawnProvider provider = world.getWorldConfig() != null ? world.getWorldConfig().getSpawnProvider() : null;
        if (provider == null) {
            return new Transform(DEFAULT_SPAWN_X, DEFAULT_SPAWN_Y, DEFAULT_SPAWN_Z);
        }
        try {
            return provider.getSpawnPoint(world, playerUuid);
        } catch (Exception e) {
            ConfigUtil.debug("SpawnCommand: spawn provider failed, using default: " + e.getMessage());
            return new Transform(DEFAULT_SPAWN_X, DEFAULT_SPAWN_Y, DEFAULT_SPAWN_Z);
        }
    }
}
