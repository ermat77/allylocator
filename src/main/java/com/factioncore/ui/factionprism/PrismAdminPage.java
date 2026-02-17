package com.factioncore.ui.factionprism;

import com.factioncore.ui.factionprism.events.PrismAdminPageEventData;
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
import com.factioncore.prism.PrismAttackRecord;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Admin page listing all prism attacks with status and timer values.
 */
public class PrismAdminPage extends InteractiveCustomUIPage<PrismAdminPageEventData> {

    private final List<PrismAttackRecord> attacks;

    public PrismAdminPage(@Nonnull PlayerRef playerRef, @Nonnull List<PrismAttackRecord> attacks) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PrismAdminPageEventData.CODEC);
        this.attacks = attacks;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        String title = I18n.get("ui.prism_admin.title");
        if (title == null || title.isEmpty()) title = "Prism attacks (admin)";
        String back = I18n.get("ui.faction_prisms.back");
        if (back == null || back.isEmpty()) back = "Back";
        String close = I18n.get("ui.faction_prisms.close");
        if (close == null || close.isEmpty()) close = "Close";

        commandBuilder.append("Pages/FactionPrism/PrismAdminPage.ui");
        commandBuilder.set("#Title #TitleText.Text", title);
        commandBuilder.set("#backButton.Text", back);
        commandBuilder.set("#closeButton.Text", close);

        if (attacks == null || attacks.isEmpty()) {
            String noAttacks = I18n.get("ui.prism_admin.no_attacks");
            if (noAttacks == null || noAttacks.isEmpty()) noAttacks = "No prism attacks";
            commandBuilder.set("#noAttacksLabel.Text", noAttacks);
            commandBuilder.set("#noAttacksLabel.Visible", true);
            commandBuilder.clear("#AttackList");
        } else {
            commandBuilder.set("#noAttacksLabel.Visible", false);
            long nowMs = System.currentTimeMillis();
            for (int i = 0; i < attacks.size(); i++) {
                PrismAttackRecord attack = attacks.get(i);
                commandBuilder.append("#AttackList", "Pages/FactionPrism/PrismAttackRow.ui");
                String selector = "#AttackList[" + i + "]";
                String attackIdShort = attack.getAttackId().toString().substring(0, Math.min(8, attack.getAttackId().toString().length()));
                String status = attack.getStatus() != null ? attack.getStatus() : "";
                long endsAtMs = attack.getEndsAtMs();
                long destructionAtMs = attack.getDestructionAtMs();
                long combatRemainingSec = (endsAtMs - nowMs + 999) / 1000;
                long destructionRemainingSec = (destructionAtMs - nowMs + 999) / 1000;
                String timersStr;
                if ("PREPARING".equals(status) || "PENDING_START".equals(status)) {
                    timersStr = "Combat in: " + Math.max(0, combatRemainingSec) + "s | End: " + Math.max(0, destructionRemainingSec) + "s";
                } else {
                    timersStr = "ends_at: " + endsAtMs + " | destruction_at: " + destructionAtMs;
                }
                commandBuilder.set(selector + " #AttackIdLabel.Text", attackIdShort + " | " + attack.getAttackWorldName());
                commandBuilder.set(selector + " #StatusLabel.Text", status);
                commandBuilder.set(selector + " #TimersLabel.Text", timersStr);
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
        @Nonnull PrismAdminPageEventData data
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if ("back".equals(data.action) || "close".equals(data.action)) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }
}
