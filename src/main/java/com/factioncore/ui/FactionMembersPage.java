package com.factioncore.ui;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UI page that displays faction members list.
 * Shows all members with their roles (owner/general/member).
 */
public class FactionMembersPage extends InteractiveCustomUIPage<FactionMembersPage.FactionMembersEventData> {
    
    /**
     * Event data for faction members page interactions.
     */
    public static class FactionMembersEventData {
        public String Action;  // "close"
    }
    
    /**
     * Codec for serializing/deserializing FactionMembersEventData.
     */
    public static final BuilderCodec<FactionMembersEventData> CODEC;
    
    static {
        BuilderCodec.Builder<FactionMembersEventData> builder = BuilderCodec.builder(
            FactionMembersEventData.class,
            FactionMembersEventData::new
        );
        builder.addField(new KeyedCodec("Action", (Codec)Codec.STRING), 
            (codec, value) -> codec.Action = value, 
            codec -> codec.Action);
        CODEC = builder.build();
    }
    
    /**
     * Creates a new FactionMembersPage.
     * 
     * @param playerRef The player reference
     */
    public FactionMembersPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, CODEC);
    }
    
    /**
     * Gets player name from UUID using Universe.
     * 
     * @param universe The universe instance
     * @param uuid The player UUID
     * @return Player name or UUID string if not found
     */
    private String getPlayerName(Universe universe, UUID uuid) {
        if (universe == null || uuid == null) {
            return uuid != null ? uuid.toString() : "Unknown";
        }
        
        try {
            PlayerRef playerRef = universe.getPlayer(uuid);
            if (playerRef != null) {
                return playerRef.getUsername();
            }
        } catch (Exception e) {
            ConfigUtil.debug("Failed to get player name for UUID " + uuid + ": " + e.getMessage());
        }
        
        return uuid.toString();
    }
    
    /**
     * Builds member list display text with names and UUIDs.
     * 
     * @param faction The faction info
     * @param universe The universe instance
     * @return Formatted member list string
     */
    private String buildMemberListText(FactionInfo faction, Universe universe) {
        List<String> memberLines = new ArrayList<>();
        
        // Add owner
        if (faction.getOwner() != null) {
            String ownerName = getPlayerName(universe, faction.getOwner());
            memberLines.add("Owner: " + ownerName + " (" + faction.getOwner() + ")");
        }
        
        // Add generals
        for (UUID generalUuid : faction.getGenerals()) {
            String generalName = getPlayerName(universe, generalUuid);
            memberLines.add("General: " + generalName + " (" + generalUuid + ")");
        }
        
        // Add regular members (excluding generals)
        for (UUID memberUuid : faction.getMembers()) {
            if (!faction.isGeneral(memberUuid)) {
                String memberName = getPlayerName(universe, memberUuid);
                memberLines.add("Member: " + memberName + " (" + memberUuid + ")");
            }
        }
        
        if (memberLines.isEmpty()) {
            return "No members";
        }
        
        return String.join("\n", memberLines);
    }
    
    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, 
                        UIEventBuilder events, Store<EntityStore> store) {
        ConfigUtil.debug("FactionMembersPage.build() called");
        
        // Load the UI file
        commands.append("Pages/FactionMembersPage.ui");
        
        // Get player's faction
        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            ConfigUtil.debug("FactionMembersPage: could not get PlayerRef component");
            return;
        }
        
        UUID playerUuid = playerRefComponent.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
        
        if (faction == null) {
            ConfigUtil.debug("FactionMembersPage: player does not have a faction");
            // Close the page if no faction
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }
        
        // Get universe for player name lookup
        Universe universe = null;
        try {
            universe = Universe.get();
        } catch (Exception e) {
            ConfigUtil.debug("Failed to get universe: " + e.getMessage());
        }
        
        // Build and set member list
        String memberListText = buildMemberListText(faction, universe);
        commands.set("#membersListPlaceholder.Text", memberListText);
        
        // Bind close button
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#closeButton",
            EventData.of("Action", "close")
        );
    }
    
    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, 
                                   FactionMembersEventData data) {
        ConfigUtil.debug("FactionMembersPage.handleDataEvent() called: action=" + data.Action);
        
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ConfigUtil.debug("FactionMembersPage.handleDataEvent(): could not get Player component");
            return;
        }
        
        if ("close".equals(data.Action)) {
            // Close the page
            ConfigUtil.debug("FactionMembersPage: closing page");
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }
}
