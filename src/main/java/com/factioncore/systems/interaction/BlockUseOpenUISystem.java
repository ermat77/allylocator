package com.factioncore.systems.interaction;

import com.factioncore.Main;
import com.factioncore.config.PrismMultiplierConfig;
import com.factioncore.faction.FactionInfo;
import com.factioncore.faction.FactionManager;
import com.factioncore.prism.PrismManager;
import com.factioncore.prism.PrismRecord;
import com.factioncore.prism.PrismMultiplierService;
import com.factioncore.ui.ExampleBlockPage;
import com.factioncore.ui.factionprism.OwnPrismPage;
import com.factioncore.ui.factionprism.OtherPrismPage;
import com.factioncore.ui.factionprism.PrismBlockPage;
import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * When the player uses (F key) a block that is configured as "open UI",
 * opens the corresponding UI page. Block type ID is matched case-insensitively.
 * <p>
 * - {@value #BLOCK_TYPE_ID_INTERACTIVE} opens ExampleBlockPage.
 * - {@value #BLOCK_TYPE_ID_PRISM_BLOCK}: opens OwnPrismPage (own faction), OtherPrismPage (other faction),
 *   or PrismBlockPage (fallback when no faction / no placer).
 */
public class BlockUseOpenUISystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {

    /**
     * Block type ID that triggers opening the example block UI when used (F).
     * Format is typically "group:ItemName", e.g. com.factioncore:Interactive_Block.
     */
    public static final String BLOCK_TYPE_ID_INTERACTIVE = "com.factioncore:Interactive_Block";

    /**
     * Block type ID for the Prism Block (custom model, opens UI with Attack button).
     */
    public static final String BLOCK_TYPE_ID_PRISM_BLOCK = "com.factioncore:Prism_Block";

    public BlockUseOpenUISystem() {
        super(UseBlockEvent.Post.class);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                      @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                      @Nonnull UseBlockEvent.Post event) {
        String blockId = event.getBlockType().getId();
        if (blockId == null) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        if (isPrismBlockType(blockId)) {
            World world = player.getWorld();
            String worldName = world != null ? world.getName() : null;
            var targetBlock = event.getTargetBlock();
            PrismRecord prism = worldName != null
                ? PrismManager.getInstance().getByPosition(worldName, targetBlock)
                : null;
            FactionInfo placerFaction = prism != null
                ? FactionManager.getInstance().getFactionById(prism.getFactionId())
                : null;
            FactionInfo interactorFaction = FactionManager.getInstance().getFactionFromPlayer(playerRef.getUuid());
            int timeAlive = prism != null ? prism.getScoreMinutes(System.currentTimeMillis()) : 0;
            boolean isOwnFaction = interactorFaction != null && placerFaction != null
                    && interactorFaction.getId().equals(placerFaction.getId());
            boolean isOtherFaction = interactorFaction != null && placerFaction != null
                    && !interactorFaction.getId().equals(placerFaction.getId());
            ConfigUtil.debug("[PrismBlockUse] blockId=" + blockId
                    + " interactor=" + playerRef.getUuid()
                    + " block=(" + targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ() + ")"
                    + " world=" + worldName
                    + " prismFound=" + (prism != null)
                    + " placerFaction=" + (placerFaction != null ? placerFaction.getId() : "null")
                    + " interactorFaction=" + (interactorFaction != null ? interactorFaction.getId() : "null")
                    + " timeAliveMin=" + timeAlive
                    + " isOwnFaction=" + isOwnFaction
                    + " isOtherFaction=" + isOtherFaction);
            if (isOwnFaction && prism != null) {
                int chunkX = prism.getChunkX();
                int chunkZ = prism.getChunkZ();
                int effectMultiplier = PrismMultiplierService.getInstance().getMultiplier(chunkX, chunkZ);
                Main main = Main.get();
                PrismMultiplierConfig multConfig = main != null && main.getPrismMultiplierConfig() != null
                    ? main.getPrismMultiplierConfig().get() : null;
                ConfigUtil.debug("[PrismBlockUse] opening OwnPrismPage: timeAlive=" + timeAlive
                        + " chunk=(" + chunkX + "," + chunkZ + ")"
                        + " effectMultiplier=" + effectMultiplier
                        + " prismMultiplierConfig=" + (multConfig != null
                            ? ("spawnChunkX=" + multConfig.getSpawnChunkX() + " spawnChunkZ=" + multConfig.getSpawnChunkZ()
                                + " maxChunkDistances=" + multConfig.getMaxChunkDistances()
                                + " multipliers=" + multConfig.getMultipliers())
                            : "null"));
                OwnPrismPage page = new OwnPrismPage(playerRef, timeAlive, effectMultiplier, prism.getId());
                player.getPageManager().openCustomPage(ref, store, page);
            } else if (isOtherFaction) {
                OtherPrismPage page = new OtherPrismPage(playerRef, placerFaction, prism != null ? prism.getId() : null);
                player.getPageManager().openCustomPage(ref, store, page);
            } else {
                // No faction / no placer: fallback to minimal PrismBlockPage (no Harvest/Attack)
                PrismBlockPage page = new PrismBlockPage(playerRef, timeAlive, placerFaction, interactorFaction, prism != null ? prism.getId() : null);
                player.getPageManager().openCustomPage(ref, store, page);
            }
            return;
        }
        if (BLOCK_TYPE_ID_INTERACTIVE.equalsIgnoreCase(blockId)) {
            ExampleBlockPage page = new ExampleBlockPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    /**
     * Returns true if the block type id is the Prism Block (full id, short form, or namespaced).
     * Use for both block type (UseBlockEvent, BreakBlockEvent) and item id (ItemStack) checks.
     */
    public static boolean isPrismBlockType(String blockId) {
        if (blockId == null) return false;
        return BLOCK_TYPE_ID_PRISM_BLOCK.equalsIgnoreCase(blockId)
                || "Prism_Block".equalsIgnoreCase(blockId)
                || blockId.endsWith(":Prism_Block");
    }
}
