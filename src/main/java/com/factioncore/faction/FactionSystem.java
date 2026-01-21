package com.factioncore.faction;

import com.factioncore.util.ConfigUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * ECS System that synchronizes FactionComponent with FactionManager.
 * 
 * This system ensures that when a player joins a world, their FactionComponent
 * is properly set based on their faction membership in FactionManager.
 * 
 * Following Hytale ECS best practices, this is an EntityTickingSystem that runs
 * every frame to keep components in sync with the manager state.
 */
public class FactionSystem extends EntityTickingSystem<EntityStore> {
    
    private final FactionManager factionManager;
    
    /**
     * Creates a new FactionSystem.
     */
    public FactionSystem() {
        this.factionManager = FactionManager.getInstance();
    }
    
    @Override
    @NullableDecl
    public Query<EntityStore> getQuery() {
        // Only process entities that have a PlayerRef component
        // This ensures we only process players, not other entities
        // ComponentType implements Query directly, so we can cast it
        // This matches the pattern used by hyfaction (CombatLogTickingSystem, etc.)
        // If PlayerRef.getComponentType() is null (not yet initialized), use Query.any() as fallback
        // The tick method already filters for PlayerRef, so this is safe
        ComponentType<?, ?> playerRefType = PlayerRef.getComponentType();
        if (playerRefType != null) {
            return (Query<EntityStore>) playerRefType;
        }
        // Fallback to Query.any() if PlayerRef is not yet initialized
        // This prevents NullPointerException during system registration
        return Query.any();
    }
    
    @Override
    public void tick(float dt, int index, @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk, 
                     @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        
        // Get PlayerRef to access player UUID
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            ConfigUtil.verboseDebug("FactionSystem.tick(): playerRef is null, skipping");
            return;
        }
        
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            ConfigUtil.verboseDebug("FactionSystem.tick(): playerUuid is null, skipping");
            return;
        }
        
        ConfigUtil.verboseDebug("FactionSystem.tick(): processing player " + playerUuid);
        
        // Get current faction from FactionManager
        FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
        ConfigUtil.verboseDebug("FactionSystem.tick(): player " + playerUuid + " faction=" + (faction != null ? faction.getName() : "null"));
        
        // Get or create FactionComponent
        FactionComponent factionComponent = store.getComponent(ref, FactionComponent.getComponentType());
        ConfigUtil.verboseDebug("FactionSystem.tick(): existing component=" + (factionComponent != null ? factionComponent.getFactionId() : "null"));
        
        if (faction != null) {
            // Player is in a faction - ensure component exists and is correct
            if (factionComponent == null) {
                // Add component if it doesn't exist
                ConfigUtil.verboseDebug("FactionSystem.tick(): adding FactionComponent for player " + playerUuid);
                factionComponent = new FactionComponent(faction.getId());
                commandBuffer.addComponent(ref, FactionComponent.getComponentType(), factionComponent);
            } else if (!faction.getId().equals(factionComponent.getFactionId())) {
                // Update component if faction ID changed - use replaceComponent
                ConfigUtil.verboseDebug("FactionSystem.tick(): updating FactionComponent for player " + playerUuid + 
                               " (old=" + factionComponent.getFactionId() + ", new=" + faction.getId() + ")");
                factionComponent = new FactionComponent(faction.getId());
                commandBuffer.replaceComponent(ref, FactionComponent.getComponentType(), factionComponent);
            } else {
                ConfigUtil.verboseDebug("FactionSystem.tick(): component already correct for player " + playerUuid);
            }
        } else {
            // Player is not in a faction - remove component if it exists
            if (factionComponent != null) {
                ConfigUtil.verboseDebug("FactionSystem.tick(): removing FactionComponent for player " + playerUuid);
                commandBuffer.removeComponent(ref, FactionComponent.getComponentType());
            } else {
                ConfigUtil.verboseDebug("FactionSystem.tick(): player " + playerUuid + " has no faction and no component");
            }
        }
    }
}
