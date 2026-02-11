package com.factioncore.commands;

import com.factioncore.Main;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Command handler for /f home
 * Teleports the player to their faction home location.
 */
public class HomeFactionCommand extends AbstractAsyncCommand {

    public HomeFactionCommand() {
        super("home", "Teleport to your faction home");
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
                player.sendMessage(Message.raw(I18n.get("chat.home.must_be_in_faction")).color(Color.RED));
                return;
            }

            if (!PermissionUtil.canUseHomeInFaction(playerUuid, faction)) {
                player.sendMessage(Message.raw(I18n.get("chat.home.no_permission")).color(Color.RED));
                return;
            }

            if (!faction.hasHome()) {
                player.sendMessage(Message.raw(I18n.get("chat.home.not_set")).color(Color.RED));
                return;
            }

            String homeDimension = faction.getHomeDimension();
            double homeX = faction.getHomeX();
            double homeY = faction.getHomeY();
            double homeZ = faction.getHomeZ();

            Universe universe = Universe.get();
            if (universe == null) {
                player.sendMessage(Message.raw(I18n.get("chat.home.error_world")).color(Color.RED));
                return;
            }
            Map<String, World> worlds = universe.getWorlds();
            if (worlds == null) {
                player.sendMessage(Message.raw(I18n.get("chat.home.error_world")).color(Color.RED));
                return;
            }
            World targetWorld = worlds.get(homeDimension);
            if (targetWorld == null) {
                ConfigUtil.debug("HomeFactionCommand: world not found: " + homeDimension);
                player.sendMessage(Message.raw(I18n.get("chat.home.error_world")).color(Color.RED));
                return;
            }

            Transform transform = new Transform(homeX, homeY, homeZ);
            // Players with * (wildcard) permission bypass delayed teleport
            boolean bypassDelay = PermissionUtil.hasPermission(playerUuid, "*");
            boolean delayed = false;
            if (!bypassDelay) {
                Object delayTpApi = Main.get().getDelayTpApi();
                ConfigUtil.debug("HomeFactionCommand: getDelayTpApi()=" + (delayTpApi != null ? delayTpApi.getClass().getName() : "null"));
                delayed = DelayTpHelper.requestDelayedTeleport(delayTpApi, "fhome", ref, store, targetWorld, transform);
                ConfigUtil.debug("HomeFactionCommand: requestDelayedTeleport(fhome) returned delayed=" + delayed);
            } else {
                ConfigUtil.debug("HomeFactionCommand: player has * permission, bypassing delayed teleport");
            }

            if (delayed) {
                player.sendMessage(Message.raw(I18n.get("chat.home.delayed")).color(Color.GREEN));
                ConfigUtil.debug("HomeFactionCommand: delayed teleport scheduled for player " + playerUuid);
            } else {
                Teleport teleport = Teleport.createForPlayer(targetWorld, transform);
                store.addComponent(ref, Teleport.getComponentType(), teleport);
                player.sendMessage(Message.raw(I18n.get("chat.home.teleported")).color(Color.GREEN));
                ConfigUtil.debug("HomeFactionCommand: teleported player " + playerUuid + " to faction home " + homeDimension + " " + homeX + "," + homeY + "," + homeZ);
            }
        }, (Executor) world);
    }
}
