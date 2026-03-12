package com.factioncore.systems.protection;

import com.factioncore.Main;
import com.factioncore.prism.PrismAttackConstants;
import com.factioncore.prism.PrismAttackRecord;
import com.factioncore.prism.PrismAttackService;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listens for death events in prism attack worlds (status IN_PROGRESS) using {@link DeathSystems.OnDeathSystem}.
 * Only runs for entities that are players or NPCs (see <a href="https://hytalemodding.dev/en/docs/guides/plugin/player-death-event#2-define-which-entities-this-system-applies-to">Player Death Event</a>).
 * - If the entity is the prism NPC (role id {@link PrismAttackConstants#PRISM_ATTACKED_NPC_ROLE_ID}): attackers win.
 * - If the entity is a player and an attacker: mark them dead; if all attackers are dead, defenders win.
 */
public class PrismAttackDeathResolutionSystem extends DeathSystems.OnDeathSystem {

    /** Cached NPCEntity component type (SDK: com.hypixel.hytale.server.npc.entities.NPCEntity) for getQuery(). */
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
                @SuppressWarnings({"unchecked", "rawtypes"})
                ComponentType<EntityStore, ?> type = (ComponentType<EntityStore, ?>) ct;
                return type;
            } catch (Exception ignored) {
                // try next class name
            }
        }
        return null;
    }

    /** Only run for deaths of players or NPCs (not other entity types). */
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        if (NPC_ENTITY_COMPONENT_TYPE != null) {
            return Query.and(
                    DeathComponent.getComponentType(),
                    Query.or(PlayerRef.getComponentType(), NPC_ENTITY_COMPONENT_TYPE)
            );
        }
        return Query.and(DeathComponent.getComponentType(), PlayerRef.getComponentType());
    }

    private static final String LOG_PREFIX = "[PrismAttackDeathResolution]";

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent deathComponent,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Object ext = commandBuffer.getExternalData();
        if (!(ext instanceof EntityStore)) {
            ConfigUtil.debug(LOG_PREFIX + " onComponentAdded: external data is not EntityStore, skipping");
            return;
        }
        EntityStore entityStore = (EntityStore) ext;
        World world = entityStore.getWorld();
        if (world == null) {
            ConfigUtil.debug(LOG_PREFIX + " onComponentAdded: world is null, skipping");
            return;
        }
        String worldName = world.getName();
        ConfigUtil.debug(LOG_PREFIX + " entity died in world=" + worldName);
        if (worldName == null || !PrismAttackService.isAttackWorldName(worldName)) {
            ConfigUtil.debug(LOG_PREFIX + " not an attack world (or null name), skipping");
            return;
        }
        PrismAttackRecord attack = PrismAttackService.getInstance().getAttackRecordByWorldName(worldName);
        if (attack == null) {
            ConfigUtil.debug(LOG_PREFIX + " no attack record for world " + worldName + ", skipping");
            return;
        }
        if (!"IN_PROGRESS".equals(attack.getStatus())) {
            ConfigUtil.debug(LOG_PREFIX + " attack status is " + attack.getStatus() + " (not IN_PROGRESS), skipping");
            return;
        }
        UUID attackId = attack.getAttackId();

        // Player death: check if attacker, mark dead, resolve defenders win if all attackers dead
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            UUID playerUuid = playerRef.getUuid();
            ConfigUtil.debug(LOG_PREFIX + " entity is player, uuid=" + playerUuid);
            if (playerUuid != null && PrismAttackService.getInstance().markAttackerDeadAndCheckAllDead(attackId, playerUuid)) {
                ConfigUtil.debug(LOG_PREFIX + " all attackers dead, resolving defenders win");
                PrismAttackService.getInstance().resolveAttackDefendersWin(attackId);
            }
            return;
        }

        // NPC death: check if prism NPC, then resolve attackers win
        boolean prismNpc = isPrismNpc(store, ref);
        if (prismNpc) {
            if (Main.get() != null) {
                Main.get().getLogger().at(Level.INFO).log(LOG_PREFIX + " prism NPC killed, resolving attackers win attackId=" + attackId);
            }
            PrismAttackService.getInstance().resolveAttackAttackersWin(attackId);
        }
    }

    @Nullable
    private static Object getNpcEntity(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (NPC_ENTITY_COMPONENT_TYPE != null) {
            Object comp = store.getComponent(ref, NPC_ENTITY_COMPONENT_TYPE);
            if (comp != null) return comp;
        }
        return null;
    }

    @Nullable
    private static Object invokeGetRole(@Nonnull Object npcEntity) {
        try {
            Method m = npcEntity.getClass().getMethod("getRole");
            return m.invoke(npcEntity);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if the entity is an NPC with role name {@link PrismAttackConstants#PRISM_ATTACKED_NPC_ROLE_ID} (Role.getRoleName()).
     */
    private boolean isPrismNpc(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        Object npc = getNpcEntity(store, ref);
        if (npc == null) return false;
        Object role = invokeGetRole(npc);
        if (role == null) return false;
        String roleId = getRoleIdFromRole(role);
        return roleId != null && PrismAttackConstants.PRISM_ATTACKED_NPC_ROLE_ID.equals(roleId);
    }

    /** Uses Role.getRoleName() for prism NPC matching (SDK exposes roleName, not id). */
    @Nullable
    private static String getRoleIdFromRole(@Nonnull Object role) {
        try {
            Method m = role.getClass().getMethod("getRoleName");
            Object name = m.invoke(role);
            return name != null ? name.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
