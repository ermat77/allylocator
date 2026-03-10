package com.factioncore.systems.protection;

import com.factioncore.prism.PrismAttackConstants;
import com.factioncore.prism.PrismAttackRecord;
import com.factioncore.prism.PrismAttackService;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * When an entity gains DeathComponent in a prism attack world (status IN_PROGRESS):
 * - If the entity is the prism NPC (role id {@link PrismAttackConstants#PRISM_ATTACKED_NPC_ROLE_ID}): attackers win.
 * - If the entity is a player and an attacker: mark them dead; if all attackers are dead, defenders win.
 */
public class PrismAttackDeathResolutionSystem extends RefChangeSystem<EntityStore, DeathComponent> {

    @Nonnull
    @Override
    public com.hypixel.hytale.component.ComponentType<EntityStore, DeathComponent> componentType() {
        return DeathComponent.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return DeathComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent deathComponent,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Object ext = commandBuffer.getExternalData();
        if (!(ext instanceof EntityStore)) {
            return;
        }
        EntityStore entityStore = (EntityStore) ext;
        World world = entityStore.getWorld();
        if (world == null) return;
        String worldName = world.getName();
        if (worldName == null || !PrismAttackService.isAttackWorldName(worldName)) {
            return;
        }
        PrismAttackRecord attack = PrismAttackService.getInstance().getAttackRecordByWorldName(worldName);
        if (attack == null || !"IN_PROGRESS".equals(attack.getStatus())) {
            return;
        }
        UUID attackId = attack.getAttackId();

        // Player death: check if attacker, mark dead, resolve defenders win if all attackers dead
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            UUID playerUuid = playerRef.getUuid();
            if (playerUuid != null && PrismAttackService.getInstance().markAttackerDeadAndCheckAllDead(attackId, playerUuid)) {
                PrismAttackService.getInstance().resolveAttackDefendersWin(attackId);
            }
            return;
        }

        // NPC death: if prism NPC, attackers win
        if (isPrismNpc(store, ref)) {
            PrismAttackService.getInstance().resolveAttackAttackersWin(attackId);
        }
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable DeathComponent oldComponent,
                                @Nonnull DeathComponent newComponent, @Nonnull Store<EntityStore> store,
                                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // No-op: we only care about component added.
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent deathComponent,
                                    @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // No-op: we only care about component added.
    }

    /**
     * Returns true if the entity is an NPC with role id {@link PrismAttackConstants#PRISM_ATTACKED_NPC_ROLE_ID}.
     * Uses Hytale NPC role API when available; extend when engine exposes role id on NPC entities.
     */
    private boolean isPrismNpc(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        // Try to resolve NPC role id from engine (e.g. RoleComponent or NpcEntity.getCurrentRoleId()).
        // When the Hytale API for NPC role id is confirmed, implement the check here.
        return false;
    }
}
