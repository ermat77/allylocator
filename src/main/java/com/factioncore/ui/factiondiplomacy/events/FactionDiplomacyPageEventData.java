package com.factioncore.ui.factiondiplomacy.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.UUID;

/**
 * Event data for FactionDiplomacyPage interactions.
 */
public class FactionDiplomacyPageEventData {
    public String action; // "close", "filter", "filterUpdate", "declareEnemy", "removeEnemy", "sendAllyRequest", ...
    public String factionId; // UUID string for enemy/ally/target faction
    public String searchFilter; // from @SearchInput (Apply / explicit bind)
    public String value; // from Value (ValueChanged often sends the control value under "Value")

    public static final BuilderCodec<FactionDiplomacyPageEventData> CODEC = BuilderCodec.builder(
        FactionDiplomacyPageEventData.class,
        FactionDiplomacyPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionDiplomacyPageEventData o, String v) -> o.action = v,
        (FactionDiplomacyPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("FactionId", Codec.STRING),
        (FactionDiplomacyPageEventData o, String v) -> o.factionId = v,
        (FactionDiplomacyPageEventData o) -> o.factionId
    )
    .add()
    .append(
        new KeyedCodec<>("@SearchInput", Codec.STRING),
        (FactionDiplomacyPageEventData o, String v) -> o.searchFilter = v,
        (FactionDiplomacyPageEventData o) -> o.searchFilter
    )
    .add()
    .append(
        new KeyedCodec<>("Value", Codec.STRING),
        (FactionDiplomacyPageEventData o, String v) -> o.value = v,
        (FactionDiplomacyPageEventData o) -> o.value
    )
    .add()
    .build();

    public UUID getFactionIdUuid() {
        if (factionId == null || factionId.isEmpty()) return null;
        try {
            return UUID.fromString(factionId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
