package com.factioncore.map;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapLoadException;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.IWorldMapProvider;

public class FactionWorldMapProvider implements IWorldMapProvider {
    public static final String ID = "FactionCore";
    public static final BuilderCodec<FactionWorldMapProvider> CODEC = BuilderCodec.builder(FactionWorldMapProvider.class, FactionWorldMapProvider::new).build();
    @Override
    public IWorldMap getGenerator(World world) throws WorldMapLoadException {
        return FactionChunkWorldMap.INSTANCE;
    }
}