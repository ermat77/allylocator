package com.factioncore.commands;

import com.factioncore.util.ConfigUtil;
import com.factioncore.util.I18n;
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
 * - /f create <name> - Creates a new faction (requires factioncore.player.create_faction permission)
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
        addSubCommand((AbstractCommand) new SetHomeFactionCommand());
        addSubCommand((AbstractCommand) new HomeFactionCommand());
        addSubCommand((AbstractCommand) new PrismFactionCommand());
        addSubCommand((AbstractCommand) new AdminFactionCommand());
        addSubCommand((AbstractCommand) new PrismAdminCommand());
    }
    
    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        ConfigUtil.debug("FactionCommand.executeAsync() called");
        
        CommandSender sender = commandContext.sender();
        
        if (!(sender instanceof Player)) {
            ConfigUtil.debug("FactionCommand: sender is not a Player");
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.players_only")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        ConfigUtil.debug("FactionCommand: showing help message");
        
        // Show help if no subcommand is provided
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.title")).color(Color.YELLOW).bold(true));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.show")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.create")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.invite")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.kick")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.claim")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.unclaim")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.claiminfo")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.sethome")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.home")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.prism")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.admin")).color(Color.WHITE));
        commandContext.sendMessage(Message.raw(I18n.get("chat.help.prismadmin")).color(Color.WHITE));

        return CompletableFuture.completedFuture(null);
    }
}
