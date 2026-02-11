package com.factioncore.systems.protection;

import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * World-level event system for DamageBlockEvent without entity context (e.g. explosions, non-player damage).
 * Blocks all such damage in claimed chunks (SimpleClaims-style GlobalDamageBlockEventSystem).
 */
public class GlobalDamageBlockProtectionSystem extends WorldEventSystem<EntityStore, DamageBlockEvent> {

    public GlobalDamageBlockProtectionSystem() {
        super(DamageBlockEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull DamageBlockEvent event) {
        Object ext = store.getExternalData();
        if (!(ext instanceof EntityStore)) {
            return;
        }
        World world = ((EntityStore) ext).getWorld();
        if (world == null) {
            return;
        }
        String worldName = world.getName();
        if (worldName == null) {
            return;
        }

        String blockName = event.getBlockType().getId().toLowerCase(Locale.ROOT);
        if (ProtectionService.isBlockIgnored(blockName)) {
            return;
        }

        int x = event.getTargetBlock().getX();
        int z = event.getTargetBlock().getZ();

        if (!ProtectionService.isAllowedToInteract(null, worldName, x, z, PermissionUtil.INTERACT_KEY_BREAK_BLOCK)) {
            event.setCancelled(true);
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}
