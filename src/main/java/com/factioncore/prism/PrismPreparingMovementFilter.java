package com.factioncore.prism;

import com.factioncore.Main;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Registers an inbound ClientMovement filter that cancels position/velocity movement
 * for players in a prism attack whose status is PREPARING (join countdown phase).
 * Head rotation (bodyOrientation, lookOrientation) and teleportAck are left intact.
 */
public final class PrismPreparingMovementFilter {

    private static final String LOG_PREFIX = "[PrismPreparingMovementFilter] ";

    private PrismPreparingMovementFilter() {}

    /**
     * Registers the filter with PacketAdapters. Call once during plugin init (e.g. from Main.setup()).
     */
    public static void register() {
        PacketAdapters.registerInbound(PrismPreparingMovementFilter::test);
    }

    /**
     * PlayerPacketFilter: return true to block packet, false to allow (we always allow after optionally modifying).
     */
    private static boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        // if (!(packet instanceof ClientMovement)) {
        //     return false;
        // }
        // Ref<EntityStore> ref = playerRef.getReference();
        // if (ref == null || !ref.isValid()) {
        //     log(Level.FINE, "playerRef.getReference() null or invalid for player %s", playerRef.getUuid());
        //     return false;
        // }
        // Store<EntityStore> store = ref.getStore();
        // World world = store.getExternalData().getWorld();
        // if (world == null) {
        //     log(Level.FINE, "world null for player %s (store.getExternalData().getWorld())", playerRef.getUuid());
        //     return false;
        // }
        // String worldName = world.getName();
        // PrismAttackRecord attack = PrismAttackService.getInstance().getActiveAttackByWorldName(worldName);
        // if (attack == null) {
        //     if (PrismAttackService.isAttackWorldName(worldName)) {
        //         log(Level.INFO, "player %s in attack world %s but no PREPARING attack (status may be IN_PROGRESS or not in cache)",
        //                 playerRef.getUuid(), worldName);
        //     } else {
        //         log(Level.FINE, "no PREPARING attack for worldName=%s player=%s (not an attack world)",
        //                 worldName, playerRef.getUuid());
        //     }
        //     return false;
        // }
        // ClientMovement movement = (ClientMovement) packet;
        // movement.movementStates = null;
        // movement.relativePosition = null;
        // movement.absolutePosition = null;
        // movement.wishMovement = null;
        // movement.velocity = null;
        // movement.riderMovementStates = null;
        // log(Level.INFO, "stripped movement for player %s in PREPARING attack world %s (attackId=%s)",
        //         playerRef.getUuid(), worldName, attack.getAttackId());
        return false;
    }

    private static void log(Level level, String format, Object... args) {
        Main.get().getLogger().at(level).log(LOG_PREFIX + String.format(format, args));
    }
}
