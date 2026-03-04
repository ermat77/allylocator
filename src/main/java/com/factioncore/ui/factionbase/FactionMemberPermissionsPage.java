package com.factioncore.ui.factionbase;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.ui.factionbase.events.FactionMemberPermissionsPageEventData;
import com.factioncore.util.I18n;
import com.factioncore.util.PermissionUtil;
import com.factioncore.util.PlayerUtils;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * UI page for managing member permissions in a faction.
 * Follows FactionAllySettingsPage pattern: CheckBoxWithLabel in UI, ValueChanged per checkbox (no Save-button value binding).
 */
public class FactionMemberPermissionsPage extends InteractiveCustomUIPage<FactionMemberPermissionsPageEventData> {

    private final UUID targetMemberUuid;
    /** When non-null (e.g. /f admin Flowers), we edit this faction's member permissions. When null, we use the player's faction. */
    private final UUID contextFactionId;

    public FactionMemberPermissionsPage(@Nonnull PlayerRef playerRef, @Nonnull UUID targetMemberUuid) {
        this(playerRef, targetMemberUuid, null);
    }

    public FactionMemberPermissionsPage(@Nonnull PlayerRef playerRef, @Nonnull UUID targetMemberUuid, UUID contextFactionId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, FactionMemberPermissionsPageEventData.CODEC);
        this.targetMemberUuid = targetMemberUuid;
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

        boolean canManage = faction != null && ((contextFactionId != null) || faction.isOwner(playerUuid) || PermissionUtil.canManageMemberPermissionsAdmin(playerUuid));
        if (faction == null || !canManage) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.member_permissions.owner_only"))
                    .color(Color.RED).bold(true));
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }

        if (targetMemberUuid == null || !faction.isMember(targetMemberUuid)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.member_permissions.invalid_member"))
                    .color(Color.RED).bold(true));
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }

        String memberName = PlayerUtils.getPlayerName(targetMemberUuid);
        Set<String> currentPermissions = com.factioncore.permission.FactionPermissionManager.getInstance().getMemberPermissions(targetMemberUuid, faction.getId());

        commandBuilder.append("Pages/FactionBase/FactionMemberPermissionsPage.ui");

        commandBuilder.set("#TitleLabel.Text", I18n.get("ui.member_permissions.title", memberName));
        commandBuilder.set("#instructionsLabel.Text", I18n.get("ui.member_permissions.instructions"));
        commandBuilder.set("#backButton.Text", I18n.get("ui.member_permissions.back"));

        // Same pattern as FactionAllySettingsPage: set #CheckBox.Value / #CheckBox.Disabled, bind ValueChanged with Action only (no @-refs)
        boolean canEdit = true;
        addPermissionToggle(commandBuilder, eventBuilder, "#Rename", "rename", PermissionUtil.FACTION_PERM_RENAME, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#Color", "color", PermissionUtil.FACTION_PERM_CHANGE_COLOR, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#Invite", "invite", PermissionUtil.FACTION_PERM_INVITE_MEMBERS, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#Allies", "allies", PermissionUtil.FACTION_PERM_MANAGE_ALLIES, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#Kick", "kick", PermissionUtil.FACTION_PERM_KICK_MEMBERS, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#Enemies", "enemies", PermissionUtil.FACTION_PERM_MANAGE_ENEMIES, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#OpenChest", "openChest", PermissionUtil.FACTION_PERM_OPEN_CHEST, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#OpenDoor", "openDoor", PermissionUtil.FACTION_PERM_OPEN_DOOR, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#OpenBench", "openBench", PermissionUtil.FACTION_PERM_OPEN_BENCH, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#UseChair", "useChair", PermissionUtil.FACTION_PERM_USE_CHAIR, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#UsePortal", "usePortal", PermissionUtil.FACTION_PERM_USE_PORTAL, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#PlaceBlock", "placeBlock", PermissionUtil.FACTION_PERM_PLACE_BLOCK, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#BreakBlock", "breakBlock", PermissionUtil.FACTION_PERM_BREAK_BLOCK, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#SetHome", "setHome", PermissionUtil.FACTION_PERM_SET_HOME, currentPermissions, canEdit);
        addPermissionToggle(commandBuilder, eventBuilder, "#Home", "home", PermissionUtil.FACTION_PERM_HOME, currentPermissions, canEdit);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#backButton", new EventData().append("Action", "back"));
    }

    /** Same as FactionAllySettingsPage addAllyToggle: set Value/Disabled and bind ValueChanged with Action only. */
    private void addPermissionToggle(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String element, String action, String permissionKey, Set<String> currentPermissions, boolean enabled) {
        boolean value = currentPermissions.contains(permissionKey);
        commandBuilder.set(element + " #CheckBox.Value", value);
        commandBuilder.set(element + " #CheckBox.Disabled", !enabled);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, element + " #CheckBox", new EventData().append("Action", action), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionMemberPermissionsPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        if ("back".equals(data.action) || "close".equals(data.action)) {
            FactionShowPage showPage = contextFactionId != null
                ? new FactionShowPage(playerRef, contextFactionId)
                : new FactionShowPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, showPage);
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = contextFactionId != null
            ? factionManager.getFactionById(contextFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);
        if (faction == null || !faction.isMember(targetMemberUuid)) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if (contextFactionId == null && !faction.isOwner(playerUuid) && !PermissionUtil.canManageMemberPermissionsAdmin(playerUuid)) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.member_permissions.owner_only")).color(Color.RED));
            return;
        }

        Set<String> permissions = com.factioncore.permission.FactionPermissionManager.getInstance().getMemberPermissions(targetMemberUuid, faction.getId());
        if (permissions == null) {
            permissions = new HashSet<>();
        }
        String action = data.action != null ? data.action : "";
        String toggledPerm = null;

        switch (action) {
            case "rename": toggledPerm = PermissionUtil.FACTION_PERM_RENAME; break;
            case "color": toggledPerm = PermissionUtil.FACTION_PERM_CHANGE_COLOR; break;
            case "invite": toggledPerm = PermissionUtil.FACTION_PERM_INVITE_MEMBERS; break;
            case "allies": toggledPerm = PermissionUtil.FACTION_PERM_MANAGE_ALLIES; break;
            case "kick": toggledPerm = PermissionUtil.FACTION_PERM_KICK_MEMBERS; break;
            case "enemies": toggledPerm = PermissionUtil.FACTION_PERM_MANAGE_ENEMIES; break;
            case "openChest": toggledPerm = PermissionUtil.FACTION_PERM_OPEN_CHEST; break;
            case "openDoor": toggledPerm = PermissionUtil.FACTION_PERM_OPEN_DOOR; break;
            case "openBench": toggledPerm = PermissionUtil.FACTION_PERM_OPEN_BENCH; break;
            case "useChair": toggledPerm = PermissionUtil.FACTION_PERM_USE_CHAIR; break;
            case "usePortal": toggledPerm = PermissionUtil.FACTION_PERM_USE_PORTAL; break;
            case "placeBlock": toggledPerm = PermissionUtil.FACTION_PERM_PLACE_BLOCK; break;
            case "breakBlock": toggledPerm = PermissionUtil.FACTION_PERM_BREAK_BLOCK; break;
            case "setHome": toggledPerm = PermissionUtil.FACTION_PERM_SET_HOME; break;
            case "home": toggledPerm = PermissionUtil.FACTION_PERM_HOME; break;
            default: break;
        }

        if (toggledPerm != null) {
            Set<String> next = new HashSet<>(permissions);
            if (next.contains(toggledPerm)) {
                next.remove(toggledPerm);
            } else {
                next.add(toggledPerm);
            }
            factionManager.setMemberPermissions(faction.getId(), targetMemberUuid, next);
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.member_permissions.updated", PlayerUtils.getPlayerName(targetMemberUuid))).color(Color.GREEN));
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
