package com.factioncore.ui.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for PrismPage interactions (test prism / leave prism).
 */
public class PrismPageEventData {
    public String action; // "testPrism", "leavePrism"

    public static final BuilderCodec<PrismPageEventData> CODEC = BuilderCodec.builder(
        PrismPageEventData.class,
        PrismPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (PrismPageEventData o, String v) -> o.action = v,
        (PrismPageEventData o) -> o.action
    )
    .add()
    .build();
}
