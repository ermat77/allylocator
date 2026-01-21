package com.factioncore.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * General configuration for the plugin.
 * Uses Hytale's recommended CODEC pattern for serialization.
 * Manages debug settings and faction name length.
 * 
 * Note: This plugin requires LuckPerms to be installed. All permissions are managed
 * exclusively through LuckPerms.
 */
public class GeneralConfig {
    
    public static final BuilderCodec<GeneralConfig> CODEC;
    
    private boolean debug = false;
    private boolean verboseDebug = false;
    private int maxFactionNameLength = 20; // Maximum length for faction names
    
    static {
        CODEC = BuilderCodec.builder(GeneralConfig.class, GeneralConfig::new)
            .append(new KeyedCodec<Boolean>("Debug", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.debug = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.debug))
            .add()
            .append(new KeyedCodec<Boolean>("VerboseDebug", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.verboseDebug = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.verboseDebug))
            .add()
            .append(new KeyedCodec<Integer>("MaxFactionNameLength", Codec.INTEGER),
                (config, value, extraInfo) -> {
                    // Validate: must be positive, reasonable max (e.g., 100)
                    int length = value.intValue();
                    if (length > 0 && length <= 100) {
                        config.maxFactionNameLength = length;
                    } else {
                        // Invalid value, use default
                        config.maxFactionNameLength = 20;
                    }
                },
                (config, extraInfo) -> Integer.valueOf(config.maxFactionNameLength))
            .add()
            .build();
    }
    
    public GeneralConfig() {
        // Default constructor for CODEC
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    public boolean isVerboseDebug() {
        return verboseDebug;
    }
    
    public void setVerboseDebug(boolean verboseDebug) {
        this.verboseDebug = verboseDebug;
    }
    
    /**
     * Gets the maximum allowed length for faction names.
     * 
     * @return The maximum faction name length in characters
     */
    public int getMaxFactionNameLength() {
        return maxFactionNameLength;
    }
    
    /**
     * Sets the maximum allowed length for faction names.
     * 
     * @param maxFactionNameLength Must be between 1 and 100 (inclusive)
     */
    public void setMaxFactionNameLength(int maxFactionNameLength) {
        if (maxFactionNameLength > 0 && maxFactionNameLength <= 100) {
            this.maxFactionNameLength = maxFactionNameLength;
        } else {
            // Invalid value, use default
            this.maxFactionNameLength = 20;
        }
    }
}
