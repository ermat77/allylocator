package com.factioncore.commands;

import com.factioncore.ui.PrismPage;
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
 * Command handler for /f prism.
 * Opens UI with "Test prism" (enter flat 16-chunk world with prefab) or "Leave prism" (return and delete world).
 */
public class PrismFactionCommand extends AbstractAsyncCommand {

    public PrismFactionCommand() {
        super("prism", "Enter or leave your prism (temporary flat world)");
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        ConfigUtil.debug("PrismFactionCommand.executeAsync() called");

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
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                commandContext.sendMessage(Message.raw(I18n.get("chat.command.no_player_ref")).color(Color.RED));
                return;
            }

            UUID playerUuid = playerRef.getUuid();
            if (!PermissionUtil.canUsePrism(playerUuid)) {
                commandContext.sendMessage(Message.raw(I18n.get("chat.prism.no_permission")).color(Color.RED).bold(true));
                return;
            }

            PrismPage page = new PrismPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
        }, (Executor) world);
    }
}
