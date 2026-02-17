package com.factioncore.ui.factionprism.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.UUID;

/**
 * Event data for OwnPrismPage (Harvest, Cancel Prism, and Change Faction testing).
 */
public class OwnPrismPageEventData {
    public String action; // "harvest", "cancelPrism", "changeFaction"
    public UUID factionId; // for changeFaction: selected faction id

    public static final BuilderCodec<OwnPrismPageEventData> CODEC = BuilderCodec.builder(
        OwnPrismPageEventData.class,
        OwnPrismPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (OwnPrismPageEventData o, String v) -> o.action = v,
        (OwnPrismPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("FactionId", Codec.STRING),
        (OwnPrismPageEventData o, String v) -> {
            try {
                o.factionId = v != null ? UUID.fromString(v) : null;
            } catch (IllegalArgumentException e) {
                o.factionId = null;
            }
        },
        (OwnPrismPageEventData o) -> o.factionId != null ? o.factionId.toString() : null
    )
    .add()
    .build();
}
