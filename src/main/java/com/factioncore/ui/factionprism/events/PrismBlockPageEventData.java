package com.factioncore.ui.factionprism.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for PrismBlockPage (Attack and Harvest buttons).
 * Harvest only sends Action; server looks up prism via PrismManager (world + block position stored on page).
 */
public class PrismBlockPageEventData {
    public String action; // "attack", "harvest"

    public static final BuilderCodec<PrismBlockPageEventData> CODEC = BuilderCodec.builder(
        PrismBlockPageEventData.class,
        PrismBlockPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (PrismBlockPageEventData o, String v) -> o.action = v,
        (PrismBlockPageEventData o) -> o.action
    )
    .add()
    .build();
}
