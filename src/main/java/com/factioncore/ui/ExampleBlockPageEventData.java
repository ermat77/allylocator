package com.factioncore.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for ExampleBlockPage (e.g. close button).
 */
public class ExampleBlockPageEventData {
    public String action;

    public static final BuilderCodec<ExampleBlockPageEventData> CODEC = BuilderCodec.builder(
        ExampleBlockPageEventData.class,
        ExampleBlockPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", com.hypixel.hytale.codec.Codec.STRING),
        (ExampleBlockPageEventData o, String v) -> o.action = v,
        (ExampleBlockPageEventData o) -> o.action
    )
    .add()
    .build();
}
