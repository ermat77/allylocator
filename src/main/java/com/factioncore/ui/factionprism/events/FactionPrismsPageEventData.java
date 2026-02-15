package com.factioncore.ui.factionprism.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.UUID;

/**
 * Event data for FactionPrismsPage (back, close, toggleMarker).
 */
public class FactionPrismsPageEventData {
    public String action;
    public UUID prismId;

    public static final BuilderCodec<FactionPrismsPageEventData> CODEC = BuilderCodec.builder(
        FactionPrismsPageEventData.class,
        FactionPrismsPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionPrismsPageEventData o, String v) -> o.action = v,
        (FactionPrismsPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("PrismId", Codec.STRING),
        (FactionPrismsPageEventData o, String v) -> {
            try {
                o.prismId = v != null ? UUID.fromString(v) : null;
            } catch (IllegalArgumentException e) {
                o.prismId = null;
            }
        },
        (FactionPrismsPageEventData o) -> o.prismId != null ? o.prismId.toString() : null
    )
    .add()
    .build();
}
