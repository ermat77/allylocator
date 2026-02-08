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
    /** Whether faction members can place blocks in their faction's claimed chunks. Default true so members can build. */
    private boolean defaultFactionBlockPlaceEnabled = true;
    /** Whether faction members can break blocks in their faction's claimed chunks. Default true so members can build. */
    private boolean defaultFactionBlockBreakEnabled = true;
    /** Whether faction members can use/interact with blocks (doors, chests, etc.) in their faction's claimed chunks. Default true. */
    private boolean defaultFactionBlockInteractEnabled = true;
    private boolean defaultFactionPVPEnabled = true;
    private String[] worldNameBlacklistForClaiming = new String[0];
    private boolean creativeModeBypassProtection = false;
    private int chunkVisualizerGridRadius = 8; // Default: 8 chunks radius (17x17 grid)
    /** Block type id substrings that ignore break/damage/interact restriction (e.g. "grass" to allow breaking grass in any claim). */
    private String[] blocksThatIgnoreRestriction = new String[0];
    /** Whether to spawn particles at claim borders when player is near. */
    private boolean enableChunkBorderParticles = false;
    /** Particle effect name for chunk borders (e.g. Hytale built-in or mod particle). */
    private String chunkBorderParticleName = "Buuz135_SimpleClaims_Spawn";

    // Default member interaction permissions (when a player joins a faction)
    private boolean defaultMemberCanInteractWithChest = true;
    private boolean defaultMemberCanPlaceBlock = true;
    private boolean defaultMemberCanBreakBlock = true;
    private boolean defaultMemberCanInteractWithDoor = true;
    private boolean defaultMemberCanInteractWithBench = true;
    private boolean defaultMemberCanInteractWithChair = true;
    private boolean defaultMemberCanInteractWithPortal = true;

    // Default ally interaction permissions (when a faction is set as ally)
    private boolean defaultAllyCanInteractWithChest = false;
    private boolean defaultAllyCanPlaceBlock = false;
    private boolean defaultAllyCanBreakBlock = false;
    private boolean defaultAllyCanInteractWithDoor = false;
    private boolean defaultAllyCanInteractWithBench = false;
    private boolean defaultAllyCanInteractWithChair = false;
    private boolean defaultAllyCanInteractWithPortal = false;

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
            .append(new KeyedCodec<Integer>("ChunkVisualizerGridRadius", Codec.INTEGER),
                (config, value, extraInfo) -> config.chunkVisualizerGridRadius = value.intValue(),
                (config, extraInfo) -> Integer.valueOf(config.chunkVisualizerGridRadius))
            .add()
            .append(new KeyedCodec<String[]>("BlocksThatIgnoreRestriction", Codec.STRING_ARRAY),
                (config, value, extraInfo) -> config.blocksThatIgnoreRestriction = value != null ? value : new String[0],
                (config, extraInfo) -> config.blocksThatIgnoreRestriction)
            .add()
            .append(new KeyedCodec<Boolean>("EnableChunkBorderParticles", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.enableChunkBorderParticles = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.enableChunkBorderParticles))
            .add()
            .append(new KeyedCodec<String>("ChunkBorderParticleName", Codec.STRING),
                (config, value, extraInfo) -> config.chunkBorderParticleName = value != null ? value : "Buuz135_SimpleClaims_Spawn",
                (config, extraInfo) -> config.chunkBorderParticleName != null ? config.chunkBorderParticleName : "Buuz135_SimpleClaims_Spawn")
            .add()
            .append(new KeyedCodec<Boolean>("DefaultMemberCanInteractWithChest", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultMemberCanInteractWithChest = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultMemberCanInteractWithChest))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultMemberCanPlaceBlock", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultMemberCanPlaceBlock = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultMemberCanPlaceBlock))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultMemberCanBreakBlock", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultMemberCanBreakBlock = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultMemberCanBreakBlock))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultMemberCanInteractWithDoor", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultMemberCanInteractWithDoor = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultMemberCanInteractWithDoor))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultMemberCanInteractWithBench", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultMemberCanInteractWithBench = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultMemberCanInteractWithBench))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultMemberCanInteractWithChair", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultMemberCanInteractWithChair = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultMemberCanInteractWithChair))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultMemberCanInteractWithPortal", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultMemberCanInteractWithPortal = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultMemberCanInteractWithPortal))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultAllyCanInteractWithChest", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultAllyCanInteractWithChest = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultAllyCanInteractWithChest))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultAllyCanPlaceBlock", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultAllyCanPlaceBlock = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultAllyCanPlaceBlock))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultAllyCanBreakBlock", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultAllyCanBreakBlock = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultAllyCanBreakBlock))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultAllyCanInteractWithDoor", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultAllyCanInteractWithDoor = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultAllyCanInteractWithDoor))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultAllyCanInteractWithBench", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultAllyCanInteractWithBench = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultAllyCanInteractWithBench))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultAllyCanInteractWithChair", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultAllyCanInteractWithChair = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultAllyCanInteractWithChair))
            .add()
            .append(new KeyedCodec<Boolean>("DefaultAllyCanInteractWithPortal", Codec.BOOLEAN),
                (config, value, extraInfo) -> config.defaultAllyCanInteractWithPortal = value.booleanValue(),
                (config, extraInfo) -> Boolean.valueOf(config.defaultAllyCanInteractWithPortal))
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
    
    public int getChunkVisualizerGridRadius() {
        return chunkVisualizerGridRadius;
    }
    
    public void setChunkVisualizerGridRadius(int chunkVisualizerGridRadius) {
        this.chunkVisualizerGridRadius = chunkVisualizerGridRadius;
    }

    /**
     * Block type id substrings that ignore break/damage/interact restriction (SimpleClaims-style).
     * If a block's type id contains any of these strings (case-insensitive), protection is skipped for that block.
     */
    public String[] getBlocksThatIgnoreRestriction() {
        return blocksThatIgnoreRestriction != null ? blocksThatIgnoreRestriction : new String[0];
    }

    public void setBlocksThatIgnoreRestriction(String[] blocksThatIgnoreRestriction) {
        this.blocksThatIgnoreRestriction = blocksThatIgnoreRestriction != null ? blocksThatIgnoreRestriction : new String[0];
    }

    public boolean isEnableChunkBorderParticles() {
        return enableChunkBorderParticles;
    }

    public void setEnableChunkBorderParticles(boolean enableChunkBorderParticles) {
        this.enableChunkBorderParticles = enableChunkBorderParticles;
    }

    public String getChunkBorderParticleName() {
        return chunkBorderParticleName != null ? chunkBorderParticleName : "Buuz135_SimpleClaims_Spawn";
    }

    public void setChunkBorderParticleName(String chunkBorderParticleName) {
        this.chunkBorderParticleName = chunkBorderParticleName != null ? chunkBorderParticleName : "Buuz135_SimpleClaims_Spawn";
    }

    public boolean isDefaultMemberCanInteractWithChest() { return defaultMemberCanInteractWithChest; }
    public void setDefaultMemberCanInteractWithChest(boolean v) { this.defaultMemberCanInteractWithChest = v; }
    public boolean isDefaultMemberCanPlaceBlock() { return defaultMemberCanPlaceBlock; }
    public void setDefaultMemberCanPlaceBlock(boolean v) { this.defaultMemberCanPlaceBlock = v; }
    public boolean isDefaultMemberCanBreakBlock() { return defaultMemberCanBreakBlock; }
    public void setDefaultMemberCanBreakBlock(boolean v) { this.defaultMemberCanBreakBlock = v; }
    public boolean isDefaultMemberCanInteractWithDoor() { return defaultMemberCanInteractWithDoor; }
    public void setDefaultMemberCanInteractWithDoor(boolean v) { this.defaultMemberCanInteractWithDoor = v; }
    public boolean isDefaultMemberCanInteractWithBench() { return defaultMemberCanInteractWithBench; }
    public void setDefaultMemberCanInteractWithBench(boolean v) { this.defaultMemberCanInteractWithBench = v; }
    public boolean isDefaultMemberCanInteractWithChair() { return defaultMemberCanInteractWithChair; }
    public void setDefaultMemberCanInteractWithChair(boolean v) { this.defaultMemberCanInteractWithChair = v; }
    public boolean isDefaultMemberCanInteractWithPortal() { return defaultMemberCanInteractWithPortal; }
    public void setDefaultMemberCanInteractWithPortal(boolean v) { this.defaultMemberCanInteractWithPortal = v; }

    public boolean isDefaultAllyCanInteractWithChest() { return defaultAllyCanInteractWithChest; }
    public void setDefaultAllyCanInteractWithChest(boolean v) { this.defaultAllyCanInteractWithChest = v; }
    public boolean isDefaultAllyCanPlaceBlock() { return defaultAllyCanPlaceBlock; }
    public void setDefaultAllyCanPlaceBlock(boolean v) { this.defaultAllyCanPlaceBlock = v; }
    public boolean isDefaultAllyCanBreakBlock() { return defaultAllyCanBreakBlock; }
    public void setDefaultAllyCanBreakBlock(boolean v) { this.defaultAllyCanBreakBlock = v; }
    public boolean isDefaultAllyCanInteractWithDoor() { return defaultAllyCanInteractWithDoor; }
    public void setDefaultAllyCanInteractWithDoor(boolean v) { this.defaultAllyCanInteractWithDoor = v; }
    public boolean isDefaultAllyCanInteractWithBench() { return defaultAllyCanInteractWithBench; }
    public void setDefaultAllyCanInteractWithBench(boolean v) { this.defaultAllyCanInteractWithBench = v; }
    public boolean isDefaultAllyCanInteractWithChair() { return defaultAllyCanInteractWithChair; }
    public void setDefaultAllyCanInteractWithChair(boolean v) { this.defaultAllyCanInteractWithChair = v; }
    public boolean isDefaultAllyCanInteractWithPortal() { return defaultAllyCanInteractWithPortal; }
    public void setDefaultAllyCanInteractWithPortal(boolean v) { this.defaultAllyCanInteractWithPortal = v; }
}
