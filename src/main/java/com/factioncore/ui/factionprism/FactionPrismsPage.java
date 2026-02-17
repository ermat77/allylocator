package com.factioncore.ui.factionprism;

import com.factioncore.faction.FactionInfo;
import com.factioncore.ui.factionbase.FactionShowPage;
import com.factioncore.ui.factionprism.events.FactionPrismsPageEventData;
import com.factioncore.faction.FactionManager;
import com.factioncore.prism.PrismAttackEventDispatcher;
import com.factioncore.prism.PrismManager;
import com.factioncore.prism.PrismMultiplierService;
import com.factioncore.prism.PrismRecord;
import com.factioncore.prism.PlayerJoinPrismAttackEvent;
import com.factioncore.util.ConfigUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Page listing all prisms of the player's faction.
 * Shows current points (time alive × multiplier) and coordinates per prism.
 * List scrolls when there are many prisms (#PrismList has TopScrolling + DefaultScrollbarStyle).
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
        try {
        ConfigUtil.debug("[FactionPrismsPage.build] ENTER playerUuid=" + playerRef.getUuid() + " adminViewFactionId=" + adminViewFactionId);

        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        ConfigUtil.debug("[FactionPrismsPage.build] FactionManager instance: " + (factionManager != null ? "ok" : "NULL"));

        FactionInfo faction = adminViewFactionId != null
            ? factionManager.getFactionById(adminViewFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);
        ConfigUtil.debug("[FactionPrismsPage.build] faction=" + (faction != null ? faction.getName() : "null") + " faction.getId()=" + (faction != null ? faction.getId() : "n/a"));

        ConfigUtil.debug("[FactionPrismsPage.build] Appending page UI: Pages/FactionPrism/FactionPrismsPage.ui");
        commandBuilder.append("Pages/FactionPrism/FactionPrismsPage.ui");

        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.faction_prisms.title"));
        commandBuilder.set("#backButton.Text", I18n.get("ui.faction_prisms.back"));
        commandBuilder.set("#closeButton.Text", I18n.get("ui.faction_prisms.close"));

        if (faction == null || faction.getId() == null) {
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
            ConfigUtil.debug("[FactionPrismsPage.build] EXIT (no faction path)");
        } else {
            List<PrismRecord> prisms = PrismManager.getInstance().getByFactionId(faction.getId());
            long nowMs = System.currentTimeMillis();

            // Build display list: valid prisms only (all prisms; list scrolls via #PrismList TopScrolling)
            ArrayList<PrismRecord> displayList = new ArrayList<>();
            if (prisms != null) {
                for (PrismRecord p : prisms) {
                    if (p != null && p.getId() != null) {
                        displayList.add(p);
                    }
                }
            }

            commandBuilder.clear("#PrismList");

            if (displayList.isEmpty()) {
                commandBuilder.set("#noPrismsLabel.Text", I18n.get("ui.faction_prisms.no_prisms"));
                commandBuilder.set("#noPrismsLabel.Visible", true);
            } else {
                commandBuilder.set("#noPrismsLabel.Visible", false);
                // Same pattern as FactionShowPage MemberList: for each index i, append then set properties (selector #PrismList[i])
                for (int i = 0; i < displayList.size(); i++) {
                    PrismRecord prism = displayList.get(i);
                    commandBuilder.append("#PrismList", "Pages/FactionPrism/PrismRow.ui");

                    String selector = "#PrismList[" + i + "]";
                    String pointsSelector = selector + " #PointsLabel";
                    String coordsSelector = selector + " #CoordsLabel";
                    String multiplierSelector = selector + " #MultiplierLabel";
                    String defendButtonSelector = selector + " #DefendButton";

                    int timeAliveMinutes = prism.getScoreMinutes(nowMs);
                    int multiplier = PrismMultiplierService.getInstance().getMultiplier(prism.getChunkX(), prism.getChunkZ());
                    int points = timeAliveMinutes * multiplier;
                    String pointsText = I18n.get("ui.faction_prisms.points", points);
                    String coordsText = I18n.get("ui.faction_prisms.coordinates", prism.getX(), prism.getY(), prism.getZ());
                    String multiplierText = I18n.get("ui.faction_prisms.multiplier", multiplier);

                    commandBuilder.set(pointsSelector + ".Text", pointsText);
                    commandBuilder.set(coordsSelector + ".Text", coordsText);
                    commandBuilder.set(multiplierSelector + ".Text", multiplierText);

                    boolean isAttacked = prism.getStatus() != null && "attacked".equals(prism.getStatus());
                    commandBuilder.set(defendButtonSelector + ".Visible", isAttacked);
                    if (isAttacked) {
                        eventBuilder.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            defendButtonSelector,
                            new EventData()
                                .append("Action", "defend")
                                .append("PrismId", prism.getId().toString())
                        );
                    }
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
            ConfigUtil.debug("[FactionPrismsPage.build] EXIT (build complete)");
        }
        } catch (Throwable t) {
            ConfigUtil.debug("[FactionPrismsPage.build] EXCEPTION: " + t.getClass().getSimpleName() + " " + t.getMessage());
            for (StackTraceElement e : t.getStackTrace()) {
                ConfigUtil.debug("[FactionPrismsPage.build]   at " + e.toString());
            }
            throw t;
        }
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

        if ("back".equals(data.action) || "close".equals(data.action)) {
            FactionShowPage showPage = adminViewFactionId != null
                ? new FactionShowPage(playerRef, adminViewFactionId)
                : new FactionShowPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, showPage);
            return;
        }

        if ("defend".equals(data.action) && data.prismId != null) {
            PrismAttackEventDispatcher.getInstance().dispatch(
                new PlayerJoinPrismAttackEvent(playerRef, ref, store, data.prismId, "defender")
            );
            player.getPageManager().setPage(ref, store, com.hypixel.hytale.protocol.packets.interface_.Page.None);
            return;
        }
    }
}
