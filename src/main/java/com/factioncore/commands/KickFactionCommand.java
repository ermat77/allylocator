package com.factioncore.commands;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Command handler for /f kick <playerName>
 * Kicks a player from the sender's faction.
 */
public class KickFactionCommand extends AbstractAsyncCommand {
    
    private RequiredArg<String> playerName;
    
    /**
     * Creates a new KickFactionCommand.
     */
    public KickFactionCommand() {
        super("kick", "Kicks a player from your faction");
        setPermissionGroup(GameMode.Adventure);
        this.playerName = withRequiredArg("playerName", "The name of the player to kick", ArgTypes.STRING);
    }
    
    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        ConfigUtil.debug("KickFactionCommand.executeAsync() called");
        
        CommandSender sender = commandContext.sender();
        
        if (!(sender instanceof Player)) {
            ConfigUtil.debug("KickFactionCommand: sender is not a Player");
            commandContext.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();
        
        if (ref == null || !ref.isValid()) {
            ConfigUtil.debug("KickFactionCommand: player reference is invalid");
            commandContext.sendMessage(Message.raw("You must be in a world to use this command").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                ConfigUtil.debug("KickFactionCommand: could not get PlayerRef");
                commandContext.sendMessage(Message.raw("Error: Could not get player reference").color(Color.RED));
                return;
            }
            
            UUID playerUuid = playerRef.getUuid();
            ConfigUtil.debug("KickFactionCommand: processing for player " + playerUuid);
            
            FactionManager factionManager = FactionManager.getInstance();
            
            // Check if player is in a faction
            FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
            if (faction == null) {
                ConfigUtil.debug("KickFactionCommand: player " + playerUuid + " is not in a faction");
                commandContext.sendMessage(Message.raw("You must be in a faction to kick players")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Check permission (owner always has permission)
            if (!PermissionUtil.canKickMembersInFaction(playerUuid, faction)) {
                ConfigUtil.debug("KickFactionCommand: player " + playerUuid + " does not have permission to kick members");
                commandContext.sendMessage(Message.raw("You don't have permission to kick members from this faction")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Get target player name from argument
            String targetPlayerName = commandContext.get(playerName);
            ConfigUtil.debug("KickFactionCommand: target player name=" + targetPlayerName);
            
            if (targetPlayerName == null || targetPlayerName.trim().isEmpty()) {
                ConfigUtil.debug("KickFactionCommand: target player name is empty");
                commandContext.sendMessage(Message.raw("Player name cannot be empty")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Find target player by username (case-insensitive)
            Universe universe = Universe.get();
            PlayerRef targetPlayerRef = universe.getPlayerByUsername(
                targetPlayerName.trim(),
                String.CASE_INSENSITIVE_ORDER,
                (a, b) -> a.equalsIgnoreCase(b)
            );
            
            if (targetPlayerRef == null) {
                ConfigUtil.debug("KickFactionCommand: player not found: " + targetPlayerName);
                commandContext.sendMessage(Message.raw("Player '" + targetPlayerName + "' not found or not online")
                        .color(Color.RED).bold(true));
                return;
            }
            
            UUID targetPlayerUuid = targetPlayerRef.getUuid();
            ConfigUtil.debug("KickFactionCommand: found target player UUID=" + targetPlayerUuid);
            
            // Check if trying to kick self
            if (targetPlayerUuid.equals(playerUuid)) {
                ConfigUtil.debug("KickFactionCommand: cannot kick self");
                commandContext.sendMessage(Message.raw("You cannot kick yourself from your faction")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Check if target player is in the same faction
            FactionInfo targetFaction = factionManager.getFactionFromPlayer(targetPlayerUuid);
            if (targetFaction == null || !targetFaction.getId().equals(faction.getId())) {
                ConfigUtil.debug("KickFactionCommand: target player is not in the same faction");
                commandContext.sendMessage(Message.raw("Player '" + targetPlayerName + "' is not in your faction")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Check if trying to kick the owner
            if (faction.isOwner(targetPlayerUuid)) {
                ConfigUtil.debug("KickFactionCommand: cannot kick owner");
                commandContext.sendMessage(Message.raw("You cannot kick the faction owner")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Remove player from faction
            boolean success = factionManager.removePlayerFromFaction(targetPlayerUuid);
            
            if (!success) {
                ConfigUtil.debug("KickFactionCommand: failed to remove player from faction");
                commandContext.sendMessage(Message.raw("Failed to kick player from faction")
                        .color(Color.RED).bold(true));
                return;
            }
            
            ConfigUtil.debug("KickFactionCommand: player kicked successfully");
            
            // Success message
            commandContext.sendMessage(Message.raw("Player '" + targetPlayerName + "' has been kicked from the faction")
                    .color(Color.GREEN).bold(true));
            
            // Notify target player if online
            try {
                Player targetPlayer = store.getComponent(
                    targetPlayerRef.getReference(),
                    Player.getComponentType()
                );
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(Message.raw("You have been kicked from faction '" + faction.getName() + "' by " + playerRef.getUsername())
                            .color(Color.RED).bold(true));
                }
            } catch (Exception e) {
                ConfigUtil.debug("KickFactionCommand: could not notify target player: " + e.getMessage());
                // Not critical, continue
            }
        }, (Executor) world);
    }
}
