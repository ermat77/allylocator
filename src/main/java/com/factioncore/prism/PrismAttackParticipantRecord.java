package com.factioncore.prism;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Immutable record for a player participating in a prism attack (one row in prism_attack_participants).
 */
public final class PrismAttackParticipantRecord {

    private final UUID attackId;
    private final UUID playerUuid;
    /** "attacker" or "defender" */
    @Nonnull private final String role;
    private final long joinedAtMs;
    private final int spawnX;
    private final int spawnY;
    private final int spawnZ;
    /** World name the player was in before joining the attack (for teleport back). */
    @Nullable private final String returnWorldName;
    private final double returnX;
    private final double returnY;
    private final double returnZ;

    public PrismAttackParticipantRecord(
            @Nonnull UUID attackId,
            @Nonnull UUID playerUuid,
            @Nonnull String role,
            long joinedAtMs,
            int spawnX, int spawnY, int spawnZ,
            @Nullable String returnWorldName,
            double returnX, double returnY, double returnZ
    ) {
        this.attackId = attackId;
        this.playerUuid = playerUuid;
        this.role = role != null ? role : "attacker";
        this.joinedAtMs = joinedAtMs;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.returnWorldName = returnWorldName;
        this.returnX = returnX;
        this.returnY = returnY;
        this.returnZ = returnZ;
    }

    @Nonnull public UUID getAttackId() { return attackId; }
    @Nonnull public UUID getPlayerUuid() { return playerUuid; }
    @Nonnull public String getRole() { return role; }
    public long getJoinedAtMs() { return joinedAtMs; }
    public int getSpawnX() { return spawnX; }
    public int getSpawnY() { return spawnY; }
    public int getSpawnZ() { return spawnZ; }
    @Nullable public String getReturnWorldName() { return returnWorldName; }
    public double getReturnX() { return returnX; }
    public double getReturnY() { return returnY; }
    public double getReturnZ() { return returnZ; }
}
