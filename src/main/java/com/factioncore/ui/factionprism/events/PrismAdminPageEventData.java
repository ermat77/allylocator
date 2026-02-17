package com.factioncore.ui.factionprism.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for PrismAdminPage (back, close).
 */
public class PrismAdminPageEventData {
    public String action;

    public static final BuilderCodec<PrismAdminPageEventData> CODEC = BuilderCodec.builder(
        PrismAdminPageEventData.class,
        PrismAdminPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (PrismAdminPageEventData o, String v) -> o.action = v,
        (PrismAdminPageEventData o) -> o.action
    )
    .add()
    .build();
}
