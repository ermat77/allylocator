package com.factioncore.systems.tick;

import com.factioncore.Main;
import com.factioncore.claim.ChunkInfo;
import com.factioncore.config.ClaimConfig;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectList;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Spawns colored particles at claim borders when the player is near the edge of a claimed chunk
 * and that edge is a boundary with another claim or wilderness. Only runs when EnableChunkBorderParticles is true.
 * Adapted from SimpleClaims ChunkBordersTickingSystem.
 */
public class ChunkBordersTickingSystem extends DelayedEntitySystem<EntityStore> {

    private final Random random = new Random();

    public ChunkBordersTickingSystem() {
        super(0.5f);
    }

    @Override
    public void tick(float v, int index, ArchetypeChunk<EntityStore> archetypeChunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        if (!isChunkBorderParticlesEnabled()) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null || player.getWorld() == null) return;

        Vector3d pos = playerRef.getTransform().getPosition();
        int chunkX = ChunkUtil.chunkCoordinate((int) pos.getX());
        int chunkZ = ChunkUtil.chunkCoordinate((int) pos.getZ());
        String dimension = player.getWorld().getName();
        FactionManager manager = FactionManager.getInstance();

        for (int x = chunkX - 1; x <= chunkX + 1; ++x) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; ++z) {
                ChunkInfo chunkInfo = manager.getChunk(dimension, x, z);
                if (chunkInfo != null) {
                    FactionInfo faction = manager.getFactionById(chunkInfo.getFactionOwner());
                    if (faction != null && isNearBorder(pos, x, z)) {
                        spawnChunkBorderParticles(x, z, faction, store, pos, dimension);
                    }
                }
            }
        }
    }

    private static boolean isChunkBorderParticlesEnabled() {
        Main main = Main.get();
        if (main == null || main.getClaimConfig() == null) return false;
        ClaimConfig config = main.getClaimConfig().get();
        return config != null && config.isEnableChunkBorderParticles();
    }

    private static String getParticleName() {
        Main main = Main.get();
        if (main != null && main.getClaimConfig() != null) {
            ClaimConfig config = main.getClaimConfig().get();
            if (config != null) return config.getChunkBorderParticleName();
        }
        return "Buuz135_SimpleClaims_Spawn";
    }

    private boolean isNearBorder(Vector3d pos, int chunkX, int chunkZ) {
        int minX = ChunkUtil.minBlock(chunkX);
        int minZ = ChunkUtil.minBlock(chunkZ);
        int maxX = ChunkUtil.maxBlock(chunkX) + 1;
        int maxZ = ChunkUtil.maxBlock(chunkZ) + 1;
        double threshold = 5.0;
        double distMinX = Math.abs(pos.getX() - minX);
        double distMaxX = Math.abs(pos.getX() - maxX);
        double distMinZ = Math.abs(pos.getZ() - minZ);
        double distMaxZ = Math.abs(pos.getZ() - maxZ);
        return distMinX < threshold || distMaxX < threshold || distMinZ < threshold || distMaxZ < threshold;
    }

    private void spawnChunkBorderParticles(int chunkX, int chunkZ, FactionInfo faction, Store<EntityStore> store, Vector3d playerPos, String dimension) {
        int minX = ChunkUtil.minBlock(chunkX);
        int minZ = ChunkUtil.minBlock(chunkZ);
        int maxX = ChunkUtil.maxBlock(chunkX) + 1;
        int maxZ = ChunkUtil.maxBlock(chunkZ) + 1;
        double threshold = 5.0;
        double playerY = playerPos.getY();
        String particleName = getParticleName();
        @SuppressWarnings("unchecked")
        SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource = (SpatialResource<Ref<EntityStore>, EntityStore>) store.getResource(EntityModule.get().getPlayerSpatialResourceType());
        int colorInt = faction.getColor();
        Color particleColor = new Color((byte) ((colorInt >> 16) & 0xFF), (byte) ((colorInt >> 8) & 0xFF), (byte) (colorInt & 0xFF));
        FactionManager manager = FactionManager.getInstance();

        if (Math.abs(playerPos.getX() - minX) < threshold) {
            ChunkInfo adjacent = manager.getChunk(dimension, chunkX - 1, chunkZ);
            if (adjacent == null || !adjacent.getFactionOwner().equals(faction.getId())) {
                for (double z = minZ; z <= maxZ; z += 0.5) {
                    if (Math.abs(playerPos.getZ() - z) < threshold && random.nextBoolean()) {
                        Vector3d pos = new Vector3d(minX, playerY, z + randomOffset());
                        ObjectList<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();
                        playerSpatialResource.getSpatialStructure().collect(playerPos, 75.0, playerRefs);
                        ParticleUtil.spawnParticleEffect(particleName, pos, 0f, 0f, 0f, 1f, particleColor, playerRefs, store);
                    }
                }
            }
        }
        if (Math.abs(playerPos.getX() - maxX) < threshold) {
            ChunkInfo adjacent = manager.getChunk(dimension, chunkX + 1, chunkZ);
            if (adjacent == null || !adjacent.getFactionOwner().equals(faction.getId())) {
                for (double z = minZ; z <= maxZ; z += 0.5) {
                    if (Math.abs(playerPos.getZ() - z) < threshold && random.nextBoolean()) {
                        Vector3d pos = new Vector3d(maxX, playerY, z + randomOffset());
                        ObjectList<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();
                        playerSpatialResource.getSpatialStructure().collect(playerPos, 75.0, playerRefs);
                        ParticleUtil.spawnParticleEffect(particleName, pos, 0f, 0f, 0f, 1f, particleColor, playerRefs, store);
                    }
                }
            }
        }
        if (Math.abs(playerPos.getZ() - minZ) < threshold) {
            ChunkInfo adjacent = manager.getChunk(dimension, chunkX, chunkZ - 1);
            if (adjacent == null || !adjacent.getFactionOwner().equals(faction.getId())) {
                for (double x = minX; x <= maxX; x += 0.5) {
                    if (Math.abs(playerPos.getX() - x) < threshold && random.nextBoolean()) {
                        Vector3d pos = new Vector3d(x + randomOffset(), playerY, minZ);
                        ObjectList<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();
                        playerSpatialResource.getSpatialStructure().collect(playerPos, 75.0, playerRefs);
                        ParticleUtil.spawnParticleEffect(particleName, pos, 0f, 0f, 0f, 1f, particleColor, playerRefs, store);
                    }
                }
            }
        }
        if (Math.abs(playerPos.getZ() - maxZ) < threshold) {
            ChunkInfo adjacent = manager.getChunk(dimension, chunkX, chunkZ + 1);
            if (adjacent == null || !adjacent.getFactionOwner().equals(faction.getId())) {
                for (double x = minX; x <= maxX; x += 0.5) {
                    if (Math.abs(playerPos.getX() - x) < threshold && random.nextBoolean()) {
                        Vector3d pos = new Vector3d(x + randomOffset(), playerY, maxZ);
                        ObjectList<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();
                        playerSpatialResource.getSpatialStructure().collect(playerPos, 75.0, playerRefs);
                        ParticleUtil.spawnParticleEffect(particleName, pos, 0f, 0f, 0f, 1f, particleColor, playerRefs, store);
                    }
                }
            }
        }
    }

    private double randomOffset() {
        return random.nextDouble() - 0.5;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
