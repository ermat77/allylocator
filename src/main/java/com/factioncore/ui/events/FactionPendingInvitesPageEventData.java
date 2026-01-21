package com.factioncore.ui.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.UUID;

/**
 * Event data for FactionPendingInvitesPage interactions.
 */
public class FactionPendingInvitesPageEventData {
    public String action; // "accept", "refuse", "createFaction", "close"
    public UUID factionId; // For accept/refuse buttons

    public static final BuilderCodec<FactionPendingInvitesPageEventData> CODEC = BuilderCodec.builder(
        FactionPendingInvitesPageEventData.class,
        FactionPendingInvitesPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionPendingInvitesPageEventData o, String v) -> o.action = v,
        (FactionPendingInvitesPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("FactionId", Codec.STRING),
        (FactionPendingInvitesPageEventData o, String v) -> {
            try {
                o.factionId = v != null ? UUID.fromString(v) : null;
            } catch (IllegalArgumentException e) {
                o.factionId = null;
            }
        },
        (FactionPendingInvitesPageEventData o) -> o.factionId != null ? o.factionId.toString() : null
    )
    .add()
    .build();
}
