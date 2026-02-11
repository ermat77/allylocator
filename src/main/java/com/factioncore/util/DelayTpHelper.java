package com.factioncore.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Helper to call DelayTp plugin API via reflection (no compile-time dependency on DelayTp).
 */
public final class DelayTpHelper {

    private DelayTpHelper() {
    }

    /**
     * Calls requestDelayedTeleport on the DelayTp plugin instance via reflection.
     *
     * @param delayTpPlugin The cached DelayTp plugin instance (Main.get().getDelayTpApi()), or null
     * @param reason        Reason key (e.g. "fhome")
     * @param playerRef     Player entity ref
     * @param store         Entity store (player's world)
     * @param targetWorld   Destination world
     * @param transform     Destination position
     * @return true if delayed teleport was scheduled, false if plugin null or call failed
     */
    public static boolean requestDelayedTeleport(@Nullable Object delayTpPlugin,
                                                 String reason,
                                                 Ref<EntityStore> playerRef,
                                                 Store<EntityStore> store,
                                                 World targetWorld,
                                                 Transform transform) {
        if (delayTpPlugin == null) {
            ConfigUtil.debug("DelayTpHelper: delayTpPlugin is null, skipping delayed teleport");
            return false;
        }
        ConfigUtil.debug("DelayTpHelper: calling requestDelayedTeleport reason=" + reason
                + " targetWorld=" + (targetWorld != null ? targetWorld.getName() : "null")
                + " refValid=" + (playerRef != null && playerRef.isValid()));
        try {
            Method m = delayTpPlugin.getClass().getMethod("requestDelayedTeleport",
                    String.class, Ref.class, Store.class, World.class, Transform.class);
            Object result = m.invoke(delayTpPlugin, reason, playerRef, store, targetWorld, transform);
            boolean ok = Boolean.TRUE.equals(result);
            ConfigUtil.debug("DelayTpHelper: requestDelayedTeleport returned " + result + " (delayed=" + ok + ")");
            return ok;
        } catch (Exception e) {
            ConfigUtil.debug("DelayTpHelper: requestDelayedTeleport failed", e);
            return false;
        }
    }
}
