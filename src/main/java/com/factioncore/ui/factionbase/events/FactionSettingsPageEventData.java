package com.factioncore.ui.factionbase.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for FactionSettingsPage interactions.
 */
public class FactionSettingsPageEventData {
    public String action; // "rename", "color", "claim", "disband", "backToFaction", "close"
    public String newName; // From rename input
    public String colorValue; // From color input

    public static final BuilderCodec<FactionSettingsPageEventData> CODEC = BuilderCodec.builder(
        FactionSettingsPageEventData.class,
        FactionSettingsPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionSettingsPageEventData o, String v) -> o.action = v,
        (FactionSettingsPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("@NewName", Codec.STRING),
        (FactionSettingsPageEventData o, String v) -> o.newName = v,
        (FactionSettingsPageEventData o) -> o.newName
    )
    .add()
    .append(
        new KeyedCodec<>("@ColorValue", Codec.STRING),
        (FactionSettingsPageEventData o, String v) -> o.colorValue = v,
        (FactionSettingsPageEventData o) -> o.colorValue
    )
    .add()
    .build();
}
