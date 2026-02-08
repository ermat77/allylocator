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
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
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
 * Command handler for /f create <name>
 * Creates a new faction with the specified name.
 */
public class CreateFactionCommand extends AbstractAsyncCommand {
    
    private RequiredArg<String> name;
    
    /**
     * Creates a new CreateFactionCommand.
     */
    public CreateFactionCommand() {
        super("create", "Creates a new faction");
        setPermissionGroup(GameMode.Adventure);
        this.name = withRequiredArg("name", "The name of the faction", ArgTypes.STRING);
    }
    
    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        ConfigUtil.debug("CreateFactionCommand.executeAsync() called");
        
        CommandSender sender = commandContext.sender();
        
        if (!(sender instanceof Player)) {
            ConfigUtil.debug("CreateFactionCommand: sender is not a Player");
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.players_only")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();
        
        if (ref == null || !ref.isValid()) {
            ConfigUtil.debug("CreateFactionCommand: player reference is invalid");
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.must_be_in_world")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                ConfigUtil.debug("CreateFactionCommand: could not get PlayerRef");
                commandContext.sendMessage(Message.raw(I18n.get("chat.command.no_player_ref")).color(Color.RED));
                return;
            }
            
            UUID playerUuid = playerRef.getUuid();
            ConfigUtil.debug("CreateFactionCommand: processing for player " + playerUuid);
            
            // Check permission
            if (!PermissionUtil.canCreateFaction(playerUuid)) {
                ConfigUtil.debug("CreateFactionCommand: player " + playerUuid + " does not have permission");
                commandContext.sendMessage(Message.raw(I18n.get("chat.create.no_permission"))
                        .color(Color.RED).bold(true));
                return;
            }
            
            FactionManager factionManager = FactionManager.getInstance();
            
            // Check if player is already in a faction
            FactionInfo existingFaction = factionManager.getFactionFromPlayer(playerUuid);
            if (existingFaction != null) {
                ConfigUtil.debug("CreateFactionCommand: player " + playerUuid + " is already in faction " + existingFaction.getName());
                commandContext.sendMessage(Message.raw(I18n.get("chat.create.already_in_faction", existingFaction.getName()))
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Get faction name from argument
            String factionName = commandContext.get(name);
            ConfigUtil.debug("CreateFactionCommand: faction name=" + factionName);
            
            if (factionName == null || factionName.trim().isEmpty()) {
                ConfigUtil.debug("CreateFactionCommand: faction name is empty");
                commandContext.sendMessage(Message.raw(I18n.get("chat.create.name_empty"))
                        .color(Color.RED).bold(true));
                return;
            }
            
            String trimmedName = factionName.trim();
            int maxLength = FactionManager.getMaxFactionNameLength();
            if (trimmedName.length() > maxLength) {
                ConfigUtil.debug("CreateFactionCommand: faction name exceeds maximum length");
                commandContext.sendMessage(Message.raw(I18n.get("chat.create.name_too_long", maxLength))
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Create the faction
            FactionInfo faction = factionManager.createFaction(trimmedName, playerUuid);
            if (faction == null) {
                ConfigUtil.debug("CreateFactionCommand: failed to create faction");
                commandContext.sendMessage(Message.raw(I18n.get("chat.create.failed"))
                        .color(Color.RED).bold(true));
                return;
            }
            
            ConfigUtil.debug("CreateFactionCommand: faction created successfully: " + faction.getName() + " (ID: " + faction.getId() + ")");
            
            // Success message
            commandContext.sendMessage(Message.raw(I18n.get("chat.create.success", faction.getName()))
                    .color(Color.GREEN).bold(true));
        }, (Executor) world);
    }
}
