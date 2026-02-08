package com.factioncore.systems.tick;

import com.factioncore.claim.ChunkInfo;
import com.factioncore.faction.FactionInfo;
import com.factioncore.util.I18n;
import com.factioncore.faction.FactionManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a title above the hotbar when the player enters/leaves a claim:
 * wilderness name (green) in unclaimed chunks, faction name (white) in claimed chunks.
 * Adapted from SimpleClaims TitleTickingSystem.
 */
public class TitleTickingSystem extends EntityTickingSystem<EntityStore> {

    private final Map<UUID, String> playerLastTitle = new ConcurrentHashMap<>();

    @Override
    public void tick(float v, int index, ArchetypeChunk<EntityStore> archetypeChunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null || player.getWorld() == null) {
            return;
        }

        String wildernessText = I18n.get("title.wilderness");
        String titleText = wildernessText;
        Message titleMessage = Message.raw(wildernessText).color(Color.GREEN);

        ChunkInfo chunkInfo = FactionManager.getInstance().getChunkRawCoords(
                player.getWorld().getName(),
                (int) Math.floor(playerRef.getTransform().getPosition().getX()),
                (int) Math.floor(playerRef.getTransform().getPosition().getZ())
        );

        if (chunkInfo != null) {
            FactionInfo faction = FactionManager.getInstance().getFactionById(chunkInfo.getFactionOwner());
            if (faction != null) {
                titleText = faction.getName();
                titleMessage = Message.raw(titleText).color(Color.WHITE);
            }
        }

        UUID uuid = playerRef.getUuid();
        String previousTitle = playerLastTitle.get(uuid);
        if (!titleText.equals(previousTitle)) {
            playerLastTitle.put(uuid, titleText);
            Message topLine = Message.raw(I18n.get("title.claim_top_line"));
            EventTitleUtil.showEventTitleToPlayer(playerRef, titleMessage, topLine, false, null, 2, 0.5f, 0.5f);
        }
    }

    public void removePlayer(UUID playerId) {
        playerLastTitle.remove(playerId);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
