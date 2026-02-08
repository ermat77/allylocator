package com.factioncore.diplomacy;

/**
 * Per-ally settings from our faction's perspective: share markers on map and allow interactions in our claimed chunks.
 * Per-interaction flags control what ally members can do in our claims (unidirectional).
 */
public class AllySettings {

    private boolean sharePlayerMarkers;
    private boolean allowInteractionsInClaimedChunks;

    private boolean allowOpenChest;
    private boolean allowOpenDoor;
    private boolean allowOpenBench;
    private boolean allowUseChair;
    private boolean allowUsePortal;
    private boolean allowPlaceBlock;
    private boolean allowBreakBlock;

    public AllySettings() {
        this(false, false, false, false, false, false, false, false, false);
    }

    public AllySettings(boolean sharePlayerMarkers, boolean allowInteractionsInClaimedChunks) {
        this(sharePlayerMarkers, allowInteractionsInClaimedChunks, false, false, false, false, false, false, false);
    }

    public AllySettings(boolean sharePlayerMarkers, boolean allowInteractionsInClaimedChunks,
                        boolean allowOpenChest, boolean allowOpenDoor, boolean allowOpenBench,
                        boolean allowUseChair, boolean allowUsePortal, boolean allowPlaceBlock, boolean allowBreakBlock) {
        this.sharePlayerMarkers = sharePlayerMarkers;
        this.allowInteractionsInClaimedChunks = allowInteractionsInClaimedChunks;
        this.allowOpenChest = allowOpenChest;
        this.allowOpenDoor = allowOpenDoor;
        this.allowOpenBench = allowOpenBench;
        this.allowUseChair = allowUseChair;
        this.allowUsePortal = allowUsePortal;
        this.allowPlaceBlock = allowPlaceBlock;
        this.allowBreakBlock = allowBreakBlock;
    }

    public boolean isSharePlayerMarkers() {
        return sharePlayerMarkers;
    }

    public void setSharePlayerMarkers(boolean sharePlayerMarkers) {
        this.sharePlayerMarkers = sharePlayerMarkers;
    }

    public boolean isAllowInteractionsInClaimedChunks() {
        return allowInteractionsInClaimedChunks;
    }

    public void setAllowInteractionsInClaimedChunks(boolean allowInteractionsInClaimedChunks) {
        this.allowInteractionsInClaimedChunks = allowInteractionsInClaimedChunks;
    }

    public boolean isAllowOpenChest() { return allowOpenChest; }
    public void setAllowOpenChest(boolean allowOpenChest) { this.allowOpenChest = allowOpenChest; }

    public boolean isAllowOpenDoor() { return allowOpenDoor; }
    public void setAllowOpenDoor(boolean allowOpenDoor) { this.allowOpenDoor = allowOpenDoor; }

    public boolean isAllowOpenBench() { return allowOpenBench; }
    public void setAllowOpenBench(boolean allowOpenBench) { this.allowOpenBench = allowOpenBench; }

    public boolean isAllowUseChair() { return allowUseChair; }
    public void setAllowUseChair(boolean allowUseChair) { this.allowUseChair = allowUseChair; }

    public boolean isAllowUsePortal() { return allowUsePortal; }
    public void setAllowUsePortal(boolean allowUsePortal) { this.allowUsePortal = allowUsePortal; }

    public boolean isAllowPlaceBlock() { return allowPlaceBlock; }
    public void setAllowPlaceBlock(boolean allowPlaceBlock) { this.allowPlaceBlock = allowPlaceBlock; }

    public boolean isAllowBreakBlock() { return allowBreakBlock; }
    public void setAllowBreakBlock(boolean allowBreakBlock) { this.allowBreakBlock = allowBreakBlock; }
}
