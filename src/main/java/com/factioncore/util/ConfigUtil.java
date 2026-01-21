package com.factioncore.util;

import com.factioncore.Main;
import com.factioncore.config.GeneralConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.Config;

import java.util.logging.Level;

/**
 * Utility class for managing plugin configuration.
 * 
 * Provides access to configuration values from GeneralConfig (CODEC-based).
 * Uses Main.getGeneralConfig() to access the configuration.
 */
public class ConfigUtil {
    
    private static HytaleLogger LOGGER;
    
    /**
     * Gets the logger instance, initializing it lazily if needed.
     * 
     * @return The HytaleLogger instance (never null)
     */
    private static HytaleLogger getLogger() {
        if (LOGGER == null) {
            try {
                LOGGER = HytaleLogger.forEnclosingClass();
            } catch (Exception e) {
                // Fallback if forEnclosingClass fails
            }
            // If still null, use alternative initialization
            if (LOGGER == null) {
                LOGGER = HytaleLogger.getLogger().getSubLogger("ConfigUtil");
            }
        }
        return LOGGER;
    }
    
    /**
     * Gets the GeneralConfig instance from Main plugin.
     * 
     * @return The GeneralConfig instance, or null if plugin is not initialized
     */
    private static GeneralConfig getGeneralConfig() {
        Main main = Main.get();
        if (main == null) {
            return null;
        }
        Config<GeneralConfig> config = main.getGeneralConfig();
        if (config == null) {
            return null;
        }
        try {
            // Try to get the config, but handle case where it's not loaded yet
            GeneralConfig result = config.get();
            return result;
        } catch (IllegalStateException e) {
            // Config is not loaded yet - try to load it
            try {
                config.load().join();
                return config.get();
            } catch (Exception loadException) {
                // Still failed, return null
                return null;
            }
        }
    }
    
    /**
     * Gets a singleton instance for backward compatibility.
     * Note: This is now a no-op since we use GeneralConfig directly.
     * 
     * @return The ConfigUtil instance
     */
    public static synchronized ConfigUtil getInstance() {
        // Return a dummy instance for backward compatibility
        // Actual config is accessed via getGeneralConfig()
        return new ConfigUtil();
    }
    
    /**
     * Checks if debug mode is enabled.
     * 
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean isDebug() {
        GeneralConfig config = getGeneralConfig();
        return config != null && config.isDebug();
    }
    
    /**
     * Sets debug mode.
     * Note: This method now updates GeneralConfig via Hytale's config system.
     * 
     * @param debug true to enable debug mode, false to disable
     */
    public void setDebug(boolean debug) {
        GeneralConfig config = getGeneralConfig();
        if (config != null) {
            config.setDebug(debug);
            if (debug) {
                getLogger().at(Level.INFO).log("Debug mode enabled");
            } else {
                getLogger().at(Level.INFO).log("Debug mode disabled");
            }
        }
    }
    
    /**
     * Logs a debug message if debug mode is enabled.
     * 
     * @param message The message to log
     */
    public static void debug(String message) {
        GeneralConfig config = getGeneralConfig();
        if (config != null && config.isDebug()) {
            getLogger().at(Level.INFO).log("[DEBUG] " + message);
        }
    }
    
    /**
     * Logs a debug message with exception if debug mode is enabled.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public static void debug(String message, Throwable throwable) {
        GeneralConfig config = getGeneralConfig();
        if (config != null && config.isDebug()) {
            getLogger().at(Level.INFO).withCause(throwable).log("[DEBUG] " + message);
        }
    }
    
    /**
     * Checks if verbose debug mode is enabled.
     * Verbose debug includes tick function logging which can be very verbose.
     * 
     * @return true if both debug and verboseDebug are enabled, false otherwise
     */
    public static boolean isVerboseDebug() {
        GeneralConfig config = getGeneralConfig();
        return config != null && config.isDebug() && config.isVerboseDebug();
    }
    
    /**
     * Logs a verbose debug message if both debug and verboseDebug modes are enabled.
     * Use this for tick functions and other frequently-called code that would spam logs.
     * 
     * @param message The message to log
     */
    public static void verboseDebug(String message) {
        if (isVerboseDebug()) {
            getLogger().at(Level.INFO).log("[VERBOSE] " + message);
        }
    }
    
    /**
     * Logs a verbose debug message with exception if both debug and verboseDebug modes are enabled.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public static void verboseDebug(String message, Throwable throwable) {
        if (isVerboseDebug()) {
            getLogger().at(Level.INFO).withCause(throwable).log("[VERBOSE] " + message);
        }
    }
}
