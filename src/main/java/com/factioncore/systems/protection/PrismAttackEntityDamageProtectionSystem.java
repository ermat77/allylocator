package com.factioncore.systems.protection;

import com.factioncore.prism.PrismAttackRecord;
import com.factioncore.prism.PrismAttackService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Cancels entity damage (to players and NPCs) in prism attack worlds when attack status is not IN_PROGRESS.
 * During PREPARING (join window), damage is cancelled; when status is IN_PROGRESS (combat), damage is allowed.
 */
public class PrismAttackEntityDamageProtectionSystem extends DamageEventSystem {

    private static final Query<EntityStore> QUERY = AllLegacyLivingEntityTypesQuery.INSTANCE;

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                      @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                      @Nonnull Damage damage) {
        Object ext = commandBuffer.getExternalData();
        if (!(ext instanceof EntityStore)) {
            return;
        }
        World world = ((EntityStore) ext).getWorld();
        String worldName = world != null ? world.getName() : null;
        if (worldName == null || !PrismAttackService.isAttackWorldName(worldName)) {
            return;
        }
        PrismAttackRecord attack = PrismAttackService.getInstance().getAttackRecordByWorldName(worldName);
        if (attack == null) {
            return;
        }
        if (!"IN_PROGRESS".equals(attack.getStatus())) {
            damage.setCancelled(true);
        }
    }
}
