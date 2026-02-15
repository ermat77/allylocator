package com.factioncore.ui.factiondiplomacy;

import com.factioncore.diplomacy.PendingAllyRequest;
import com.factioncore.ui.factionbase.FactionMembersPage;
import com.factioncore.ui.factiondiplomacy.events.FactionPendingAllyRequestsPageEventData;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Page showing pending ally requests with Accept / Decline / Cancel actions.
 */
public class FactionPendingAllyRequestsPage extends InteractiveCustomUIPage<FactionPendingAllyRequestsPageEventData> {

    /** When non-null (e.g. opened from admin Diplomacy), we show this faction's pending requests. When null, we use the player's faction. */
    private final UUID contextFactionId;

    public FactionPendingAllyRequestsPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    /**
     * Opens pending ally requests. When contextFactionId is non-null (admin flow), we show that faction's requests.
     * When null, we show the player's faction's requests.
     */
    public FactionPendingAllyRequestsPage(@Nonnull PlayerRef playerRef, UUID contextFactionId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, FactionPendingAllyRequestsPageEventData.CODEC);
        this.contextFactionId = contextFactionId;
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
        FactionInfo faction = contextFactionId != null
            ? factionManager.getFactionById(contextFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);

        commandBuilder.append("Pages/FactionDiplomacy/FactionPendingAllyRequestsPage.ui");

        // Set static UI text from I18n
        commandBuilder.set("#pendingAllyTitleLabel.Text", I18n.get("ui.pending_requests.title"));
        commandBuilder.set("#noPendingRequestsLabel.Text", I18n.get("ui.pending_requests.no_requests"));
        commandBuilder.set("#backButton.Text", I18n.get("ui.pending_requests.back"));
        commandBuilder.set("#closeButton.Text", I18n.get("ui.pending_requests.close"));

        if (faction == null) {
            commandBuilder.set("#noPendingRequestsLabel.Text", I18n.get("ui.pending_requests.must_be_in_faction"));
            commandBuilder.set("#noPendingRequestsLabel.Visible", true);
            commandBuilder.set("#PendingRequestsList.Visible", false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#backButton",
                new EventData().append("Action", "back"));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#closeButton",
                new EventData().append("Action", "close"));
            return;
        }

        UUID factionId = faction.getId();
        boolean canManageAllies = (contextFactionId != null) || PermissionUtil.canManageAlliesInFaction(playerUuid, faction);
        List<PendingAllyRequest> pending = factionManager.getPendingAllyRequestsForFaction(factionId);

        commandBuilder.clear("#PendingRequestsList");
        if (pending.isEmpty()) {
            commandBuilder.set("#noPendingRequestsLabel.Visible", true);
        } else {
            commandBuilder.set("#noPendingRequestsLabel.Visible", false);
            int i = 0;
            for (PendingAllyRequest req : pending) {
                boolean weAreRequester = req.getRequesterFactionId().equals(factionId);
                UUID otherId = weAreRequester ? req.getTargetFactionId() : req.getRequesterFactionId();
                FactionInfo other = factionManager.getFactionById(otherId);
                String otherName = other != null ? other.getName() : otherId.toString();
                String stateText = weAreRequester ? " (Awaiting their response)" : " (Awaiting your response)";
                commandBuilder.append("#PendingRequestsList", "Pages/FactionDiplomacy/PendingRequestRow.ui");
                String sel = "#PendingRequestsList[" + i + "]";
                commandBuilder.set(sel + " #RequestLabel.Text", otherName + stateText);
                commandBuilder.set(sel + " #MembersButton.Text", I18n.get("ui.row.members"));
                commandBuilder.set(sel + " #AcceptButton.Text", I18n.get("ui.row.accept"));
                commandBuilder.set(sel + " #DeclineButton.Text", I18n.get("ui.row.decline"));
                commandBuilder.set(sel + " #CancelButton.Text", I18n.get("ui.row.cancel"));
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #MembersButton",
                    new EventData().append("Action", "openMembers").append("FactionId", otherId.toString()));
                if (weAreRequester) {
                    commandBuilder.set(sel + " #AcceptButton.Visible", false);
                    commandBuilder.set(sel + " #DeclineButton.Visible", false);
                    commandBuilder.set(sel + " #CancelButton.Visible", canManageAllies);
                    if (canManageAllies) {
                        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #CancelButton",
                            new EventData().append("Action", "cancelAllyRequest").append("FactionId", otherId.toString()));
                    }
                } else {
                    commandBuilder.set(sel + " #AcceptButton.Visible", canManageAllies);
                    commandBuilder.set(sel + " #DeclineButton.Visible", canManageAllies);
                    commandBuilder.set(sel + " #CancelButton.Visible", false);
                    if (canManageAllies) {
                        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #AcceptButton",
                            new EventData().append("Action", "acceptAllyRequest").append("FactionId", req.getRequesterFactionId().toString()));
                        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #DeclineButton",
                            new EventData().append("Action", "declineAllyRequest").append("FactionId", req.getRequesterFactionId().toString()));
                    }
                }
                i++;
            }
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#backButton",
            new EventData().append("Action", "back"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#closeButton",
            new EventData().append("Action", "close"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionPendingAllyRequestsPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if ("close".equals(data.action)) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }

        if ("back".equals(data.action)) {
            FactionDiplomacyPage diplomacyPage = new FactionDiplomacyPage(playerRef, contextFactionId);
            player.getPageManager().openCustomPage(ref, store, diplomacyPage);
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = contextFactionId != null
            ? factionManager.getFactionById(contextFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);
        if (faction == null) return;

        UUID factionId = faction.getId();
        UUID targetId = data.getFactionIdUuid();

        if ("acceptAllyRequest".equals(data.action) && targetId != null) {
            if (contextFactionId == null && !PermissionUtil.canManageAlliesInFaction(playerUuid, faction)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You don't have permission to manage allies").color(Color.RED));
                return;
            }
            boolean accepted = factionManager.acceptAllyRequest(factionId, targetId);
            if (accepted) {
                FactionInfo target = factionManager.getFactionById(targetId);
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.pending_allies.accepted", target != null ? target.getName() : targetId.toString())).color(Color.GREEN));
            } else {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Failed to accept ally request").color(Color.RED));
            }
            refreshPage(ref, store);
            return;
        }

        if ("declineAllyRequest".equals(data.action) && targetId != null) {
            if (contextFactionId == null && !PermissionUtil.canManageAlliesInFaction(playerUuid, faction)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You don't have permission to manage allies").color(Color.RED));
                return;
            }
            factionManager.declineAllyRequest(factionId, targetId);
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.pending_allies.declined")).color(Color.YELLOW));
            refreshPage(ref, store);
            return;
        }

        if ("cancelAllyRequest".equals(data.action) && targetId != null) {
            if (contextFactionId == null && !PermissionUtil.canManageAlliesInFaction(playerUuid, faction)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You don't have permission to manage allies").color(Color.RED));
                return;
            }
            factionManager.cancelAllyRequest(factionId, targetId);
            refreshPage(ref, store);
            return;
        }

        if ("openMembers".equals(data.action) && targetId != null) {
            FactionMembersPage membersPage = new FactionMembersPage(playerRef, targetId,
                (r, s) -> {
                    ConfigUtil.debug("[Back->PendingAlly] callback entered; r=" + (r != null ? "non-null, valid=" + r.isValid() : "null") + ", s=" + (s != null ? "non-null" : "null"));
                    if (r == null || !r.isValid() || s == null) {
                        ConfigUtil.debug("[Back->PendingAlly] early return: invalid ref or null store");
                        return;
                    }
                    Player p = s.getComponent(r, Player.getComponentType());
                    PlayerRef currentPlayerRef = s.getComponent(r, PlayerRef.getComponentType());
                    ConfigUtil.debug("[Back->PendingAlly] p=" + (p != null ? "non-null" : "null")
                        + ", currentPlayerRef=" + (currentPlayerRef != null ? "non-null" : "null")
                        + ", pageManager=" + (p != null && p.getPageManager() != null ? "non-null" : "null"));
                    if (p == null || currentPlayerRef == null || p.getPageManager() == null) {
                        ConfigUtil.debug("[Back->PendingAlly] early return: missing component or pageManager");
                        return;
                    }
                    World world = p.getWorld();
                    ConfigUtil.debug("[Back->PendingAlly] world=" + (world != null ? world.getName() : "null"));
                    Runnable openPage = () -> {
                        try {
                            ConfigUtil.debug("[Back->PendingAlly] openPage runnable started; r.isValid()=" + r.isValid());
                            if (r.isValid()) {
                                p.getPageManager().openCustomPage(r, s, new FactionPendingAllyRequestsPage(currentPlayerRef, contextFactionId));
                                ConfigUtil.debug("[Back->PendingAlly] openCustomPage(PendingAlly) completed");
                            } else {
                                ConfigUtil.debug("[Back->PendingAlly] ref no longer valid, skipping open");
                            }
                        } catch (Throwable t) {
                            ConfigUtil.debug("[Back->PendingAlly] openPage runnable threw: " + t.getMessage(), t);
                            throw t;
                        }
                    };
                    if (world != null) {
                        ConfigUtil.debug("[Back->PendingAlly] scheduling open on world " + world.getName());
                        world.execute(openPage);
                    } else {
                        ConfigUtil.debug("[Back->PendingAlly] world null, calling openPage.run() directly");
                        openPage.run();
                    }
                });
            player.getPageManager().openCustomPage(ref, store, membersPage);
            return;
        }
    }

    private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!ref.isValid()) return;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        build(ref, commandBuilder, eventBuilder, store);
        sendUpdate(commandBuilder, eventBuilder, true);
    }
}
