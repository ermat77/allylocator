package com.factioncore.ui.factionbase.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for ConfirmationPage (confirm / cancel).
 */
public class ConfirmationPageEventData {
    public String action; // "confirm", "cancel"

    public static final BuilderCodec<ConfirmationPageEventData> CODEC = BuilderCodec.builder(
        ConfirmationPageEventData.class,
        ConfirmationPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (ConfirmationPageEventData o, String v) -> o.action = v,
        (ConfirmationPageEventData o) -> o.action
    )
    .add()
    .build();
}
