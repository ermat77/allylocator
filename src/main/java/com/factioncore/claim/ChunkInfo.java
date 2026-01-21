package com.factioncore.claim;

import com.factioncore.claim.tracking.ModifiedTracking;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a claimed chunk.
 * Stores chunk coordinates, faction owner, and creation tracking.
 */
public class ChunkInfo {
    
    public static final BuilderCodec<ChunkInfo> CODEC;
    public static final ArrayCodec<ChunkInfo> CODEC_ARRAY;
    
    private UUID factionOwner;
    private int chunkX;
    private int chunkZ;
    private ModifiedTracking createdTracked;
    
    static {
        CODEC = BuilderCodec.builder(ChunkInfo.class, ChunkInfo::new)
            .append(new KeyedCodec<UUID>("UUID", Codec.UUID_STRING),
                (chunkInfo, uuid, extraInfo) -> chunkInfo.setFactionOwner(uuid),
                (chunkInfo, extraInfo) -> chunkInfo.getFactionOwner())
            .add()
            .append(new KeyedCodec<Integer>("ChunkX", Codec.INTEGER),
                (chunkInfo, value, extraInfo) -> chunkInfo.setChunkX(value.intValue()),
                (chunkInfo, extraInfo) -> Integer.valueOf(chunkInfo.getChunkX()))
            .add()
            .append(new KeyedCodec<Integer>("ChunkZ", Codec.INTEGER),
                (chunkInfo, value, extraInfo) -> chunkInfo.setChunkZ(value.intValue()),
                (chunkInfo, extraInfo) -> Integer.valueOf(chunkInfo.getChunkZ()))
            .add()
            .append(new KeyedCodec<ModifiedTracking>("CreatedTracker", ModifiedTracking.CODEC),
                (chunkInfo, tracking, extraInfo) -> chunkInfo.setCreatedTracked(tracking),
                (chunkInfo, extraInfo) -> chunkInfo.getCreatedTracked())
            .add()
            .build();
        
        CODEC_ARRAY = new ArrayCodec<>(CODEC, ChunkInfo[]::new);
    }
    
    /**
     * Formats chunk coordinates as a string key.
     * 
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return Formatted string "chunkX:chunkZ"
     */
    public static String formatCoordinates(int chunkX, int chunkZ) {
        return chunkX + ":" + chunkZ;
    }
    
    public ChunkInfo(UUID factionOwner, int chunkX, int chunkZ) {
        this.factionOwner = factionOwner;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.createdTracked = new ModifiedTracking(UUID.randomUUID(), "-", LocalDateTime.now().toString());
    }
    
    public ChunkInfo() {
        this(UUID.randomUUID(), 0, 0);
    }
    
    public UUID getFactionOwner() {
        return factionOwner;
    }
    
    public void setFactionOwner(UUID factionOwner) {
        this.factionOwner = factionOwner;
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    public void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }
    
    public ModifiedTracking getCreatedTracked() {
        return createdTracked;
    }
    
    public void setCreatedTracked(ModifiedTracking createdTracked) {
        this.createdTracked = createdTracked;
    }
    
    /**
     * Storage class for dimension-based chunk storage.
     */
    public static final class DimensionStorage {
        public static final BuilderCodec<DimensionStorage> CODEC;
        private ChunkInfoStorage[] chunkInfoStorages;
        
        static {
            CODEC = BuilderCodec.builder(DimensionStorage.class, DimensionStorage::new)
                .append(new KeyedCodec<ChunkInfoStorage[]>("Dimensions", ChunkInfoStorage.CODEC_ARRAY),
                    (dimensionStorage, infoStorages, extraInfo) -> dimensionStorage.setChunkInfoStorages(infoStorages),
                    (dimensionStorage, extraInfo) -> dimensionStorage.getChunkInfoStorages())
                .add()
                .build();
        }
        
        public DimensionStorage(ChunkInfoStorage[] chunkInfoStorages) {
            this.chunkInfoStorages = chunkInfoStorages;
        }
        
        public DimensionStorage() {
            this(new ChunkInfoStorage[0]);
        }
        
        public ChunkInfoStorage[] getChunkInfoStorages() {
            return chunkInfoStorages;
        }
        
        public void setChunkInfoStorages(ChunkInfoStorage[] chunkInfoStorages) {
            this.chunkInfoStorages = chunkInfoStorages;
        }
    }
    
    /**
     * Storage class for chunk info by dimension.
     */
    public static class ChunkInfoStorage {
        public static final BuilderCodec<ChunkInfoStorage> CODEC;
        public static final ArrayCodec<ChunkInfoStorage> CODEC_ARRAY;
        
        private String dimension;
        private ChunkInfo[] chunkInfos;
        
        static {
            CODEC = BuilderCodec.builder(ChunkInfoStorage.class, ChunkInfoStorage::new)
                .append(new KeyedCodec<String>("Dimension", Codec.STRING),
                    (chunkInfoStorage, string, extraInfo) -> chunkInfoStorage.setDimension(string),
                    (chunkInfoStorage, extraInfo) -> chunkInfoStorage.getDimension())
                .add()
                .append(new KeyedCodec<ChunkInfo[]>("ChunkInfo", ChunkInfo.CODEC_ARRAY),
                    (chunkInfoStorage, chunkInfos, extraInfo) -> chunkInfoStorage.setChunkInfos(chunkInfos),
                    (chunkInfoStorage, extraInfo) -> chunkInfoStorage.getChunkInfos())
                .add()
                .build();
            
            CODEC_ARRAY = new ArrayCodec<>(CODEC, ChunkInfoStorage[]::new);
        }
        
        public ChunkInfoStorage() {
            this("", new ChunkInfo[0]);
        }
        
        public ChunkInfoStorage(String dimension, ChunkInfo[] infos) {
            this.dimension = dimension;
            this.chunkInfos = infos;
        }
        
        public String getDimension() {
            return dimension;
        }
        
        public void setDimension(String dimension) {
            this.dimension = dimension;
        }
        
        public ChunkInfo[] getChunkInfos() {
            return chunkInfos;
        }
        
        public void setChunkInfos(ChunkInfo[] chunkInfos) {
            this.chunkInfos = chunkInfos;
        }
    }
}
