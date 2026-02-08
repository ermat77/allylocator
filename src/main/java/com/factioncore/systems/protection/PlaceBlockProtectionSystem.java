package com.factioncore.systems.protection;

import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * ECS system that handles block place protection for claimed chunks.
 */
public class PlaceBlockProtectionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    
    public PlaceBlockProtectionSystem() {
        super(PlaceBlockEvent.class);
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
                      @Nonnull PlaceBlockEvent event) {
        
        ConfigUtil.verboseDebug("PlaceBlockProtectionSystem.handle() called");
        
        // Get entity ref
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }
        
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }
        
        Player player = store.getComponent(ref, Player.getComponentType());
        World world = player != null ? player.getWorld() : null;
        if (world == null) {
            return;
        }
        
        String dimension = world.getName();
        Vector3i targetBlock = event.getTargetBlock();
        int blockX = targetBlock.getX();
        int blockZ = targetBlock.getZ();
        
        ConfigUtil.verboseDebug("PlaceBlockProtectionSystem: player=" + playerUuid + ", dimension=" + dimension + ", block=" + blockX + "," + blockZ);
        
        // Check if block place is allowed
        boolean allowed = ProtectionService.isAllowedToInteract(
            playerUuid,
            dimension,
            blockX,
            blockZ,
            ProtectionService::isBlockPlaceEnabled
        );
        
        if (!allowed) {
            ConfigUtil.debug("PlaceBlockProtectionSystem: blocking block place for player " + playerUuid);
            event.setCancelled(true);
        }
    }
}
