package com.factioncore.prism;

import com.factioncore.Main;
import com.factioncore.util.I18n;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * HUD that shows "XX seconds before the attack starts" for players in a PREPARING prism attack.
 * Used with the MultipleHUD mod so it can be displayed alongside other HUDs.
 */
public final class PrismAttackTimerHud extends CustomUIHud {

    private static final String UI_PATH = "HUD/PrismAttackTimerHUD.ui";
    private static final String TIMER_ELEMENT = "#TimerText.Text";

    private final long combatStartsAtMs;

    public PrismAttackTimerHud(@Nonnull PlayerRef playerRef, long combatStartsAtMs) {
        super(playerRef);
        this.combatStartsAtMs = combatStartsAtMs;
        log(Level.FINE, "PrismAttackTimerHud created for player %s, combatStartsAtMs=%d", playerRef.getUuid(), combatStartsAtMs);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append(UI_PATH);
        log(Level.FINE, "PrismAttackTimerHud build() appended UI path: %s", UI_PATH);
    }

    @Override
    public void update(boolean firstTime, @Nonnull UICommandBuilder uiCommandBuilder) {
        long now = System.currentTimeMillis();
        long remainingMs = combatStartsAtMs - now;
        int seconds = (int) Math.max(0, (remainingMs + 999) / 1000);
        String message = I18n.get("ui.prism_attack.timer_seconds", String.valueOf(seconds));
        if (message == null || message.isEmpty()) {
            message = seconds + " seconds before the attack starts";
        }
        uiCommandBuilder.set(TIMER_ELEMENT, message);
        if (firstTime) {
            log(Level.INFO, "PrismAttackTimerHud update(firstTime=true): set text to \"%s\"", message);
        }
    }

    private static void log(Level level, String format, Object... args) {
        if (Main.get() != null) {
            Main.get().getLogger().at(level).log("[PrismAttackTimerHud] " + String.format(format, args));
        }
    }
}
