package com.factioncore.systems.protection;

import com.factioncore.prism.PrismAttackRecord;
import com.factioncore.prism.PrismAttackService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Cancels entity damage (to players and NPCs) in prism attack worlds when attack status is not IN_PROGRESS.
 * During PREPARING (join window), damage is cancelled; when status is IN_PROGRESS (combat), damage is allowed.
 * Uses a query that includes both players (PlayerRef) and NPCs (NpcEntity component type), so damage is
 * applied consistently to all entities in the attack world during combat.
 */
public class PrismAttackEntityDamageProtectionSystem extends DamageEventSystem {

    @Nullable
    private static final ComponentType<EntityStore, ?> NPC_ENTITY_COMPONENT_TYPE = resolveNpcEntityComponentType();

    @Nullable
    private static ComponentType<EntityStore, ?> resolveNpcEntityComponentType() {
        String[] npcClasses = {
                "com.hypixel.hytale.server.npc.entities.NPCEntity",
                "com.hypixel.hytale.server.core.entity.entities.NpcEntity",
                "com.hypixel.hytale.server.core.modules.npc.NpcEntity"
        };
        for (String className : npcClasses) {
            try {
                Class<?> c = Class.forName(className);
                Method getType = c.getMethod("getComponentType");
                Object ct = getType.invoke(null);
                @SuppressWarnings("unchecked")
                ComponentType<EntityStore, ?> type = (ComponentType<EntityStore, ?>) ct;
                return type;
            } catch (Exception ignored) {
                // try next class name
            }
        }
        return null;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        if (NPC_ENTITY_COMPONENT_TYPE != null) {
            return Query.or(PlayerRef.getComponentType(), NPC_ENTITY_COMPONENT_TYPE);
        }
        return PlayerRef.getComponentType();
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
