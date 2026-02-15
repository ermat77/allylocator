package com.factioncore.ui.factionprism;

import com.factioncore.faction.FactionInfo;
import com.factioncore.prism.PrismService;
import com.factioncore.systems.interaction.BlockUseOpenUISystem;
import com.factioncore.ui.factionprism.events.PrismBlockPageEventData;
import com.factioncore.util.I18n;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.UUID;

/**
 * UI page for prism block when the interactor has no faction or the placer has no faction (fallback).
 * Also shows time alive, Harvest (own faction), and Attack (other faction) when used in that context.
 * For own-faction and other-faction cases, {@link BlockUseOpenUISystem} opens {@link OwnPrismPage} and {@link OtherPrismPage} instead.
 */
public class PrismBlockPage extends InteractiveCustomUIPage<PrismBlockPageEventData> {

    private final int timeAlive;
    @Nullable private final FactionInfo placerFaction;
    @Nullable private final FactionInfo interactorFaction;
    @Nullable private final UUID prismId;

    /** Opens UI with no faction context (e.g. when placer not recorded). */
    public PrismBlockPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, 0, null, null, null);
    }

    /** Opens UI with placer and interactor faction (for Attack logic). */
    public PrismBlockPage(@Nonnull PlayerRef playerRef,
                          @Nullable FactionInfo placerFaction,
                          @Nullable FactionInfo interactorFaction) {
        this(playerRef, 0, placerFaction, interactorFaction, null);
    }

    /** Opens UI with time alive and factions (no prism ID; Harvest will do nothing if clicked). */
    public PrismBlockPage(@Nonnull PlayerRef playerRef,
                          int timeAlive,
                          @Nullable FactionInfo placerFaction,
                          @Nullable FactionInfo interactorFaction) {
        this(playerRef, timeAlive, placerFaction, interactorFaction, null);
    }

    /** Opens UI with time alive, factions, and prism ID; Harvest calls PrismService.harvestPrism(prismId, playerUuid, ref, store). */
    public PrismBlockPage(@Nonnull PlayerRef playerRef,
                          int timeAlive,
                          @Nullable FactionInfo placerFaction,
                          @Nullable FactionInfo interactorFaction,
                          @Nullable UUID prismId) {
        super(playerRef, CustomPageLifetime.CanDismiss, PrismBlockPageEventData.CODEC);
        this.timeAlive = timeAlive;
        this.placerFaction = placerFaction;
        this.interactorFaction = interactorFaction;
        this.prismId = prismId;
    }

    @Nullable
    public FactionInfo getPlacerFaction() { return placerFaction; }

    @Nullable
    public FactionInfo getInteractorFaction() { return interactorFaction; }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/FactionPrism/PrismBlockPage.ui");
        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.prism_block.title"));
        commandBuilder.set("#TimeAliveText.Text", I18n.get("ui.prism_block.time_alive", timeAlive));
        boolean showHarvest = interactorFaction != null && placerFaction != null
                && interactorFaction.getId().equals(placerFaction.getId());
        boolean showAttack = interactorFaction != null && placerFaction != null
                && !interactorFaction.getId().equals(placerFaction.getId());
        commandBuilder.set("#HarvestButton.Visible", showHarvest);
        if (showHarvest) {
            commandBuilder.set("#HarvestButton.Text", I18n.get("ui.prism_block.harvest"));
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#HarvestButton",
                    new EventData().append("Action", "harvest")
            );
        }
        commandBuilder.set("#AttackButton.Visible", showAttack);
        if (showAttack) {
            commandBuilder.set("#AttackButton.Text", I18n.get("ui.prism_block.attack"));
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#AttackButton",
                    new EventData().append("Action", "attack")
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PrismBlockPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if ("harvest".equals(data.action)) {
            if (prismId != null) {
                int earned = PrismService.getInstance().harvestPrism(prismId, playerRef.getUuid(), ref, store);
                if (earned > 0) {
                    player.sendMessage(Message.raw(I18n.get("chat.faction_points.earned", earned)).color(Color.GREEN));
                }
            }
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if ("attack".equals(data.action)) {
            // Placeholder: add attack logic later
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }
}
