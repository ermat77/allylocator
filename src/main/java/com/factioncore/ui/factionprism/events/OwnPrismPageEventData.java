package com.factioncore.ui.factionprism.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for OwnPrismPage (Harvest and Cancel Prism buttons).
 */
public class OwnPrismPageEventData {
    public String action; // "harvest", "cancelPrism"

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
    .build();
}
