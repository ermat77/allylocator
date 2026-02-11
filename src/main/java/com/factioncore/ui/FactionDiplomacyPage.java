package com.factioncore.ui;

import com.factioncore.diplomacy.PendingAllyRequest;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.ui.events.FactionDiplomacyPageEventData;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Diplomacy page: manage enemies, pending ally requests, allies, and other factions (with search).
 */
public class FactionDiplomacyPage extends InteractiveCustomUIPage<FactionDiplomacyPageEventData> {

    private volatile String searchFilter = "";
    /** When non-null, manage this faction (admin context). When null, use player's faction. */
    private final UUID contextFactionId;

    public FactionDiplomacyPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    /**
     * Opens diplomacy for a specific faction (e.g. when opened from /f admin).
     *
     * @param playerRef       The player viewing the page
     * @param contextFactionId The faction to manage, or null for player's own faction
     */
    public FactionDiplomacyPage(@Nonnull PlayerRef playerRef, UUID contextFactionId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, FactionDiplomacyPageEventData.CODEC);
        this.contextFactionId = contextFactionId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        ConfigUtil.debug("[Diplomacy] build() called; ref.valid=" + (ref != null && ref.isValid()));
        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = contextFactionId != null
            ? factionManager.getFactionById(contextFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);
        ConfigUtil.debug("[Diplomacy] build() faction=" + (faction != null ? faction.getName() : "null") + ", searchFilter=\"" + (searchFilter != null ? searchFilter : "null") + "\"");

        commandBuilder.append("Pages/FactionDiplomacyPage.ui");

        // Set static UI text from I18n
        commandBuilder.set("#diplomacyTitleLabel.Text", I18n.get("ui.diplomacy.title"));
        commandBuilder.set("#enemiesHeaderLabel.Text", I18n.get("ui.diplomacy.enemies"));
        commandBuilder.set("#noEnemiesLabel.Text", I18n.get("ui.diplomacy.no_enemies"));
        commandBuilder.set("#alliesHeaderLabel.Text", I18n.get("ui.diplomacy.allies"));
        commandBuilder.set("#manageRequestsButton.Text", I18n.get("ui.diplomacy.manage_requests"));
        commandBuilder.set("#noAlliesLabel.Text", I18n.get("ui.diplomacy.no_allies"));
        commandBuilder.set("#otherFactionsHeaderLabel.Text", I18n.get("ui.diplomacy.other_factions"));
        commandBuilder.set("#SearchInput.PlaceholderText", I18n.get("ui.diplomacy.filter_placeholder"));
        commandBuilder.set("#noOtherFactionsLabel.Text", I18n.get("ui.diplomacy.no_other_factions"));
        commandBuilder.set("#backButton.Text", I18n.get("ui.diplomacy.back"));
        commandBuilder.set("#closeButton.Text", I18n.get("ui.diplomacy.close"));

        if (faction == null) {
            ConfigUtil.debug("[Diplomacy] build() no faction, hiding sections and binding back/close only");
            commandBuilder.set("#enemiesHeaderLabel.Visible", false);
            commandBuilder.set("#alliesHeaderRow.Visible", false);
            commandBuilder.set("#otherFactionsHeaderLabel.Visible", false);
            commandBuilder.set("#otherFactionsFilterRow.Visible", false);
            commandBuilder.set("#noEnemiesLabel.Visible", false);
            commandBuilder.set("#noAlliesLabel.Visible", false);
            commandBuilder.set("#noOtherFactionsLabel.Text", I18n.get("ui.diplomacy.must_be_in_faction"));
            commandBuilder.set("#noOtherFactionsLabel.Visible", true);
            commandBuilder.set("#EnemyList.Visible", false);
            commandBuilder.set("#AllyList.Visible", false);
            commandBuilder.set("#OtherFactionsList.Visible", false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#backButton",
                new EventData().append("Action", "back"));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#closeButton",
                new EventData().append("Action", "close"));
            return;
        }

        UUID factionId = faction.getId();
        boolean isAdminContext = contextFactionId != null;
        boolean canManageEnemies = isAdminContext || PermissionUtil.canManageEnemiesInFaction(playerUuid, faction);
        boolean canManageAllies = isAdminContext || PermissionUtil.canManageAlliesInFaction(playerUuid, faction);

        // Enemies list
        Set<UUID> enemies = factionManager.getEnemies(factionId);
        commandBuilder.clear("#EnemyList");
        if (enemies.isEmpty()) {
            commandBuilder.set("#noEnemiesLabel.Visible", true);
        } else {
            commandBuilder.set("#noEnemiesLabel.Visible", false);
            int i = 0;
            for (UUID enemyId : enemies) {
                FactionInfo enemyFaction = factionManager.getFactionById(enemyId);
                String name = enemyFaction != null ? enemyFaction.getName() : enemyId.toString();
                commandBuilder.append("#EnemyList", "Pages/EnemyRow.ui");
                String sel = "#EnemyList[" + i + "]";
                commandBuilder.set(sel + " #EnemyLabel.Text", name);
                commandBuilder.set(sel + " #MembersButton.Text", I18n.get("ui.row.members"));
                commandBuilder.set(sel + " #RemoveButton.Text", I18n.get("ui.row.remove"));
                commandBuilder.set(sel + " #RemoveButton.Visible", canManageEnemies);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #MembersButton",
                    new EventData().append("Action", "openMembers").append("FactionId", enemyId.toString()));
                if (canManageEnemies) {
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #RemoveButton",
                        new EventData().append("Action", "removeEnemy").append("FactionId", enemyId.toString()));
                }
                i++;
            }
        }

        // Manage Requests button (opens pending ally requests page)
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#manageRequestsButton",
            new EventData().append("Action", "manageRequests"));

        // Allies list
        Set<UUID> allies = factionManager.getAllies(factionId);
        commandBuilder.clear("#AllyList");
        if (allies.isEmpty()) {
            commandBuilder.set("#noAlliesLabel.Visible", true);
        } else {
            commandBuilder.set("#noAlliesLabel.Visible", false);
            int i = 0;
            for (UUID allyId : allies) {
                FactionInfo allyFaction = factionManager.getFactionById(allyId);
                String name = allyFaction != null ? allyFaction.getName() : allyId.toString();
                commandBuilder.append("#AllyList", "Pages/AllyRow.ui");
                String sel = "#AllyList[" + i + "]";
                commandBuilder.set(sel + " #AllyLabel.Text", name);
                commandBuilder.set(sel + " #MembersButton.Text", I18n.get("ui.row.members"));
                commandBuilder.set(sel + " #SettingsButton.Text", I18n.get("ui.row.settings"));
                commandBuilder.set(sel + " #RemoveButton.Text", I18n.get("ui.row.remove"));
                commandBuilder.set(sel + " #RemoveButton.Visible", canManageAllies);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #MembersButton",
                    new EventData().append("Action", "openMembers").append("FactionId", allyId.toString()));
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #SettingsButton",
                    new EventData().append("Action", "openAllySettings").append("FactionId", allyId.toString()));
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #RemoveButton",
                    new EventData().append("Action", "removeAlly").append("FactionId", allyId.toString()));
                i++;
            }
        }

        // Other factions: all minus own, allies, enemies, and any in pending with us
        List<PendingAllyRequest> pending = factionManager.getPendingAllyRequestsForFaction(factionId);
        Set<UUID> exclude = new HashSet<>();
        exclude.add(factionId);
        exclude.addAll(allies);
        exclude.addAll(enemies);
        for (PendingAllyRequest req : pending) {
            exclude.add(req.getRequesterFactionId());
            exclude.add(req.getTargetFactionId());
        }
        String filter = searchFilter != null ? searchFilter.trim().toLowerCase() : "";
        List<FactionInfo> otherFactions = new ArrayList<>();
        for (Map.Entry<UUID, FactionInfo> e : factionManager.getAllFactions().entrySet()) {
            if (exclude.contains(e.getKey())) continue;
            if (!filter.isEmpty() && (e.getValue().getName() == null || !e.getValue().getName().toLowerCase().contains(filter))) continue;
            otherFactions.add(e.getValue());
        }

        commandBuilder.clear("#OtherFactionsList");
        if (otherFactions.isEmpty()) {
            commandBuilder.set("#noOtherFactionsLabel.Visible", true);
        } else {
            commandBuilder.set("#noOtherFactionsLabel.Visible", false);
            int i = 0;
            for (FactionInfo other : otherFactions) {
                commandBuilder.append("#OtherFactionsList", "Pages/OtherFactionRow.ui");
                String sel = "#OtherFactionsList[" + i + "]";
                commandBuilder.set(sel + " #FactionLabel.Text", other.getName());
                commandBuilder.set(sel + " #MembersButton.Text", I18n.get("ui.row.members"));
                commandBuilder.set(sel + " #SendAllyRequestButton.Text", I18n.get("ui.row.ally"));
                commandBuilder.set(sel + " #DeclareEnemyButton.Text", I18n.get("ui.row.enemy"));
                commandBuilder.set(sel + " #SendAllyRequestButton.Visible", canManageAllies);
                commandBuilder.set(sel + " #DeclareEnemyButton.Visible", canManageEnemies);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #MembersButton",
                    new EventData().append("Action", "openMembers").append("FactionId", other.getId().toString()));
                if (canManageAllies) {
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #SendAllyRequestButton",
                        new EventData().append("Action", "sendAllyRequest").append("FactionId", other.getId().toString()));
                }
                if (canManageEnemies) {
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #DeclareEnemyButton",
                        new EventData().append("Action", "declareEnemy").append("FactionId", other.getId().toString()));
                }
                i++;
            }
        }

        // Restore search field value so it persists when list is refreshed
        commandBuilder.set("#SearchInput.Value", searchFilter != null ? searchFilter : "");
        ConfigUtil.debug("[Diplomacy] build() binding search: ValueChanged->filterUpdate; otherFactionsCount=" + otherFactions.size());

        // Search: ValueChanged only updates server-side filter (no refresh) so input keeps focus while typing.
        // Use only @SearchInput for the control value; avoid "Value" as the client may send the literal "#SearchInput.Value" for that key and cause issues.
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
            new EventData().append("Action", "filterUpdate").append("@SearchInput", "#SearchInput.Value"));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#backButton",
            new EventData().append("Action", "back"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#closeButton",
            new EventData().append("Action", "close"));
    }

    /**
     * Builds only the "Other factions" list and search input (partial update).
     * Used on filterUpdate so we can sendUpdate(..., false) and avoid full page replace (keeps focus, avoids freeze).
     * Mirrors SimpleClaims PartyListGui: on search they only buildList + sendUpdate(..., false).
     */
    private void buildOtherFactionsListOnly(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
        if (faction == null) return;

        UUID factionId = faction.getId();
        boolean isAdminContext = contextFactionId != null;
        boolean canManageEnemies = isAdminContext || PermissionUtil.canManageEnemiesInFaction(playerUuid, faction);
        boolean canManageAllies = isAdminContext || PermissionUtil.canManageAlliesInFaction(playerUuid, faction);
        Set<UUID> allies = factionManager.getAllies(factionId);
        Set<UUID> enemies = factionManager.getEnemies(factionId);
        List<PendingAllyRequest> pending = factionManager.getPendingAllyRequestsForFaction(factionId);
        Set<UUID> exclude = new HashSet<>();
        exclude.add(factionId);
        exclude.addAll(allies);
        exclude.addAll(enemies);
        for (PendingAllyRequest req : pending) {
            exclude.add(req.getRequesterFactionId());
            exclude.add(req.getTargetFactionId());
        }
        String filter = searchFilter != null ? searchFilter.trim().toLowerCase() : "";
        List<FactionInfo> otherFactions = new ArrayList<>();
        for (Map.Entry<UUID, FactionInfo> e : factionManager.getAllFactions().entrySet()) {
            if (exclude.contains(e.getKey())) continue;
            if (!filter.isEmpty() && (e.getValue().getName() == null || !e.getValue().getName().toLowerCase().contains(filter))) continue;
            otherFactions.add(e.getValue());
        }

        commandBuilder.set("#SearchInput.Value", searchFilter != null ? searchFilter : "");
        commandBuilder.clear("#OtherFactionsList");
        if (otherFactions.isEmpty()) {
            commandBuilder.set("#noOtherFactionsLabel.Visible", true);
        } else {
            commandBuilder.set("#noOtherFactionsLabel.Visible", false);
            int i = 0;
            for (FactionInfo other : otherFactions) {
                commandBuilder.append("#OtherFactionsList", "Pages/OtherFactionRow.ui");
                String sel = "#OtherFactionsList[" + i + "]";
                commandBuilder.set(sel + " #FactionLabel.Text", other.getName());
                commandBuilder.set(sel + " #MembersButton.Text", I18n.get("ui.row.members"));
                commandBuilder.set(sel + " #SendAllyRequestButton.Text", I18n.get("ui.row.ally"));
                commandBuilder.set(sel + " #DeclareEnemyButton.Text", I18n.get("ui.row.enemy"));
                commandBuilder.set(sel + " #SendAllyRequestButton.Visible", canManageAllies);
                commandBuilder.set(sel + " #DeclareEnemyButton.Visible", canManageEnemies);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #MembersButton",
                    new EventData().append("Action", "openMembers").append("FactionId", other.getId().toString()));
                if (canManageAllies) {
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #SendAllyRequestButton",
                        new EventData().append("Action", "sendAllyRequest").append("FactionId", other.getId().toString()));
                }
                if (canManageEnemies) {
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #DeclareEnemyButton",
                        new EventData().append("Action", "declareEnemy").append("FactionId", other.getId().toString()));
                }
                i++;
            }
        }
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
            new EventData().append("Action", "filterUpdate").append("@SearchInput", "#SearchInput.Value"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionDiplomacyPageEventData data) {
        try {
            ConfigUtil.debug("[Diplomacy] handleDataEvent() entered; action=" + (data != null ? data.action : "null")
                + " searchFilter=" + (data != null && data.searchFilter != null ? "\"" + data.searchFilter + "\"" : "null")
                + " value=" + (data != null && data.value != null ? "\"" + data.value + "\"" : "null")
                + " factionId=" + (data != null ? data.factionId : "null"));
        } catch (Throwable t) {
            ConfigUtil.debug("[Diplomacy] handleDataEvent() failed while logging entry: " + t.getMessage(), t);
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ConfigUtil.debug("[Diplomacy] handleDataEvent() player=null, return");
            return;
        }

        String action = data.action;
        if (action == null) {
            ConfigUtil.debug("[Diplomacy] handleDataEvent() action=null, return");
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = contextFactionId != null
            ? factionManager.getFactionById(contextFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);

        if ("close".equals(action)) {
            ConfigUtil.debug("[Diplomacy] handleDataEvent() close");
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }

        if ("back".equals(action)) {
            ConfigUtil.debug("[Diplomacy] handleDataEvent() back -> FactionShowPage");
            FactionShowPage showPage = contextFactionId != null
                ? new FactionShowPage(playerRef, contextFactionId)
                : new FactionShowPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, showPage);
            return;
        }

        if ("filterUpdate".equals(action)) {
            String resolved = getSearchFilterFromData(data);
            ConfigUtil.debug("[Diplomacy] handleDataEvent() filterUpdate: resolved searchFilter=\"" + resolved + "\", sending partial update (list only)");
            searchFilter = resolved;
            if (faction != null) {
                UICommandBuilder commandBuilder = new UICommandBuilder();
                UIEventBuilder eventBuilder = new UIEventBuilder();
                buildOtherFactionsListOnly(ref, commandBuilder, eventBuilder, store);
                sendUpdate(commandBuilder, eventBuilder, false);
            }
            return;
        }
        if (faction == null) {
            ConfigUtil.debug("[Diplomacy] handleDataEvent() faction=null for action=" + action + ", return");
            return;
        }

        UUID factionId = faction.getId();
        UUID targetId = data.getFactionIdUuid();

        if ("declareEnemy".equals(action) && targetId != null) {
            if (contextFactionId == null && !PermissionUtil.canManageEnemiesInFaction(playerUuid, faction)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.no_permission_enemies")).color(Color.RED));
                return;
            }
            FactionInfo target = factionManager.getFactionById(targetId);
            String targetName = target != null ? target.getName() : targetId.toString();
            ConfirmationPage confirmPage = new ConfirmationPage(
                playerRef,
                "Confirm",
                "Declare " + targetName + " as enemy?",
                "Yes",
                "No",
                () -> {
                    factionManager.addEnemy(factionId, targetId);
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.declared_enemy", targetName)).color(Color.YELLOW));
                    FactionDiplomacyPage diplomacyPage = new FactionDiplomacyPage(playerRef, contextFactionId);
                    player.getPageManager().openCustomPage(ref, store, diplomacyPage);
                },
                () -> {
                    FactionDiplomacyPage diplomacyPage = new FactionDiplomacyPage(playerRef, contextFactionId);
                    player.getPageManager().openCustomPage(ref, store, diplomacyPage);
                }
            );
            player.getPageManager().openCustomPage(ref, store, confirmPage);
            return;
        }

        if ("removeEnemy".equals(action) && targetId != null) {
            if (contextFactionId == null && !PermissionUtil.canManageEnemiesInFaction(playerUuid, faction)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.no_permission_enemies")).color(Color.RED));
                return;
            }
            factionManager.removeEnemy(factionId, targetId);
            refreshPage(ref, store);
            return;
        }

        if ("removeAlly".equals(action) && targetId != null) {
            if (contextFactionId == null && !PermissionUtil.canManageAlliesInFaction(playerUuid, faction)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.no_permission_allies")).color(Color.RED));
                return;
            }
            FactionInfo allyFaction = factionManager.getFactionById(targetId);
            String allyName = allyFaction != null ? allyFaction.getName() : targetId.toString();
            boolean removed = factionManager.removeAlly(factionId, targetId);
            if (removed) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.ally_removed", allyName)).color(Color.GREEN));
            }
            refreshPage(ref, store);
            return;
        }

        if ("manageRequests".equals(action)) {
            FactionPendingAllyRequestsPage pendingPage = new FactionPendingAllyRequestsPage(playerRef, contextFactionId);
            player.getPageManager().openCustomPage(ref, store, pendingPage);
            return;
        }

        if ("sendAllyRequest".equals(action) && targetId != null) {
            if (contextFactionId == null && !PermissionUtil.canManageAlliesInFaction(playerUuid, faction)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.no_permission_allies")).color(Color.RED));
                return;
            }
            FactionInfo target = factionManager.getFactionById(targetId);
            String targetName = target != null ? target.getName() : targetId.toString();
            ConfirmationPage confirmPage = new ConfirmationPage(
                playerRef,
                "Confirm",
                "Send ally request to " + targetName + "?",
                "Yes",
                "No",
                () -> {
                    boolean sent = factionManager.sendAllyRequest(factionId, targetId);
                    if (sent) {
                        player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.ally_request_sent", targetName)).color(Color.GREEN));
                    } else {
                        player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.ally_request_failed")).color(Color.RED));
                    }
                    FactionDiplomacyPage diplomacyPage = new FactionDiplomacyPage(playerRef, contextFactionId);
                    player.getPageManager().openCustomPage(ref, store, diplomacyPage);
                },
                () -> {
                    FactionDiplomacyPage diplomacyPage = new FactionDiplomacyPage(playerRef, contextFactionId);
                    player.getPageManager().openCustomPage(ref, store, diplomacyPage);
                }
            );
            player.getPageManager().openCustomPage(ref, store, confirmPage);
            return;
        }

        if ("openAllySettings".equals(action)) {
            ConfigUtil.debug("[Diplomacy] openAllySettings: entered; targetId=" + targetId + ", data.factionId=" + (data != null ? data.factionId : "null"));
            if (targetId == null) {
                ConfigUtil.debug("[Diplomacy] openAllySettings: factionId missing in event data");
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Could not open ally settings (missing data). Try again.").color(Color.RED));
                return;
            }
            if (player.getPageManager() == null) {
                ConfigUtil.debug("[Diplomacy] openAllySettings: pageManager null, skipping");
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.cannot_open_ally_settings")).color(Color.RED));
                return;
            }
            FactionInfo allyFaction = factionManager.getFactionById(targetId);
            if (allyFaction == null) {
                ConfigUtil.debug("[Diplomacy] openAllySettings: ally faction not found for targetId=" + targetId);
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.diplomacy.faction_no_longer_exists")).color(Color.RED));
                return;
            }
            ConfigUtil.debug("[Diplomacy] openAllySettings: opening FactionAllySettingsPage for ally=" + (allyFaction.getName() != null ? allyFaction.getName() : targetId));
            World world = player.getWorld();
            ConfigUtil.debug("[Diplomacy] openAllySettings: world=" + (world != null ? world.getName() : "null"));
            Runnable openPage = () -> {
                try {
                    ConfigUtil.debug("[Diplomacy] openAllySettings: runnable started; ref.isValid()=" + ref.isValid());
                    if (!ref.isValid()) {
                        ConfigUtil.debug("[Diplomacy] openAllySettings: ref no longer valid, skipping open");
                        return;
                    }
                    FactionAllySettingsPage settingsPage = new FactionAllySettingsPage(playerRef, targetId, contextFactionId);
                    player.getPageManager().openCustomPage(ref, store, settingsPage);
                    ConfigUtil.debug("[Diplomacy] openAllySettings: openCustomPage(AllySettings) completed");
                } catch (Throwable t) {
                    ConfigUtil.debug("[Diplomacy] openAllySettings: runnable threw: " + t.getMessage(), t);
                    throw t;
                }
            };
            if (world != null) {
                ConfigUtil.debug("[Diplomacy] openAllySettings: scheduling open on world " + world.getName());
                world.execute(openPage);
            } else {
                ConfigUtil.debug("[Diplomacy] openAllySettings: world null, calling openPage.run() directly");
                openPage.run();
            }
            return;
        }

        if ("openMembers".equals(action) && targetId != null) {
            UUID backContextFactionId = contextFactionId;
            FactionMembersPage membersPage = new FactionMembersPage(playerRef, targetId,
                (r, s) -> {
                    ConfigUtil.debug("[Back->Diplomacy] callback entered; r=" + (r != null ? "non-null, valid=" + r.isValid() : "null") + ", s=" + (s != null ? "non-null" : "null"));
                    if (r == null || !r.isValid() || s == null) {
                        ConfigUtil.debug("[Back->Diplomacy] early return: invalid ref or null store");
                        return;
                    }
                    Player p = s.getComponent(r, Player.getComponentType());
                    PlayerRef currentPlayerRef = s.getComponent(r, PlayerRef.getComponentType());
                    ConfigUtil.debug("[Back->Diplomacy] p=" + (p != null ? "non-null" : "null")
                        + ", currentPlayerRef=" + (currentPlayerRef != null ? "non-null" : "null")
                        + ", pageManager=" + (p != null && p.getPageManager() != null ? "non-null" : "null"));
                    if (p == null || currentPlayerRef == null || p.getPageManager() == null) {
                        ConfigUtil.debug("[Back->Diplomacy] early return: missing component or pageManager");
                        return;
                    }
                    World world = p.getWorld();
                    ConfigUtil.debug("[Back->Diplomacy] world=" + (world != null ? world.getName() : "null"));
                    Runnable openPage = () -> {
                        try {
                            ConfigUtil.debug("[Back->Diplomacy] openPage runnable started; r.isValid()=" + r.isValid());
                            if (r.isValid()) {
                                p.getPageManager().openCustomPage(r, s, new FactionDiplomacyPage(currentPlayerRef, backContextFactionId));
                                ConfigUtil.debug("[Back->Diplomacy] openCustomPage(Diplomacy) completed");
                            } else {
                                ConfigUtil.debug("[Back->Diplomacy] ref no longer valid, skipping open");
                            }
                        } catch (Throwable t) {
                            ConfigUtil.debug("[Back->Diplomacy] openPage runnable threw: " + t.getMessage(), t);
                            throw t;
                        }
                    };
                    if (world != null) {
                        ConfigUtil.debug("[Back->Diplomacy] scheduling open on world " + world.getName());
                        world.execute(openPage);
                    } else {
                        ConfigUtil.debug("[Back->Diplomacy] world null, calling openPage.run() directly");
                        openPage.run();
                    }
                });
            player.getPageManager().openCustomPage(ref, store, membersPage);
            return;
        }
    }

    private static String getSearchFilterFromData(FactionDiplomacyPageEventData data) {
        if (data == null) return "";
        if (data.searchFilter != null && !data.searchFilter.isEmpty()) return data.searchFilter;
        if (data.value != null && !data.value.isEmpty()) return data.value;
        return "";
    }

    private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        ConfigUtil.debug("[Diplomacy] refreshPage() called; ref=" + (ref != null ? "non-null, valid=" + ref.isValid() : "null"));
        if (ref == null || !ref.isValid()) {
            ConfigUtil.debug("[Diplomacy] refreshPage() ref null or invalid, return");
            return;
        }
        try {
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            build(ref, commandBuilder, eventBuilder, store);
            sendUpdate(commandBuilder, eventBuilder, true);
            ConfigUtil.debug("[Diplomacy] refreshPage() sendUpdate done");
        } catch (Throwable t) {
            ConfigUtil.debug("[Diplomacy] refreshPage() failed: " + t.getMessage(), t);
            throw t;
        }
    }
}
