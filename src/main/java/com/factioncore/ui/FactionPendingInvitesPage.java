package com.factioncore.ui;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.invite.PendingInvite;
import com.factioncore.ui.events.FactionPendingInvitesPageEventData;
import com.factioncore.util.I18n;
import com.factioncore.util.PermissionUtil;
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

import java.awt.Color;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * UI page for viewing pending faction invites.
 * Uses native Hytale UI system with .ui files.
 */
public class FactionPendingInvitesPage extends InteractiveCustomUIPage<FactionPendingInvitesPageEventData> {
    
    public FactionPendingInvitesPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, FactionPendingInvitesPageEventData.CODEC);
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
        List<PendingInvite> pendingInvites = factionManager.getPendingInvitesForPlayer(playerUuid);
        
        commandBuilder.append("Pages/FactionPendingInvitesPage");

        // Set static UI text from I18n
        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.pending_invites.title"));
        commandBuilder.set("#headerLabel.Text", I18n.get("ui.pending_invites.header"));
        commandBuilder.set("#noInvitesLabel.Text", I18n.get("ui.pending_invites.no_invites"));
        commandBuilder.set("#createFactionButton.Text", I18n.get("ui.pending_invites.create_faction"));
        commandBuilder.set("#closeButton.Text", I18n.get("ui.pending_invites.close"));
        
        // Build invite list dynamically using appendInline
        if (pendingInvites.isEmpty()) {
            commandBuilder.set("#noInvitesLabel.Visible", true);
        } else {
            commandBuilder.set("#noInvitesLabel.Visible", false);
            
            // Build invite cards using UI DSL syntax
            for (PendingInvite invite : pendingInvites) {
                UUID factionId = invite.getFactionId();
                FactionInfo faction = factionManager.getFactionById(factionId);
                String factionName = faction != null ? faction.getName() : "Unknown Faction";
                String inviterName = invite.getInviterName() != null ? invite.getInviterName() : "Unknown";
                
                // Build UI DSL for invite card (labels and buttons from I18n)
                String factionLabel = escapeText(I18n.get("ui.pending_invites.faction_label", factionName));
                String invitedByLabel = escapeText(I18n.get("ui.pending_invites.invited_by", inviterName));
                String acceptText = escapeText(I18n.get("ui.row.accept"));
                String refuseText = escapeText(I18n.get("ui.row.decline"));
                String inviteCard = String.format(
                    "Group { LayoutMode: Top; Anchor: (Bottom: 10); " +
                    "Label { Text: \"%s\"; Anchor: (Height: 25); } " +
                    "Label { Text: \"%s\"; Anchor: (Height: 20); } " +
                    "Group { LayoutMode: Right; Anchor: (Height: 35); " +
                    "$C.@TextButton #accept_%s { Text: \"%s\"; } " +
                    "$C.@CancelTextButton #refuse_%s { Text: \"%s\"; } " +
                    "} }",
                    factionLabel,
                    invitedByLabel,
                    factionId.toString(),
                    acceptText,
                    factionId.toString(),
                    refuseText
                );
                
                // Append invite card to list
                commandBuilder.appendInline("#InvitesList", inviteCard);
                
                // Bind accept button
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#accept_" + factionId.toString(),
                    new EventData()
                        .append("Action", "accept")
                        .append("FactionId", factionId.toString())
                );
                
                // Bind refuse button
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#refuse_" + factionId.toString(),
                    new EventData()
                        .append("Action", "refuse")
                        .append("FactionId", factionId.toString())
                );
            }
        }
        
        // Show create faction button if player has permission
        if (PermissionUtil.canCreateFaction(playerUuid)) {
            commandBuilder.set("#createFactionButton.Visible", true);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#createFactionButton",
                new EventData()
                    .append("Action", "createFaction")
            );
        } else {
            commandBuilder.set("#createFactionButton.Visible", false);
        }
        
        // Bind close button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#closeButton",
            new EventData()
                .append("Action", "close")
        );
    }
    
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionPendingInvitesPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        
        UUID playerUuid = playerRef.getUuid();
        
        if ("close".equals(data.action)) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        
        if ("createFaction".equals(data.action)) {
            FactionCreatePage createPage = new FactionCreatePage(playerRef);
            player.getPageManager().openCustomPage(ref, store, createPage);
            return;
        }
        
        if (data.factionId == null) {
            return;
        }
        
        UUID factionId = data.factionId;
        FactionManager factionManager = FactionManager.getInstance();
        
        if ("accept".equals(data.action)) {
            // Re-check that player doesn't have a faction
            FactionInfo currentFaction = factionManager.getFactionFromPlayer(playerUuid);
            if (currentFaction != null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.pending_invites.already_in_faction"))
                    .color(Color.RED).bold(true));
                player.getPageManager().setPage(ref, store, Page.None);
                return;
            }
            
            // Get the faction
            FactionInfo targetFaction = factionManager.getFactionById(factionId);
            if (targetFaction == null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.pending_invites.faction_gone"))
                    .color(Color.RED).bold(true));
                // Refresh page to remove invalid invite
                FactionPendingInvitesPage page = new FactionPendingInvitesPage(playerRef);
                player.getPageManager().openCustomPage(ref, store, page);
                return;
            }
            
            // Add player to faction (this will automatically clear all pending invites)
            boolean success = factionManager.addPlayerToFaction(playerUuid, factionId);
            
            if (success) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You have joined faction '" + targetFaction.getName() + "'!")
                    .color(Color.GREEN).bold(true));
                // Close the page - player now has a faction
                player.getPageManager().setPage(ref, store, Page.None);
            } else {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.pending_invites.join_failed"))
                    .color(Color.RED).bold(true));
                // Refresh page
                FactionPendingInvitesPage page = new FactionPendingInvitesPage(playerRef);
                player.getPageManager().openCustomPage(ref, store, page);
            }
        } else if ("refuse".equals(data.action)) {
            // Get faction name for message
            FactionInfo faction = factionManager.getFactionById(factionId);
            String factionName = faction != null ? faction.getName() : "Unknown";
            
            // Remove the invite
            boolean removed = factionManager.cancelPendingInvite(playerUuid, factionId);
            
            if (removed) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.pending_invites.refused", factionName))
                    .color(Color.YELLOW).bold(true));
                // Refresh the page to show updated list
                FactionPendingInvitesPage page = new FactionPendingInvitesPage(playerRef);
                player.getPageManager().openCustomPage(ref, store, page);
            } else {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.pending_invites.refuse_failed"))
                    .color(Color.RED).bold(true));
                // Refresh page anyway
                FactionPendingInvitesPage page = new FactionPendingInvitesPage(playerRef);
                player.getPageManager().openCustomPage(ref, store, page);
            }
        }
    }
    
    private String escapeText(String text) {
        if (text == null) {
            return "";
        }
        // Escape quotes for UI DSL
        return text.replace("\"", "\\\"");
    }
}
