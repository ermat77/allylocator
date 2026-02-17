package com.factioncore.prism;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Event dispatched when a player chooses to join a prism attack (as attacker or defender).
 * The prism attack event handler runs pre-teleport checks and adds the Teleport component.
 */
public final class PlayerJoinPrismAttackEvent {

    private final PlayerRef playerRef;
    private final Ref<EntityStore> ref;
    private final Store<EntityStore> store;
    private final UUID prismId;
    /** "attacker" or "defender" */
    private final String role;

    public PlayerJoinPrismAttackEvent(
            @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nullable UUID prismId,
            @Nonnull String role
    ) {
        this.playerRef = playerRef;
        this.ref = ref;
        this.store = store;
        this.prismId = prismId;
        this.role = role != null ? role : "attacker";
    }

    @Nonnull public PlayerRef getPlayerRef() { return playerRef; }
    @Nonnull public Ref<EntityStore> getRef() { return ref; }
    @Nonnull public Store<EntityStore> getStore() { return store; }
    @Nullable public UUID getPrismId() { return prismId; }
    @Nonnull public String getRole() { return role; }
}
