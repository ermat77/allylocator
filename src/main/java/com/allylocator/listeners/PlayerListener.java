package com.allylocator.listeners;

import com.allylocator.Main;
import com.allylocator.systems.DisableOtherPlayersMarkersSystem;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import java.util.logging.Level;

/**
 * Listener for player-related events.
 * Handles player connection and ready events to apply map marker filters.
 */
public class PlayerListener {
    private final Main plugin;
    private final DisableOtherPlayersMarkersSystem markerSystem;
    
    /**
     * Creates a new PlayerListener instance.
     * 
     * @param plugin The main plugin instance
     * @param markerSystem The system that manages marker filtering
     */
    public PlayerListener(Main plugin, DisableOtherPlayersMarkersSystem markerSystem) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (markerSystem == null) {
            throw new IllegalArgumentException("MarkerSystem cannot be null");
        }
        
        this.plugin = plugin;
        this.markerSystem = markerSystem;
    }
    
    /**
     * Handles player connection events.
     * Applies the marker filter when a player connects to the server.
     * 
     * @param event The player connect event
     */
    public void onPlayerConnect(PlayerConnectEvent event) {
        try {
            if (event == null) {
                plugin.getLogger().at(Level.WARNING).log("PlayerConnectEvent is null");
                return;
            }
            
            // Note: getPlayer() is deprecated but still functional
            // Future versions should use getHolder() and getComponent() pattern
            var player = event.getPlayer();
            if (player == null) {
                plugin.getLogger().at(Level.WARNING).log("Player is null in PlayerConnectEvent");
                return;
            }
            
            markerSystem.applyFilter(player, event.getWorld());
        } catch (Exception e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e).log("Error handling PlayerConnectEvent: " + e.getMessage());
        }
    }
    
    /**
     * Handles player ready events.
     * Applies the marker filter when a player is ready in the world.
     * 
     * @param event The player ready event
     */
    public void onPlayerReady(PlayerReadyEvent event) {
        try {
            if (event == null) {
                plugin.getLogger().at(Level.WARNING).log("PlayerReadyEvent is null");
                return;
            }
            
            // Note: getPlayer() is deprecated but still functional
            // Future versions should use getHolder() and getComponent() pattern
            var player = event.getPlayer();
            if (player == null) {
                plugin.getLogger().at(Level.WARNING).log("Player is null in PlayerReadyEvent");
                return;
            }
            
            markerSystem.applyFilter(player, player.getWorld());
        } catch (Exception e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e).log("Error handling PlayerReadyEvent: " + e.getMessage());
        }
    }
}
