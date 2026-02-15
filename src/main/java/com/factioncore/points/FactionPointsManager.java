package com.factioncore.points;

import com.factioncore.faction.FactionManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for Faction Points (FP). Holds in-memory map factionId -> total FP.
 * Persists via DatabaseManager; loads on startup (or on first use).
 * Callers pass eventType and pointsToAdd; the manager does not use other parameters.
 */
public final class FactionPointsManager {

    private static final FactionPointsManager INSTANCE = new FactionPointsManager();

    private final Map<UUID, Integer> factionPoints = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Void> initializationFuture;

    private FactionPointsManager() {}

    @Nonnull
    public static FactionPointsManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes from database (load faction_points). Call once after FactionManager is ready.
     */
    public CompletableFuture<Void> initializeAsync() {
        if (initializationFuture != null) {
            return initializationFuture;
        }
        initializationFuture = FactionManager.getInstance().getDatabaseManager()
                .loadFactionPointsAsync()
                .thenAccept(map -> {
                    factionPoints.clear();
                    factionPoints.putAll(map);
                });
        return initializationFuture;
    }

    private void ensureLoaded() {
        if (initializationFuture == null) {
            initializeAsync();
        }
        initializationFuture.join();
    }

    /**
     * Awards points to a faction. Caller computes points (e.g. 1 per minute for harvest).
     *
     * @param factionId   faction to award points to (e.g. prism owner faction)
     * @param eventType  e.g. "harvestPrism" (for logging/future use)
     * @param pointsToAdd number of FP to add; must be &gt; 0
     * @return the number of points awarded, or 0 if pointsToAdd &lt;= 0
     */
    public int awardPoints(@Nonnull UUID factionId, @Nonnull String eventType, int pointsToAdd) {
        ensureLoaded();
        if (pointsToAdd <= 0) {
            return 0;
        }
        int newTotal = factionPoints.merge(factionId, pointsToAdd, Integer::sum);
        FactionManager.getInstance().getDatabaseManager().saveFactionPoints(factionId, newTotal);
        return pointsToAdd;
    }

    /**
     * Returns the total FP for the faction, or 0 if not found.
     */
    public int getPoints(@Nonnull UUID factionId) {
        ensureLoaded();
        return factionPoints.getOrDefault(factionId, 0);
    }

    /**
     * Returns the total FP for the faction, or the default value if not found.
     */
    public int getPointsOrDefault(@Nonnull UUID factionId, int defaultValue) {
        ensureLoaded();
        return factionPoints.getOrDefault(factionId, defaultValue);
    }
}
