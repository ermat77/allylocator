# AllyLocator Plugin Summary

## Overview
**AllyLocator** is a Hytale server plugin that filters player markers on the world map to show only party/faction members. It provides a cleaner, more focused map experience by hiding all players except those in your group.

## Core Functionality

### Primary Purpose
- **Filters player markers** on the in-game world map
- **Shows only party/faction members** (excluding yourself)
- **Hides all other players** from the map view
- **Disables teleporting** to player markers

### How It Works

1. **Marker Provider Replacement**
   - Replaces the default player marker providers with a custom filtered provider
   - The custom provider (`PartyFilteredPlayerIconMarkerProvider`) only sends markers for players in the same party/faction
   - Automatically re-applies filters periodically to prevent default providers from re-appearing

2. **Party/Faction Detection**
   - Automatically detects and integrates with party/faction plugins:
     - **Hyfaction** (supports both parties and factions)
     - **SimpleClaims** (supports parties)
   - Uses Java Reflection to interact with these plugins without compile-time dependencies
   - Falls back gracefully if neither plugin is installed (hides all player markers)

3. **Event-Driven Filtering**
   - Applies filters when players connect to the server
   - Re-applies filters when players become ready in the world
   - Ensures filters are active for all players at all times

## Key Features

### ✅ Multi-Plugin Support
- Works with **Hyfaction** plugin (factions and parties)
- Works with **SimpleClaims** plugin (parties)
- Automatically detects which plugin is available
- No configuration required

### ✅ Automatic Filtering
- Filters are applied automatically when players join
- No commands needed - works out of the box
- Periodic checks ensure filters stay active

### ✅ Privacy Protection
- Hides your own marker from yourself
- Hides all non-party/faction players
- Only shows markers for players in your group

### ✅ Teleportation Disabled
- Prevents teleporting to player markers
- Adds an extra layer of gameplay balance

## Technical Architecture

### Main Components

1. **Main.java**
   - Plugin entry point
   - Initializes systems and registers event listeners
   - Manages plugin lifecycle (setup, start, shutdown)

2. **DisableOtherPlayersMarkersSystem**
   - Core filtering system
   - Manages marker provider replacement
   - Handles periodic filter re-application
   - Tracks monitored worlds

3. **PartyService**
   - Unified interface for party/faction plugins
   - Uses reflection to interact with Hyfaction and SimpleClaims
   - Handles plugin detection and initialization
   - Provides party membership checking methods

4. **PartyFilteredPlayerIconMarkerProvider**
   - Custom marker provider implementation
   - Filters markers based on party/faction membership
   - Respects distance limits and world map settings

5. **PlayerListener**
   - Handles player connection and ready events
   - Triggers filter application for each player

### Integration Method
- Uses **Java Reflection** to call methods from external plugins
- No compile-time dependencies on party/faction plugins
- Lazy initialization - only initializes when needed
- Graceful fallback if plugins are not available

## Behavior Scenarios

### Scenario 1: Hyfaction Installed
- Plugin detects Hyfaction at startup
- Checks for factions first, then parties
- Shows markers only for faction/party members
- Logs: "Hyfaction plugin detected and initialized"

### Scenario 2: SimpleClaims Installed
- Plugin detects SimpleClaims at startup
- Checks for party membership
- Shows markers only for party members
- Logs: "SimpleClaims plugin detected and initialized"

### Scenario 3: No Party Plugin Installed
- Plugin detects no party/faction plugins
- Hides all player markers (including your own)
- Logs: "None (all markers will be filtered)"
- Still functions, just with maximum privacy

### Scenario 4: Player Not in Party/Faction
- If viewing player has no party/faction, all other players are hidden
- Provides maximum privacy when not in a group

## Configuration

### Required
- None! The plugin works automatically.

### Optional Dependencies
- **Hyfaction** plugin (for faction/party support)
- **SimpleClaims** plugin (for party support)

### Build Configuration
- Supports code obfuscation via ProGuard
- Can build obfuscated JARs for distribution
- See `OBFUSCATION.md` for details

## Installation

1. Build the plugin: `./gradlew build`
2. Copy JAR to Hytale server plugins folder
3. (Optional) Install Hyfaction or SimpleClaims plugin
4. Restart server
5. Plugin activates automatically

## Version Information
- **Current Version**: 0.0.2
- **Package**: `com.allylocator`
- **Main Class**: `com.allylocator.Main`

## Use Cases

### For Players
- **Privacy**: Hide your location from non-party members
- **Clarity**: See only your allies on the map
- **Coordination**: Easily locate party/faction members

### For Server Admins
- **Enhanced Gameplay**: Encourages party/faction formation
- **Privacy Features**: Protects player locations
- **Zero Configuration**: Works immediately after installation

## Technical Notes

- Uses reflection to avoid compile-time dependencies
- Handles both "parties" and "factions" (Hyfaction supports both)
- Periodic task runs every 30 seconds to maintain filters
- WeakHashMap used for world tracking to prevent memory leaks
- TaskRegistry integration for automatic cleanup on plugin disable

## Future Enhancements (Potential)
- Configuration file for custom filtering rules
- Whitelist/blacklist support
- Distance-based filtering options
- Integration with additional party plugins
