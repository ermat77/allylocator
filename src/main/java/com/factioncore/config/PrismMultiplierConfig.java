package com.factioncore.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for prism effect multiplier by chunk distance from spawn.
 * Spawn chunk and bands (maxChunkDistance → multiplier) are stored in prism_multiplier_config.json.
 * Distance is Chebyshev: max(|chunkX - spawnChunkX|, |chunkZ - spawnChunkZ|).
 * First matching band (smallest maxChunkDistance such that distance <= maxChunkDistance) wins.
 * Bands are stored as comma-separated strings: MaxChunkDistances and Multipliers (e.g. "5,10,15,20,25" and "50,10,5,2,1").
 */
public class PrismMultiplierConfig {

    public static final BuilderCodec<PrismMultiplierConfig> CODEC;

    static {
        CODEC = BuilderCodec.builder(PrismMultiplierConfig.class, PrismMultiplierConfig::new)
            .append(new KeyedCodec<>("SpawnChunkX", Codec.INTEGER),
                (config, value, extraInfo) -> config.spawnChunkX = value != null ? value.intValue() : 0,
                (config, extraInfo) -> Integer.valueOf(config.spawnChunkX))
            .add()
            .append(new KeyedCodec<>("SpawnChunkZ", Codec.INTEGER),
                (config, value, extraInfo) -> config.spawnChunkZ = value != null ? value.intValue() : 0,
                (config, extraInfo) -> Integer.valueOf(config.spawnChunkZ))
            .add()
            .append(new KeyedCodec<>("MaxChunkDistances", Codec.STRING),
                (config, value, extraInfo) -> config.maxChunkDistancesStr = value != null ? value : "5,10,15,20,25",
                (config, extraInfo) -> config.maxChunkDistancesStr != null ? config.maxChunkDistancesStr : "5,10,15,20,25")
            .add()
            .append(new KeyedCodec<>("Multipliers", Codec.STRING),
                (config, value, extraInfo) -> config.multipliersStr = value != null ? value : "50,10,5,2,1",
                (config, extraInfo) -> config.multipliersStr != null ? config.multipliersStr : "50,10,5,2,1")
            .add()
            .build();
    }

    private int spawnChunkX = 0;
    private int spawnChunkZ = 0;
    private String maxChunkDistancesStr = "5,10,15,20,25";
    private String multipliersStr = "50,10,5,2,1";

    public PrismMultiplierConfig() {}

    private static List<Integer> parseIntList(String s) {
        List<Integer> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) {
            try {
                out.add(Integer.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public int getSpawnChunkX() { return spawnChunkX; }
    public int getSpawnChunkZ() { return spawnChunkZ; }
    public List<Integer> getMaxChunkDistances() { return parseIntList(maxChunkDistancesStr); }
    public List<Integer> getMultipliers() { return parseIntList(multipliersStr); }

    /**
     * Returns the multiplier for the given chunk distance (Chebyshev from spawn).
     * Finds the band with smallest maxChunkDistance such that distance <= maxChunkDistance.
     */
    public int getMultiplierForDistance(int distance) {
        List<Integer> maxDists = getMaxChunkDistances();
        List<Integer> mults = getMultipliers();
        if (maxDists.isEmpty() || mults.size() != maxDists.size()) return 1;
        int bestMult = 1;
        int bestMax = Integer.MAX_VALUE;
        for (int i = 0; i < maxDists.size(); i++) {
            int maxD = maxDists.get(i).intValue();
            if (distance <= maxD && maxD < bestMax) {
                bestMax = maxD;
                bestMult = i < mults.size() ? mults.get(i).intValue() : 1;
            }
        }
        return bestMult;
    }
}
