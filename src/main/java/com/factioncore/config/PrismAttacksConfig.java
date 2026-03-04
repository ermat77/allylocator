package com.factioncore.config;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for prism attacks: timer (seconds players can join attack/defense) and list of prefabs.
 * Stored in prism_attacks.json with structure:
 * <pre>
 * {
 *   "attackerTimeSeconds": 60,
 *   "timeBeforeAutoDestruction": 600,
 *   "prefabs": [
 *     { "prefabId": "...", "attackersPositions": [{ "x", "y", "z" }, ...], "defendersPositions": [...] }
 *   ]
 * }
 * </pre>
 * If attackersPositions/defendersPositions are missing or empty, block IDs (attackersBlockId, defendersBlockId)
 * are used as fallback to scan the prefab for spawn positions.
 *
 * <p>Prefab IDs are resolved via the server's {@link com.hypixel.hytale.server.core.prefab.PrefabStore}:
 * each prefabId is a server prefab key (e.g. {@code prefab_prism.prefab.json}) looked up with
 * {@code PrefabStore.get().getServerPrefab(prefabKey)} when pasting the attack arena.
 */
public class PrismAttacksConfig {

    private int attackerTimeSeconds = 60;
    /** Seconds after attack start before the attack world is auto-removed (world destruction timer). */
    private int timeBeforeAutoDestruction = 600;
    private List<PrefabEntry> prefabs = defaultPrefabsList();

    private static List<PrefabEntry> defaultPrefabsList() {
        List<PrefabEntry> list = new ArrayList<>();
        List<PositionEntry> defaultAttackers = new ArrayList<>();
        defaultAttackers.add(new PositionEntry(0, 1, 0));
        defaultAttackers.add(new PositionEntry(1, 1, 0));
        List<PositionEntry> defaultDefenders = new ArrayList<>();
        defaultDefenders.add(new PositionEntry(0, 2, 0));
        defaultDefenders.add(new PositionEntry(1, 2, 0));
        list.add(new PrefabEntry("prefab_prism.prefab.json", "com.factioncore:Red_Crystal", "com.factioncore:Blue_Crystal", defaultAttackers, defaultDefenders));
        return list;
    }

    public PrismAttacksConfig() {}

    /** Seconds players can join the attack before combat starts (join window). */
    public int getAttackerTimeSeconds() {
        return attackerTimeSeconds > 0 ? attackerTimeSeconds : 60;
    }

    /** Seconds after attack start before the attack world is auto-removed. Used only for world destruction timing. */
    public int getTimeBeforeAutoDestruction() {
        return timeBeforeAutoDestruction > 0 ? timeBeforeAutoDestruction : 600;
    }

    /** @deprecated Use {@link #getAttackerTimeSeconds()}. Kept for compatibility. */
    @Deprecated
    public int getAttackTimerSeconds() {
        return getAttackerTimeSeconds();
    }

    /**
     * Returns the list of prefab entries (prefab id, attackers block id, defenders block id).
     */
    @Nonnull
    public List<PrefabEntry> getPrefabs() {
        if (prefabs == null || prefabs.isEmpty()) {
            return Collections.unmodifiableList(defaultPrefabsList());
        }
        return Collections.unmodifiableList(prefabs);
    }

    /**
     * One coordinate entry for config (Gson: { "x": 0, "y": 1, "z": 0 }).
     */
    public static final class PositionEntry {
        private int x;
        private int y;
        private int z;

        public PositionEntry() {}

        public PositionEntry(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
    }

    /**
     * One prefab entry: server prefab key, optional block ids (fallback), and optional spawn position lists.
     */
    public static final class PrefabEntry {
        private String prefabId = "prefab_prism.prefab.json";
        private String attackersBlockId = "com.factioncore:Red_Crystal";
        private String defendersBlockId = "com.factioncore:Blue_Crystal";
        private List<PositionEntry> attackersPositions;
        private List<PositionEntry> defendersPositions;

        public PrefabEntry() {}

        public PrefabEntry(String prefabId, String attackersBlockId, String defendersBlockId) {
            this.prefabId = prefabId != null ? prefabId : "";
            this.attackersBlockId = attackersBlockId != null ? attackersBlockId : "";
            this.defendersBlockId = defendersBlockId != null ? defendersBlockId : "";
        }

        public PrefabEntry(String prefabId, String attackersBlockId, String defendersBlockId,
                          List<PositionEntry> attackersPositions, List<PositionEntry> defendersPositions) {
            this.prefabId = prefabId != null ? prefabId : "";
            this.attackersBlockId = attackersBlockId != null ? attackersBlockId : "";
            this.defendersBlockId = defendersBlockId != null ? defendersBlockId : "";
            this.attackersPositions = attackersPositions != null ? new ArrayList<>(attackersPositions) : null;
            this.defendersPositions = defendersPositions != null ? new ArrayList<>(defendersPositions) : null;
        }

        public String getPrefabId() { return prefabId; }
        public String getAttackersBlockId() { return attackersBlockId; }
        public String getDefendersBlockId() { return defendersBlockId; }

        /** Attackers spawn positions from config; empty if not specified (use block-ID fallback). */
        @Nonnull
        public List<PositionEntry> getAttackersPositions() {
            return attackersPositions != null ? Collections.unmodifiableList(attackersPositions) : Collections.emptyList();
        }

        /** Defenders spawn positions from config; empty if not specified (use block-ID fallback). */
        @Nonnull
        public List<PositionEntry> getDefendersPositions() {
            return defendersPositions != null ? Collections.unmodifiableList(defendersPositions) : Collections.emptyList();
        }
    }
}
