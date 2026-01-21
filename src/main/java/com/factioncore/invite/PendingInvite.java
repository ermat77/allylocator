package com.factioncore.invite;

import java.util.UUID;

/**
 * Represents a pending faction invite.
 * Stores information about who was invited and by which faction.
 */
public class PendingInvite {
    
    private UUID playerUuid;
    private UUID factionId;
    private String inviterName;
    private String inviteDate;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public PendingInvite() {
    }
    
    /**
     * Creates a new pending invite.
     * 
     * @param playerUuid The UUID of the invited player
     * @param factionId The UUID of the faction that sent the invite
     * @param inviterName The name of the player who sent the invite
     * @param inviteDate The date when the invite was sent
     */
    public PendingInvite(UUID playerUuid, UUID factionId, String inviterName, String inviteDate) {
        this.playerUuid = playerUuid;
        this.factionId = factionId;
        this.inviterName = inviterName;
        this.inviteDate = inviteDate;
    }
    
    /**
     * Gets the UUID of the invited player.
     * 
     * @return The player UUID
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Sets the UUID of the invited player.
     * 
     * @param playerUuid The player UUID
     */
    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }
    
    /**
     * Gets the UUID of the faction that sent the invite.
     * 
     * @return The faction UUID
     */
    public UUID getFactionId() {
        return factionId;
    }
    
    /**
     * Sets the UUID of the faction that sent the invite.
     * 
     * @param factionId The faction UUID
     */
    public void setFactionId(UUID factionId) {
        this.factionId = factionId;
    }
    
    /**
     * Gets the name of the player who sent the invite.
     * 
     * @return The inviter name
     */
    public String getInviterName() {
        return inviterName;
    }
    
    /**
     * Sets the name of the player who sent the invite.
     * 
     * @param inviterName The inviter name
     */
    public void setInviterName(String inviterName) {
        this.inviterName = inviterName;
    }
    
    /**
     * Gets the date when the invite was sent.
     * 
     * @return The invite date
     */
    public String getInviteDate() {
        return inviteDate;
    }
    
    /**
     * Sets the date when the invite was sent.
     * 
     * @param inviteDate The invite date
     */
    public void setInviteDate(String inviteDate) {
        this.inviteDate = inviteDate;
    }
}
