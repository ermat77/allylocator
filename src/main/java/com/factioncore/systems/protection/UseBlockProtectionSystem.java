package com.factioncore.systems.protection;

import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS system that handles block interaction protection for claimed chunks.
 * Handles UseBlockEvent.Pre (the cancellable version).
 */
public class UseBlockProtectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    
    public UseBlockProtectionSystem() {
        super(UseBlockEvent.Pre.class);
    }
    
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
    
    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, 
                      @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                      @Nonnull UseBlockEvent.Pre event) {
        
        ConfigUtil.verboseDebug("UseBlockProtectionSystem.handle() called");
        
        // Get entity ref from interaction context
        InteractionContext context = event.getContext();
        Ref<EntityStore> ref = context.getEntity();
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
        
        // Get world name from command buffer
        com.hypixel.hytale.component.CommandBuffer<EntityStore> cmdBuffer = context.getCommandBuffer();
        if (cmdBuffer == null) {
            return;
        }
        
        EntityStore entityStore = (EntityStore) cmdBuffer.getExternalData();
        com.hypixel.hytale.server.core.universe.world.World world = entityStore.getWorld();
        if (world == null) {
            return;
        }
        
        String dimension = world.getName();
        Vector3i targetBlock = event.getTargetBlock();
        int blockX = targetBlock.getX();
        int blockZ = targetBlock.getZ();
        
        ConfigUtil.verboseDebug("UseBlockProtectionSystem: player=" + playerUuid + ", dimension=" + dimension + ", block=" + blockX + "," + blockZ);
        
        // Check if block interact is allowed
        boolean allowed = ProtectionService.isAllowedToInteract(
            playerUuid,
            dimension,
            blockX,
            blockZ,
            ProtectionService::isBlockInteractEnabled
        );
        
        if (!allowed) {
            ConfigUtil.debug("UseBlockProtectionSystem: blocking block interact for player " + playerUuid);
            event.setCancelled(true);
        }
    }
}
