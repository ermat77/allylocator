package com.factioncore.prism;

import com.factioncore.Main;
import com.factioncore.util.I18n;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nonnull;
import java.awt.Color;
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
     * Uses EventTitleUtil like delaytp. Call from the world thread when the player is in the attack world.
     */
    public static void showHudForPlayerIfInActiveAttack(
            @Nonnull PlayerRef playerRef,
            @Nonnull Player player,
            @Nonnull PrismAttackRecord attack) {
        if (Main.get() != null) {
            Main.get().getLogger().at(Level.FINE).log("[PrismAttackHudHelper] showHudForPlayerIfInActiveAttack player=%s world=%s", playerRef.getUuid(), attack.getAttackWorldName());
        }
        long remainingMs = attack.getEndsAtMs() - System.currentTimeMillis();
        int seconds = (int) Math.max(0, (remainingMs + 999) / 1000);
        String message = I18n.get("ui.prism_attack.timer_seconds", String.valueOf(seconds));
        if (message == null || message.isEmpty()) message = seconds + " seconds before the attack starts";
        Message title = Message.raw(message).color(Color.WHITE);
        Message sub = Message.raw("").color(Color.GRAY);
        EventTitleUtil.showEventTitleToPlayer(playerRef, title, sub, false, null, 0.5f, 0.1f, 0.1f);
        // MultipleHUD (commented out): MultipleHudBridge.setCustomHud(player, playerRef, new PrismAttackTimerHud(playerRef, attack.getEndsAtMs()));
    }
}
