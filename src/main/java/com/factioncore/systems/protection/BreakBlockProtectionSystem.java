package com.factioncore.systems.protection;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS system that handles block break protection for claimed chunks.
 */
public class BreakBlockProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    public BreakBlockProtectionSystem() {
        super(BreakBlockEvent.class);
    }
    
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
    
    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, 
                      @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                      @Nonnull BreakBlockEvent event) {
        
        ConfigUtil.verboseDebug("BreakBlockProtectionSystem.handle() called");
        
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
        
        ConfigUtil.verboseDebug("BreakBlockProtectionSystem: player=" + playerUuid + ", dimension=" + dimension + ", block=" + blockX + "," + blockZ);
        
        // Check if block break is allowed
        boolean allowed = ProtectionService.isAllowedToInteract(
            playerUuid,
            dimension,
            blockX,
            blockZ,
            ProtectionService::isBlockBreakEnabled
        );
        
        if (!allowed) {
            ConfigUtil.debug("BreakBlockProtectionSystem: blocking block break for player " + playerUuid);
            event.setCancelled(true);
        }
    }
}
