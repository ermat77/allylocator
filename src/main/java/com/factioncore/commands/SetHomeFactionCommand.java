package com.factioncore.commands;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.I18n;
import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Command handler for /f sethome
 * Sets the faction home to the player's current location.
 */
public class SetHomeFactionCommand extends AbstractAsyncCommand {

    public SetHomeFactionCommand() {
        super("sethome", "Set your faction home to your current location");
        setPermissionGroup(GameMode.Adventure);
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
            FactionManager manager = FactionManager.getInstance();
            FactionInfo faction = manager.getFactionFromPlayer(playerUuid);

            if (faction == null) {
                player.sendMessage(Message.raw(I18n.get("chat.sethome.must_be_in_faction")).color(Color.RED));
                return;
            }

            if (!PermissionUtil.canSetHomeInFaction(playerUuid, faction)) {
                player.sendMessage(Message.raw(I18n.get("chat.sethome.no_permission")).color(Color.RED));
                return;
            }

            World playerWorld = player.getWorld();
            if (playerWorld == null) {
                player.sendMessage(Message.raw(I18n.get("chat.sethome.error_world")).color(Color.RED));
                return;
            }

            double x = playerRef.getTransform().getPosition().getX();
            double y = playerRef.getTransform().getPosition().getY();
            double z = playerRef.getTransform().getPosition().getZ();
            String dimension = playerWorld.getName();

            manager.setFactionHome(faction.getId(), dimension, x, y, z);
            player.sendMessage(Message.raw(I18n.get("chat.sethome.success")).color(Color.GREEN));
            ConfigUtil.debug("SetHomeFactionCommand: faction " + faction.getName() + " home set to " + dimension + " " + x + "," + y + "," + z);
        }, (Executor) world);
    }
}
