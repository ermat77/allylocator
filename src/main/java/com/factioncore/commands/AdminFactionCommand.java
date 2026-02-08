package com.factioncore.commands;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.ui.FactionShowPage;
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
 * Command handler for /f admin &lt;factionname|factionuuid&gt;
 * Opens the faction show UI for the given faction as if the player were the owner (superadmin mode).
 * Accepts faction name (use quotes for spaces: /f admin 'Faction With Space') or faction UUID.
 * Requires factioncore.admin.openFaction or factioncore.admin.* permission.
 */
public class AdminFactionCommand extends AbstractAsyncCommand {

    private final RequiredArg<String> factionNameArg;

    public AdminFactionCommand() {
        super("admin", "Opens a faction's UI as superadmin (manage any faction)");
        setPermissionGroup(GameMode.Adventure);
        this.factionNameArg = withRequiredArg("factionname", "The name of the faction to manage", ArgTypes.STRING);
    }

    @Override
    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();

        if (!(sender instanceof Player)) {
            commandContext.sendMessage(Message.raw(I18n.get("chat.command.players_only")).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String rawInput = commandContext.get(factionNameArg);
        if (rawInput == null || rawInput.trim().isEmpty()) {
            commandContext.sendMessage(Message.raw(I18n.get("chat.admin.usage")).color(Color.YELLOW));
            return CompletableFuture.completedFuture(null);
        }
        String input = stripSurroundingQuotes(rawInput.trim());

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
            if (!PermissionUtil.canOpenFactionAdmin(playerUuid)) {
                commandContext.sendMessage(Message.raw(I18n.get("chat.admin.no_permission"))
                    .color(Color.RED).bold(true));
                return;
            }

            FactionManager factionManager = FactionManager.getInstance();
            FactionInfo faction = resolveFaction(factionManager, input);
            if (faction == null) {
                commandContext.sendMessage(Message.raw(I18n.get("chat.admin.faction_not_found", input)).color(Color.RED));
                return;
            }

            FactionShowPage page = new FactionShowPage(playerRef, faction.getId());
            player.getPageManager().openCustomPage(ref, store, page);
            ConfigUtil.debug("AdminFactionCommand: opened faction " + faction.getName() + " for admin " + playerUuid);
        }, (Executor) world);
    }

    /**
     * Strips surrounding single or double quotes so /f admin 'Faction With Space' works.
     */
    private static String stripSurroundingQuotes(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        char first = s.charAt(0);
        if ((first == '\'' || first == '"') && s.charAt(s.length() - 1) == first) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /** Resolve by UUID first, then by name (case-insensitive). */
    private static FactionInfo resolveFaction(FactionManager factionManager, String input) {
        try {
            UUID uuid = UUID.fromString(input);
            FactionInfo byId = factionManager.getFactionById(uuid);
            if (byId != null) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
            // Not a valid UUID, try name
        }
        return factionManager.getFactionByName(input);
    }
}
