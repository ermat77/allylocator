package com.factioncore.prism;

import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box2D;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for prism (temporary flat 16-chunk) worlds: create, paste prefab, teleport in/out, remove.
 */
public final class PrismService {

    private static final PrismService INSTANCE = new PrismService();

    private static final String PRISM_WORLD_PREFIX = "prism-";
    private static final String PREFAB_KEY = "prefab_prism.prefab.json";
    private static final int REMOVE_WORLD_DELAY_SECONDS = 2;

    private final Map<UUID, PrismReturnLocation> returnLocations = new ConcurrentHashMap<>();

    /**
     * Holds the world name and transform to return the player to when leaving the prism.
     */
    public static final class PrismReturnLocation {
        public final String worldName;
        public final Transform transform;

        public PrismReturnLocation(String worldName, Transform transform) {
            this.worldName = worldName;
            this.transform = transform;
        }
    }

    /**
     * Returns true if the given world name is a prism world for any player.
     */
    public static boolean isPrismWorld(String worldName) {
        return worldName != null && worldName.startsWith(PRISM_WORLD_PREFIX);
    }

    /**
     * Returns the prism world name for the given player UUID.
     */
    public static String prismWorldName(UUID playerUuid) {
        return PRISM_WORLD_PREFIX + playerUuid.toString();
    }

    /**
     * Saves the player's current location and starts prism creation: flat 16-chunk world,
     * paste prefab at (0,0,0), then teleport player to (0,1,0).
     * Must be called on the player's current world executor (store context).
     */
    public void enterPrism(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull PlayerRef playerRef,
            @Nonnull Runnable onError
    ) {
        World currentWorld = store.getExternalData().getWorld();
        UUID playerUuid = playerRef.getUuid();
        String prismName = prismWorldName(playerUuid);

        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            ConfigUtil.debug("PrismService: no TransformComponent for player");
            onError.run();
            return;
        }
        Transform returnTransform = transformComponent.getTransform().clone();
        String returnWorldName = currentWorld.getName();
        returnLocations.put(playerUuid, new PrismReturnLocation(returnWorldName, returnTransform));

        Universe universe = Universe.get();
        if (universe.getWorld(prismName) != null) {
            try {
                universe.removeWorld(prismName);
            } catch (Exception e) {
                ConfigUtil.debug("PrismService: failed to remove existing prism world: " + e.getMessage());
            }
        }

        Path universePath = universe.getPath();
        Path savePath = universePath.resolve("worlds").resolve(prismName);
        try {
            Files.createDirectories(savePath);
        } catch (Exception e) {
            ConfigUtil.debug("PrismService: failed to create prism dir: " + e.getMessage());
            returnLocations.remove(playerUuid);
            onError.run();
            return;
        }

        WorldConfig worldConfig = buildPrismWorldConfig();
        CompletableFuture<World> worldFuture = Universe.get().makeWorld(prismName, savePath, worldConfig, true);

        worldFuture
                .thenCompose(world -> waitForChunkAt(world, 0, 0))
                .thenAccept(prismWorld -> {
                    try {
                        pastePrefabAtOrigin(prismWorld);
                    } catch (Exception e) {
                        ConfigUtil.debug("PrismService: prefab paste failed: " + e.getMessage(), e);
                        returnLocations.remove(playerUuid);
                        prismWorld.execute(onError);
                        return;
                    }
                    Teleport teleport = Teleport.createForPlayer(prismWorld, new Transform(0, 1, 0));
                    currentWorld.execute(() -> {
                        if (ref.isValid()) {
                            store.addComponent(ref, Teleport.getComponentType(), teleport);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    ConfigUtil.debug("PrismService: enter prism failed: " + throwable.getMessage(), throwable);
                    returnLocations.remove(playerUuid);
                    currentWorld.execute(onError);
                    return null;
                });
    }

    /**
     * Teleports the player back to the saved location and removes the prism world after a short delay.
     * Must be called on the prism world executor (store context).
     */
    public void leavePrism(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull PlayerRef playerRef,
            @Nonnull Runnable onError
    ) {
        UUID playerUuid = playerRef.getUuid();
        PrismReturnLocation returnLocation = returnLocations.remove(playerUuid);
        if (returnLocation == null) {
            ConfigUtil.debug("PrismService: no return location for player " + playerUuid);
            onError.run();
            return;
        }

        World prismWorld = store.getExternalData().getWorld();
        String prismWorldName = prismWorld.getName();
        Universe universe = Universe.get();
        World returnWorld = universe.getWorld(returnLocation.worldName);
        if (returnWorld == null) {
            ConfigUtil.debug("PrismService: return world not found: " + returnLocation.worldName);
            onError.run();
            return;
        }

        Teleport teleport = Teleport.createForPlayer(returnWorld, returnLocation.transform);
        store.addComponent(ref, Teleport.getComponentType(), teleport);

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(REMOVE_WORLD_DELAY_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                if (Universe.get().getWorld(prismWorldName) != null) {
                    Universe.get().removeWorld(prismWorldName);
                }
            } catch (Exception e) {
                ConfigUtil.debug("PrismService: removeWorld failed: " + e.getMessage());
            }
        });
    }

    @Nullable
    public PrismReturnLocation getReturnLocation(UUID playerUuid) {
        return returnLocations.get(playerUuid);
    }

    private static WorldConfig buildPrismWorldConfig() {
        WorldConfig config = new WorldConfig();
        config.setUuid(UUID.randomUUID());
        config.setDisplayName("Prism");
        config.setSeed(System.currentTimeMillis());
        config.setDeleteOnRemove(true);

        BuilderCodec<?> flatCodec = (BuilderCodec<?>) IWorldGenProvider.CODEC.getCodecFor("Flat");
        if (flatCodec != null) {
            config.setWorldGenProvider((IWorldGenProvider) flatCodec.getDefaultValue());
        }

        Box2D region = new Box2D(new Vector2d(0, 0), new Vector2d(96, 96));
        config.getChunkConfig().setPregenerateRegion(region);
        config.getChunkConfig().setKeepLoadedRegion(region);
        config.setSpawnProvider(new GlobalSpawnProvider(new Transform(0, 1, 0)));
        return config;
    }

    private static CompletableFuture<World> waitForChunkAt(World world, int blockX, int blockZ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        return world.getChunkStore().getChunkReferenceAsync(chunkIndex)
                .thenApply(ref -> world);
    }

    private static void pastePrefabAtOrigin(World prismWorld) {
        BlockSelection selection = PrefabStore.get().getServerPrefab(PREFAB_KEY);
        if (selection == null) {
            throw new IllegalStateException("Prism prefab not found: " + PREFAB_KEY);
        }
        selection.placeNoReturn(prismWorld, new Vector3i(0, 0, 0), prismWorld.getEntityStore().getStore());
    }

    @Nonnull
    public static PrismService getInstance() {
        return INSTANCE;
    }
}
