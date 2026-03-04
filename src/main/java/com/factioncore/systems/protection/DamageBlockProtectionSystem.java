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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
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
 * ECS system that handles block damage protection for claimed chunks.
 * Cancels DamageBlockEvent when the player is not allowed to break blocks in the chunk.
 * SimpleClaims uses both BreakBlockEvent and DamageBlockEvent; blocking damage prevents the break from completing.
 */
public class DamageBlockProtectionSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    public DamageBlockProtectionSystem() {
        super(DamageBlockEvent.class);
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
                      @Nonnull DamageBlockEvent event) {

        // Blocks in ignore list skip protection (SimpleClaims-style)
        String blockName = event.getBlockType().getId().toLowerCase(Locale.ROOT);
        if (ProtectionService.isBlockIgnored(blockName)) {
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
        if (ProtectionService.isBlockBreakDisabledInWorld(dimension)) {
            if (!PermissionUtil.canBypassProtectionAdmin(playerUuid)) {
                ConfigUtil.debug("DamageBlockProtectionSystem: blocking block damage in prism attack world for player " + playerUuid);
                event.setCancelled(true);
                return;
            }
        }
        int blockX = event.getTargetBlock().getX();
        int blockZ = event.getTargetBlock().getZ();

        ConfigUtil.verboseDebug("DamageBlockProtectionSystem: player=" + playerUuid + ", dimension=" + dimension + ", block=" + blockX + "," + blockZ);

        // Permission key path: same-faction + ally settings
        boolean allowed = ProtectionService.isAllowedToInteract(
            playerUuid,
            dimension,
            blockX,
            blockZ,
            PermissionUtil.INTERACT_KEY_BREAK_BLOCK
        );

        if (!allowed) {
            ConfigUtil.debug("DamageBlockProtectionSystem: blocking block damage for player " + playerUuid);
            event.setCancelled(true);
        }
    }
}
