package com.factioncore.commands;

import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Main faction command handler.
 * 
 * Currently supports:
 * - /f create <name> - Creates a new faction (requires factioncore.create_faction permission)
 */
public class FactionCommand extends AbstractAsyncCommand {
    
    /**
     * Creates a new FactionCommand.
     */
    public FactionCommand() {
        super("f", "Faction management commands");
        setPermissionGroup(GameMode.Adventure);
        
        // Register subcommands
        addSubCommand((AbstractCommand) new CreateFactionCommand());
        addSubCommand((AbstractCommand) new ShowFactionCommand());
        addSubCommand((AbstractCommand) new InviteFactionCommand());
        addSubCommand((AbstractCommand) new KickFactionCommand());
        addSubCommand((AbstractCommand) new ClaimChunkCommand());
        addSubCommand((AbstractCommand) new UnclaimChunkCommand());
        addSubCommand((AbstractCommand) new ClaimInfoCommand());
    }
    
    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        ConfigUtil.debug("FactionCommand.executeAsync() called");
        
        CommandSender sender = commandContext.sender();
        
        if (!(sender instanceof Player)) {
            ConfigUtil.debug("FactionCommand: sender is not a Player");
            commandContext.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        ConfigUtil.debug("FactionCommand: showing help message");
        
        // Show help if no subcommand is provided
        commandContext.sendMessage(Message.raw("Faction Commands:").color(Color.YELLOW).bold(true));
        commandContext.sendMessage(Message.raw("  /f show - Show your faction or create a new one").color(Color.WHITE));
        commandContext.sendMessage(Message.raw("  /f create <name> - Create a new faction").color(Color.WHITE));
        commandContext.sendMessage(Message.raw("  /f invite <player> - Invite a player to your faction").color(Color.WHITE));
        commandContext.sendMessage(Message.raw("  /f kick <player> - Kick a player from your faction").color(Color.WHITE));
        commandContext.sendMessage(Message.raw("  /f claim - Claim the chunk where you are").color(Color.WHITE));
        commandContext.sendMessage(Message.raw("  /f unclaim - Unclaim the chunk where you are").color(Color.WHITE));
        commandContext.sendMessage(Message.raw("  /f claiminfo - Show information about the current chunk").color(Color.WHITE));
        
        return CompletableFuture.completedFuture(null);
    }
}
