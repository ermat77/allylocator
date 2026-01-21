package com.factioncore.systems.protection;

import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS system that handles block place protection for claimed chunks.
 */
public class PlaceBlockProtectionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    
    public PlaceBlockProtectionSystem() {
        super(PlaceBlockEvent.class);
    }
    
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
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
        
        // Get PlayerRef to access player UUID
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return; // Not a player
        }
        
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }
        
        // Get world name
        EntityStore entityStore = (EntityStore) store.getExternalData();
        com.hypixel.hytale.server.core.universe.world.World world = entityStore.getWorld();
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
