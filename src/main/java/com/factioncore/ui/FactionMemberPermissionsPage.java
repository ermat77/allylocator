package com.factioncore.ui;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.ui.events.FactionMemberPermissionsPageEventData;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * UI page for managing member permissions in a faction.
 * Only the faction owner can access this page.
 * Uses native Hytale UI system with .ui files.
 */
public class FactionMemberPermissionsPage extends InteractiveCustomUIPage<FactionMemberPermissionsPageEventData> {
    
    private final UUID targetMemberUuid;
    
    public FactionMemberPermissionsPage(@Nonnull PlayerRef playerRef, @Nonnull UUID targetMemberUuid) {
        super(playerRef, CustomPageLifetime.CanDismiss, FactionMemberPermissionsPageEventData.CODEC);
        this.targetMemberUuid = targetMemberUuid;
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
        FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
        
        // Validate owner or admin (can manage member permissions for any faction)
        boolean canManage = faction != null && (faction.isOwner(playerUuid) || PermissionUtil.canManageMemberPermissionsAdmin(playerUuid));
        if (faction == null || !canManage) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.member_permissions.owner_only"))
                    .color(Color.RED).bold(true));
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }
        
        // Validate target member
        if (targetMemberUuid == null || !faction.isMember(targetMemberUuid)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.member_permissions.invalid_member"))
                    .color(Color.RED).bold(true));
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }
        
        // Get member name and current permissions
        String memberName = getPlayerName(targetMemberUuid);
        Set<String> currentPermissions = com.factioncore.permission.FactionPermissionManager.getInstance().getMemberPermissions(targetMemberUuid, faction.getId());
        
        commandBuilder.append("Pages/FactionMemberPermissionsPage");

        // Set static UI text from I18n
        commandBuilder.set("#instructionsLabel.Text", I18n.get("ui.member_permissions.instructions"));
        commandBuilder.set("#renameGroup #renameLabel.Text", I18n.get("ui.member_permissions.rename"));
        commandBuilder.set("#colorGroup #colorLabel.Text", I18n.get("ui.member_permissions.color"));
        commandBuilder.set("#inviteGroup #inviteLabel.Text", I18n.get("ui.member_permissions.invite"));
        commandBuilder.set("#alliesGroup #alliesLabel.Text", I18n.get("ui.member_permissions.allies"));
        commandBuilder.set("#kickGroup #kickLabel.Text", I18n.get("ui.member_permissions.kick"));
        commandBuilder.set("#enemiesGroup #enemiesLabel.Text", I18n.get("ui.member_permissions.enemies"));
        commandBuilder.set("#openChestGroup #openChestLabel.Text", I18n.get("ui.member_permissions.open_chest"));
        commandBuilder.set("#openDoorGroup #openDoorLabel.Text", I18n.get("ui.member_permissions.open_door"));
        commandBuilder.set("#openBenchGroup #openBenchLabel.Text", I18n.get("ui.member_permissions.open_bench"));
        commandBuilder.set("#useChairGroup #useChairLabel.Text", I18n.get("ui.member_permissions.use_chair"));
        commandBuilder.set("#usePortalGroup #usePortalLabel.Text", I18n.get("ui.member_permissions.use_portal"));
        commandBuilder.set("#placeBlockGroup #placeBlockLabel.Text", I18n.get("ui.member_permissions.place_block"));
        commandBuilder.set("#breakBlockGroup #breakBlockLabel.Text", I18n.get("ui.member_permissions.break_block"));
        commandBuilder.set("#saveButton.Text", I18n.get("ui.member_permissions.save"));
        commandBuilder.set("#cancelButton.Text", I18n.get("ui.member_permissions.cancel"));
        
        // Update title with member name
        commandBuilder.set("#Title #TitleText.Text", I18n.get("ui.member_permissions.title", memberName));
        
        // Set checkbox states from current permissions
        commandBuilder.set("#RenameCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_RENAME));
        commandBuilder.set("#ColorCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_CHANGE_COLOR));
        commandBuilder.set("#InviteCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_INVITE_MEMBERS));
        commandBuilder.set("#AlliesCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_MANAGE_ALLIES));
        commandBuilder.set("#KickCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_KICK_MEMBERS));
        commandBuilder.set("#EnemiesCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_MANAGE_ENEMIES));
        commandBuilder.set("#OpenChestCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_OPEN_CHEST));
        commandBuilder.set("#OpenDoorCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_OPEN_DOOR));
        commandBuilder.set("#OpenBenchCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_OPEN_BENCH));
        commandBuilder.set("#UseChairCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_USE_CHAIR));
        commandBuilder.set("#UsePortalCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_USE_PORTAL));
        commandBuilder.set("#PlaceBlockCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_PLACE_BLOCK));
        commandBuilder.set("#BreakBlockCheckbox.Value", currentPermissions.contains(PermissionUtil.FACTION_PERM_BREAK_BLOCK));
        
        // Bind save button with all checkbox values and target member UUID
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#saveButton",
            new EventData()
                .append("Action", "save")
                .append("TargetMemberUuid", targetMemberUuid.toString())
                .append("@RenamePermission", "#RenameCheckbox.Value")
                .append("@ColorPermission", "#ColorCheckbox.Value")
                .append("@InvitePermission", "#InviteCheckbox.Value")
                .append("@AlliesPermission", "#AlliesCheckbox.Value")
                .append("@KickPermission", "#KickCheckbox.Value")
                .append("@EnemiesPermission", "#EnemiesCheckbox.Value")
                .append("@OpenChestPermission", "#OpenChestCheckbox.Value")
                .append("@OpenDoorPermission", "#OpenDoorCheckbox.Value")
                .append("@OpenBenchPermission", "#OpenBenchCheckbox.Value")
                .append("@UseChairPermission", "#UseChairCheckbox.Value")
                .append("@UsePortalPermission", "#UsePortalCheckbox.Value")
                .append("@PlaceBlockPermission", "#PlaceBlockCheckbox.Value")
                .append("@BreakBlockPermission", "#BreakBlockCheckbox.Value")
        );
        
        // Bind cancel button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#cancelButton",
            new EventData()
                .append("Action", "cancel")
        );
    }
    
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionMemberPermissionsPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        
        if ("cancel".equals(data.action)) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        
        if (!"save".equals(data.action)) {
            return;
        }
        
        UUID playerUuid = playerRef.getUuid();
        
        // Re-validate owner or admin
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo currentFaction = factionManager.getFactionFromPlayer(playerUuid);
        boolean canManage = currentFaction != null && (currentFaction.isOwner(playerUuid) || PermissionUtil.canManageMemberPermissionsAdmin(playerUuid));
        if (currentFaction == null || !canManage) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.member_permissions.owner_only"))
                .color(Color.RED).bold(true));
            return;
        }
        
        // Get target member UUID from event data (fallback to field if not set)
        UUID targetUuid = data.targetMemberUuid != null ? data.targetMemberUuid : targetMemberUuid;
        if (targetUuid == null || !currentFaction.isMember(targetUuid)) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Invalid member")
                .color(Color.RED).bold(true));
            return;
        }
        
        // Build permission set from checkbox values
        Set<String> selectedPermissions = new HashSet<>();
        if (Boolean.TRUE.equals(data.renamePermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_RENAME);
        }
        if (Boolean.TRUE.equals(data.colorPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_CHANGE_COLOR);
        }
        if (Boolean.TRUE.equals(data.invitePermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_INVITE_MEMBERS);
        }
        if (Boolean.TRUE.equals(data.alliesPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_MANAGE_ALLIES);
        }
        if (Boolean.TRUE.equals(data.kickPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_KICK_MEMBERS);
        }
        if (Boolean.TRUE.equals(data.enemiesPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_MANAGE_ENEMIES);
        }
        if (Boolean.TRUE.equals(data.openChestPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_OPEN_CHEST);
        }
        if (Boolean.TRUE.equals(data.openDoorPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_OPEN_DOOR);
        }
        if (Boolean.TRUE.equals(data.openBenchPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_OPEN_BENCH);
        }
        if (Boolean.TRUE.equals(data.useChairPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_USE_CHAIR);
        }
        if (Boolean.TRUE.equals(data.usePortalPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_USE_PORTAL);
        }
        if (Boolean.TRUE.equals(data.placeBlockPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_PLACE_BLOCK);
        }
        if (Boolean.TRUE.equals(data.breakBlockPermission)) {
            selectedPermissions.add(PermissionUtil.FACTION_PERM_BREAK_BLOCK);
        }
        
        // Update permissions via FactionPermissionManager (non-blocking, updates cache immediately)
        com.factioncore.permission.FactionPermissionManager.getInstance().setMemberPermissions(targetUuid, selectedPermissions, currentFaction.getId());
        
        // Also update FactionInfo for database serialization (backward compatibility)
        currentFaction.setMemberPermissions(targetUuid, selectedPermissions);
        
        // Save faction to database (queued, async, non-blocking)
        factionManager.saveFaction(currentFaction);
        
        String memberName = getPlayerName(targetUuid);
        player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.member_permissions.updated", memberName))
            .color(Color.GREEN).bold(true));
        
        ConfigUtil.debug("Permissions saved for member: " + targetUuid);
        
        // Close the page
        player.getPageManager().setPage(ref, store, Page.None);
    }
    
    /**
     * Gets player name from UUID using Universe.
     */
    private String getPlayerName(UUID uuid) {
        if (uuid == null) {
            return "Unknown";
        }
        
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                PlayerRef playerRef = universe.getPlayer(uuid);
                if (playerRef != null) {
                    return playerRef.getUsername();
                }
            }
        } catch (Exception e) {
            ConfigUtil.debug("Failed to get player name for UUID " + uuid + ": " + e.getMessage());
        }
        
        return uuid.toString();
    }
}
