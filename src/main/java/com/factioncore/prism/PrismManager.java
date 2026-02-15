package com.factioncore.prism;

import com.factioncore.faction.FactionManager;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for prism data. Holds in-memory cache keyed by (world, x, y, z).
 * Persists to DatabaseManager; loads on startup.
 */
public final class PrismManager {

    private static final PrismManager INSTANCE = new PrismManager();

    private final Map<String, PrismRecord> positionToPrism = new ConcurrentHashMap<>();
    private final Map<UUID, PrismRecord> idToPrism = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Void> initializationFuture;

    private PrismManager() {}

    /**
     * Gets the singleton instance. Call after FactionManager.getInstance() so DB is available.
     */
    @Nonnull
    public static PrismManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes from database (load prisms). Call once after FactionManager is ready.
     */
    public CompletableFuture<Void> initializeAsync() {
        if (initializationFuture != null) {
            return initializationFuture;
        }
        initializationFuture = FactionManager.getInstance().getDatabaseManager()
                .loadPrismsAsync()
                .thenAccept(list -> {
                    positionToPrism.clear();
                    idToPrism.clear();
                    for (PrismRecord r : list) {
                        String posKey = key(r.getWorldName(), r.getX(), r.getY(), r.getZ());
                        positionToPrism.put(posKey, r);
                        idToPrism.put(r.getId(), r);
                    }
                });
        return initializationFuture;
    }

    private static String key(String worldName, int x, int y, int z) {
        return worldName + "|" + x + "|" + y + "|" + z;
    }

    @Nullable
    public PrismRecord getByPosition(@Nonnull String worldName, @Nonnull Vector3i position) {
        return positionToPrism.get(key(worldName, position.getX(), position.getY(), position.getZ()));
    }

    @Nullable
    public PrismRecord getById(@Nonnull UUID prismId) {
        return idToPrism.get(prismId);
    }

    /**
     * Returns all prisms owned by the given faction. Returns a new list snapshot.
     */
    @Nonnull
    public List<PrismRecord> getByFactionId(@Nonnull UUID factionId) {
        List<PrismRecord> list = new ArrayList<>();
        for (PrismRecord r : idToPrism.values()) {
            if (r.getFactionId().equals(factionId)) {
                list.add(r);
            }
        }
        return list;
    }

    /**
     * Returns true if the in-memory map contains any prism in the given chunk.
     */
    public boolean hasPrismInChunk(@Nonnull String worldName, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        for (PrismRecord r : positionToPrism.values()) {
            if (!worldName.equals(r.getWorldName())) continue;
            if (r.getX() >= minX && r.getX() <= maxX && r.getZ() >= minZ && r.getZ() <= maxZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts or replaces a prism at the given position. Generates prism uuid and sets created_at = now.
     * Score (1 point per minute) is computed from created_at when the prism is interacted with.
     */
    public void put(@Nonnull String worldName, @Nonnull Vector3i position,
                    @Nonnull UUID factionId, @Nonnull UUID playerUuid) {
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        UUID prismId = UUID.randomUUID();
        long createdAtMs = System.currentTimeMillis();
        PrismRecord record = new PrismRecord(worldName, x, y, z, prismId, factionId, playerUuid, createdAtMs);
        String posKey = key(worldName, x, y, z);
        positionToPrism.put(posKey, record);
        idToPrism.put(prismId, record);
        FactionManager.getInstance().getDatabaseManager()
                .insertOrReplacePrism(worldName, x, y, z, prismId, factionId, playerUuid, createdAtMs);
    }

    /**
     * Removes prism at the given position. Queues DB delete.
     */
    public void remove(@Nonnull String worldName, @Nonnull Vector3i position) {
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        PrismRecord removed = positionToPrism.remove(key(worldName, x, y, z));
        if (removed != null) {
            idToPrism.remove(removed.getId());
        }
        FactionManager.getInstance().getDatabaseManager().deletePrism(worldName, x, y, z);
    }
}
