package com.factioncore.prism;

import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.points.FactionPointsManager;
import com.factioncore.systems.interaction.BlockUseOpenUISystem;
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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

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

    /**
     * Harvests a prism block by ID: looks up the prism, verifies the player is in the prism's faction,
     * computes time alive, awards FP to the prism's faction, then removes the block and DB record.
     * Call from any UI or class when a player harvests a prism (e.g. PrismBlockPage Harvest button).
     *
     * @param prismId   the prism block's UUID (from {@link PrismRecord#getId()})
     * @param playerUuid the UUID of the player performing the harvest (for faction membership check)
     * @return the number of FP awarded, or 0 if prism not found, no points, or player not in prism's faction
     */
    public int harvestPrism(@Nonnull UUID prismId, @Nonnull UUID playerUuid) {
        return harvestPrism(prismId, playerUuid, null, null);
    }

    /**
     * Harvests a prism block. Removes the block in the world via setBlockToAir (ref/store are ignored; kept for API compatibility).
     *
     * @param prismId   the prism block's UUID (from {@link PrismRecord#getId()})
     * @param playerUuid the UUID of the player performing the harvest (for faction membership check)
     * @param ref       ignored (kept for compatibility with PrismBlockPage)
     * @param store     ignored (kept for compatibility with PrismBlockPage)
     * @return the number of FP awarded, or 0 if prism not found, no points, or player not in prism's faction
     */
    public int harvestPrism(
            @Nonnull UUID prismId,
            @Nonnull UUID playerUuid,
            @Nullable Ref<EntityStore> ref,
            @Nullable Store<EntityStore> store
    ) {
        PrismRecord prism = PrismManager.getInstance().getById(prismId);
        if (prism == null) return 0;
        FactionInfo playerFaction = FactionManager.getInstance().getFactionFromPlayer(playerUuid);
        if (playerFaction == null || !playerFaction.getId().equals(prism.getFactionId())) {
            return 0;
        }
        int timeAliveMinutes = prism.getScoreMinutes(System.currentTimeMillis());
        if (timeAliveMinutes <= 0) return 0;
        int multiplier = PrismMultiplierService.getInstance().getMultiplier(prism.getChunkX(), prism.getChunkZ());
        int pointsToAdd = timeAliveMinutes * multiplier;
        int earned = FactionPointsManager.getInstance().awardPoints(prism.getFactionId(), "harvestPrism", pointsToAdd);

        String worldName = prism.getWorldName();
        int x = prism.getX();
        int y = prism.getY();
        int z = prism.getZ();
        PrismManager.getInstance().remove(worldName, new Vector3i(x, y, z));

        World world = Universe.get().getWorld(worldName);
        if (world != null) {
            world.execute(() -> setBlockToAir(world, x, y, z));
        }

        return earned;
    }

    /**
     * Cancels a prism: verifies the player is in the prism's faction, removes the prism from the world and DB,
     * and gives one Prism Block item back to the player.
     *
     * @param prismId    the prism UUID (from {@link PrismRecord#getId()})
     * @param playerUuid the player performing the cancel
     * @param ref        player entity ref (for giving item)
     * @param store      entity store
     * @return true if cancel succeeded (prism removed and item given), false otherwise
     */
    public boolean cancelPrism(
            @Nonnull UUID prismId,
            @Nonnull UUID playerUuid,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store
    ) {
        ConfigUtil.debug("[PrismCancel] cancelPrism called: prismId=" + prismId + " playerUuid=" + playerUuid
                + " ref=" + ref + " ref.isValid()=" + (ref != null && ref.isValid()) + " store=" + (store != null));
        PrismRecord prism = PrismManager.getInstance().getById(prismId);
        if (prism == null) {
            ConfigUtil.debug("[PrismCancel] prism not found for id=" + prismId + ", returning false");
            return false;
        }
        FactionInfo playerFaction = FactionManager.getInstance().getFactionFromPlayer(playerUuid);
        if (playerFaction == null || !playerFaction.getId().equals(prism.getFactionId())) {
            ConfigUtil.debug("[PrismCancel] faction check failed: playerFaction=" + (playerFaction != null ? playerFaction.getId() : "null")
                    + " prismFactionId=" + prism.getFactionId() + ", returning false");
            return false;
        }
        String worldName = prism.getWorldName();
        int x = prism.getX();
        int y = prism.getY();
        int z = prism.getZ();
        PrismManager.getInstance().remove(worldName, new Vector3i(x, y, z));
        ConfigUtil.debug("[PrismCancel] prism removed from manager, setting block to air at (" + x + "," + y + "," + z + ") world=" + worldName);

        World world = Universe.get().getWorld(worldName);
        if (world != null) {
            world.execute(() -> setBlockToAir(world, x, y, z));
        } else {
            ConfigUtil.debug("[PrismCancel] world not found: " + worldName + ", cannot set block to air");
        }

        ConfigUtil.debug("[PrismCancel] calling givePrismBlockToPlayer");
        givePrismBlockToPlayer(ref, store);
        return true;
    }

    private static void givePrismBlockToPlayer(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: ref=" + ref + " ref.isValid()=" + (ref != null && ref.isValid()) + " store=" + (store != null));
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: player component is null, cannot give item");
            return;
        }
        ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: player obtained, getInventory()=" + (player.getInventory() != null));
        try {
            com.hypixel.hytale.server.core.inventory.Inventory inv = player.getInventory();
            if (inv == null) {
                ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: inventory is null");
                return;
            }
            CombinedItemContainer combined = inv.getCombinedHotbarFirst();
            ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: getCombinedHotbarFirst()=" + (combined != null));
            // Use short item ID so client resolves it with mod namespace (Server/Item/Items/Prism_Block.json)
            String prismItemId = "Prism_Block";
            ItemStack stack = new ItemStack(prismItemId, 1);
            ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: ItemStack created with id=" + prismItemId + " getItemId()=" + stack.getItemId() + " qty=" + stack.getQuantity());
            if (combined != null) {
                com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction tx = combined.addItemStack(stack);
                ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: combined.addItemStack result: remainder=" + (tx != null ? tx.getRemainder() : "null"));
            } else {
                com.hypixel.hytale.server.core.inventory.container.ItemContainer storage = inv.getStorage();
                ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: using storage fallback, getStorage()=" + (storage != null));
                if (storage != null) {
                    com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction tx = storage.addItemStack(stack);
                    ConfigUtil.debug("[PrismCancel] givePrismBlockToPlayer: storage.addItemStack result: remainder=" + (tx != null ? tx.getRemainder() : "null"));
                }
            }
        } catch (Exception e) {
            ConfigUtil.debug("PrismService: givePrismBlockToPlayer failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the block at (x,y,z) to air/empty. Uses lowercase "air" to avoid "Unknown key! Air"
     * (engine rejects "hytale:air" / "Air"). If that fails, tries BlockType.EMPTY.getId().
     */
    private static void setBlockToAir(@Nonnull World world, int x, int y, int z) {
        try {
            world.setBlock(x, y, z, "air");
        } catch (IllegalArgumentException e) {
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType air = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY;
            String airId = (air != null && air.getId() != null) ? air.getId() : "air";
            world.setBlock(x, y, z, airId);
        }
    }

    @Nonnull
    public static PrismService getInstance() {
        return INSTANCE;
    }
}
