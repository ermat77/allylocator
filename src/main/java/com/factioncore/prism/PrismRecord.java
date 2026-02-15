package com.factioncore.prism;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Immutable record for a prism at a world position.
 * Stored in DB and in PrismManager in-memory cache.
 * Score (1 point per minute) is computed from created_at when the prism is interacted with.
 * <p>
 * Ownership for FP is the <strong>faction</strong> ({@link #factionId}), not the placer.
 * The faction ID is fixed at placement time; if the placer leaves the faction and another
 * member of that same faction harvests the prism, points still go to that original faction.
 */
public final class PrismRecord {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final UUID id;
    /** Faction that owns this prism (at placement time). FP from harvesting go to this faction. */
    private final UUID factionId;
    /** Player who placed the prism (for audit); not used for FP attribution. */
    private final UUID playerUuid;
    /** Epoch millis when the prism was placed. */
    private final long createdAtMs;

    public PrismRecord(@Nonnull String worldName, int x, int y, int z,
                       @Nonnull UUID id,
                       @Nonnull UUID factionId, @Nonnull UUID playerUuid,
                       long createdAtMs) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.id = id;
        this.factionId = factionId;
        this.playerUuid = playerUuid;
        this.createdAtMs = createdAtMs;
    }

    @Nonnull
    public String getWorldName() { return worldName; }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    @Nonnull
    public UUID getId() { return id; }

    @Nonnull
    public UUID getFactionId() { return factionId; }

    @Nonnull
    public UUID getPlayerUuid() { return playerUuid; }

    /** Creation time in epoch milliseconds. */
    public long getCreatedAtMs() { return createdAtMs; }

    /**
     * Score in minutes (1 point per minute since creation).
     * @param nowMs current time in epoch milliseconds
     */
    public int getScoreMinutes(long nowMs) {
        long elapsedMs = nowMs - createdAtMs;
        if (elapsedMs < 0) return 0;
        return (int) (elapsedMs / 60_000L);
    }

    /** Chunk X from block X (blockX >> 4). */
    public int getChunkX() { return x >> 4; }

    /** Chunk Z from block Z (blockZ >> 4). */
    public int getChunkZ() { return z >> 4; }
}
