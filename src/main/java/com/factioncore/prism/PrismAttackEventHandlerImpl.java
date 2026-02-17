package com.factioncore.prism;

import com.factioncore.Main;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.I18n;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Event handler for prism attack join: runs pre-teleport checks, assigns spawn, inserts participant,
 * then adds Teleport component on the current world's store.
 */
public final class PrismAttackEventHandlerImpl implements PrismAttackEventHandler {

    private static final PrismAttackEventHandlerImpl INSTANCE = new PrismAttackEventHandlerImpl();

    @Nonnull
    public static PrismAttackEventHandlerImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void onPlayerJoinPrismAttack(@Nonnull PlayerJoinPrismAttackEvent event) {
        UUID prismId = event.getPrismId();
        UUID playerUuid = event.getPlayerRef().getUuid();
        String role = event.getRole();
        Ref<EntityStore> ref = event.getRef();
        Store<EntityStore> store = event.getStore();

        ConfigUtil.debug("[PrismAttackEventHandler] onPlayerJoinPrismAttack entered prismId=" + prismId + " playerUuid=" + playerUuid + " role=" + role);

        if (prismId == null) {
            ConfigUtil.debug("[PrismAttackEventHandler] early return: prismId == null");
            sendError(store, ref, "ui.prism_attack.no_prism");
            return;
        }

        PrismAttackRecord attack = PrismAttackService.getInstance().getAttackByPrismId(prismId);
        if (attack == null) {
            ConfigUtil.debug("[PrismAttackEventHandler] early return: attack not found for prismId=" + prismId);
            sendError(store, ref, "ui.prism_attack.attack_not_found");
            return;
        }

        // 1. Attack exists and is joinable: status PREPARING or PENDING_START (only for first attacker)
        String status = attack.getStatus();
        if (!"PREPARING".equals(status) && !"PENDING_START".equals(status)) {
            ConfigUtil.debug("[PrismAttackEventHandler] early return: attack status not joinable status=" + status);
            sendError(store, ref, "ui.prism_attack.attack_not_joinable");
            return;
        }
        if ("PENDING_START".equals(status) && !"attacker".equalsIgnoreCase(role)) {
            ConfigUtil.debug("[PrismAttackEventHandler] early return: PENDING_START but role is not attacker role=" + role);
            sendError(store, ref, "ui.prism_attack.attack_not_started");
            return;
        }

        // 2. Faction/role check
        FactionInfo playerFaction = FactionManager.getInstance().getFactionFromPlayer(playerUuid);
        if (playerFaction == null) {
            ConfigUtil.debug("[PrismAttackEventHandler] early return: player not in faction");
            sendError(store, ref, "ui.prism_attack.must_be_in_faction");
            return;
        }
        if ("defender".equalsIgnoreCase(role)) {
            UUID defenderFactionId = attack.getDefenderFactionId();
            Set<UUID> allies = FactionManager.getInstance().getAllies(defenderFactionId);
            if (!defenderFactionId.equals(playerFaction.getId()) && !allies.contains(playerFaction.getId())) {
                ConfigUtil.debug("[PrismAttackEventHandler] early return: defender must be faction or ally");
                sendError(store, ref, "ui.prism_attack.defender_must_be_faction_or_ally");
                return;
            }
        } else {
            if (!attack.getAttackerFactionId().equals(playerFaction.getId())) {
                ConfigUtil.debug("[PrismAttackEventHandler] early return: attacker must be attacker faction");
                sendError(store, ref, "ui.prism_attack.attacker_must_be_attacker_faction");
                return;
            }
        }

        // 5. Attack world exists
        World attackWorld = Universe.get().getWorld(attack.getAttackWorldName());
        if (attackWorld == null) {
            ConfigUtil.debug("[PrismAttackEventHandler] early return: attack world not found " + attack.getAttackWorldName());
            sendError(store, ref, "ui.prism_attack.world_not_found");
            return;
        }

        // 6. Current world and return position (for teleport back when attack ends)
        World currentWorld = store.getExternalData().getWorld();
        if (currentWorld == null) {
            ConfigUtil.debug("[PrismAttackEventHandler] early return: currentWorld == null");
            sendError(store, ref, "ui.prism_attack.current_world_null");
            return;
        }
        String returnWorldName = currentWorld.getName();
        if (returnWorldName == null) returnWorldName = "world";
        double returnX = 0, returnY = 0, returnZ = 0;
        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent != null && playerRefComponent.getTransform() != null) {
            returnX = playerRefComponent.getTransform().getPosition().getX();
            returnY = playerRefComponent.getTransform().getPosition().getY();
            returnZ = playerRefComponent.getTransform().getPosition().getZ();
        }

        // 7. Spawn assignment and DB insert (with return position)
        Optional<int[]> spawnOpt = PrismAttackService.getInstance().assignParticipantSlot(
                attack.getAttackId(), playerUuid, role, returnWorldName, returnX, returnY, returnZ);
        if (!spawnOpt.isPresent()) {
            ConfigUtil.debug("[PrismAttackEventHandler] early return: no slot or already joined");
            sendError(store, ref, "ui.prism_attack.no_slot_or_already_joined");
            return;
        }
        if ("PENDING_START".equals(attack.getStatus())) {
            logInfo("[PrismAttackEventHandler] before updateAttackStatusToActive: attackId=%s attackWorldName=%s status=%s",
                    attack.getAttackId(), attack.getAttackWorldName(), attack.getStatus());
            PrismAttackService.getInstance().updateAttackStatusToActive(attack.getAttackId());
            logInfo("[PrismAttackEventHandler] after updateAttackStatusToActive: attackId=%s", attack.getAttackId());
        }
        int[] spawn = spawnOpt.get();
        Transform spawnTransform = new Transform(spawn[0], spawn[1], spawn[2]);

        ConfigUtil.debug("[PrismAttackEventHandler] scheduling teleport for player=" + playerUuid + " to attack world " + attack.getAttackWorldName() + " at " + spawn[0] + "," + spawn[1] + "," + spawn[2]);
        Teleport teleport = Teleport.createForPlayer(attackWorld, spawnTransform);
        World finalCurrentWorld = currentWorld;
        finalCurrentWorld.execute(() -> {
            if (ref.isValid()) {
                store.addComponent(ref, Teleport.getComponentType(), teleport);
            }
        });
    }

    private void sendError(Store<EntityStore> store, Ref<EntityStore> ref, String messageKey) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            String msg = I18n.get(messageKey);
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(msg).color(Color.RED));
            }
        }
    }

    private static void logInfo(String format, Object... args) {
        if (Main.get() != null) {
            Main.get().getLogger().at(Level.INFO).log(String.format(format, args));
        }
    }
}
