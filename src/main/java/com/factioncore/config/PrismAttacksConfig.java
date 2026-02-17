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
 *     { "prefabId": "...", "attackersBlockId": "...", "defendersBlockId": "..." }
 *   ]
 * }
 * </pre>
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
        list.add(new PrefabEntry("prefab_prism.prefab.json", "com.factioncore:Red_Crystal", "com.factioncore:Blue_Crystal"));
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
     * One prefab entry: server prefab key and block ids for attacker/defender spawn blocks.
     */
    public static final class PrefabEntry {
        private String prefabId = "prefab_prism.prefab.json";
        private String attackersBlockId = "com.factioncore:Red_Crystal";
        private String defendersBlockId = "com.factioncore:Blue_Crystal";

        public PrefabEntry() {}

        public PrefabEntry(String prefabId, String attackersBlockId, String defendersBlockId) {
            this.prefabId = prefabId != null ? prefabId : "";
            this.attackersBlockId = attackersBlockId != null ? attackersBlockId : "";
            this.defendersBlockId = defendersBlockId != null ? defendersBlockId : "";
        }

        public String getPrefabId() { return prefabId; }
        public String getAttackersBlockId() { return attackersBlockId; }
        public String getDefendersBlockId() { return defendersBlockId; }
    }
}
