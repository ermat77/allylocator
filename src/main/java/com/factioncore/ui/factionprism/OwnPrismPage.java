package com.factioncore.ui.factionprism;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.prism.PrismManager;
import com.factioncore.prism.PrismService;
import com.factioncore.ui.factionprism.events.OwnPrismPageEventData;
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
import java.util.Map;
import java.util.UUID;

/**
 * UI page when the player interacts with their own faction's prism block.
 * Shows time alive, effect multiplier, Harvest button, and Cancel Prism button (removes prism and returns block to inventory).
 */
public class OwnPrismPage extends InteractiveCustomUIPage<OwnPrismPageEventData> {

    private final int timeAlive;
    private final int effectMultiplier;
    @Nullable private final UUID prismId;

    public OwnPrismPage(@Nonnull PlayerRef playerRef,
                        int timeAlive,
                        int effectMultiplier,
                        @Nullable UUID prismId) {
        super(playerRef, CustomPageLifetime.CanDismiss, OwnPrismPageEventData.CODEC);
        this.timeAlive = timeAlive;
        this.effectMultiplier = effectMultiplier;
        this.prismId = prismId;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/FactionPrism/OwnPrismPage.ui");
        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.own_prism.title"));
        commandBuilder.set("#TimeAliveText.Text", I18n.get("ui.own_prism.time_alive", timeAlive));
        commandBuilder.set("#EffectMultiplierText.Text", I18n.get("ui.own_prism.effect_multiplier", effectMultiplier));
        commandBuilder.set("#HarvestButton.Text", I18n.get("ui.own_prism.harvest"));
        commandBuilder.set("#CancelPrismButton.Text", I18n.get("ui.own_prism.cancel_prism"));
        commandBuilder.set("#ChangeFactionLabel.Text", I18n.get("ui.own_prism.change_faction_testing"));
        Map<UUID, FactionInfo> allFactions = FactionManager.getInstance().getAllFactions();
        int i = 0;
        for (Map.Entry<UUID, FactionInfo> e : allFactions.entrySet()) {
            FactionInfo f = e.getValue();
            if (f == null || f.getName() == null) continue;
            commandBuilder.append("#FactionList", "Pages/FactionPrism/FactionSelectRow.ui");
            String sel = "#FactionList[" + i + "]";
            commandBuilder.set(sel + " #SelectButton.Text", f.getName());
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel + " #SelectButton",
                    new EventData().append("Action", "changeFaction").append("FactionId", e.getKey().toString())
            );
            i++;
        }
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#HarvestButton",
                new EventData().append("Action", "harvest")
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CancelPrismButton",
                new EventData().append("Action", "cancelPrism")
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull OwnPrismPageEventData data) {
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
        if ("cancelPrism".equals(data.action)) {
            if (prismId != null) {
                boolean ok = PrismService.getInstance().cancelPrism(prismId, playerRef.getUuid(), ref, store);
                if (ok) {
                    player.sendMessage(Message.raw(I18n.get("chat.own_prism.cancelled")).color(Color.GREEN));
                } else {
                    player.sendMessage(Message.raw(I18n.get("chat.own_prism.cancel_failed")).color(Color.RED));
                }
            }
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if ("changeFaction".equals(data.action) && prismId != null && data.factionId != null) {
            FactionInfo faction = FactionManager.getInstance().getFactionById(data.factionId);
            PrismManager.getInstance().updateFaction(prismId, data.factionId);
            String name = faction != null ? faction.getName() : data.factionId.toString();
            player.sendMessage(Message.raw(I18n.get("chat.own_prism.faction_changed", name)).color(Color.GREEN));
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }
}
