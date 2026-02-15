package com.factioncore.systems.interaction;

import com.factioncore.prism.PrismManager;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * When a Prism_Block is broken, removes the stored placer (BlockPlacerStore) and prism (PrismManager) for that position.
 * Ignores breaks of other block types so prism records stay until the actual prism block is broken.
 */
public class PrismBlockPlacerClearSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public PrismBlockPlacerClearSystem() {
        super(BreakBlockEvent.class);
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
                       @Nonnull BreakBlockEvent event) {
        if (event.getBlockType() == null
                || !BlockUseOpenUISystem.isPrismBlockType(event.getBlockType().getId())) {
            return;
        }

        String blockId = event.getBlockType().getId();
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        World world = player.getWorld();
        if (world == null) return;

        var targetBlock = event.getTargetBlock();
        ConfigUtil.debug("[PrismBlockBreak] blockId=" + blockId
                + " block=(" + targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ() + ")"
                + " world=" + world.getName());
        PrismManager.getInstance().remove(world.getName(), targetBlock);
    }
}
