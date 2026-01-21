package com.factioncore.ui.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for FactionCreatePage interactions.
 */
public class FactionCreatePageEventData {
    public String action; // "create", "cancel"
    public String factionName; // From text input

    public static final BuilderCodec<FactionCreatePageEventData> CODEC = BuilderCodec.builder(
        FactionCreatePageEventData.class,
        FactionCreatePageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionCreatePageEventData o, String v) -> o.action = v,
        (FactionCreatePageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("@FactionName", Codec.STRING),
        (FactionCreatePageEventData o, String v) -> o.factionName = v,
        (FactionCreatePageEventData o) -> o.factionName
    )
    .add()
    .build();
}
