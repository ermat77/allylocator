package com.factioncore.faction;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/**
 * ECS Component that tracks a player's faction membership.
 * 
 * This component is attached to player entities to indicate which faction they belong to.
 * Following Hytale ECS best practices, this is a lightweight component that only stores
 * the faction ID reference, not the full faction data.
 */
public class FactionComponent implements Component<EntityStore> {
    
    private UUID factionId;
    
    @Override
    public FactionComponent clone() {
        return new FactionComponent(this.factionId);
    }
    
    /**
     * Creates a new FactionComponent with the given faction ID.
     * 
     * @param factionId The UUID of the faction this player belongs to
     */
    public FactionComponent(UUID factionId) {
        this.factionId = factionId;
    }
    
    /**
     * Creates an empty FactionComponent (for deserialization).
     */
    public FactionComponent() {
        this.factionId = null;
    }
    
    /**
     * Gets the faction ID this player belongs to.
     * 
     * @return The faction UUID, or null if not in a faction
     */
    public UUID getFactionId() {
        return factionId;
    }
    
    /**
     * Sets the faction ID for this player.
     * 
     * @param factionId The faction UUID to set
     */
    public void setFactionId(UUID factionId) {
        this.factionId = factionId;
    }
    
    /**
     * Gets the component type for FactionComponent.
     * This follows Hytale's ECS pattern for component type access.
     * The component must be registered in the plugin's setup() method before this can be called.
     * 
     * @return The ComponentType instance for FactionComponent
     */
    public static ComponentType<EntityStore, FactionComponent> getComponentType() {
        com.factioncore.Main plugin = com.factioncore.Main.get();
        if (plugin == null) {
            throw new IllegalStateException("Main plugin instance is not available. Component may not be registered yet.");
        }
        ComponentType<EntityStore, FactionComponent> componentType = plugin.getFactionComponentType();
        if (componentType == null) {
            throw new IllegalStateException("FactionComponent has not been registered. Ensure it is registered in Main.setup().");
        }
        return componentType;
    }
}
