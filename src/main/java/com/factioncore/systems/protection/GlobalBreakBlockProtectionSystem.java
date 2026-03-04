package com.factioncore.systems.protection;

import com.factioncore.Main;
import com.factioncore.util.PermissionUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * World-level event system for BreakBlockEvent without entity context (e.g. explosions, custom items).
 * Blocks all such breaks in claimed chunks (SimpleClaims-style GlobalBreakBlockEventSystem).
 */
public class GlobalBreakBlockProtectionSystem extends WorldEventSystem<EntityStore, BreakBlockEvent> {

    public GlobalBreakBlockProtectionSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
        // Unconditional log to confirm this world-level handler is invoked (e.g. explosions, or if game only fires world-level)
        if (Main.get() != null) {
            Main.get().getLogger().at(Level.INFO).log("[FactionCore] GlobalBreakBlockProtectionSystem.handle() invoked (world-level)");
        }
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
        if (ProtectionService.isBlockBreakDisabledInWorld(worldName)) {
            event.setCancelled(true);
            return;
        }

        String blockName = event.getBlockType().getId().toLowerCase(Locale.ROOT);
        if (ProtectionService.isBlockIgnored(blockName)) {
            return;
        }

        int x = event.getTargetBlock().getX();
        int z = event.getTargetBlock().getZ();

        // Deny in claimed chunks when there is no player (null)
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
