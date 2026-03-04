package com.factioncore.ui.factionbase;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.points.FactionPointsManager;
import com.factioncore.ui.factionbase.events.FactionLeaderboardPageEventData;
import com.factioncore.util.I18n;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Page showing faction points leaderboard (ranked list with scrollbar).
 * Highlights the row of the current player's faction (or viewed faction in admin mode) with a slightly different background.
 */
public class FactionLeaderboardPage extends InteractiveCustomUIPage<FactionLeaderboardPageEventData> {

    private final UUID adminViewFactionId;

    public FactionLeaderboardPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    public FactionLeaderboardPage(@Nonnull PlayerRef playerRef, UUID adminViewFactionId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, FactionLeaderboardPageEventData.CODEC);
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
        UUID currentFactionId = adminViewFactionId != null
            ? adminViewFactionId
            : (factionManager.getFactionFromPlayer(playerUuid) != null ? factionManager.getFactionFromPlayer(playerUuid).getId() : null);

        commandBuilder.append("Pages/FactionBase/FactionLeaderboardPage.ui");

        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.faction_leaderboard.title"));
        commandBuilder.set("#backButton.Text", I18n.get("ui.faction_leaderboard.back"));
        commandBuilder.set("#closeButton.Text", I18n.get("ui.faction_leaderboard.close"));

        FactionPointsManager pointsManager = FactionPointsManager.getInstance();
        Map<UUID, FactionInfo> allFactions = factionManager.getAllFactions();
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>();
        for (UUID factionId : allFactions.keySet()) {
            int points = pointsManager.getPoints(factionId);
            ranked.add(Map.entry(factionId, points));
        }
        ranked.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());

        if (ranked.isEmpty()) {
            commandBuilder.set("#noLeaderboardLabel.Text", I18n.get("ui.faction_leaderboard.no_factions"));
            commandBuilder.set("#noLeaderboardLabel.Visible", true);
            commandBuilder.clear("#LeaderboardList");
        } else {
            commandBuilder.set("#noLeaderboardLabel.Visible", false);
            commandBuilder.clear("#LeaderboardList");

            for (int i = 0; i < ranked.size(); i++) {
                Map.Entry<UUID, Integer> entry = ranked.get(i);
                UUID factionId = entry.getKey();
                int points = entry.getValue();
                FactionInfo info = factionManager.getFactionById(factionId);
                String factionName = info != null ? info.getName() : factionId.toString();

                commandBuilder.append("#LeaderboardList", "Pages/FactionBase/LeaderboardRow.ui");
                String selector = "#LeaderboardList[" + i + "]";
                commandBuilder.set(selector + " #RankLabel.Text", String.valueOf(i + 1));
                commandBuilder.set(selector + " #FactionNameLabel.Text", factionName);
                commandBuilder.set(selector + " #PointsLabel.Text", I18n.get("ui.faction_show.points", points));

                if (factionId.equals(currentFactionId)) {
                    commandBuilder.set(selector + ".Background.Color", "#1e2833fa");
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
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull FactionLeaderboardPageEventData data
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
        }
    }
}
