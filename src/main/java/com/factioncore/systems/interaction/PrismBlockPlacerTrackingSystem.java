package com.factioncore.systems.interaction;

import com.factioncore.faction.FactionManager;
import com.factioncore.prism.PrismManager;
import com.factioncore.util.ConfigUtil;
import com.factioncore.util.I18n;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * When a player places a Prism_Block: enforces one active prism per chunk (cancel + message if duplicate),
 * then records prism in PrismManager. Ignores non-Prism_Block placements.
 * Logs decisions when debug mode is enabled (ConfigUtil).
 */
public class PrismBlockPlacerTrackingSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public PrismBlockPlacerTrackingSystem() {
        super(PlaceBlockEvent.class);
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

    private static final String LOG_PREFIX = "[PrismBlockPlacer] ";

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull PlaceBlockEvent event) {
        if (!isPrismBlock(event.getItemInHand())) {
            ConfigUtil.debug(LOG_PREFIX + "ignored: not Prism_Block, itemInHand="
                    + (event.getItemInHand() == null ? "null" : event.getItemInHand().getItemId()));
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            ConfigUtil.debug(LOG_PREFIX + "ignored: ref null or invalid");
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            ConfigUtil.debug(LOG_PREFIX + "ignored: no PlayerRef");
            return;
        }

        UUID placerUuid = playerRef.getUuid();
        if (placerUuid == null) {
            ConfigUtil.debug(LOG_PREFIX + "ignored: placer UUID null");
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            ConfigUtil.debug(LOG_PREFIX + "denied: no world, player=" + placerUuid);
            return;
        }

        var faction = FactionManager.getInstance().getFactionFromPlayer(placerUuid);
        if (faction == null) {
            ConfigUtil.debug(LOG_PREFIX + "denied: player not in a faction, player=" + placerUuid);
            return;
        }

        Vector3i targetBlock = event.getTargetBlock();
        String worldName = world.getName();
        int chunkX = targetBlock.getX() >> 4;
        int chunkZ = targetBlock.getZ() >> 4;

        if (PrismManager.getInstance().hasPrismInChunk(worldName, chunkX, chunkZ)) {
            ConfigUtil.debug(LOG_PREFIX + "denied: prism already in chunk itemId=" + event.getItemInHand().getItemId()
                    + " world=" + worldName
                    + " chunk=(" + chunkX + "," + chunkZ + ") block=(" + targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ()
                    + ") player=" + placerUuid + " faction=" + faction.getId() + " (event cancelled)");
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw(I18n.get("chat.prism_block.one_per_chunk")).color(Color.RED));
            }
            event.setCancelled(true);
            return;
        }

        ConfigUtil.debug(LOG_PREFIX + "allowed: itemId=" + event.getItemInHand().getItemId()
                + " placed at (" + targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ()
                + ") chunk=(" + chunkX + "," + chunkZ + ") world=" + worldName
                + " player=" + placerUuid + " faction=" + faction.getId());
        PrismManager.getInstance().put(worldName, targetBlock, faction.getId(), placerUuid);
    }

    private static boolean isPrismBlock(@Nullable ItemStack itemInHand) {
        if (itemInHand == null) return false;
        String itemId = itemInHand.getItemId();
        if (itemId == null) return false;
        // ItemStack may return full id (com.factioncore:Prism_Block) or short form (Prism_Block)
        return BlockUseOpenUISystem.BLOCK_TYPE_ID_PRISM_BLOCK.equals(itemId)
                || "Prism_Block".equals(itemId)
                || itemId.endsWith(":Prism_Block");
    }
}
