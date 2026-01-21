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
import com.factioncore.ui.events.FactionCreatePageEventData;
import com.factioncore.util.PermissionUtil;
import java.awt.Color;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * UI page for creating a new faction.
 * Uses native Hytale UI system with .ui files.
 */
public class FactionCreatePage extends InteractiveCustomUIPage<FactionCreatePageEventData> {
    
    public FactionCreatePage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, FactionCreatePageEventData.CODEC);
    }
    
    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/FactionCreatePage");
        
        // Bind create button with text input value
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#createButton",
            new EventData()
                .append("Action", "create")
                .append("@FactionName", "#FactionNameInput.Value")
        );
        
        // Bind cancel button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#cancelButton",
            new EventData()
                .append("Action", "cancel")
        );
    }
    
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionCreatePageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        
        UUID playerUuid = playerRef.getUuid();
        
        if ("cancel".equals(data.action)) {
            // Close the page
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        
        if (!"create".equals(data.action)) {
            return;
        }
        
        // Get faction name from event data
        String factionName = data.factionName;
        
        // Validate input
        if (factionName == null || factionName.trim().isEmpty()) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Faction name cannot be empty")
                .color(Color.RED).bold(true));
            return;
        }
        
        String trimmedName = factionName.trim();
        int maxLength = FactionManager.getMaxFactionNameLength();
        if (trimmedName.length() > maxLength) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Faction name cannot exceed " + maxLength + " characters")
                .color(Color.RED).bold(true));
            return;
        }
        
        // Check permission
        if (!PermissionUtil.canCreateFaction(playerUuid)) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You don't have permission to create factions")
                .color(Color.RED).bold(true));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        
        FactionManager factionManager = FactionManager.getInstance();
        
        // Check if player is already in a faction
        FactionInfo existingFaction = factionManager.getFactionFromPlayer(playerUuid);
        if (existingFaction != null) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You are already in a faction: " + existingFaction.getName())
                .color(Color.RED).bold(true));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        
        // Create the faction
        FactionInfo faction = factionManager.createFaction(trimmedName, playerUuid);
        
        if (faction == null) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Failed to create faction. The name might already be taken.")
                .color(Color.RED).bold(true));
        } else {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Faction '" + faction.getName() + "' created successfully!")
                .color(Color.GREEN).bold(true));
            
            // Close the page after successful creation
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }
}
