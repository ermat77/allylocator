package com.factioncore.ui.factionbase.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for FactionLeaderboardPage (back, close).
 */
public class FactionLeaderboardPageEventData {
    public String action;

    public static final BuilderCodec<FactionLeaderboardPageEventData> CODEC = BuilderCodec.builder(
        FactionLeaderboardPageEventData.class,
        FactionLeaderboardPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionLeaderboardPageEventData o, String v) -> o.action = v,
        (FactionLeaderboardPageEventData o) -> o.action
    )
    .add()
    .build();
}
