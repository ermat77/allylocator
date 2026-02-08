package com.factioncore.ui;

import com.factioncore.Main;
import com.factioncore.claim.ChunkInfo;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.I18n;
import com.factioncore.util.MessageHelper;
import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * ChunkVisualizerPage - A page that displays a grid of chunks around the player's position.
 * Allows players to claim/unclaim chunks by clicking on squares in the grid.
 * Displays a simple grey grid with faction color outlines for claimed chunks.
 */
public class ChunkVisualizerPage extends InteractiveCustomUIPage<ChunkVisualizerPage.ChunkVisualizerData> {

    private final String dimension;
    private final int centerChunkX;
    private final int centerChunkZ;
    /** When non-null, claim/unclaim for this faction (admin context). When null, use player's faction. */
    private final UUID claimForFactionId;

    public ChunkVisualizerPage(@Nonnull PlayerRef playerRef, String dimension, int centerChunkX, int centerChunkZ) {
        this(playerRef, dimension, centerChunkX, centerChunkZ, null);
    }

    /**
     * Opens chunk visualizer for a specific faction (e.g. when opened from /f admin).
     *
     * @param claimForFactionId The faction to claim/unclaim for, or null for player's own faction
     */
    public ChunkVisualizerPage(@Nonnull PlayerRef playerRef, String dimension, int centerChunkX, int centerChunkZ, UUID claimForFactionId) {
        super(playerRef, CustomPageLifetime.CanDismiss, ChunkVisualizerData.CODEC);
        this.dimension = dimension;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.claimForFactionId = claimForFactionId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, 
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        // Load UI file
        uiCommandBuilder.append("Pages/ChunkVisualizer.ui");
        
        // Set static UI text from I18n
        uiCommandBuilder.set("#Title #TitleText.Text", I18n.get("ui.chunk_visualizer.title"));
        uiCommandBuilder.set("#ClaimedChunksInfo #ClaimedChunksLabel.Text", I18n.get("ui.chunk_visualizer.claimed_chunks"));
        
        // Get grid size from config
        int radius = Main.get().getClaimConfig().get().getChunkVisualizerGridRadius();
        int gridSize = radius * 2 + 1;
        
        // Get player faction
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        
        FactionManager manager = FactionManager.getInstance();
        FactionInfo playerFaction = claimForFactionId != null
            ? manager.getFactionById(claimForFactionId)
            : manager.getFactionFromPlayer(playerRef.getUuid());
        
        // Set claim count info
        if (playerFaction != null) {
            uiCommandBuilder.set("#ClaimedChunksInfo #ClaimedChunksCount.Text", 
                String.valueOf(manager.getAmountOfClaims(playerFaction)));
            uiCommandBuilder.set("#ClaimedChunksInfo #MaxChunksCount.Text", 
                String.valueOf(playerFaction.getMaxClaimAmount()));
        }
        
        // CRITICAL: Clear container before appending
        uiCommandBuilder.clear("#ChunkCards");
        
        // Build grid: rows first, then items in each row
        for (int z = 0; z < gridSize; z++) {
            // Create row container
            uiCommandBuilder.appendInline("#ChunkCards", 
                "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            
            for (int x = 0; x < gridSize; x++) {
                // Calculate world chunk coordinates
                int worldChunkX = centerChunkX + x - radius;
                int worldChunkZ = centerChunkZ + z - radius;
                
                // Append chunk entry component
                uiCommandBuilder.append("#ChunkCards[" + z + "]", 
                    "Pages/ChunkEntry.ui");
                
                String selector = "#ChunkCards[" + z + "][" + x + "]";
                String buttonSelector = selector + " #Button";
                
                // Check if chunk is claimed
                ChunkInfo chunk = manager.getChunk(dimension, worldChunkX, worldChunkZ);
                
                if (chunk != null) {
                    // Claimed chunk - fill background with faction color
                    FactionInfo chunkFaction = manager.getFactionById(chunk.getFactionOwner());
                    if (chunkFaction != null) {
                        Color factionColor = new Color(chunkFaction.getColor());
                        uiCommandBuilder.set(buttonSelector + ".Background.Color",
                            ColorParseUtil.colorToHexAlpha(factionColor));
                        uiCommandBuilder.set(buttonSelector + ".OutlineColor",
                            ColorParseUtil.colorToHexAlpha(factionColor));
                        uiCommandBuilder.set(buttonSelector + ".OutlineSize", 1);
                    } else {
                        uiCommandBuilder.set(buttonSelector + ".Background.Color", "#80808080");
                    }
                } else {
                    // Unclaimed chunk - grey background, 0.5 opacity
                    uiCommandBuilder.set(buttonSelector + ".Background.Color", "#80808080");
                }
                
                // Check if center chunk (player position)
                if (x == radius && z == radius) {
                    uiCommandBuilder.set(buttonSelector + ".Text", "+");
                }
                
                // Build tooltip
                MessageHelper.ML tooltipBuilder = null;
                
                if (chunk != null) {
                    // Claimed chunk - tooltip and events
                    FactionInfo chunkFaction = manager.getFactionById(chunk.getFactionOwner());
                    if (chunkFaction != null) {
                        
                        // Build tooltip
                        tooltipBuilder = MessageHelper.multiLine()
                            .append(Message.raw(I18n.get("ui.chunk_visualizer.owner")).bold(true).color("#93844c"))
                            .append(Message.raw(chunkFaction.getName())).nl()
                            .append(Message.raw(I18n.get("ui.chunk_visualizer.description")).bold(true).color("#93844c"))
                            .append(Message.raw(chunkFaction.getDescription()));
                        
                        // Add unclaim option if player owns this chunk
                        if (playerFaction != null && 
                            chunkFaction.getId().equals(playerFaction.getId())) {
                            tooltipBuilder = tooltipBuilder.nl().nl()
                                .append(Message.raw(I18n.get("ui.chunk_visualizer.right_click_unclaim"))
                                    .bold(true).color(Color.RED.darker().darker()));
                        }
                        
                        // Bind right-click for unclaim (if player owns)
                        if (playerFaction != null && 
                            chunkFaction.getId().equals(playerFaction.getId())) {
                            uiEventBuilder.addEventBinding(
                                CustomUIEventBindingType.RightClicking,
                                buttonSelector,
                                EventData.of(ChunkVisualizerData.KEY_ACTION, "Unclaim")
                                    .append(ChunkVisualizerData.KEY_CHUNK_X, String.valueOf(worldChunkX))
                                    .append(ChunkVisualizerData.KEY_CHUNK_Z, String.valueOf(worldChunkZ))
                            );
                        }
                    }
                } else {
                    // Unclaimed chunk - show wilderness
                    tooltipBuilder = MessageHelper.multiLine()
                        .append(Message.raw(I18n.get("ui.chunk_visualizer.wilderness")).bold(true).color(Color.GREEN.darker()));
                    
                    if (playerFaction != null) {
                        tooltipBuilder = tooltipBuilder.nl().nl()
                            .append(Message.raw(I18n.get("ui.chunk_visualizer.left_click_claim"))
                                .bold(true).color(Color.GRAY));
                        
                        // Bind left-click for claim
                        uiEventBuilder.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            buttonSelector,
                            EventData.of(ChunkVisualizerData.KEY_ACTION, "Claim")
                                .append(ChunkVisualizerData.KEY_CHUNK_X, String.valueOf(worldChunkX))
                                .append(ChunkVisualizerData.KEY_CHUNK_Z, String.valueOf(worldChunkZ))
                        );
                    } else {
                        tooltipBuilder = tooltipBuilder.nl().nl()
                            .append(Message.raw(I18n.get("ui.chunk_visualizer.create_faction_to_claim"))
                                .bold(true).color(Color.GRAY));
                    }
                }
                
                // Set tooltip
                if (tooltipBuilder != null) {
                    uiCommandBuilder.set(buttonSelector + ".TooltipTextSpans", tooltipBuilder.build());
                }
            }
        }
    }
    
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, 
                                @Nonnull ChunkVisualizerData data) {
        try {
            ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: START - action: " + data.action + ", chunkX: " + data.chunkX + ", chunkZ: " + data.chunkZ);
            
            // handleDataEvent runs on World thread - can access EntityStore directly
            if (ref == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - ref is null");
                return;
            }
            if (store == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - store is null");
                return;
            }
            if (data == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - data is null");
                return;
            }
            
            if (!ref.isValid()) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - ref is invalid");
                return;
            }
            
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            
            if (player == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - player is null");
                return;
            }
            if (playerRef == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - playerRef is null");
                return;
            }
            
            ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Player and PlayerRef retrieved successfully");
            
            // Validate event data
            if (data.action == null || data.chunkX == null || data.chunkZ == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - Invalid event data (action: " + data.action + ", chunkX: " + data.chunkX + ", chunkZ: " + data.chunkZ + ")");
                return;
            }
            
            ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Getting FactionManager");
            FactionManager manager = FactionManager.getInstance();
            if (manager == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - FactionManager is null");
                return;
            }
            
            ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Getting player faction for UUID: " + playerRef.getUuid());
            FactionInfo playerFaction = claimForFactionId != null
                ? manager.getFactionById(claimForFactionId)
                : manager.getFactionFromPlayer(playerRef.getUuid());
            
            if (playerFaction == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Player is not in a faction");
                player.sendMessage(Message.raw(I18n.get("chat.claim.must_be_in_faction"))
                    .color(Color.RED));
                refreshPage(ref, store);
                return;
            }
            
            ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Getting player world");
            World world = player.getWorld();
            if (world == null) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - player.getWorld() returned null");
                player.sendMessage(Message.raw(I18n.get("chat.chunk.error_world"))
                    .color(Color.RED));
                return;
            }
            if (!world.getName().equals(dimension)) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - World mismatch (expected: " + dimension + ", got: " + world.getName() + ")");
                player.sendMessage(Message.raw(I18n.get("chat.chunk.error_world"))
                    .color(Color.RED));
                return;
            }
            
            ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: World validated - " + world.getName());
            
            // Validate chunk coordinates
            int radius = Main.get().getClaimConfig().get().getChunkVisualizerGridRadius();
            int maxOffset = radius;
            if (Math.abs(data.chunkX - centerChunkX) > maxOffset || 
                Math.abs(data.chunkZ - centerChunkZ) > maxOffset) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - Invalid chunk coordinates (chunk: " + data.chunkX + ", " + data.chunkZ + ", center: " + centerChunkX + ", " + centerChunkZ + ", maxOffset: " + maxOffset + ")");
                player.sendMessage(Message.raw(I18n.get("chat.chunk.error_coords"))
                    .color(Color.RED));
                return;
            }
            
            if ("Claim".equals(data.action)) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Processing CLAIM action for chunk (" + data.chunkX + ", " + data.chunkZ + ")");
                
                // Check permission: owner or member with claim permission, or admin claiming for any faction
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Checking claim permission");
                boolean canClaim = claimForFactionId != null
                    ? PermissionUtil.canClaimChunkAdmin(playerRef.getUuid())
                    : PermissionUtil.canClaimChunkInFaction(playerRef.getUuid(), playerFaction);
                if (!canClaim) {
                    ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Permission denied for claim");
                    player.sendMessage(Message.raw(I18n.get("chat.claim.no_permission"))
                        .color(Color.RED));
                    refreshPage(ref, store);
                    return;
                }
                
                // Validate chunk is unclaimed
                ChunkInfo existing = manager.getChunk(dimension, data.chunkX, data.chunkZ);
                if (existing != null) {
                    ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Chunk already claimed");
                    player.sendMessage(Message.raw(I18n.get("chat.claim.already_claimed"))
                        .color(Color.YELLOW));
                    refreshPage(ref, store);
                    return;
                }
                
                // Validate claim limit
                if (!manager.hasEnoughClaimsLeft(playerFaction)) {
                    int current = manager.getAmountOfClaims(playerFaction);
                    int max = playerFaction.getMaxClaimAmount();
                    ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Claim limit reached (" + current + "/" + max + ")");
                    player.sendMessage(Message.raw(I18n.get("chat.claim.limit_reached", current, max))
                        .color(Color.RED));
                    refreshPage(ref, store);
                    return;
                }
                
                // Claim chunk (synchronous - FactionManager is thread-safe)
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Claiming chunk (" + data.chunkX + ", " + data.chunkZ + ")");
                manager.claimChunkBy(dimension, data.chunkX, data.chunkZ, 
                    playerFaction, player, playerRef);
                
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Chunk claimed successfully");
                player.sendMessage(Message.raw(I18n.get("chat.claim.success"))
                    .color(Color.GREEN));

                // Queue world map cache invalidation (processed by WorldMapUpdateTickingSystem)
                FactionManager.getInstance().queueMapUpdate(world, data.chunkX, data.chunkZ);
                
                // Refresh UI immediately (data updated, UI needs refresh)
                refreshPage(ref, store);
                
            } else if ("Unclaim".equals(data.action)) {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Processing UNCLAIM action for chunk (" + data.chunkX + ", " + data.chunkZ + ")");
                
                // Check permission: owner or member with unclaim permission, or admin unclaiming for any faction
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Checking unclaim permission");
                boolean canUnclaim = claimForFactionId != null
                    ? PermissionUtil.canUnclaimChunkAdmin(playerRef.getUuid())
                    : PermissionUtil.canUnclaimChunkInFaction(playerRef.getUuid(), playerFaction);
                if (!canUnclaim) {
                    ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Permission denied for unclaim");
                    player.sendMessage(Message.raw(I18n.get("chat.chunk.no_permission_unclaim"))
                        .color(Color.RED));
                    refreshPage(ref, store);
                    return;
                }
                // Validate chunk is claimed by player's faction
                ChunkInfo chunk = manager.getChunk(dimension, data.chunkX, data.chunkZ);
                if (chunk == null || !chunk.getFactionOwner().equals(playerFaction.getId())) {
                    ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - Chunk not owned by player's faction");
                    player.sendMessage(Message.raw("You can only unclaim your own faction's chunks")
                        .color(Color.RED));
                    refreshPage(ref, store);
                    return;
                }
                
                // Unclaim chunk
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Unclaiming chunk (" + data.chunkX + ", " + data.chunkZ + ")");
                manager.unclaim(dimension, data.chunkX, data.chunkZ);
                
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: Chunk unclaimed successfully");
                player.sendMessage(Message.raw(I18n.get("chat.unclaim.success"))
                    .color(Color.GREEN));

                // Queue world map cache invalidation (processed by WorldMapUpdateTickingSystem)
                FactionManager.getInstance().queueMapUpdate(world, data.chunkX, data.chunkZ);
                
                // Refresh UI immediately
                refreshPage(ref, store);
            } else {
                ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: ERROR - Unknown action: " + data.action);
            }
        } catch (Exception e) {
            ConfigUtil.debug("ChunkVisualizerPage.handleDataEvent: EXCEPTION in handleDataEvent: " + e.getMessage(), e);
        }
    }

    private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!ref.isValid()) {
            return;
        }
        
        // Get world for async execution
        EntityStore entityStore = (EntityStore) store.getExternalData();
        World world = entityStore.getWorld();
        
        // Make refresh async to avoid blocking world thread
        // The build() method rebuilds the entire grid which can be expensive (17x17 = 289 cells)
        // By making it async, we prevent the "Loading..." freeze when clicking on the grid
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            build(ref, commandBuilder, eventBuilder, store);
            
            // sendUpdate() handles threading internally - do not wrap
            sendUpdate(commandBuilder, eventBuilder, true);
        }, (Executor) world);
    }

    public static class ChunkVisualizerData {
        static final String KEY_ACTION = "Action";
        static final String KEY_CHUNK_X = "ChunkX";
        static final String KEY_CHUNK_Z = "ChunkZ";
        
        public static final BuilderCodec<ChunkVisualizerData> CODEC = BuilderCodec
            .builder(ChunkVisualizerData.class, ChunkVisualizerData::new)
            .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), 
                (data, s) -> data.action = s, data -> data.action)
            .addField(new KeyedCodec<>(KEY_CHUNK_X, Codec.STRING),
                (data, s) -> {
                    try {
                        data.chunkX = (s != null && !s.isEmpty()) ? Integer.parseInt(s) : null;
                    } catch (NumberFormatException e) {
                        data.chunkX = null;
                    }
                },
                data -> data.chunkX != null ? String.valueOf(data.chunkX) : null)
            .addField(new KeyedCodec<>(KEY_CHUNK_Z, Codec.STRING),
                (data, s) -> {
                    try {
                        data.chunkZ = (s != null && !s.isEmpty()) ? Integer.parseInt(s) : null;
                    } catch (NumberFormatException e) {
                        data.chunkZ = null;
                    }
                },
                data -> data.chunkZ != null ? String.valueOf(data.chunkZ) : null)
            .build();
        
        private String action; // "Claim" or "Unclaim"
        private Integer chunkX; // Chunk X coordinate
        private Integer chunkZ; // Chunk Z coordinate
    }
}
