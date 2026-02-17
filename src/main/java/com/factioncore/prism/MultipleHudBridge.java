package com.factioncore.prism;

import com.factioncore.Main;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional bridge to the MultipleHUD mod (sources in MHUD-main).
 * NOT USED: prism attack countdown now uses Hytale EventTitleUtil (like delaytp) instead.
 * Kept for reference; uncomment usage in PrismAttackHudHelper, PrismAttackHudTicker, PrismAttackHudTickingSystem to re-enable.
 */
final class MultipleHudBridge {

    private static final String HUD_ID = "PrismAttackTimer";
    private static final String MULTIPLE_HUD_CLASS = "com.buuz135.mhud.MultipleHUD";

    @Nullable
    private static Object multipleHudInstance;
    @Nullable
    private static Method setCustomHudMethod;
    @Nullable
    private static Method hideCustomHudMethod;
    private static boolean resolved;

    private MultipleHudBridge() {}

    static void setCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull CustomUIHud hud) {
        ensureResolved();
        if (multipleHudInstance == null || setCustomHudMethod == null) {
            log(Level.INFO, "PrismAttack HUD not shown: MultipleHUD mod not present or setCustomHud method unavailable (instance=%s, method=%s)",
                    multipleHudInstance != null, setCustomHudMethod != null);
            return;
        }
        try {
            setCustomHudMethod.invoke(multipleHudInstance, player, playerRef, HUD_ID, hud);
            log(Level.INFO, "setCustomHud invoked for player %s (HUD should be visible if client supports it)", playerRef.getUuid());
        } catch (Exception e) {
            log(Level.WARNING, "setCustomHud failed for player %s: %s", playerRef.getUuid(), e.getMessage());
            if (Main.get() != null) {
                Main.get().getLogger().at(Level.FINE).withCause(e).log("[PrismAttack HUD] setCustomHud exception");
            }
        }
    }

    static void hideCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        ensureResolved();
        if (multipleHudInstance == null || hideCustomHudMethod == null) return;
        try {
            // Mod API: hideCustomHud(Player, String hudIdentifier) — 2-arg is current; 3-arg (with PlayerRef) is deprecated
            hideCustomHudMethod.invoke(multipleHudInstance, player, HUD_ID);
        } catch (Exception e) {
            log(Level.FINE, "hideCustomHud failed for player %s: %s", playerRef.getUuid(), e.getMessage());
        }
    }

    private static void ensureResolved() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> clazz = Class.forName(MULTIPLE_HUD_CLASS);
            Method getInstance = clazz.getMethod("getInstance");
            multipleHudInstance = getInstance.invoke(null);
            if (multipleHudInstance != null) {
                setCustomHudMethod = clazz.getMethod("setCustomHud", Player.class, PlayerRef.class, String.class, CustomUIHud.class);
                hideCustomHudMethod = clazz.getMethod("hideCustomHud", Player.class, String.class);
                log(Level.INFO, "PrismAttack HUD: MultipleHUD mod found, bridge ready");
            } else {
                log(Level.INFO, "PrismAttack HUD: MultipleHUD class found but getInstance() returned null");
            }
        } catch (Exception e) {
            log(Level.INFO, "PrismAttack HUD: MultipleHUD mod not present (class not found or API mismatch): %s", e.getMessage());
        }
    }

    private static void log(Level level, String format, Object... args) {
        if (Main.get() != null) {
            Main.get().getLogger().at(level).log("[PrismAttack HUD] " + String.format(format, args));
        }
    }
}
