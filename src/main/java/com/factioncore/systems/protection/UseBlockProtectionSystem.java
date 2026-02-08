package com.factioncore.systems.protection;

import com.factioncore.util.ConfigUtil;
import com.factioncore.util.PermissionUtil;
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
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * ECS system that handles block interaction protection for claimed chunks.
 * Handles UseBlockEvent.Pre (the cancellable version).
 */
public class UseBlockProtectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    
    public UseBlockProtectionSystem() {
        super(UseBlockEvent.Pre.class);
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
                      @Nonnull UseBlockEvent.Pre event) {
        
        ConfigUtil.verboseDebug("UseBlockProtectionSystem.handle() called");
        
        // Blocks in ignore list skip protection (SimpleClaims-style)
        String blockName = event.getBlockType().getId().toLowerCase(Locale.ROOT);
        boolean ignored = ProtectionService.isBlockIgnored(blockName);
        
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
        
        ConfigUtil.verboseDebug("UseBlockProtectionSystem: player=" + playerUuid + ", dimension=" + dimension + ", block=" + blockX + "," + blockZ + " blockName=" + blockName);

        String permissionKey = mapBlockToInteractPermissionKey(blockName);
        boolean allowed = ProtectionService.isAllowedToInteract(playerUuid, dimension, blockX, blockZ, permissionKey);

        if (!ignored && !allowed) {
            ConfigUtil.debug("UseBlockProtectionSystem: blocking block interact for player " + playerUuid);
            event.setCancelled(true);
        }
    }

    /**
     * Maps block type id (lowercase) to interaction permission key (e.g. open_chest, open_door).
     * Matches SimpleClaims-style: chest, door, bench (not furniture), chair/stool, portal/teleporter.
     */
    private static String mapBlockToInteractPermissionKey(String blockName) {
        if (blockName == null) return PermissionUtil.INTERACT_KEY_OPEN_CHEST;
        if (blockName.contains("chest")) return PermissionUtil.INTERACT_KEY_OPEN_CHEST;
        if (blockName.contains("door")) return PermissionUtil.INTERACT_KEY_OPEN_DOOR;
        if (blockName.contains("bench") && !blockName.contains("furniture")) return PermissionUtil.INTERACT_KEY_OPEN_BENCH;
        if (blockName.contains("chair") || blockName.contains("stool") || (blockName.contains("bench") && blockName.contains("furniture")))
            return PermissionUtil.INTERACT_KEY_USE_CHAIR;
        if (blockName.contains("portal") || blockName.contains("teleporter")) return PermissionUtil.INTERACT_KEY_USE_PORTAL;
        return PermissionUtil.INTERACT_KEY_OPEN_CHEST;
    }
}
