package com.factioncore.ui;

import com.factioncore.diplomacy.AllySettings;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.ui.events.FactionAllySettingsPageEventData;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Ally settings sub-page: share PlayerMarkers on map, allow interactions in claimed chunks.
 * Follows SimpleClaims PermissionEditGui: CheckBoxWithLabel in UI, ValueChanged per checkbox (no Save-button value binding).
 */
public class FactionAllySettingsPage extends InteractiveCustomUIPage<FactionAllySettingsPageEventData> {

    private final UUID allyFactionId;
    /** When non-null (e.g. /f admin Flowers), we edit this faction's ally settings. When null, we use the player's faction. */
    private final UUID contextFactionId;

    /** Opens ally settings for the player's own faction and the given ally. */
    public FactionAllySettingsPage(@Nonnull PlayerRef playerRef, @Nonnull UUID allyFactionId) {
        this(playerRef, allyFactionId, null);
    }

    /**
     * Opens ally settings. When contextFactionId is non-null (admin flow), we edit that faction's settings for the ally.
     * When null, we edit the player's faction's settings for the ally.
     */
    public FactionAllySettingsPage(@Nonnull PlayerRef playerRef, @Nonnull UUID allyFactionId, UUID contextFactionId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, FactionAllySettingsPageEventData.CODEC);
        this.allyFactionId = allyFactionId;
        this.contextFactionId = contextFactionId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        ConfigUtil.debug("[AllySettings] build() called; allyFactionId=" + allyFactionId + ", contextFactionId=" + contextFactionId + ", ref.valid=" + (ref != null && ref.isValid()));
        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = contextFactionId != null
            ? factionManager.getFactionById(contextFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);
        FactionInfo allyFaction = factionManager.getFactionById(allyFactionId);
        ConfigUtil.debug("[AllySettings] build() faction=" + (faction != null ? faction.getName() : "null") + " (contextFactionId=" + contextFactionId + ") allyFaction=" + (allyFaction != null ? allyFaction.getName() : "null"));

        commandBuilder.append("Pages/FactionAllySettingsPage.ui");

        // Title and back button only (like SimpleClaims: no .Text on CheckBoxWithLabel from Java)
        commandBuilder.set("#TitleLabel.Text", faction != null && allyFaction != null
            ? "Ally settings: " + (allyFaction.getName() != null ? allyFaction.getName() : "Unknown")
            : I18n.get("ui.ally_settings.title"));
        commandBuilder.set("#backButton.Text", I18n.get("ui.ally_settings.back"));

        if (faction == null || allyFaction == null) {
            ConfigUtil.debug("[AllySettings] build() early return: faction or allyFaction null");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#backButton",
                new EventData().append("Action", "back"));
            return;
        }

        boolean canManageAllies = (contextFactionId != null) || PermissionUtil.canManageAlliesInFaction(playerUuid, faction);
        AllySettings settings = factionManager.getAllySettings(faction.getId(), allyFactionId);
        if (settings == null) settings = new AllySettings();

        // SimpleClaims pattern: only #Element #CheckBox.Value and #CheckBox.Disabled; ValueChanged with Action only (no @-refs)
        addAllyToggle(commandBuilder, eventBuilder, "#ShareMarkers", "shareMarkers", settings.isSharePlayerMarkers(), canManageAllies);
        addAllyToggle(commandBuilder, eventBuilder, "#AllowInteractions", "allowInteractions", settings.isAllowInteractionsInClaimedChunks(), canManageAllies);
        addAllyToggle(commandBuilder, eventBuilder, "#OpenChest", "openChest", settings.isAllowOpenChest(), canManageAllies);
        addAllyToggle(commandBuilder, eventBuilder, "#OpenDoor", "openDoor", settings.isAllowOpenDoor(), canManageAllies);
        addAllyToggle(commandBuilder, eventBuilder, "#OpenBench", "openBench", settings.isAllowOpenBench(), canManageAllies);
        addAllyToggle(commandBuilder, eventBuilder, "#UseChair", "useChair", settings.isAllowUseChair(), canManageAllies);
        addAllyToggle(commandBuilder, eventBuilder, "#UsePortal", "usePortal", settings.isAllowUsePortal(), canManageAllies);
        addAllyToggle(commandBuilder, eventBuilder, "#PlaceBlock", "placeBlock", settings.isAllowPlaceBlock(), canManageAllies);
        addAllyToggle(commandBuilder, eventBuilder, "#BreakBlock", "breakBlock", settings.isAllowBreakBlock(), canManageAllies);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#backButton", new EventData().append("Action", "back"));
        ConfigUtil.debug("[AllySettings] build() completed successfully");
    }

    /** Like SimpleClaims addPermissionToggle: set Value/Disabled and bind ValueChanged with Action only. */
    private void addAllyToggle(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String element, String action, boolean value, boolean enabled) {
        commandBuilder.set(element + " #CheckBox.Value", value);
        commandBuilder.set(element + " #CheckBox.Disabled", !enabled);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, element + " #CheckBox", new EventData().append("Action", action), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionAllySettingsPageEventData data) {
        ConfigUtil.debug("[AllySettings] handleDataEvent() entered; action=" + (data != null ? data.action : "null"));
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ConfigUtil.debug("[AllySettings] handleDataEvent() player=null, return");
            return;
        }

        if ("back".equals(data.action) || "close".equals(data.action)) {
            ConfigUtil.debug("[AllySettings] handleDataEvent() back/close");
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = contextFactionId != null
            ? factionManager.getFactionById(contextFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);
        if (faction == null) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if (contextFactionId == null && !PermissionUtil.canManageAlliesInFaction(playerUuid, faction)) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.ally_settings.no_permission")).color(Color.RED));
            return;
        }

        AllySettings settings = factionManager.getAllySettings(faction.getId(), allyFactionId);
        if (settings == null) settings = new AllySettings();
        boolean updated = false;
        String action = data.action != null ? data.action : "";

        if ("shareMarkers".equals(action)) { settings = new AllySettings(!settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(), settings.isAllowOpenChest(), settings.isAllowOpenDoor(), settings.isAllowOpenBench(), settings.isAllowUseChair(), settings.isAllowUsePortal(), settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()); updated = true; }
        else if ("allowInteractions".equals(action)) { settings = new AllySettings(settings.isSharePlayerMarkers(), !settings.isAllowInteractionsInClaimedChunks(), settings.isAllowOpenChest(), settings.isAllowOpenDoor(), settings.isAllowOpenBench(), settings.isAllowUseChair(), settings.isAllowUsePortal(), settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()); updated = true; }
        else if ("openChest".equals(action)) { settings = new AllySettings(settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(), !settings.isAllowOpenChest(), settings.isAllowOpenDoor(), settings.isAllowOpenBench(), settings.isAllowUseChair(), settings.isAllowUsePortal(), settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()); updated = true; }
        else if ("openDoor".equals(action)) { settings = new AllySettings(settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(), settings.isAllowOpenChest(), !settings.isAllowOpenDoor(), settings.isAllowOpenBench(), settings.isAllowUseChair(), settings.isAllowUsePortal(), settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()); updated = true; }
        else if ("openBench".equals(action)) { settings = new AllySettings(settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(), settings.isAllowOpenChest(), settings.isAllowOpenDoor(), !settings.isAllowOpenBench(), settings.isAllowUseChair(), settings.isAllowUsePortal(), settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()); updated = true; }
        else if ("useChair".equals(action)) { settings = new AllySettings(settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(), settings.isAllowOpenChest(), settings.isAllowOpenDoor(), settings.isAllowOpenBench(), !settings.isAllowUseChair(), settings.isAllowUsePortal(), settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()); updated = true; }
        else if ("usePortal".equals(action)) { settings = new AllySettings(settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(), settings.isAllowOpenChest(), settings.isAllowOpenDoor(), settings.isAllowOpenBench(), settings.isAllowUseChair(), !settings.isAllowUsePortal(), settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()); updated = true; }
        else if ("placeBlock".equals(action)) { settings = new AllySettings(settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(), settings.isAllowOpenChest(), settings.isAllowOpenDoor(), settings.isAllowOpenBench(), settings.isAllowUseChair(), settings.isAllowUsePortal(), !settings.isAllowPlaceBlock(), settings.isAllowBreakBlock()); updated = true; }
        else if ("breakBlock".equals(action)) { settings = new AllySettings(settings.isSharePlayerMarkers(), settings.isAllowInteractionsInClaimedChunks(), settings.isAllowOpenChest(), settings.isAllowOpenDoor(), settings.isAllowOpenBench(), settings.isAllowUseChair(), settings.isAllowUsePortal(), settings.isAllowPlaceBlock(), !settings.isAllowBreakBlock()); updated = true; }

        if (updated) {
            factionManager.setAllySettings(faction.getId(), allyFactionId, settings);
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.ally_settings.saved")).color(Color.GREEN));
            refreshPage(ref, store);
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
