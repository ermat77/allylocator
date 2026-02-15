package com.factioncore.prism;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of per-player "marked" prism IDs for map display.
 * Toggling a checkbox on the Faction Prisms page adds or removes a prism ID for that player.
 * Map marker display (showing markers on the world map) may be implemented later via a custom MarkerProvider.
 */
public final class PrismMarkerService {

    private static final PrismMarkerService INSTANCE = new PrismMarkerService();

    /** playerUuid -> set of prism IDs this player has marked for map display */
    private final Map<UUID, Set<UUID>> playerToMarkedPrisms = new ConcurrentHashMap<>();

    private PrismMarkerService() {}

    @Nonnull
    public static PrismMarkerService getInstance() {
        return INSTANCE;
    }

    /** Returns true if the player has marked this prism for map display. */
    public boolean isMarked(@Nonnull UUID playerUuid, @Nonnull UUID prismId) {
        Set<UUID> set = playerToMarkedPrisms.get(playerUuid);
        return set != null && set.contains(prismId);
    }

    /** Toggles the marked state for this player and prism. Returns the new state (true = marked). */
    public boolean toggleMarked(@Nonnull UUID playerUuid, @Nonnull UUID prismId) {
        Set<UUID> set = playerToMarkedPrisms.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
        if (set.contains(prismId)) {
            set.remove(prismId);
            return false;
        } else {
            set.add(prismId);
            return true;
        }
    }

    /** Returns an unmodifiable set of prism IDs marked by this player (may be empty). */
    @Nonnull
    public Set<UUID> getMarkedPrismIds(@Nonnull UUID playerUuid) {
        Set<UUID> set = playerToMarkedPrisms.get(playerUuid);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }
}
