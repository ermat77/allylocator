package com.factioncore.diplomacy;

import java.util.UUID;

/**
 * Represents a pending ally request between two factions.
 * State is derived when building UI: "awaitingResponseFromOtherFaction" when we are the requester,
 * "awaitingResponseFromYourFaction" when we are the target.
 */
public class PendingAllyRequest {

    private UUID requesterFactionId;
    private UUID targetFactionId;
    private String createdDate;

    public PendingAllyRequest() {
    }

    public PendingAllyRequest(UUID requesterFactionId, UUID targetFactionId, String createdDate) {
        this.requesterFactionId = requesterFactionId;
        this.targetFactionId = targetFactionId;
        this.createdDate = createdDate;
    }

    public UUID getRequesterFactionId() {
        return requesterFactionId;
    }

    public void setRequesterFactionId(UUID requesterFactionId) {
        this.requesterFactionId = requesterFactionId;
    }

    public UUID getTargetFactionId() {
        return targetFactionId;
    }

    public void setTargetFactionId(UUID targetFactionId) {
        this.targetFactionId = targetFactionId;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }
}
