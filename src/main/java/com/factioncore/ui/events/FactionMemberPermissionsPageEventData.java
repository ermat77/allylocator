package com.factioncore.ui.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.UUID;

/**
 * Event data for FactionMemberPermissionsPage interactions.
 */
public class FactionMemberPermissionsPageEventData {
    public String action; // "save", "cancel"
    public UUID targetMemberUuid;
    public Boolean renamePermission; // From checkbox
    public Boolean colorPermission; // From checkbox
    public Boolean invitePermission; // From checkbox
    public Boolean alliesPermission; // From checkbox
    public Boolean kickPermission; // From checkbox
    public Boolean enemiesPermission; // From checkbox

    public static final BuilderCodec<FactionMemberPermissionsPageEventData> CODEC = BuilderCodec.builder(
        FactionMemberPermissionsPageEventData.class,
        FactionMemberPermissionsPageEventData::new
    )
    .append(
        new KeyedCodec<>("Action", Codec.STRING),
        (FactionMemberPermissionsPageEventData o, String v) -> o.action = v,
        (FactionMemberPermissionsPageEventData o) -> o.action
    )
    .add()
    .append(
        new KeyedCodec<>("TargetMemberUuid", Codec.STRING),
        (FactionMemberPermissionsPageEventData o, String v) -> {
            try {
                o.targetMemberUuid = v != null ? UUID.fromString(v) : null;
            } catch (IllegalArgumentException e) {
                o.targetMemberUuid = null;
            }
        },
        (FactionMemberPermissionsPageEventData o) -> o.targetMemberUuid != null ? o.targetMemberUuid.toString() : null
    )
    .add()
    .append(
        new KeyedCodec<>("@RenamePermission", Codec.BOOLEAN),
        (FactionMemberPermissionsPageEventData o, Boolean v) -> o.renamePermission = v,
        (FactionMemberPermissionsPageEventData o) -> o.renamePermission
    )
    .add()
    .append(
        new KeyedCodec<>("@ColorPermission", Codec.BOOLEAN),
        (FactionMemberPermissionsPageEventData o, Boolean v) -> o.colorPermission = v,
        (FactionMemberPermissionsPageEventData o) -> o.colorPermission
    )
    .add()
    .append(
        new KeyedCodec<>("@InvitePermission", Codec.BOOLEAN),
        (FactionMemberPermissionsPageEventData o, Boolean v) -> o.invitePermission = v,
        (FactionMemberPermissionsPageEventData o) -> o.invitePermission
    )
    .add()
    .append(
        new KeyedCodec<>("@AlliesPermission", Codec.BOOLEAN),
        (FactionMemberPermissionsPageEventData o, Boolean v) -> o.alliesPermission = v,
        (FactionMemberPermissionsPageEventData o) -> o.alliesPermission
    )
    .add()
    .append(
        new KeyedCodec<>("@KickPermission", Codec.BOOLEAN),
        (FactionMemberPermissionsPageEventData o, Boolean v) -> o.kickPermission = v,
        (FactionMemberPermissionsPageEventData o) -> o.kickPermission
    )
    .add()
    .append(
        new KeyedCodec<>("@EnemiesPermission", Codec.BOOLEAN),
        (FactionMemberPermissionsPageEventData o, Boolean v) -> o.enemiesPermission = v,
        (FactionMemberPermissionsPageEventData o) -> o.enemiesPermission
    )
    .add()
    .build();
}
