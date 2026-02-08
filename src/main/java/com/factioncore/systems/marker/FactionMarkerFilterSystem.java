package com.factioncore.systems.marker;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * ECS System that periodically updates faction-based marker filters for players.
 * 
 * This system follows Hytale ECS best practices:
 * - Extends EntityTickingSystem to run every frame
 * - Uses queries to filter which entities to process
 * - Updates marker filters periodically (not every frame for performance)
 * 
 * The actual filtering logic is in FactionMarkerFilterService.
 */
public class FactionMarkerFilterSystem extends EntityTickingSystem<EntityStore> {
    
    private int tick = 0;
    private static final int UPDATE_INTERVAL_TICKS = 200; // Update every 10 seconds (200 ticks / 20 ticks per second)
    
    /**
     * Creates a new FactionMarkerFilterSystem.
     */
    public FactionMarkerFilterSystem() {
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
        // Increment tick counter
        this.tick++;
        
        // Only update filters periodically, not every frame
        if (this.tick < UPDATE_INTERVAL_TICKS) {
            return;
        }
        
        // Reset tick counter
        this.tick = 0;
        
        // Get the entity reference
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        
        // Get Player and PlayerRef components
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        
        if (player == null || playerRef == null) {
            return;
        }
        
        // Apply marker filter using the service
        FactionMarkerFilterService.applyFilterToPlayer(player, playerRef);
    }
}
