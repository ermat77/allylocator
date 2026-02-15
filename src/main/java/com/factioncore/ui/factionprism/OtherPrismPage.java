package com.factioncore.ui.factionprism;

import com.factioncore.faction.FactionInfo;
import com.factioncore.ui.factionbase.FactionMembersPage;
import com.factioncore.ui.factionprism.events.OtherPrismPageEventData;
import com.factioncore.util.I18n;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * UI page when the player interacts with another faction's prism block.
 * Shows Attack (no-op for now) and Members (opens FactionMembersPage for the owning faction, with back to this page).
 */
public class OtherPrismPage extends InteractiveCustomUIPage<OtherPrismPageEventData> {

    @Nullable private final FactionInfo placerFaction;
    @Nullable private final UUID prismId;

    public OtherPrismPage(@Nonnull PlayerRef playerRef,
                         @Nullable FactionInfo placerFaction,
                         @Nullable UUID prismId) {
        super(playerRef, CustomPageLifetime.CanDismiss, OtherPrismPageEventData.CODEC);
        this.placerFaction = placerFaction;
        this.prismId = prismId;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/FactionPrism/OtherPrismPage.ui");
        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.other_prism.title"));
        commandBuilder.set("#AttackButton.Text", I18n.get("ui.other_prism.attack"));
        commandBuilder.set("#MembersButton.Text", I18n.get("ui.other_prism.members"));
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AttackButton",
                new EventData().append("Action", "attack")
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MembersButton",
                new EventData().append("Action", "members")
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull OtherPrismPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if ("attack".equals(data.action)) {
            // Placeholder: attack logic will be handled later
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if ("members".equals(data.action)) {
            if (placerFaction != null) {
                FactionMembersPage membersPage = new FactionMembersPage(playerRef, placerFaction.getId(), (r, s) -> {
                    Player p = s.getComponent(r, Player.getComponentType());
                    if (p != null) {
                        OtherPrismPage backPage = new OtherPrismPage(playerRef, placerFaction, prismId);
                        p.getPageManager().openCustomPage(r, s, backPage);
                    }
                });
                player.getPageManager().openCustomPage(ref, store, membersPage);
            } else {
                player.getPageManager().setPage(ref, store, Page.None);
            }
        }
    }
}
