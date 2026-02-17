package com.factioncore.prism;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Immutable record for a prism attack session (one row in prism_attacks).
 * Used for DB persistence and startup recovery.
 */
public final class PrismAttackRecord {

    private final UUID attackId;
    private final UUID prismId;
    @Nonnull private final String attackWorldName;
    @Nonnull private final UUID defenderFactionId;
    @Nonnull private final UUID attackerFactionId;
    @Nonnull private final String status;
    private final long startedAtMs;
    /** Combat start time (when status switches to IN_PROGRESS). */
    private final long endsAtMs;
    /** When the attack world is auto-removed and resolution runs. */
    private final long destructionAtMs;
    @Nullable private final String prefabId;
    @Nullable private final String attackersBlockId;
    @Nullable private final String defendersBlockId;
    @Nullable private final String defenderPositionsJson;
    @Nullable private final String attackerPositionsJson;

    public PrismAttackRecord(
            @Nonnull UUID attackId,
            @Nonnull UUID prismId,
            @Nonnull String attackWorldName,
            @Nonnull UUID defenderFactionId,
            @Nonnull UUID attackerFactionId,
            @Nonnull String status,
            long startedAtMs,
            long endsAtMs,
            long destructionAtMs,
            @Nullable String prefabId,
            @Nullable String attackersBlockId,
            @Nullable String defendersBlockId,
            @Nullable String defenderPositionsJson,
            @Nullable String attackerPositionsJson
    ) {
        this.attackId = attackId;
        this.prismId = prismId;
        this.attackWorldName = attackWorldName;
        this.defenderFactionId = defenderFactionId;
        this.attackerFactionId = attackerFactionId;
        this.status = status != null ? status : "PENDING_START";
        this.startedAtMs = startedAtMs;
        this.endsAtMs = endsAtMs;
        this.destructionAtMs = destructionAtMs;
        this.prefabId = prefabId;
        this.attackersBlockId = attackersBlockId;
        this.defendersBlockId = defendersBlockId;
        this.defenderPositionsJson = defenderPositionsJson;
        this.attackerPositionsJson = attackerPositionsJson;
    }

    @Nonnull public UUID getAttackId() { return attackId; }
    @Nonnull public UUID getPrismId() { return prismId; }
    @Nonnull public String getAttackWorldName() { return attackWorldName; }
    @Nonnull public UUID getDefenderFactionId() { return defenderFactionId; }
    @Nonnull public UUID getAttackerFactionId() { return attackerFactionId; }
    @Nonnull public String getStatus() { return status; }
    public long getStartedAtMs() { return startedAtMs; }
    /** Combat start time (when status becomes IN_PROGRESS). */
    public long getEndsAtMs() { return endsAtMs; }
    /** When the attack world is auto-removed. */
    public long getDestructionAtMs() { return destructionAtMs; }
    @Nullable public String getPrefabId() { return prefabId; }
    @Nullable public String getAttackersBlockId() { return attackersBlockId; }
    @Nullable public String getDefendersBlockId() { return defendersBlockId; }
    @Nullable public String getDefenderPositionsJson() { return defenderPositionsJson; }
    @Nullable public String getAttackerPositionsJson() { return attackerPositionsJson; }
}
