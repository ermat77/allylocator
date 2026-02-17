package com.factioncore.prism;

import com.factioncore.Main;
import com.factioncore.faction.FactionManager;
import com.factioncore.util.I18n;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Ticks prism attack countdown for players in PREPARING attacks. Uses EventTitleUtil (Hytale API, like delaytp).
 * Shows title at most once per integer second. Clears display state when combat starts or attack ends.
 */
public final class PrismAttackHudTicker {

    /** Last remaining-int (seconds) we showed per player; only show once per second. Cleared in clearHudForAttack. */
    private static final Map<UUID, Integer> lastShownSecondPerPlayer = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PrismAttack-HudTicker");
        t.setDaemon(true);
        return t;
    });

    static void start() {
        ticker.scheduleAtFixedRate(PrismAttackHudTicker::tick, 1, 1, TimeUnit.SECONDS);
    }

    static void clearHudForAttack(@Nonnull UUID attackId) {
        PrismAttackRecord attack = PrismAttackService.getInstance().getAttackById(attackId);
        if (attack == null) return;
        FactionManager.getInstance().getDatabaseManager()
                .loadPrismAttackParticipantsForAttacksAsync(List.of(attackId))
                .thenAccept(participants -> {
                    for (PrismAttackParticipantRecord p : participants) {
                        lastShownSecondPerPlayer.remove(p.getPlayerUuid());
                    }
                })
                .exceptionally(throwable -> null);
        // MultipleHUD (commented out): was calling MultipleHudBridge.hideCustomHud for each participant
    }

    private static void tick() {
        List<PrismAttackRecord> attacks = PrismAttackService.getInstance().getActiveAttacksForHud();
        if (attacks.isEmpty()) return;
        log(Level.FINE, "PrismAttackHudTicker tick: %d PREPARING attack(s)", attacks.size());
        for (PrismAttackRecord attack : attacks) {
            if (!"PREPARING".equals(attack.getStatus())) continue;
            World world = Universe.get().getWorld(attack.getAttackWorldName());
            if (world == null) {
                log(Level.INFO, "PrismAttackHudTicker: attack %s has null world for %s, skipping", attack.getAttackId(), attack.getAttackWorldName());
                continue;
            }
            try {
                List<PrismAttackParticipantRecord> participants = FactionManager.getInstance().getDatabaseManager()
                        .loadPrismAttackParticipantsForAttacksAsync(List.of(attack.getAttackId())).get();
                if (participants.isEmpty()) {
                    log(Level.FINE, "PrismAttackHudTicker: attack %s has no participants", attack.getAttackId());
                    continue;
                }
                final long endsAtMs = attack.getEndsAtMs();
                final UUID attackIdForLog = attack.getAttackId();
                final int participantCount = participants.size();
                world.execute(() -> {
                    Collection<PlayerRef> refs = world.getPlayerRefs();
                    if (refs == null) {
                        log(Level.INFO, "PrismAttackHudTicker: world %s returned null getPlayerRefs() for attack %s", world.getName(), attackIdForLog);
                        return;
                    }
                    int shown = 0;
                    long now = System.currentTimeMillis();
                    long remainingMs = endsAtMs - now;
                    int seconds = (int) Math.max(0, (remainingMs + 999) / 1000);
                    for (PrismAttackParticipantRecord p : participants) {
                        UUID playerUuid = p.getPlayerUuid();
                        for (PlayerRef pr : refs) {
                                if (pr != null && playerUuid.equals(pr.getUuid())) {
                                Integer lastShown = lastShownSecondPerPlayer.get(playerUuid);
                                if (seconds >= 1 && (lastShown == null || !lastShown.equals(seconds))) {
                                    lastShownSecondPerPlayer.put(playerUuid, seconds);
                                    String message = I18n.get("ui.prism_attack.timer_seconds", String.valueOf(seconds));
                                    if (message == null || message.isEmpty()) message = seconds + " seconds before the attack starts";
                                    Message title = Message.raw(message).color(Color.WHITE);
                                    Message sub = Message.raw("").color(Color.GRAY);
                                    EventTitleUtil.showEventTitleToPlayer(pr, title, sub, false, null, 0.5f, 0.1f, 0.1f);
                                    shown++;
                                }
                                break;
                            }
                        }
                    }
                    if (shown > 0) {
                        log(Level.FINE, "PrismAttackHudTicker: showed title for %d/%d participants in attack %s", shown, participantCount, attackIdForLog);
                    } else if (refs.isEmpty()) {
                        log(Level.FINE, "PrismAttackHudTicker: no players in world for attack %s", attackIdForLog);
                    }
                });
            } catch (Exception e) {
                log(Level.WARNING, "PrismAttackHudTicker: exception for attack %s: %s", attack.getAttackId(), e.getMessage());
                if (Main.get() != null) {
                    Main.get().getLogger().at(Level.FINE).withCause(e).log("[PrismAttackHudTicker] tick exception");
                }
            }
        }
    }

    private static void log(Level level, String format, Object... args) {
        if (Main.get() != null) {
            Main.get().getLogger().at(level).log("[PrismAttackHudTicker] " + String.format(format, args));
        }
    }
}
