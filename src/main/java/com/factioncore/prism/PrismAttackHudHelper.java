package com.factioncore.prism;

import com.factioncore.Main;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Helper for prism attack countdown display. Uses Hytale EventTitleUtil (like delaytp) instead of MultipleHUD.
 */
public final class PrismAttackHudHelper {

    private PrismAttackHudHelper() {}

    /** Clears countdown display state for all participants in the given attack. */
    public static void clearHudForAttack(@Nonnull java.util.UUID attackId) {
        PrismAttackHudTicker.clearHudForAttack(attackId);
    }

    /** Starts the ticker that shows countdown titles for players in PREPARING attacks. */
    public static void startHudTicker() {
        PrismAttackHudTicker.start();
    }

    /**
     * Shows the prism attack countdown title for a player in a PREPARING attack (e.g. after ClientReady).
     * Uses shared wall-clock logic so late joiners see the correct remaining seconds and lastShown is seeded.
     * Call from the world thread when the player is in the attack world.
     */
    public static void showHudForPlayerIfInActiveAttack(
            @Nonnull PlayerRef playerRef,
            @Nonnull Player player,
            @Nonnull PrismAttackRecord attack) {
        if (Main.get() != null) {
            Main.get().getLogger().at(Level.FINE).log("[PrismAttackHudHelper] showHudForPlayerIfInActiveAttack player=%s world=%s", playerRef.getUuid(), attack.getAttackWorldName());
        }
        PrismAttackHudTicker.showCountdownForPlayerIfChanged(playerRef, attack);
    }
}
