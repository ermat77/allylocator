package com.factioncore.ui.factionprism;

import com.factioncore.faction.FactionInfo;
import com.factioncore.ui.factionbase.FactionShowPage;
import com.factioncore.ui.factionprism.events.FactionPrismsPageEventData;
import com.factioncore.faction.FactionManager;
import com.factioncore.prism.PrismManager;
import com.factioncore.prism.PrismMarkerService;
import com.factioncore.prism.PrismMultiplierService;
import com.factioncore.prism.PrismRecord;
import com.factioncore.util.I18n;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Page listing all prisms of the player's faction.
 * Shows current points (time alive × multiplier), coordinates, and a checkbox to toggle map marker.
 */
public class FactionPrismsPage extends InteractiveCustomUIPage<FactionPrismsPageEventData> {

    private final UUID adminViewFactionId;

    public FactionPrismsPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    public FactionPrismsPage(@Nonnull PlayerRef playerRef, UUID adminViewFactionId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, FactionPrismsPageEventData.CODEC);
        this.adminViewFactionId = adminViewFactionId;
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
        FactionInfo faction = adminViewFactionId != null
            ? factionManager.getFactionById(adminViewFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);

        commandBuilder.append("Pages/FactionPrism/FactionPrismsPage.ui");
        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.faction_prisms.title"));
        commandBuilder.set("#backButton.Text", I18n.get("ui.faction_prisms.back"));
        commandBuilder.set("#closeButton.Text", I18n.get("ui.faction_prisms.close"));

        if (faction == null) {
            commandBuilder.set("#noPrismsLabel.Text", I18n.get("ui.faction_prisms.no_prisms"));
            commandBuilder.set("#noPrismsLabel.Visible", true);
            commandBuilder.clear("#PrismList");
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#backButton",
                new EventData().append("Action", "back")
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#closeButton",
                new EventData().append("Action", "close")
            );
            return;
        }

        List<PrismRecord> prisms = PrismManager.getInstance().getByFactionId(faction.getId());
        long nowMs = System.currentTimeMillis();
        PrismMarkerService markerService = PrismMarkerService.getInstance();

        if (prisms.isEmpty()) {
            commandBuilder.set("#noPrismsLabel.Text", I18n.get("ui.faction_prisms.no_prisms"));
            commandBuilder.set("#noPrismsLabel.Visible", true);
            commandBuilder.clear("#PrismList");
        } else {
            commandBuilder.set("#noPrismsLabel.Visible", false);
            commandBuilder.clear("#PrismList");
            for (int i = 0; i < prisms.size(); i++) {
                PrismRecord prism = prisms.get(i);
                int timeAliveMinutes = prism.getScoreMinutes(nowMs);
                int multiplier = PrismMultiplierService.getInstance().getMultiplier(prism.getChunkX(), prism.getChunkZ());
                int points = timeAliveMinutes * multiplier;
                String pointsText = I18n.get("ui.faction_prisms.points", points);
                String coordsText = I18n.get("ui.faction_prisms.coordinates", prism.getX(), prism.getY(), prism.getZ());
                boolean marked = markerService.isMarked(playerUuid, prism.getId());

                commandBuilder.append("#PrismList", "Pages/FactionPrism/PrismRow.ui");
                String selector = "#PrismList[" + i + "]";
                String pointsSelector = selector + " #PointsLabel";
                String coordsSelector = selector + " #CoordsLabel";
                String labelSelector = selector + " #ShowOnMapLabel";
                String checkboxSelector = selector + " #ShowOnMapCheckbox";

                commandBuilder.set(pointsSelector + ".Text", pointsText);
                commandBuilder.set(coordsSelector + ".Text", coordsText);
                commandBuilder.set(labelSelector + ".Text", I18n.get("ui.faction_prisms.show_on_map"));
                commandBuilder.set(checkboxSelector + ".Value", marked);

                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    checkboxSelector,
                    new EventData()
                        .append("Action", "toggleMarker")
                        .append("PrismId", prism.getId().toString()),
                    false
                );
            }
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#backButton",
            new EventData().append("Action", "back")
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#closeButton",
            new EventData().append("Action", "close")
        );
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull FactionPrismsPageEventData data
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        UUID playerUuid = playerRef.getUuid();

        if ("back".equals(data.action) || "close".equals(data.action)) {
            FactionShowPage showPage = adminViewFactionId != null
                ? new FactionShowPage(playerRef, adminViewFactionId)
                : new FactionShowPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, showPage);
            return;
        }

        if ("toggleMarker".equals(data.action) && data.prismId != null) {
            PrismMarkerService.getInstance().toggleMarked(playerUuid, data.prismId);
            // Reopen page to refresh checkbox state
            FactionPrismsPage prismsPage = adminViewFactionId != null
                ? new FactionPrismsPage(playerRef, adminViewFactionId)
                : new FactionPrismsPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, prismsPage);
            return;
        }
    }
}
