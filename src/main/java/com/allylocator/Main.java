package com.allylocator;

import com.allylocator.listeners.PlayerListener;
import com.allylocator.systems.DisableOtherPlayersMarkersSystem;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import java.util.logging.Level;

/**
 * Main plugin class for AllyLocator.
 * Disables other players' map markers except for party/faction members.
 */
public class Main extends JavaPlugin {

    private DisableOtherPlayersMarkersSystem disableMarkersSystem;
    private PlayerListener playerListener;

    /**
     * Creates a new Main plugin instance.
     * 
     * @param init The plugin initialization data
     */
    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
    }

    /**
     * Called during plugin setup phase.
     * Registers components, systems, commands, and events.
     */
    @Override
    protected void setup() {
        super.setup();
        
        try {
            // Initialize system to disable other players' markers
            this.disableMarkersSystem = new DisableOtherPlayersMarkersSystem(
                getLogger(), 
                getTaskRegistry()
            );
            
            // Initialize listener
            this.playerListener = new PlayerListener(this, disableMarkersSystem);
            
            // Register events
            registerEvents();
            
            String pluginStatus = com.allylocator.systems.PartyService.getPluginStatus();
            getLogger().at(Level.INFO).log("AllyLocator plugin setup complete! Party plugin: " + pluginStatus);
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error during plugin setup: " + e.getMessage());
            throw e; // Re-throw to prevent plugin from loading in an invalid state
        }
    }

    /**
     * Called after all plugins have been set up.
     * Suitable for starting background tasks or initializing plugin state.
     */
    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("AllyLocator plugin started!");
    }

    /**
     * Called when the server is shutting down.
     * Provides a place to clean up resources and save data.
     */
    @Override
    protected void shutdown() {
        try {
            getLogger().at(Level.INFO).log("AllyLocator plugin is shutting down...");
            
            // Cleanup resources
            if (disableMarkersSystem != null) {
                disableMarkersSystem.shutdown();
            }
            
            getLogger().at(Level.INFO).log("AllyLocator plugin shutdown complete!");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error during plugin shutdown: " + e.getMessage());
        }
    }

    /**
     * Registers all event listeners.
     */
    private void registerEvents() {
        try {
            getEventRegistry().registerGlobal(PlayerConnectEvent.class, playerListener::onPlayerConnect);
            getEventRegistry().registerGlobal(PlayerReadyEvent.class, playerListener::onPlayerReady);
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error registering events: " + e.getMessage());
            throw e; // Re-throw to prevent plugin from loading without event handlers
        }
    }
}
