package com.factioncore.ui.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.UUID;

/**
 * Event data for FactionPendingAllyRequestsPage (accept, decline, cancel, close).
 */
public class FactionPendingAllyRequestsPageEventData {
    public String action; // "close", "acceptAllyRequest", "declineAllyRequest", "cancelAllyRequest"
    public String factionId; // UUID string: for accept/decline = requester faction; for cancel = target faction

    public static final BuilderCodec<FactionPendingAllyRequestsPageEventData> CODEC = BuilderCodec.builder(
        FactionPendingAllyRequestsPageEventData.class,
        FactionPendingAllyRequestsPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionPendingAllyRequestsPageEventData o, String v) -> o.action = v,
        (FactionPendingAllyRequestsPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("FactionId", Codec.STRING),
        (FactionPendingAllyRequestsPageEventData o, String v) -> o.factionId = v,
        (FactionPendingAllyRequestsPageEventData o) -> o.factionId
    )
    .add()
    .build();

    public UUID getFactionIdUuid() {
        if (factionId == null || factionId.isEmpty()) return null;
        try {
            return UUID.fromString(factionId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
