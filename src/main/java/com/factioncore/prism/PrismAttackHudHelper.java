package com.factioncore.prism;

import com.factioncore.Main;
import com.factioncore.util.I18n;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.Collection;
import java.util.List;
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

    /**
     * Shows "Teleporting back in X seconds" to all participants currently in the attack world.
     * Call from the world thread (e.g. inside attackWorld.execute(...)).
     */
    public static void showTeleportBackCountdown(@Nonnull World attackWorld,
                                                 @Nonnull List<PrismAttackParticipantRecord> participants,
                                                 int secondsRemaining) {
        String message = I18n.get("ui.prism_attack.teleport_back_seconds", String.valueOf(secondsRemaining));
        if (message == null || message.isEmpty()) message = "Teleporting back in " + secondsRemaining + " seconds";
        Message title = Message.raw(message).color(Color.WHITE);
        Message sub = Message.raw("").color(Color.GRAY);
        Collection<PlayerRef> refs = attackWorld.getPlayerRefs();
        if (refs == null) return;
        for (PrismAttackParticipantRecord p : participants) {
            java.util.UUID playerUuid = p.getPlayerUuid();
            for (PlayerRef pr : refs) {
                if (pr != null && playerUuid.equals(pr.getUuid())) {
                    EventTitleUtil.showEventTitleToPlayer(pr, title, sub, false, null, 0.5f, 0.1f, 0.1f);
                    break;
                }
            }
        }
    }

    /**
     * Shows "Combat starts" / "Le combat commence" title and plays a sound to all participants in the attack world.
     * Call from the world thread (e.g. inside attackWorld.execute(...)).
     */
    public static void showCombatStartsMessage(@Nonnull World attackWorld,
                                               @Nonnull List<PrismAttackParticipantRecord> participants) {
        String message = I18n.get("ui.prism_attack.combat_starts");
        if (message == null || message.isEmpty()) message = "Combat starts";
        Message title = Message.raw(message).color(Color.WHITE);
        Message sub = Message.raw("").color(Color.GRAY);
        Collection<PlayerRef> refs = attackWorld.getPlayerRefs();
        if (refs == null) return;
        EntityStore entityStore = attackWorld.getEntityStore();
        if (entityStore == null) return;
        for (PrismAttackParticipantRecord p : participants) {
            java.util.UUID playerUuid = p.getPlayerUuid();
            for (PlayerRef pr : refs) {
                if (pr != null && playerUuid.equals(pr.getUuid())) {
                    EventTitleUtil.showEventTitleToPlayer(pr, title, sub, false, null, 0.5f, 0.1f, 0.1f);
                    playCombatStartSound(pr, entityStore);
                    break;
                }
            }
        }
    }

    /**
     * Plays a combat-start sound to the player. Follows the Hytale guide:
     * https://hytalemodding.dev/en/docs/guides/plugin/playing-sounds
     * Uses EntityModule.get().getTransformComponentType() for the transform and
     * SoundUtil.playSoundEvent3dToPlayer(ref, index, SoundCategory.UI, position, store.getStore()).
     */
    private static void playCombatStartSound(@Nonnull PlayerRef pr, @Nonnull EntityStore entityStore) {
        if (Main.get() != null) {
            Main.get().getLogger().at(Level.INFO).log("[PrismAttackHudHelper] playCombatStartSound: attempting for player " + pr.getUuid());
        }
        try {
            Ref<EntityStore> playerRef = pr.getReference();
            if (playerRef == null || !playerRef.isValid()) {
                if (Main.get() != null) {
                    Main.get().getLogger().at(Level.INFO).log("[PrismAttackHudHelper] playCombatStartSound: playerRef null or invalid for " + pr.getUuid());
                }
                return;
            }
            Store<EntityStore> store = entityStore.getStore();
            if (store == null) {
                if (Main.get() != null) {
                    Main.get().getLogger().at(Level.INFO).log("[PrismAttackHudHelper] playCombatStartSound: entityStore.getStore() null for player " + pr.getUuid());
                }
                return;
            }
            TransformComponent transform = store.getComponent(playerRef, EntityModule.get().getTransformComponentType());
            if (transform == null) {
                if (Main.get() != null) {
                    Main.get().getLogger().at(Level.INFO).log("[PrismAttackHudHelper] playCombatStartSound: no TransformComponent for player " + pr.getUuid());
                }
                return;
            }
            Object position = transform.getTransform().getPosition();
            Class<?> soundEventClass = Class.forName("com.hypixel.hytale.server.core.asset.type.SoundEvent");
            Object assetMap = soundEventClass.getMethod("getAssetMap").invoke(null);
            int index = ((Number) assetMap.getClass().getMethod("getIndex", String.class).invoke(assetMap, "SFX_Weapon_Charge_Swing")).intValue();
            if (Main.get() != null) {
                Main.get().getLogger().at(Level.INFO).log("[PrismAttackHudHelper] playCombatStartSound: sound index=" + index + " for player " + pr.getUuid());
            }
            Class<?> soundCategoryClass = Class.forName("com.hypixel.hytale.server.core.asset.type.SoundCategory");
            Object soundCategoryUI = Enum.valueOf((Class<Enum>) soundCategoryClass, "UI");
            Class.forName("com.hypixel.hytale.server.core.util.SoundUtil")
                    .getMethod("playSoundEvent3dToPlayer", Ref.class, int.class, soundCategoryClass, position.getClass(), Store.class)
                    .invoke(null, playerRef, index, soundCategoryUI, position, store);
            if (Main.get() != null) {
                Main.get().getLogger().at(Level.INFO).log("[PrismAttackHudHelper] playCombatStartSound: played successfully for player " + pr.getUuid());
            }
        } catch (Exception e) {
            if (Main.get() != null) {
                Main.get().getLogger().at(Level.INFO).log("[PrismAttackHudHelper] playCombatStartSound failed for player " + pr.getUuid() + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                Main.get().getLogger().at(Level.FINE).withCause(e).log("[PrismAttackHudHelper] playCombatStartSound exception");
            }
        }
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
            @Nonnull PrismAttackRecord attack) {
        if (Main.get() != null) {
            Main.get().getLogger().at(Level.FINE).log("[PrismAttackHudHelper] showHudForPlayerIfInActiveAttack player=%s world=%s", playerRef.getUuid(), attack.getAttackWorldName());
        }
        PrismAttackHudTicker.showCountdownForPlayerIfChanged(playerRef, attack);
    }
}
