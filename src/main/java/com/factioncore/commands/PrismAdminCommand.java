package com.factioncore.commands;

import com.factioncore.faction.FactionManager;
import com.factioncore.ui.factionprism.PrismAdminPage;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Command handler for /f prismadmin.
 * Opens the prism attacks admin UI listing all prism attacks with status and timer values.
 * Requires factioncore.admin.prismadmin or factioncore.admin.* permission.
 */
public class PrismAdminCommand extends AbstractAsyncCommand {

    public PrismAdminCommand() {
        super("prismadmin", "Opens prism attacks admin UI (list all attacks, status, timers)");
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
        if (world == null) {
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.must_be_in_world")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        // Store/ref must only be accessed on the world thread
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                commandContext.sendMessage(Message.raw(I18n.get("chat.command.no_player_ref")).color(Color.RED));
                return;
            }
            if (!PermissionUtil.canOpenPrismAdmin(playerRef.getUuid())) {
                commandContext.sendMessage(Message.raw(I18n.get("chat.admin.no_permission")).color(Color.RED).bold(true));
                return;
            }
            FactionManager.getInstance().getDatabaseManager()
                .loadAllPrismAttacksAsync()
                .thenAccept((List<com.factioncore.prism.PrismAttackRecord> attacks) -> {
                    world.execute(() -> {
                        PrismAdminPage page = new PrismAdminPage(playerRef, attacks != null ? attacks : Collections.emptyList());
                        player.getPageManager().openCustomPage(ref, store, page);
                    });
                });
        }, (Executor) world);
    }
}
