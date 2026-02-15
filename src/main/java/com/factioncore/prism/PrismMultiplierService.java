package com.factioncore.prism;

import com.factioncore.Main;
import com.factioncore.config.PrismMultiplierConfig;
import com.factioncore.util.ConfigUtil;

import javax.annotation.Nonnull;

/**
 * Computes prism effect multiplier from chunk position using PrismMultiplierConfig.
 * Distance from spawn is Chebyshev: max(|chunkX - spawnChunkX|, |chunkZ - spawnChunkZ|).
 * Returns the multiplier of the first band where distance <= maxChunkDistance (smallest such maxChunkDistance wins).
 */
public final class PrismMultiplierService {

    private static final PrismMultiplierService INSTANCE = new PrismMultiplierService();
    private static final int DEFAULT_MULTIPLIER = 1;

    private PrismMultiplierService() {}

    @Nonnull
    public static PrismMultiplierService getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the effect multiplier for the given chunk coordinates.
     * Uses spawn and bands from prism_multiplier_config; if config is null or no band matches, returns DEFAULT_MULTIPLIER.
     */
    public int getMultiplier(int chunkX, int chunkZ) {
        PrismMultiplierConfig config = getConfig();
        if (config == null) {
            ConfigUtil.debug("[PrismMultiplier] config is null, returning default multiplier " + DEFAULT_MULTIPLIER);
            return DEFAULT_MULTIPLIER;
        }
        int spawnX = config.getSpawnChunkX();
        int spawnZ = config.getSpawnChunkZ();
        int distance = Math.max(Math.abs(chunkX - spawnX), Math.abs(chunkZ - spawnZ));
        int multiplier = config.getMultiplierForDistance(distance);
        ConfigUtil.debug("[PrismMultiplier] chunkX=" + chunkX + " chunkZ=" + chunkZ
                + " spawnChunk=(" + spawnX + "," + spawnZ + ")"
                + " distance=" + distance
                + " multiplier=" + multiplier
                + " config: spawnChunkX=" + spawnX + " spawnChunkZ=" + spawnZ
                + " maxChunkDistances=" + config.getMaxChunkDistances()
                + " multipliers=" + config.getMultipliers());
        return multiplier;
    }

    private PrismMultiplierConfig getConfig() {
        Main main = Main.get();
        if (main == null) return null;
        com.hypixel.hytale.server.core.util.Config<PrismMultiplierConfig> configWrapper = main.getPrismMultiplierConfig();
        return configWrapper != null ? configWrapper.get() : null;
    }
}
