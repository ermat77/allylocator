package com.factioncore.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for chunk claiming system.
 * Uses Hytale's recommended CODEC pattern for serialization.
 * Stored separately from main config.json for better organization.
 */
public class ClaimConfig {
    
    public static final BuilderCodec<ClaimConfig> CODEC;
    
    private int defaultFactionClaimsAmount = 25;
    private boolean defaultFactionBlockPlaceEnabled = false;
    private boolean defaultFactionBlockBreakEnabled = false;
    private boolean defaultFactionBlockInteractEnabled = false;
    private boolean defaultFactionPVPEnabled = false;
    private String[] worldNameBlacklistForClaiming = new String[0];
    private boolean creativeModeBypassProtection = false;
    
    static {
        CODEC = BuilderCodec.builder(ClaimConfig.class, ClaimConfig::new)
            .append(new KeyedCodec<Integer>("DefaultFactionClaimsAmount", Codec.INTEGER),
                (config, value, extraInfo) -> config.defaultFactionClaimsAmount = value.intValue(),
                (config, extraInfo) -> Integer.valueOf(config.defaultFactionClaimsAmount))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultFactionBlockPlaceEnabled", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultFactionBlockPlaceEnabled = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultFactionBlockPlaceEnabled))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultFactionBlockBreakEnabled", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultFactionBlockBreakEnabled = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultFactionBlockBreakEnabled))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultFactionBlockInteractEnabled", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultFactionBlockInteractEnabled = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultFactionBlockInteractEnabled))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultFactionPVPEnabled", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultFactionPVPEnabled = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultFactionPVPEnabled))
            .add()
            .append(new KeyedCodec<String[]>("WorldNameBlacklistForClaiming", Codec.STRING_ARRAY),
                (config, value, extraInfo) -> config.worldNameBlacklistForClaiming = value,
                (config, extraInfo) -> config.worldNameBlacklistForClaiming)
            .add()
            .append(new KeyedCodec<Boolean>("CreativeModeBypassProtection", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.creativeModeBypassProtection = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.creativeModeBypassProtection))
            .add()
            .build();
    }
    
    public ClaimConfig() {
        // Default constructor for CODEC
    }
    
    public int getDefaultFactionClaimsAmount() {
        return defaultFactionClaimsAmount;
    }
    
    public void setDefaultFactionClaimsAmount(int defaultFactionClaimsAmount) {
        this.defaultFactionClaimsAmount = defaultFactionClaimsAmount;
    }
    
    public boolean isDefaultFactionBlockPlaceEnabled() {
        return defaultFactionBlockPlaceEnabled;
    }
    
    public void setDefaultFactionBlockPlaceEnabled(boolean defaultFactionBlockPlaceEnabled) {
        this.defaultFactionBlockPlaceEnabled = defaultFactionBlockPlaceEnabled;
    }
    
    public boolean isDefaultFactionBlockBreakEnabled() {
        return defaultFactionBlockBreakEnabled;
    }
    
    public void setDefaultFactionBlockBreakEnabled(boolean defaultFactionBlockBreakEnabled) {
        this.defaultFactionBlockBreakEnabled = defaultFactionBlockBreakEnabled;
    }
    
    public boolean isDefaultFactionBlockInteractEnabled() {
        return defaultFactionBlockInteractEnabled;
    }
    
    public void setDefaultFactionBlockInteractEnabled(boolean defaultFactionBlockInteractEnabled) {
        this.defaultFactionBlockInteractEnabled = defaultFactionBlockInteractEnabled;
    }
    
    public boolean isDefaultFactionPVPEnabled() {
        return defaultFactionPVPEnabled;
    }
    
    public void setDefaultFactionPVPEnabled(boolean defaultFactionPVPEnabled) {
        this.defaultFactionPVPEnabled = defaultFactionPVPEnabled;
    }
    
    public String[] getWorldNameBlacklistForClaiming() {
        return worldNameBlacklistForClaiming;
    }
    
    public void setWorldNameBlacklistForClaiming(String[] worldNameBlacklistForClaiming) {
        this.worldNameBlacklistForClaiming = worldNameBlacklistForClaiming;
    }
    
    public boolean isCreativeModeBypassProtection() {
        return creativeModeBypassProtection;
    }
    
    public void setCreativeModeBypassProtection(boolean creativeModeBypassProtection) {
        this.creativeModeBypassProtection = creativeModeBypassProtection;
    }
}
