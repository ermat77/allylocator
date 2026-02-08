package com.factioncore.systems.tick;

import com.factioncore.faction.FactionManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Processes the world map cache invalidation queue.
 * Runs on ChunkStore; when a world has pending chunk indices in the queue,
 * clears them via WorldMapManager.clearImagesInChunks and each player's
 * WorldMapTracker.clearChunks so the map shows updated claims on next open.
 */
public class WorldMapUpdateTickingSystem extends DelayedSystem<ChunkStore> {

    public WorldMapUpdateTickingSystem() {
        super(3);
    }

    @Override
    public void delayedTick(float v, int i, @NonNullDecl Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        FactionManager manager = FactionManager.getInstance();
        if (!manager.getMapUpdateQueue().containsKey(world.getName())) {
            return;
        }
        final LongSet chunks = manager.getMapUpdateQueue().get(world.getName());
        world.execute(() -> {
            if (world.getWorldMapManager() != null) {
                world.getWorldMapManager().clearImagesInChunks(chunks);
            }
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                var ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) continue;
                Player player = world.getEntityStore().getStore().getComponent(ref, Player.getComponentType());
                if (player != null && player.getWorldMapTracker() != null) {
                    player.getWorldMapTracker().clearChunks(chunks);
                }
            }
            manager.getMapUpdateQueue().remove(world.getName());
            manager.getWorldsNeedingUpdates().remove(world.getName());
        });
    }
}
