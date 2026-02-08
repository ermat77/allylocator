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
    private boolean renderClaimNamesOnWorldMap = true;
    /** Name shown in title when player is in unclaimed chunk (e.g. "Wilderness"). */
    private String wildernessName = "Wilderness";
    /** Top line of the claim title shown above hotbar (e.g. "Claim"). */
    private String claimTitleTopLine = "Claim";
    /** Language code for UI and chat (e.g. "en", "fr"). */
    private String language = "en";

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
            .append(new KeyedCodec<Boolean>("RenderClaimNamesOnWorldMap", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.renderClaimNamesOnWorldMap = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.renderClaimNamesOnWorldMap))
            .add()
            .append(new KeyedCodec<String>("WildernessName", Codec.STRING),
                (config, value, extraInfo) -> config.wildernessName = value != null ? value : "Wilderness",
                (config, extraInfo) -> config.wildernessName != null ? config.wildernessName : "Wilderness")
            .add()
            .append(new KeyedCodec<String>("ClaimTitleTopLine", Codec.STRING),
                (config, value, extraInfo) -> config.claimTitleTopLine = value != null ? value : "Claim",
                (config, extraInfo) -> config.claimTitleTopLine != null ? config.claimTitleTopLine : "Claim")
            .add()
            .append(new KeyedCodec<String>("Language", Codec.STRING),
                (config, value, extraInfo) -> {
                    if (value != null) {
                        String normalized = value.trim().toLowerCase();
                        if ("en".equals(normalized) || "fr".equals(normalized)) {
                            config.language = normalized;
                        }
                    }
                },
                (config, extraInfo) -> config.language != null ? config.language : "en")
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

    /**
     * Whether to render faction claim names on the world map.
     *
     * @return true if claim names should be drawn on the map
     */
    public boolean isRenderClaimNamesOnWorldMap() {
        return renderClaimNamesOnWorldMap;
    }

    public void setRenderClaimNamesOnWorldMap(boolean renderClaimNamesOnWorldMap) {
        this.renderClaimNamesOnWorldMap = renderClaimNamesOnWorldMap;
    }

    public String getWildernessName() {
        return wildernessName != null ? wildernessName : "Wilderness";
    }

    public void setWildernessName(String wildernessName) {
        this.wildernessName = wildernessName != null ? wildernessName : "Wilderness";
    }

    public String getClaimTitleTopLine() {
        return claimTitleTopLine != null ? claimTitleTopLine : "Claim";
    }

    public void setClaimTitleTopLine(String claimTitleTopLine) {
        this.claimTitleTopLine = claimTitleTopLine != null ? claimTitleTopLine : "Claim";
    }

    /**
     * Gets the language code for UI and chat (e.g. "en", "fr").
     *
     * @return The language code
     */
    public String getLanguage() {
        return language != null ? language : "en";
    }

    /**
     * Sets the language code. Only "en" and "fr" are supported; others are ignored.
     *
     * @param language The language code (normalized to lowercase)
     */
    public void setLanguage(String language) {
        if (language != null) {
            String normalized = language.trim().toLowerCase();
            if ("en".equals(normalized) || "fr".equals(normalized)) {
                this.language = normalized;
            }
        }
    }
}
