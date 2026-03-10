package com.factioncore.prism;

import com.factioncore.Main;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.logging.Level;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * EntityTickingSystem that sets the prism attack countdown HUD for players in PREPARING attack worlds.
 * Runs in the world's entity tick (same context as MultipleHUD's example) so the HUD is displayed correctly.
 * To investigate HUD issues: set log level to FINE for this plugin to see per-tick flow (early returns and HUD set).
 */
public final class PrismAttackHudTickingSystem extends EntityTickingSystem<EntityStore> {

    public PrismAttackHudTickingSystem() {}

    @Override
    public void tick(float dt, int index, @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk,
                    @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            log(Level.FINE, "PrismAttackHud: early return - player or playerRef null (index=%s)", index);
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            log(Level.FINE, "PrismAttackHud: early return - world null for player %s", playerRef.getUuid());
            return;
        }
        String worldName = world.getName();
        if (!PrismAttackService.isAttackWorldName(worldName)) {
            log(Level.FINE, "PrismAttackHud: early return - not an attack world (worldName=%s)", worldName);
            return;
        }

        PrismAttackRecord attack = PrismAttackService.getInstance().getActiveAttackByWorldName(worldName);
        if (attack == null) {
            log(Level.FINE, "PrismAttackHud: no active attack for world %s (player %s) - see PrismAttackService getActiveAttackByWorldName log for cache", worldName, playerRef.getUuid());
            return;
        }
        if (!"PREPARING".equals(attack.getStatus())) {
            return;
        }

        // Show countdown from wall-clock so late joiners see correct remaining seconds on first tick.
        PrismAttackHudTicker.showCountdownForPlayerIfChanged(playerRef, attack);
        log(Level.FINE, "PrismAttackHud: player %s in PREPARING world %s (title shown by ticker if changed)", playerRef.getUuid(), worldName);
    }

    private static void log(Level level, String format, Object... args) {
        if (Main.get() != null) {
            Main.get().getLogger().at(level).log("[PrismAttackHud] " + String.format(format, args));
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
