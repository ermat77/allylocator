package com.factioncore.claim.tracking;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.UUID;

/**
 * Tracks who modified something and when.
 * Used for audit trail of chunk claims.
 */
public class ModifiedTracking {
    
    public static final BuilderCodec<ModifiedTracking> CODEC;
    
    private UUID userUuid;
    private String userName;
    private String date;
    
    static {
        CODEC = BuilderCodec.builder(ModifiedTracking.class, ModifiedTracking::new)
            .append(new KeyedCodec<UUID>("UserUUID", Codec.UUID_STRING),
                (modifiedTracking, uuid, extraInfo) -> modifiedTracking.userUuid = uuid,
                (modifiedTracking, extraInfo) -> modifiedTracking.userUuid)
            .add()
            .append(new KeyedCodec<String>("UserName", Codec.STRING),
                (modifiedTracking, string, extraInfo) -> modifiedTracking.userName = string,
                (modifiedTracking, extraInfo) -> modifiedTracking.userName)
            .add()
            .append(new KeyedCodec<String>("Date", Codec.STRING),
                (modifiedTracking, string, extraInfo) -> modifiedTracking.date = string,
                (modifiedTracking, extraInfo) -> modifiedTracking.date)
            .add()
            .build();
    }
    
    public ModifiedTracking(UUID userUuid, String userName, String date) {
        this.userUuid = userUuid;
        this.userName = userName;
        this.date = date;
    }
    
    public ModifiedTracking() {
        this(UUID.randomUUID(), "-", "");
    }
    
    public UUID getUserUuid() {
        return userUuid;
    }
    
    public void setUserUuid(UUID userUuid) {
        this.userUuid = userUuid;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
    
    @Override
    public String toString() {
        return "ModifiedTracking{userUuid=" + userUuid + ", userName='" + userName + "', date='" + date + "'}";
    }
}
