package com.factioncore.systems.interaction;

import com.factioncore.ui.ExampleBlockPage;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * When the player uses (F key) a block that is configured as "open UI",
 * opens the example block UI page. Block type ID is matched case-insensitively.
 * <p>
 * To make your block open this UI: use block type ID
 * {@value #BLOCK_TYPE_ID_INTERACTIVE} (from your pack's Group + item name, e.g.
 * Server/Item/Items/Interactive_Block.json with manifest Group "com.factioncore"
 * gives "com.factioncore:Interactive_Block").
 */
public class BlockUseOpenUISystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {

    /**
     * Block type ID that triggers opening the UI when used (F).
     * Format is typically "group:ItemName", e.g. com.factioncore:Interactive_Block.
     */
    public static final String BLOCK_TYPE_ID_INTERACTIVE = "com.factioncore:Interactive_Block";

    public BlockUseOpenUISystem() {
        super(UseBlockEvent.Post.class);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                      @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                      @Nonnull UseBlockEvent.Post event) {
        String blockId = event.getBlockType().getId();
        if (blockId == null || !BLOCK_TYPE_ID_INTERACTIVE.equalsIgnoreCase(blockId)) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        ExampleBlockPage page = new ExampleBlockPage(playerRef);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
