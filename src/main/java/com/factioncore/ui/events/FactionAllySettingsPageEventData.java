package com.factioncore.ui.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for FactionAllySettingsPage (ally settings checkboxes and save/close).
 */
public class FactionAllySettingsPageEventData {
    public String action; // "save", "close"
    public Boolean shareMarkers;
    public Boolean allowInteractions;
    public Boolean openChest;
    public Boolean openDoor;
    public Boolean openBench;
    public Boolean useChair;
    public Boolean usePortal;
    public Boolean placeBlock;
    public Boolean breakBlock;

    public static final BuilderCodec<FactionAllySettingsPageEventData> CODEC = BuilderCodec.builder(
        FactionAllySettingsPageEventData.class,
        FactionAllySettingsPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionAllySettingsPageEventData o, String v) -> o.action = v,
        (FactionAllySettingsPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("@ShareMarkers", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.shareMarkers = v,
        (FactionAllySettingsPageEventData o) -> o.shareMarkers
    )
    .add()
    .append(
        new KeyedCodec<>("@AllowInteractions", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.allowInteractions = v,
        (FactionAllySettingsPageEventData o) -> o.allowInteractions
    )
    .add()
    .append(
        new KeyedCodec<>("@OpenChest", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.openChest = v,
        (FactionAllySettingsPageEventData o) -> o.openChest
    )
    .add()
    .append(
        new KeyedCodec<>("@OpenDoor", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.openDoor = v,
        (FactionAllySettingsPageEventData o) -> o.openDoor
    )
    .add()
    .append(
        new KeyedCodec<>("@OpenBench", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.openBench = v,
        (FactionAllySettingsPageEventData o) -> o.openBench
    )
    .add()
    .append(
        new KeyedCodec<>("@UseChair", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.useChair = v,
        (FactionAllySettingsPageEventData o) -> o.useChair
    )
    .add()
    .append(
        new KeyedCodec<>("@UsePortal", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.usePortal = v,
        (FactionAllySettingsPageEventData o) -> o.usePortal
    )
    .add()
    .append(
        new KeyedCodec<>("@PlaceBlock", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.placeBlock = v,
        (FactionAllySettingsPageEventData o) -> o.placeBlock
    )
    .add()
    .append(
        new KeyedCodec<>("@BreakBlock", Codec.BOOLEAN),
        (FactionAllySettingsPageEventData o, Boolean v) -> o.breakBlock = v,
        (FactionAllySettingsPageEventData o) -> o.breakBlock
    )
    .add()
    .build();
}
