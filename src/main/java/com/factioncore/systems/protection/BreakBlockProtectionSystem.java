package com.factioncore.systems.protection;

import com.factioncore.Main;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ECS system that handles block break protection for claimed chunks.
 */
public class BreakBlockProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    public BreakBlockProtectionSystem() {
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
    
    private static final String PREFIX = "[BreakBlock] ";

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, 
                      @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                      @Nonnull BreakBlockEvent event) {
        // Unconditional log to confirm this entity-level handler is invoked when a player breaks a block
        if (Main.get() != null) {
            Main.get().getLogger().at(Level.INFO).log("[FactionCore] BreakBlockProtectionSystem.handle() invoked (entity-level)");
        }
        // Log once per break when Debug is on (no need for VerboseDebug) so you can confirm the system runs
        ConfigUtil.debug(PREFIX + "break event at (" + event.getTargetBlock().getX() + "," + event.getTargetBlock().getZ() + ")");
        ConfigUtil.verboseDebug(PREFIX + "handle() called");
        
        String blockId = event.getBlockType() != null ? event.getBlockType().getId() : "null";
        String blockName = blockId.toLowerCase(Locale.ROOT);
        ConfigUtil.verboseDebug(PREFIX + "block type id=" + blockId);

        // Blocks in ignore list (e.g. grass) skip protection (SimpleClaims-style)
        boolean ignored = ProtectionService.isBlockIgnored(blockName);
        ConfigUtil.verboseDebug(PREFIX + "block in ignore list=" + ignored + " (blockName=" + blockName + ")");
        if (ignored) {
            ConfigUtil.verboseDebug(PREFIX + "allowing (block ignored), not cancelling");
            return;
    }
        
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            ConfigUtil.verboseDebug(PREFIX + "ref null or invalid, skipping");
            return;
        }
        
        // Get PlayerRef to access player UUID
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            ConfigUtil.verboseDebug(PREFIX + "no PlayerRef on entity, skipping");
            return; // Not a player
        }
        
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            ConfigUtil.verboseDebug(PREFIX + "PlayerRef uuid null, skipping");
            return;
        }
        
        // Get world name
        EntityStore entityStore = (EntityStore) store.getExternalData();
        World world = entityStore != null ? entityStore.getWorld() : null;
        if (world == null) {
            ConfigUtil.verboseDebug(PREFIX + "world null, skipping");
            return;
        }
        
        String dimension = world.getName();
        Vector3i targetBlock = event.getTargetBlock();
        int blockX = targetBlock.getX();
        int blockZ = targetBlock.getZ();
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        ConfigUtil.verboseDebug(PREFIX + "player=" + playerUuid + " dimension=" + dimension + " blockPos=(" + blockX + "," + blockZ + ") chunk=(" + chunkX + "," + chunkZ + ")");
        
        // Check if block break is allowed (permission key path: same-faction + ally settings)
        boolean allowed = ProtectionService.isAllowedToInteract(
            playerUuid,
            dimension,
            blockX,
            blockZ,
            PermissionUtil.INTERACT_KEY_BREAK_BLOCK
        );
        
        ConfigUtil.verboseDebug(PREFIX + "isAllowedToInteract(break)=" + allowed + " for player=" + playerUuid);
        if (!allowed) {
            ConfigUtil.debug(PREFIX + "BLOCKING break for player " + playerUuid + " at (" + blockX + "," + blockZ + ") in " + dimension);
            event.setCancelled(true);
        } else {
            ConfigUtil.verboseDebug(PREFIX + "allowing break, not cancelling");
        }
    }
}
