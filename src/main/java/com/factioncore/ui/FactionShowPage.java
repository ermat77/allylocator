package com.factioncore.ui;

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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.invite.PendingInvite;
import com.factioncore.ui.events.FactionShowPageEventData;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.I18n;
import com.factioncore.util.PlayerUtils;
import com.factioncore.util.PermissionUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * FactionShowPage - A page that displays faction information.
 *
 * This page demonstrates:
 *   1. Dynamic UI state based on player faction status
 *   2. Reading input values from TextFields (faction name)
 *   3. Using the @-prefix to bind input values to EventData
 *   4. Handling multiple button actions (settings, create, close, permissions, cancelInvite)
 *
 * KEY CONCEPT: The @-prefix
 *   - In the Codec: "@FactionName" means "this value comes from an input"
 *   - In EventData.append(): "@FactionName", "#FactionNameInput.Value" binds the input value
 *   - When the event fires, the current input value is automatically included
 */
public class FactionShowPage extends InteractiveCustomUIPage<FactionShowPageEventData> {

    /** When non-null, show this faction as admin (full owner UI and actions). */
    private final UUID adminViewFactionId;

    public FactionShowPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    /**
     * Opens the faction show UI for a specific faction as superadmin (full owner capabilities).
     * When adminViewFactionId is non-null, that faction is shown and the viewer is treated as owner.
     *
     * @param playerRef         The player viewing the page
     * @param adminViewFactionId The faction to manage, or null for normal (player's own faction) view
     */
    public FactionShowPage(@Nonnull PlayerRef playerRef, UUID adminViewFactionId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, FactionShowPageEventData.CODEC);
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
        
        // Load the faction UI file first (always needed)
        commandBuilder.append("Pages/FactionShowPage.ui");
        
        // Set static UI text from I18n (so title, headers, and buttons are translated)
        commandBuilder.set("#factionTitleLabel.Text", I18n.get("ui.faction_show.title"));
        commandBuilder.set("#membersHeaderLabel.Text", I18n.get("ui.faction_show.members"));
        commandBuilder.set("#noMembersLabel.Text", I18n.get("ui.faction_show.no_members"));
        commandBuilder.set("#pendingInvitesHeaderLabel.Text", I18n.get("ui.faction_show.pending_invites"));
        commandBuilder.set("#noPendingInvitesLabel.Text", I18n.get("ui.faction_show.no_pending_invites"));
        commandBuilder.set("#diplomacyButton.Text", I18n.get("ui.faction_show.button_diplomacy"));
        commandBuilder.set("#claimButton.Text", I18n.get("ui.faction_show.button_claim"));
        commandBuilder.set("#createFactionButton.Text", I18n.get("ui.faction_show.button_create_faction"));
        commandBuilder.set("#createFactionButtonInSection.Text", I18n.get("ui.faction_show.button_create_faction"));
        commandBuilder.set("#settingsButton.Text", I18n.get("ui.faction_show.button_settings"));
        commandBuilder.set("#closeButton.Text", I18n.get("ui.faction_show.button_close"));
        commandBuilder.set("#noPermissionSection #noPermissionLabel.Text", I18n.get("ui.faction_show.no_permission_create_join"));
        
        // Check permissions first
        boolean canShow = PermissionUtil.canShowFaction(playerUuid);
        boolean canCreate = PermissionUtil.canCreateFaction(playerUuid);
        
        // If no show permission, show no permission state and return early
        if (!canShow) {
            buildNoPermissionState(commandBuilder, eventBuilder);
            return;
        }
        
        // Check faction state (admin mode: show target faction; normal: player's faction)
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = adminViewFactionId != null
            ? factionManager.getFactionById(adminViewFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);
        List<PendingInvite> pendingInvites = faction != null && adminViewFactionId == null
            ? factionManager.getPendingInvitesForPlayer(playerUuid)
            : (faction != null ? factionManager.getPendingInvitesForFaction(faction.getId()) : List.of());

        // Admin mode with invalid faction
        if (adminViewFactionId != null && faction == null) {
            buildNoPermissionState(commandBuilder, eventBuilder);
            commandBuilder.set("#noPermissionSection #noPermissionLabel.Text", I18n.get("ui.faction_show.not_found"));
            return;
        }

        // Decision tree: faction → invites → create → no permission
        if (faction != null) {
            // Player has a faction - show faction info
            buildFactionInfoState(commandBuilder, eventBuilder, ref, store, faction, playerUuid);
        } else if (!pendingInvites.isEmpty()) {
            // Player has pending invites - show invites page
            buildPendingInvitesState(commandBuilder, eventBuilder, ref, store, pendingInvites, playerUuid);
        } else if (canCreate) {
            // Player can create faction - show create form
            buildCreateFactionState(commandBuilder, eventBuilder, ref, store, playerUuid);
        } else {
            // Player doesn't have create permission - show no permission message
            buildNoPermissionState(commandBuilder, eventBuilder);
        }
    }
    
    private void buildFactionInfoState(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder,
                                      Ref<EntityStore> ref, Store<EntityStore> store,
                                      FactionInfo faction, UUID playerUuid) {
        ConfigUtil.debug("FactionShowPage.buildFactionInfoState() called for faction: " + faction.getName() + " (ID: " + faction.getId() + ")");
        FactionManager factionManager = FactionManager.getInstance();
        
        // Show faction info section, hide others
        // IMPORTANT: Set visibility FIRST before trying to access child elements
        ConfigUtil.debug("FactionShowPage: Setting visibility - showing #factionInfoSection, hiding others");
        commandBuilder.set("#factionInfoSection.Visible", true);
        commandBuilder.set("#pendingInvitesPageSection.Visible", false);
        commandBuilder.set("#createFactionSection.Visible", false);
        commandBuilder.set("#noPermissionSection.Visible", false);
        
        // Set faction name and member count
        ConfigUtil.debug("FactionShowPage: Setting faction name: " + faction.getName() + ", member count: " + faction.getMemberCount());
        commandBuilder.set("#factionNameLabel.Text", faction.getName());
        commandBuilder.set("#memberCountLabel.Text", I18n.get("ui.faction_show.member_count", faction.getMemberCount()));
        
        // Get universe for player name lookup (using safe helper)
        Universe universe = getUniverseSafely();
        
        // Build member list
        List<MemberInfo> members = buildMemberList(faction, universe);
        ConfigUtil.debug("FactionShowPage: Building member list, found " + members.size() + " members");
        
        // Clear the member list container and rebuild dynamically
        commandBuilder.clear("#MemberList");
        
        if (members.isEmpty()) {
            // Show "no members" label when list is empty
            ConfigUtil.debug("FactionShowPage: No members found, showing #noMembersLabel");
            commandBuilder.set("#noMembersLabel.Visible", true);
        } else {
            // Hide "no members" label and show members
            ConfigUtil.debug("FactionShowPage: Hiding #noMembersLabel, showing " + members.size() + " members");
            commandBuilder.set("#noMembersLabel.Visible", false);
            
            // Check if player is owner (or admin viewing this faction)
            boolean isOwner = (adminViewFactionId != null) || faction.isOwner(playerUuid);
            ConfigUtil.debug("FactionShowPage: Player is owner (or admin): " + isOwner);
            
            // Dynamically append member rows using the PluginListPage pattern
            for (int i = 0; i < members.size(); i++) {
                MemberInfo member = members.get(i);
                String rolePrefix = getRolePrefix(member.role);
                String memberText = rolePrefix + member.name;
                
                // Append the member row UI file to the list
                commandBuilder.append("#MemberList", "Pages/MemberRow.ui");
                
                // Use index-based selector to set properties (like PluginListPage)
                String selector = "#MemberList[" + i + "]";
                String labelSelector = selector + " #MemberLabel";
                String buttonSelector = selector + " #PermissionsButton";
                
                // Set member label text and row button from I18n
                commandBuilder.set(labelSelector + ".Text", memberText);
                commandBuilder.set(buttonSelector + ".Text", I18n.get("ui.row.permissions"));
                
                // Show/hide permissions button based on ownership
                if (isOwner && !member.uuid.equals(playerUuid)) {
                    commandBuilder.set(buttonSelector + ".Visible", true);
                    
                    // Bind permissions button event
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        buttonSelector,
                        new EventData()
                            .append("Action", "permissions")
                            .append("MemberUuid", member.uuid.toString())
                    );
                } else {
                    commandBuilder.set(buttonSelector + ".Visible", false);
                }
            }
        }
        
        // Pending Invites section (owner or admin only)
        boolean isOwner = (adminViewFactionId != null) || faction.isOwner(playerUuid);
        ConfigUtil.debug("FactionShowPage: Processing pending invites section, isOwner: " + isOwner);
        if (isOwner) {
            commandBuilder.set("#pendingInvitesSection.Visible", true);
            
            // Get pending invites for this faction
            List<PendingInvite> pendingInvites = factionManager.getPendingInvitesForFaction(faction.getId());
            ConfigUtil.debug("FactionShowPage: Found " + pendingInvites.size() + " pending invites");
            
            // Clear existing pending invites list
            ConfigUtil.debug("FactionShowPage: Clearing #PendingInvitesList");
            commandBuilder.clear("#PendingInvitesList");
            
            // Set label visibility after clearing
            if (pendingInvites.isEmpty()) {
                ConfigUtil.debug("FactionShowPage: No pending invites, showing #noPendingInvitesLabel");
                commandBuilder.set("#noPendingInvitesLabel.Visible", true);
            } else {
                ConfigUtil.debug("FactionShowPage: Hiding #noPendingInvitesLabel, adding " + pendingInvites.size() + " invites");
                commandBuilder.set("#noPendingInvitesLabel.Visible", false);
            }
            
            if (!pendingInvites.isEmpty()) {
                // Add each pending invite with cancel button using reusable component
                for (int i = 0; i < pendingInvites.size(); i++) {
                    PendingInvite invite = pendingInvites.get(i);
                    UUID invitedPlayerUuid = invite.getPlayerUuid();
                    String playerName = PlayerUtils.getPlayerName(universe, invitedPlayerUuid);
                    ConfigUtil.debug("FactionShowPage: Processing pending invite for: " + playerName + " (UUID: " + invitedPlayerUuid + ")");
                    
                    // Append the invite row component
                    commandBuilder.append("#PendingInvitesList", "Pages/InviteRow.ui");
                    
                    // Use index-based selector to set properties
                    String selector = "#PendingInvitesList[" + i + "]";
                    String labelSelector = selector + " #InviteLabel";
                    String buttonSelector = selector + " #CancelButton";
                    
                    // Set invite label text and row button from I18n
                    commandBuilder.set(labelSelector + ".Text", "📨 " + playerName);
                    commandBuilder.set(buttonSelector + ".Text", I18n.get("ui.row.cancel"));
                    
                    // Bind cancel invite button event
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        buttonSelector,
                        new EventData()
                            .append("Action", "cancelInvite")
                            .append("InvitedPlayerUuid", invitedPlayerUuid.toString())
                    );
                }
            }
        } else {
            ConfigUtil.debug("FactionShowPage: Player is not owner, hiding pending invites section");
            commandBuilder.set("#pendingInvitesSection.Visible", false);
        }
        
        // Show faction action buttons, hide create faction button
        commandBuilder.set("#diplomacyButton.Visible", true);
        commandBuilder.set("#claimButton.Visible", true);
        commandBuilder.set("#settingsButton.Visible", true);
        commandBuilder.set("#createFactionButton.Visible", false);
        commandBuilder.set("#createFactionButtonInSection.Visible", false); // Hide button in section (section is hidden)
        
        // Bind diplomacy button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#diplomacyButton",
            new EventData()
                .append("Action", "diplomacy")
        );
        
        // Bind claim button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#claimButton",
            new EventData()
                .append("Action", "claim")
        );
        
        // Bind settings button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#settingsButton",
            new EventData()
                .append("Action", "settings")
        );
        
        // Bind close button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#closeButton",
            new EventData()
                .append("Action", "close")
        );
    }
    
    private void buildPendingInvitesState(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder,
                                         Ref<EntityStore> ref, Store<EntityStore> store,
                                         List<PendingInvite> pendingInvites, UUID playerUuid) {
        // Show pending invites section, hide others
        commandBuilder.set("#factionInfoSection.Visible", false);
        commandBuilder.set("#pendingInvitesPageSection.Visible", true);
        commandBuilder.set("#createFactionSection.Visible", false);
        commandBuilder.set("#noPermissionSection.Visible", false);
        
        // Hide all action buttons
        commandBuilder.set("#diplomacyButton.Visible", false);
        commandBuilder.set("#claimButton.Visible", false);
        commandBuilder.set("#settingsButton.Visible", false);
        commandBuilder.set("#createFactionButton.Visible", false); // Always hide bottom button
        commandBuilder.set("#createFactionButtonInSection.Visible", false); // Hide button in section (section is hidden)
        
        // Bind close button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#closeButton",
            new EventData()
                .append("Action", "close")
        );
        
        // For pending invites, redirect to dedicated page
        // This is simpler than trying to build dynamic invite cards here
        // The UI will show a message, but we'll handle navigation in handleDataEvent
    }
    
    private void buildCreateFactionState(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder,
                                        Ref<EntityStore> ref, Store<EntityStore> store,
                                        UUID playerUuid) {
        // Hide other sections first
        commandBuilder.set("#factionInfoSection.Visible", false);
        commandBuilder.set("#pendingInvitesPageSection.Visible", false);
        commandBuilder.set("#noPermissionSection.Visible", false);
        
        // Show create faction section and set its labels/placeholder/button from I18n
        commandBuilder.set("#createFactionSection.Visible", true);
        commandBuilder.set("#createFactionSection #instructionsLabel.Text", I18n.get("ui.create.instructions"));
        commandBuilder.set("#createFactionSection #FactionNameInput.PlaceholderText", I18n.get("ui.create.placeholder"));
        commandBuilder.set("#createFactionButtonInSection.Text", I18n.get("ui.create.create_button"));
        
        // Hide all action buttons
        commandBuilder.set("#diplomacyButton.Visible", false);
        commandBuilder.set("#claimButton.Visible", false);
        commandBuilder.set("#settingsButton.Visible", false);
        commandBuilder.set("#createFactionButton.Visible", false);
        
        // Show create button in section (next to input field)
        commandBuilder.set("#createFactionButtonInSection.Visible", true);
        ConfigUtil.debug("FactionShowPage: Showing create faction section and button");
        
        // Bind create button in section with ALL input values
        // The EventData captures:
        //   - "Action" = "createFaction" (explicit value)
        //   - "@FactionName" = current value of #FactionNameInput TextField
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#createFactionButtonInSection",
            new EventData()
                .append("Action", "createFaction")
                .append("@FactionName", "#FactionNameInput.Value")  // Read TextField value
        );
        
        // Bind close button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#closeButton",
            new EventData()
                .append("Action", "close")
        );
    }
    
    private void buildNoPermissionState(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        // Hide all other sections
        commandBuilder.set("#factionInfoSection.Visible", false);
        commandBuilder.set("#pendingInvitesPageSection.Visible", false);
        commandBuilder.set("#createFactionSection.Visible", false);
        
        // Show no permission section
        commandBuilder.set("#noPermissionSection.Visible", true);
        
        // Hide all action buttons
        commandBuilder.set("#diplomacyButton.Visible", false);
        commandBuilder.set("#claimButton.Visible", false);
        commandBuilder.set("#settingsButton.Visible", false);
        commandBuilder.set("#createFactionButton.Visible", false);
        commandBuilder.set("#createFactionButtonInSection.Visible", false);
        
        // Bind close button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#closeButton",
            new EventData()
                .append("Action", "close")
        );
    }
    
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull FactionShowPageEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        
        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        // In admin mode use the viewed faction; otherwise use player's faction
        FactionInfo currentFaction = adminViewFactionId != null
            ? factionManager.getFactionById(adminViewFactionId)
            : factionManager.getFactionFromPlayer(playerUuid);
        
        if ("close".equals(data.action)) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        
        if ("diplomacy".equals(data.action)) {
            if (currentFaction == null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.must_be_in_faction_diplomacy"))
                    .color(Color.RED).bold(true));
                return;
            }
            FactionDiplomacyPage diplomacyPage = adminViewFactionId != null
                ? new FactionDiplomacyPage(playerRef, adminViewFactionId)
                : new FactionDiplomacyPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, diplomacyPage);
            return;
        }
        
        if ("claim".equals(data.action)) {
            if (currentFaction == null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.must_be_in_faction_claim"))
                    .color(Color.RED).bold(true));
                return;
            }
            
            // handleDataEvent runs on World thread - can access EntityStore directly
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            
            // Get player's current chunk position
            int blockX = (int) playerRef.getTransform().getPosition().getX();
            int blockZ = (int) playerRef.getTransform().getPosition().getZ();
            int chunkX = com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate(blockX);
            int chunkZ = com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate(blockZ);
            
            World world = player.getWorld();
            if (world == null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.error_world"))
                    .color(Color.RED));
                return;
            }
            
            String dimension = world.getName();
            
            // Validate dimension allows claiming
            if (!factionManager.canClaimInDimension(world)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You cannot claim chunks in this dimension")
                    .color(Color.RED));
                return;
            }
            
            // Open chunk visualizer page (pass admin faction when in admin mode)
            ChunkVisualizerPage visualizerPage = adminViewFactionId != null
                ? new ChunkVisualizerPage(playerRef, dimension, chunkX, chunkZ, adminViewFactionId)
                : new ChunkVisualizerPage(playerRef, dimension, chunkX, chunkZ);
            player.getPageManager().openCustomPage(ref, store, visualizerPage);
            return;
        }
        
        if ("settings".equals(data.action)) {
            if (currentFaction == null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.must_be_in_faction_settings"))
                    .color(Color.RED).bold(true));
                return;
            }
            
            FactionSettingsPage settingsPage = adminViewFactionId != null
                ? new FactionSettingsPage(playerRef, adminViewFactionId)
                : new FactionSettingsPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, settingsPage);
            return;
        }
        
        if ("permissions".equals(data.action) && data.memberUuid != null) {
            boolean canManagePerms = currentFaction != null && ((adminViewFactionId != null) || currentFaction.isOwner(playerUuid));
            if (!canManagePerms) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.owner_permissions"))
                    .color(Color.RED).bold(true));
                return;
            }
            
            FactionMemberPermissionsPage permissionsPage = new FactionMemberPermissionsPage(playerRef, data.memberUuid, adminViewFactionId);
            player.getPageManager().openCustomPage(ref, store, permissionsPage);
            return;
        }
        
        if ("cancelInvite".equals(data.action) && data.invitedPlayerUuid != null) {
            boolean canCancelInvite = currentFaction != null && ((adminViewFactionId != null) || currentFaction.isOwner(playerUuid));
            if (!canCancelInvite) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You must be the faction owner to cancel invites")
                    .color(Color.RED).bold(true));
                return;
            }
            
            Universe universe = getUniverseSafely();
            String playerName = PlayerUtils.getPlayerName(universe, data.invitedPlayerUuid);
            
            // Cancel the invite
            boolean cancelled = factionManager.cancelPendingInvite(
                data.invitedPlayerUuid,
                currentFaction.getId()
            );
            
            if (cancelled) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.invite_cancelled", playerName))
                    .color(Color.GREEN).bold(true));
                
                // Refresh the page to show updated list
                refreshPage(ref, store);
            } else {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.failed_cancel_invite"))
                    .color(Color.RED).bold(true));
                // Refresh to clear loading state
                refreshPage(ref, store);
            }
            return;
        }
        
        if ("createFaction".equals(data.action)) {
            // Handle faction creation directly when triggered from inline form
            if (data.factionName != null && !data.factionName.trim().isEmpty()) {
                // Inline creation - handle directly
                handleInlineFactionCreation(ref, store, player, playerUuid, data.factionName);
                return;
            }
            
            // If no factionName provided, check for pending invites or navigate to create page
            List<PendingInvite> pendingInvites = factionManager.getPendingInvitesForPlayer(playerUuid);
            if (!pendingInvites.isEmpty()) {
                FactionPendingInvitesPage invitesPage = new FactionPendingInvitesPage(playerRef);
                player.getPageManager().openCustomPage(ref, store, invitesPage);
                return;
            }
            
            // Otherwise, open create faction page
            FactionCreatePage createPage = new FactionCreatePage(playerRef);
            player.getPageManager().openCustomPage(ref, store, createPage);
            return;
        }
        
        // Close the page for any unhandled actions
        player.getPageManager().setPage(ref, store, Page.None);
    }
    
    /**
     * Helper class to hold member information.
     */
    private static class MemberInfo {
        UUID uuid;
        String name;
        String role;
        
        MemberInfo(UUID uuid, String name, String role) {
            this.uuid = uuid;
            this.name = name;
            this.role = role;
        }
    }
    
    /**
     * Builds a list of members with their roles.
     */
    private static List<MemberInfo> buildMemberList(FactionInfo faction, Universe universe) {
        List<MemberInfo> members = new ArrayList<>();
        
        // Add owner first
        if (faction.getOwner() != null) {
            String ownerName = PlayerUtils.getPlayerName(universe, faction.getOwner());
            members.add(new MemberInfo(faction.getOwner(), ownerName, "owner"));
        }
        
        // Add generals
        for (UUID generalUuid : faction.getGenerals()) {
            String generalName = PlayerUtils.getPlayerName(universe, generalUuid);
            members.add(new MemberInfo(generalUuid, generalName, "general"));
        }
        
        // Add regular members (excluding generals)
        for (UUID memberUuid : faction.getMembers()) {
            if (!faction.isGeneral(memberUuid)) {
                String memberName = PlayerUtils.getPlayerName(universe, memberUuid);
                members.add(new MemberInfo(memberUuid, memberName, "member"));
            }
        }
        
        return members;
    }
    
    /**
     * Gets a prefix string for a role.
     * TEMPORARILY REMOVED EMOJIS FOR TESTING
     */
    private static String getRolePrefix(String role) {
        switch (role) {
            case "owner":
                return I18n.get("ui.faction_show.role_owner");
            case "general":
                return I18n.get("ui.faction_show.role_general");
            case "member":
                return I18n.get("ui.faction_show.role_member");
            default:
                return "";
        }
    }
    
    /**
     * Refreshes the page to update UI with latest faction data and clear loading state.
     * sendUpdate() already handles world thread execution internally, so we don't need to wrap it.
     */
    private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!ref.isValid()) {
            return;
        }
        
        // Rebuild the page with updated data
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        build(ref, commandBuilder, eventBuilder, store);
        
        // Send update to refresh the UI (sendUpdate handles world thread execution internally)
        // Use clear=true to properly reset event bindings
        sendUpdate(commandBuilder, eventBuilder, true);
    }
    
    /**
     * Safely gets the Universe instance, checking if it's initialized and ready.
     * Returns null if Universe is not available or not ready.
     */
    private static Universe getUniverseSafely() {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                return null;
            }
            
            // Check if universe is ready
            try {
                CompletableFuture<Void> readyFuture = universe.getUniverseReady();
                if (readyFuture == null || !readyFuture.isDone()) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
            
            return universe;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Handles inline faction creation from the create form.
     */
    private void handleInlineFactionCreation(Ref<EntityStore> ref, Store<EntityStore> store,
                                             Player player, UUID playerUuid, String factionName) {
        FactionManager factionManager = FactionManager.getInstance();
        
        // Validate input
        if (factionName == null || factionName.trim().isEmpty()) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.name_empty"))
                .color(Color.RED).bold(true));
            refreshPage(ref, store);
            return;
        }
        
        String trimmedName = factionName.trim();
        int maxLength = FactionManager.getMaxFactionNameLength();
        if (trimmedName.length() > maxLength) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.name_too_long", maxLength))
                .color(Color.RED).bold(true));
            refreshPage(ref, store);
            return;
        }
        
        // Check permission
        if (!PermissionUtil.canCreateFaction(playerUuid)) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.no_permission_create"))
                .color(Color.RED).bold(true));
            refreshPage(ref, store);
            return;
        }
        
        // Check if player is already in a faction
        FactionInfo existingFaction = factionManager.getFactionFromPlayer(playerUuid);
        if (existingFaction != null) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.already_in_faction", existingFaction.getName()))
                .color(Color.RED).bold(true));
            refreshPage(ref, store);
            return;
        }
        
        // Create the faction
        FactionInfo faction = factionManager.createFaction(trimmedName, playerUuid);
        
        if (faction == null) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Failed to create faction. The name might already be taken.")
                .color(Color.RED).bold(true));
            refreshPage(ref, store);
        } else {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(I18n.get("chat.faction.created_success", faction.getName()))
                .color(Color.GREEN).bold(true));
            
            // Refresh the page to show the new faction state
            refreshPage(ref, store);
        }
    }
}
