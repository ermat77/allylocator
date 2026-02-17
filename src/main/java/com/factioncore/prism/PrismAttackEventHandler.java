package com.factioncore.prism;

import javax.annotation.Nonnull;

/**
 * Handler for prism attack join events. Runs pre-teleport checks and adds the Teleport component.
 * Only one handler should be registered (e.g. the implementation that uses PrismAttackService).
 */
public interface PrismAttackEventHandler {

    /**
     * Called when a player has requested to join an attack or defense.
     * Run all pre-teleport checks; if they pass, assign spawn, insert participant, then
     * currentWorld.execute(() -> store.addComponent(ref, Teleport.getComponentType(), teleport)).
     * If any check fails, send a message to the player and do not add Teleport.
     */
    void onPlayerJoinPrismAttack(@Nonnull PlayerJoinPrismAttackEvent event);
}
