package com.factioncore.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.ui.events.FactionSettingsPageEventData;
import com.factioncore.util.PermissionUtil;
import java.awt.Color;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * UI page for faction settings.
 * Allows renaming faction, changing color, claiming, and disbanding.
 * Uses native Hytale UI system with .ui files.
 */
public class FactionSettingsPage extends InteractiveCustomUIPage<FactionSettingsPageEventData> {
    
    public FactionSettingsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, FactionSettingsPageEventData.CODEC);
    }
    
    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
        
        if (faction == null) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You must be in a faction to access settings")
                    .color(Color.RED).bold(true));
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }
        
        // Check permissions for each feature
        boolean isOwner = faction.isOwner(playerUuid);
        boolean canRename = isOwner || PermissionUtil.canRenameFactionInFaction(playerUuid, faction);
        boolean canChangeColor = isOwner || PermissionUtil.canChangeColorInFaction(playerUuid, faction);
        
        String currentFactionName = faction.getName();
        
        commandBuilder.append("Pages/FactionSettingsPage.ui");
        
        // Set current faction name in text input
        commandBuilder.set("#RenameInput.Value", currentFactionName);
        
        // Disable rename input and button if user doesn't have permission
        if (!canRename) {
            commandBuilder.set("#RenameInput.Enabled", false);
            commandBuilder.set("#renameButton.Enabled", false);
        }
        
        // Disable color picker if user doesn't have permission
        if (!canChangeColor) {
            commandBuilder.set("#ColorPicker.Enabled", false);
        }
        
        // Set current faction color in color picker
        int currentColor = faction.getColor();
        String colorHex = String.format("%06X", currentColor);
        commandBuilder.set("#ColorPicker.Value", "#" + colorHex);
        
        // Show disband button only for owner
        commandBuilder.set("#disbandButton.Visible", isOwner);
        
        // Bind rename button with text input value
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#renameButton",
            new EventData()
                .append("Action", "rename")
                .append("@NewName", "#RenameInput.Value")
        );
        
        // Bind color picker value change
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ColorPicker",
            new EventData()
                .append("Action", "color")
                .append("@ColorValue", "#ColorPicker.Value")
        );
        
        // Bind disband button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#disbandButton",
            new EventData()
                .append("Action", "disband")
        );
        
        // Bind back button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#backButton",
            new EventData()
                .append("Action", "backToFaction")
        );
        
        // Bind close button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#closeButton",
            new EventData()
                .append("Action", "close")
        );
    }
    
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionSettingsPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        
        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
        
        if (faction == null) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You must be in a faction to access settings")
                .color(Color.RED).bold(true));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        
        switch (data.action) {
            case "close":
                player.getPageManager().setPage(ref, store, Page.None);
                break;
                
            case "backToFaction":
                FactionShowPage page = new FactionShowPage(playerRef);
                player.getPageManager().openCustomPage(ref, store, page);
                break;
                
            case "rename":
                handleRename(ref, store, player, playerUuid, faction, data.newName);
                break;
                
            case "color":
                handleColor(ref, store, player, playerUuid, faction, data.colorValue);
                break;
                
            case "disband":
                handleDisband(ref, store, player, playerUuid, faction, factionManager);
                break;
        }
    }
    
    private void handleRename(Ref<EntityStore> ref, Store<EntityStore> store, Player player, 
                              UUID playerUuid, FactionInfo faction, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Faction name cannot be empty. Please enter a new name.")
                .color(Color.RED).bold(true));
            // Refresh UI to clear loading state
            refreshPage(ref, store);
            return;
        }
        
        String trimmedName = newName.trim();
        int maxLength = FactionManager.getMaxFactionNameLength();
        if (trimmedName.length() > maxLength) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Faction name cannot exceed " + maxLength + " characters")
                .color(Color.RED).bold(true));
            // Refresh UI to clear loading state
            refreshPage(ref, store);
            return;
        }
        
        // Check permission
        if (!PermissionUtil.canRenameFactionInFaction(playerUuid, faction)) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You don't have permission to rename this faction")
                .color(Color.RED).bold(true));
            // Refresh UI to clear loading state
            refreshPage(ref, store);
            return;
        }
        
        // Check if name already exists
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo existing = factionManager.getFactionByName(trimmedName);
        if (existing != null && !existing.getId().equals(faction.getId())) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("A faction with that name already exists")
                .color(Color.RED).bold(true));
            // Refresh UI to clear loading state
            refreshPage(ref, store);
            return;
        }
        
        faction.setName(trimmedName);
        player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Faction renamed to '" + trimmedName + "'")
            .color(Color.GREEN).bold(true));
        
        // Refresh UI immediately to update display and recreate event bindings
        refreshPage(ref, store);
        
        // Save faction to database (queued, async, non-blocking)
        factionManager.saveFaction(faction);
    }
    
    private void handleColor(Ref<EntityStore> ref, Store<EntityStore> store, Player player,
                             UUID playerUuid, FactionInfo faction, String colorValue) {
        if (colorValue == null || colorValue.trim().isEmpty()) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Color value cannot be empty")
                .color(Color.RED).bold(true));
            // Refresh UI to clear loading state
            refreshPage(ref, store);
            return;
        }
        
        // Check permission
        if (!PermissionUtil.canChangeColorInFaction(playerUuid, faction)) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You don't have permission to change this faction's color")
                .color(Color.RED).bold(true));
            // Refresh UI to clear loading state
            refreshPage(ref, store);
            return;
        }
        
        try {
            String colorStr = colorValue.trim();
            
            // Remove # if present
            if (colorStr.startsWith("#")) {
                colorStr = colorStr.substring(1);
            }
            
            // Handle if it's a decimal integer string (convert to hex)
            if (colorStr.matches("^[0-9]+$")) {
                try {
                    int decimalColor = Integer.parseInt(colorStr);
                    // Convert to hex and ensure it's 6 digits
                    colorStr = String.format("%06X", decimalColor);
                } catch (NumberFormatException e) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Invalid color format. Received: " + colorValue)
                        .color(Color.RED).bold(true));
                    // Refresh UI to clear loading state
                    refreshPage(ref, store);
                    return;
                }
            }
            
            // ColorPicker returns #RRGGBBAA (8 chars with alpha), we only need RGB (6 chars)
            // Extract first 6 hex characters if it's 8 characters
            if (colorStr.length() == 8) {
                colorStr = colorStr.substring(0, 6);
            }
            
            // Validate it's exactly 6 hex characters
            if (colorStr.length() != 6) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Invalid color format. Color must be exactly 6 hex characters (e.g., #FFFFFF). Received: " + colorValue)
                    .color(Color.RED).bold(true));
                // Refresh UI to clear loading state
                refreshPage(ref, store);
                return;
            }
            
            // Validate it's a valid hex string
            if (!colorStr.matches("[0-9A-Fa-f]{6}")) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Invalid color format. Color must contain only hex characters (0-9, A-F). Received: " + colorValue)
                    .color(Color.RED).bold(true));
                // Refresh UI to clear loading state
                refreshPage(ref, store);
                return;
            }
            
            // Parse hex color
            int color = Integer.parseInt(colorStr, 16);
            
            faction.setColor(color);
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Faction color changed")
                .color(Color.GREEN).bold(true));
            
            // Refresh UI immediately to update display and recreate event bindings
            refreshPage(ref, store);
            
            // Save faction to database (queued, async, non-blocking)
            FactionManager factionManager = FactionManager.getInstance();
            factionManager.saveFaction(faction);
        } catch (NumberFormatException e) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Invalid color format. Use hex format (e.g., #FFFFFF). Received: " + colorValue)
                .color(Color.RED).bold(true));
            // Refresh UI to clear loading state
            refreshPage(ref, store);
        }
    }
    
    private void handleClaim(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        // Claim action (no action for now, as requested)
        player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Claim functionality not yet implemented")
            .color(Color.YELLOW).bold(true));
    }
    
    private void handleDisband(Ref<EntityStore> ref, Store<EntityStore> store, Player player,
                               UUID playerUuid, FactionInfo faction, FactionManager factionManager) {
        String factionName = faction.getName();
        
        // Call disbandFaction which now includes permission checks (owner OR admin)
        boolean disbanded = factionManager.disbandFaction(faction.getId(), playerUuid);
        
        if (disbanded) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Faction '" + factionName + "' has been disbanded")
                .color(Color.ORANGE).bold(true));
            
            // Close the page
            player.getPageManager().setPage(ref, store, Page.None);
        } else {
            // Permission denied or faction not found
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You don't have permission to disband this faction")
                .color(Color.RED).bold(true));
            // Refresh UI to clear loading state
            refreshPage(ref, store);
        }
    }
    
    /**
     * Refreshes the page to update UI with latest faction data and clear loading state.
     * sendUpdate() already handles world thread execution internally, so we don't need to wrap it.
     */
    private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!ref.isValid()) {
            return;
        }
        
        // Rebuild the page with updated data
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        build(ref, commandBuilder, eventBuilder, store);
        
        // Send update to refresh the UI (sendUpdate handles world thread execution internally)
        // Use clear=true to properly reset event bindings
        sendUpdate(commandBuilder, eventBuilder, true);
    }
}
