package com.factioncore.prism;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches {@link PlayerJoinPrismAttackEvent} to registered handlers.
 * UI (OtherPrismPage, FactionPrismsPage) calls {@link #dispatch(PlayerJoinPrismAttackEvent)};
 * the prism attack event handler runs checks and adds Teleport.
 */
public final class PrismAttackEventDispatcher {

    private static final PrismAttackEventDispatcher INSTANCE = new PrismAttackEventDispatcher();

    private final List<PrismAttackEventHandler> handlers = new CopyOnWriteArrayList<>();

    private PrismAttackEventDispatcher() {}

    @Nonnull
    public static PrismAttackEventDispatcher getInstance() {
        return INSTANCE;
    }

    public void addHandler(@Nonnull PrismAttackEventHandler handler) {
        handlers.add(handler);
    }

    public void removeHandler(@Nonnull PrismAttackEventHandler handler) {
        handlers.remove(handler);
    }

    /**
     * Dispatches the event to all registered handlers. Called from UI when player clicks Attack or Defend.
     */
    public void dispatch(@Nonnull PlayerJoinPrismAttackEvent event) {
        for (PrismAttackEventHandler handler : handlers) {
            handler.onPlayerJoinPrismAttack(event);
        }
    }
}
