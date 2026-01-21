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
 * Command handler for /f invite <playerName>
 * Invites a player to the sender's faction.
 */
public class InviteFactionCommand extends AbstractAsyncCommand {
    
    private RequiredArg<String> playerName;
    
    /**
     * Creates a new InviteFactionCommand.
     */
    public InviteFactionCommand() {
        super("invite", "Invites a player to your faction");
        setPermissionGroup(GameMode.Adventure);
        this.playerName = withRequiredArg("playerName", "The name of the player to invite", ArgTypes.STRING);
    }
    
    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        ConfigUtil.debug("InviteFactionCommand.executeAsync() called");
        
        CommandSender sender = commandContext.sender();
        
        if (!(sender instanceof Player)) {
            ConfigUtil.debug("InviteFactionCommand: sender is not a Player");
            commandContext.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();
        
        if (ref == null || !ref.isValid()) {
            ConfigUtil.debug("InviteFactionCommand: player reference is invalid");
            commandContext.sendMessage(Message.raw("You must be in a world to use this command").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                ConfigUtil.debug("InviteFactionCommand: could not get PlayerRef");
                commandContext.sendMessage(Message.raw("Error: Could not get player reference").color(Color.RED));
                return;
            }
            
            UUID playerUuid = playerRef.getUuid();
            ConfigUtil.debug("InviteFactionCommand: processing for player " + playerUuid);
            
            FactionManager factionManager = FactionManager.getInstance();
            
            // Check if player is in a faction
            FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
            if (faction == null) {
                ConfigUtil.debug("InviteFactionCommand: player " + playerUuid + " is not in a faction");
                commandContext.sendMessage(Message.raw("You must be in a faction to invite players")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Check permission (owner always has permission)
            if (!PermissionUtil.canInviteMembersInFaction(playerUuid, faction)) {
                ConfigUtil.debug("InviteFactionCommand: player " + playerUuid + " does not have permission to invite members");
                commandContext.sendMessage(Message.raw("You don't have permission to invite members to this faction")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Get target player name from argument
            String targetPlayerName = commandContext.get(playerName);
            ConfigUtil.debug("InviteFactionCommand: target player name=" + targetPlayerName);
            
            if (targetPlayerName == null || targetPlayerName.trim().isEmpty()) {
                ConfigUtil.debug("InviteFactionCommand: target player name is empty");
                commandContext.sendMessage(Message.raw("Player name cannot be empty")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Find target player by username (case-insensitive)
            // Use direct iteration to ensure exact match
            Universe universe = Universe.get();
            PlayerRef targetPlayerRef = null;
            String targetNameLower = targetPlayerName.trim().toLowerCase();
            
            // Iterate through all players to find exact match
            for (PlayerRef onlinePlayerRef : universe.getPlayers()) {
                if (onlinePlayerRef != null) {
                    String playerUsername = onlinePlayerRef.getUsername();
                    if (playerUsername != null && playerUsername.toLowerCase().equals(targetNameLower)) {
                        targetPlayerRef = onlinePlayerRef;
                        break;
                    }
                }
            }
            
            if (targetPlayerRef == null) {
                ConfigUtil.debug("InviteFactionCommand: player not found: " + targetPlayerName);
                commandContext.sendMessage(Message.raw("Player doesn't exist")
                        .color(Color.RED).bold(true));
                return;
            }
            
            UUID targetPlayerUuid = targetPlayerRef.getUuid();
            String foundUsername = targetPlayerRef.getUsername();
            ConfigUtil.debug("InviteFactionCommand: found target player UUID=" + targetPlayerUuid + ", username=" + foundUsername);
            
            // Check if trying to invite self
            if (targetPlayerUuid.equals(playerUuid)) {
                ConfigUtil.debug("InviteFactionCommand: cannot invite self");
                commandContext.sendMessage(Message.raw("You cannot invite yourself to your faction")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Check if target player is already in a faction
            FactionInfo targetFaction = factionManager.getFactionFromPlayer(targetPlayerUuid);
            if (targetFaction != null) {
                ConfigUtil.debug("InviteFactionCommand: target player is already in a faction");
                commandContext.sendMessage(Message.raw("Player '" + targetPlayerName + "' is already in a faction")
                        .color(Color.RED).bold(true));
                return;
            }
            
            // Send invite
            String inviterName = playerRef.getUsername();
            boolean success = factionManager.invitePlayerToFaction(
                targetPlayerUuid,
                faction.getId(),
                inviterName
            );
            
            if (!success) {
                ConfigUtil.debug("InviteFactionCommand: failed to send invite");
                commandContext.sendMessage(Message.raw("Failed to send invite. The player may already have a pending invite from your faction.")
                        .color(Color.RED).bold(true));
                return;
            }
            
            ConfigUtil.debug("InviteFactionCommand: invite sent successfully");
            
            // Success message
            commandContext.sendMessage(Message.raw("Invite sent to '" + targetPlayerName + "'!")
                    .color(Color.GREEN).bold(true));
            
            // Notify target player if online
            try {
                Player targetPlayer = store.getComponent(
                    targetPlayerRef.getReference(),
                    Player.getComponentType()
                );
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(Message.raw("You have been invited to join faction '" + faction.getName() + "' by " + inviterName)
                            .color(Color.YELLOW).bold(true));
                }
            } catch (Exception e) {
                ConfigUtil.debug("InviteFactionCommand: could not notify target player: " + e.getMessage());
                // Not critical, continue
            }
        }, (Executor) world);
    }
}
