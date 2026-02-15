package com.factioncore.ui.factionbase;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.I18n;
import com.factioncore.util.PlayerUtils;
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
import java.util.function.BiConsumer;

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
    
    /** When non-null, show this faction's members (read-only). When null, show the player's own faction. */
    private final UUID viewFactionId;

    /** When non-null, invoked when back is pressed; when null, back button is hidden and not bound. */
    private final BiConsumer<Ref<EntityStore>, Store<EntityStore>> backTarget;

    /**
     * Creates a new FactionMembersPage for the player's own faction (no back target).
     *
     * @param playerRef The player reference
     */
    public FactionMembersPage(PlayerRef playerRef) {
        this(playerRef, null, null);
    }

    /**
     * Creates a new FactionMembersPage. When factionId is non-null, shows that faction's members (read-only)
     * with title "Members of &lt;name&gt;". When null, shows the player's own faction.
     *
     * @param playerRef The player reference
     * @param factionId The faction to view, or null for player's faction
     */
    public FactionMembersPage(PlayerRef playerRef, UUID factionId) {
        this(playerRef, factionId, null);
    }

    /**
     * Creates a new FactionMembersPage with an optional back target. When backTarget is non-null,
     * the back button is shown and bound; when back is pressed, backTarget is invoked with (ref, store).
     *
     * @param playerRef The player reference
     * @param factionId The faction to view, or null for player's faction
     * @param backTarget Callback to run when back is pressed, or null to hide the back button
     */
    public FactionMembersPage(PlayerRef playerRef, UUID factionId,
                              BiConsumer<Ref<EntityStore>, Store<EntityStore>> backTarget) {
        super(playerRef, CustomPageLifetime.CanDismiss, CODEC);
        this.viewFactionId = factionId;
        this.backTarget = backTarget;
    }
    
    /**
     * Builds member list display text with player names only.
     *
     * @param faction The faction info
     * @param universe The universe instance
     * @return Formatted member list string
     */
    private String buildMemberListText(FactionInfo faction, Universe universe) {
        List<String> memberLines = new ArrayList<>();
        
        // Add owner
        if (faction.getOwner() != null) {
            String ownerName = PlayerUtils.getPlayerName(universe, faction.getOwner());
            memberLines.add(I18n.get("ui.faction_show.role_owner") + ownerName);
        }
        
        // Add generals
        for (UUID generalUuid : faction.getGenerals()) {
            String generalName = PlayerUtils.getPlayerName(universe, generalUuid);
            memberLines.add(I18n.get("ui.faction_show.role_general") + generalName);
        }
        
        // Add regular members (excluding generals)
        for (UUID memberUuid : faction.getMembers()) {
            if (!faction.isGeneral(memberUuid)) {
                String memberName = PlayerUtils.getPlayerName(universe, memberUuid);
                memberLines.add(I18n.get("ui.faction_show.role_member") + memberName);
            }
        }
        
        if (memberLines.isEmpty()) {
            return I18n.get("ui.faction_show.no_members");
        }
        
        return String.join("\n", memberLines);
    }
    
    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
                        UIEventBuilder events, Store<EntityStore> store) {
        ConfigUtil.debug("FactionMembersPage.build() called");

        // Load the UI file
        commands.append("Pages/FactionBase/FactionMembersPage.ui");

        // Set static UI text from I18n
        commands.set("#Title #TitleText.Text", I18n.get("ui.members.faction_members"));
        commands.set("#membersListLabel.Text", I18n.get("ui.members.header"));
        commands.set("#membersListPlaceholder.Text", I18n.get("ui.members.loading"));
        commands.set("#backButton.Text", I18n.get("ui.members.back"));
        commands.set("#closeButton.Text", I18n.get("ui.members.close"));

        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            ConfigUtil.debug("FactionMembersPage: could not get PlayerRef component");
            return;
        }

        UUID playerUuid = playerRefComponent.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction;

        if (viewFactionId != null) {
            faction = factionManager.getFactionById(viewFactionId);
            if (faction == null) {
                commands.set("#Title #TitleText.Text", I18n.get("ui.members.faction_not_found"));
                commands.set("#membersListPlaceholder.Text", I18n.get("ui.members.faction_no_longer_exists"));
                events.addEventBinding(CustomUIEventBindingType.Activating, "#closeButton",
                    EventData.of("Action", "close"));
                if (backTarget != null) {
                    commands.set("#backButton.Visible", true);
                    events.addEventBinding(CustomUIEventBindingType.Activating, "#backButton",
                        EventData.of("Action", "back"));
                } else {
                    commands.set("#backButton.Visible", false);
                }
                return;
            }
            commands.set("#Title #TitleText.Text", I18n.get("ui.members.members_of", faction.getName()));
        } else {
            faction = factionManager.getFactionFromPlayer(playerUuid);
            if (faction == null) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().setPage(ref, store, Page.None);
                }
                return;
            }
            commands.set("#Title #TitleText.Text", I18n.get("ui.members.faction_members"));
        }

        Universe universe = null;
        try {
            universe = Universe.get();
        } catch (Exception e) {
            ConfigUtil.debug("Failed to get universe: " + e.getMessage());
        }

        String memberListText = buildMemberListText(faction, universe);
        commands.set("#membersListPlaceholder.Text", memberListText);

        if (backTarget != null) {
            commands.set("#backButton.Visible", true);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#backButton",
                EventData.of("Action", "back"));
        } else {
            commands.set("#backButton.Visible", false);
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#closeButton",
            EventData.of("Action", "close"));
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
            ConfigUtil.debug("FactionMembersPage: closing page");
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if ("back".equals(data.Action)) {
            ConfigUtil.debug("FactionMembersPage: back action received; ref=" + (ref != null ? "non-null" : "null")
                + ", ref.isValid()=" + (ref != null && ref.isValid())
                + ", store=" + (store != null ? "non-null" : "null")
                + ", backTarget=" + (backTarget != null ? "non-null" : "null"));
            if (backTarget != null) {
                try {
                    backTarget.accept(ref, store);
                    ConfigUtil.debug("FactionMembersPage: backTarget.accept() returned");
                } catch (Throwable t) {
                    ConfigUtil.debug("FactionMembersPage: backTarget.accept() threw: " + t.getMessage(), t);
                    throw t;
                }
            } else {
                ConfigUtil.debug("FactionMembersPage: back ignored (no backTarget)");
            }
        }
    }
}
