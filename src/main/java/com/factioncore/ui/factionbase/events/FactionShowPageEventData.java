package com.factioncore.ui.factionbase.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.UUID;

/**
 * Event data for FactionShowPage interactions.
 */
public class FactionShowPageEventData {
    public String action; // "settings", "close", "permissions", "cancelInvite", "createFaction"
    public UUID memberUuid; // For permissions button
    public UUID invitedPlayerUuid; // For cancel invite button
    public String factionName; // For create faction button

    public static final BuilderCodec<FactionShowPageEventData> CODEC = BuilderCodec.builder(
        FactionShowPageEventData.class,
        FactionShowPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionShowPageEventData o, String v) -> o.action = v,
        (FactionShowPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("MemberUuid", Codec.STRING),
        (FactionShowPageEventData o, String v) -> {
            try {
                o.memberUuid = v != null ? UUID.fromString(v) : null;
            } catch (IllegalArgumentException e) {
                o.memberUuid = null;
            }
        },
        (FactionShowPageEventData o) -> o.memberUuid != null ? o.memberUuid.toString() : null
    )
    .add()
    .append(
        new KeyedCodec<>("InvitedPlayerUuid", Codec.STRING),
        (FactionShowPageEventData o, String v) -> {
            try {
                o.invitedPlayerUuid = v != null ? UUID.fromString(v) : null;
            } catch (IllegalArgumentException e) {
                o.invitedPlayerUuid = null;
            }
        },
        (FactionShowPageEventData o) -> o.invitedPlayerUuid != null ? o.invitedPlayerUuid.toString() : null
    )
    .add()
    .append(
        new KeyedCodec<>("@FactionName", Codec.STRING),
        (FactionShowPageEventData o, String v) -> o.factionName = v,
        (FactionShowPageEventData o) -> o.factionName
    )
    .add()
    .build();
}
