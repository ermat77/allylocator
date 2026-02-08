package com.factioncore.util;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.UUID;

/**
 * Utility to resolve player display name by UUID.
 * Uses online player (PlayerRef) when available, otherwise falls back to {@link PlayerNameCache}
 * loaded from universe/players JSON at startup.
 */
public final class PlayerUtils {

    private static final String FALLBACK_NAME = "Unknown";

    private PlayerUtils() {}

    /**
     * Returns the display name for the given player UUID.
     * 1. If the player is online, returns {@link PlayerRef#getUsername()}.
     * 2. Otherwise returns the name from {@link PlayerNameCache} (universe/players JSON).
     * 3. Otherwise returns "Unknown".
     *
     * @param uuid Player UUID (may be null)
     * @return Display name, or "Unknown" if not found
     */
    public static String getPlayerName(UUID uuid) {
        if (uuid == null) {
            return FALLBACK_NAME;
        }
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                PlayerRef playerRef = universe.getPlayer(uuid);
                if (playerRef != null) {
                    String name = playerRef.getUsername();
                    if (name != null && !name.isEmpty()) {
                        PlayerNameCache.put(uuid, name);
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            ConfigUtil.debug("PlayerUtils.getPlayerName: " + e.getMessage());
        }
        String cached = PlayerNameCache.get(uuid);
        return cached != null && !cached.isEmpty() ? cached : FALLBACK_NAME;
    }

    /**
     * Same as {@link #getPlayerName(UUID)} but uses the provided Universe when non-null.
     * Call this when you already have a Universe reference to avoid calling Universe.get().
     *
     * @param universe Universe instance (may be null)
     * @param uuid     Player UUID (may be null)
     * @return Display name, or "Unknown" if not found
     */
    public static String getPlayerName(Universe universe, UUID uuid) {
        if (uuid == null) {
            return FALLBACK_NAME;
        }
        if (universe != null) {
            try {
                PlayerRef playerRef = universe.getPlayer(uuid);
                if (playerRef != null) {
                    String name = playerRef.getUsername();
                    if (name != null && !name.isEmpty()) {
                        PlayerNameCache.put(uuid, name);
                        return name;
                    }
                }
            } catch (Exception e) {
                ConfigUtil.debug("PlayerUtils.getPlayerName(universe,uuid): " + e.getMessage());
            }
        }
        String cached = PlayerNameCache.get(uuid);
        return cached != null && !cached.isEmpty() ? cached : FALLBACK_NAME;
    }
}
