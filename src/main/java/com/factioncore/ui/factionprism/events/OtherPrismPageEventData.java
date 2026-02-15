package com.factioncore.ui.factionprism.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for OtherPrismPage (Attack and Members buttons).
 */
public class OtherPrismPageEventData {
    public String action; // "attack", "members"

    public static final BuilderCodec<OtherPrismPageEventData> CODEC = BuilderCodec.builder(
        OtherPrismPageEventData.class,
        OtherPrismPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (OtherPrismPageEventData o, String v) -> o.action = v,
        (OtherPrismPageEventData o) -> o.action
    )
    .add()
    .build();
}
