package com.factioncore.ui.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for ExampleBlockPage (e.g. close button).
 */
public class ExampleBlockPageEventData {
    public String action; // "close"

    public static final BuilderCodec<ExampleBlockPageEventData> CODEC = BuilderCodec.builder(
        ExampleBlockPageEventData.class,
        ExampleBlockPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (ExampleBlockPageEventData o, String v) -> o.action = v,
        (ExampleBlockPageEventData o) -> o.action
    )
    .add()
    .build();
}
