package com.factioncore.commands;

import com.factioncore.ui.FactionShowPage;
import com.factioncore.util.ConfigUtil;
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
 * Command handler for /f show
 * Opens a UI page showing the player's current faction, or a form to create one if they don't have one.
 */
public class ShowFactionCommand extends AbstractAsyncCommand {
    
    /**
     * Creates a new ShowFactionCommand.
     */
    public ShowFactionCommand() {
        super("show", "Shows your faction information or create a new faction");
        setPermissionGroup(GameMode.Adventure);
    }
    
    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        ConfigUtil.debug("ShowFactionCommand.executeAsync() called");
        
        CommandSender sender = commandContext.sender();
        
        if (!(sender instanceof Player)) {
            ConfigUtil.debug("ShowFactionCommand: sender is not a Player");
            commandContext.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();
        
        if (ref == null || !ref.isValid()) {
            ConfigUtil.debug("ShowFactionCommand: player reference is invalid");
            commandContext.sendMessage(Message.raw("You must be in a world to use this command").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                ConfigUtil.debug("ShowFactionCommand: could not get PlayerRef");
                commandContext.sendMessage(Message.raw("Error: Could not get player reference").color(Color.RED));
                return;
            }
            
            UUID playerUuid = playerRef.getUuid();
            ConfigUtil.debug("ShowFactionCommand: opening UI for player " + playerUuid);
            
            // Check permission
            if (!PermissionUtil.canShowFaction(playerUuid)) {
                ConfigUtil.debug("ShowFactionCommand: player " + playerUuid + " does not have permission");
                commandContext.sendMessage(Message.raw("You don't have permission to view faction information")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Open the UI page using native Hytale UI system
            FactionShowPage page = new FactionShowPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
            
            ConfigUtil.debug("ShowFactionCommand: UI page opened successfully");
        }, (Executor) world);
    }
}
