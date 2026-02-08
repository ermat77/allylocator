# FactionCore Plugin - Technical Documentation

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [ECS Integration](#ecs-integration)
4. [Component Registration](#component-registration)
5. [Core Components](#core-components)
6. [Chunk Claiming System](#chunk-claiming-system)
7. [Protection Systems](#protection-systems)
8. [Configuration System](#configuration-system)
9. [Command System](#command-system)
10. [Logging System](#logging-system)
11. [Permissions System](#permissions-system)
12. [Data Flow](#data-flow)
13. [Performance Considerations](#performance-considerations)
14. [Extension Points](#extension-points)
15. [File Storage Best Practices](#file-storage-best-practices)
16. [World Map Integration](#world-map-integration)
17. [UI System](#ui-system)

---

## Overview

FactionCore is a Hytale server plugin implementing a faction system with map marker filtering. Built on Hytale's Entity Component System (ECS) for efficient, scalable faction management.

**Key Features:**
- Faction creation, membership management
- Chunk claiming and protection system
- Interactive chunk visualizer UI with click-to-claim functionality
- World map integration showing claimed chunks with faction colors
- SQLite database persistence (with in-memory caching for fast lookups)
- Map marker filtering (faction-only visibility)
- ECS-based architecture
- Event-driven updates
- Block protection (break, place, interact)

---

## Architecture

### System Architecture

```
┌─────────────────────────────────────────────┐
│         FactionCore Plugin                  │
├─────────────────────────────────────────────┤
│                                             │
│  Commands → FactionManager → ECS Systems   │
│     ↓              ↓              ↓         │
│  /f create    JSON Storage   Components    │
│                                             │
│  Events → MarkerFilterService → Filters    │
│                                             │
└─────────────────────────────────────────────┘
```

### Component Layers

1. **Command Layer**: User commands (`/f create`, `/f show`)
2. **Manager Layer**: Business logic & persistence (`FactionManager`, `FactionPermissionManager`)
3. **ECS Layer**: Component sync & filtering (`FactionSystem`, `FactionMarkerFilterSystem`)
4. **Service Layer**: Reusable logic (`FactionMarkerFilterService`)
5. **Utility Layer**: Logging, permissions, config (`ConfigUtil`, `PermissionUtil`)

### Data Flow Architecture

```
User Command/Event
    ↓
FactionManager (Singleton - Source of Truth)
    ├─ In-Memory Maps (PRIMARY storage during gameplay)
    │  ├─ Factions (ConcurrentHashMap)
    │  ├─ Chunk Claims (ConcurrentHashMap)
    │  └─ Pending Invites (ConcurrentHashMap)
    └─ DatabaseManager (ONLY for persistence)
       └─ SQLite Database (factioncore.db)
    ↓
FactionPermissionManager (Singleton - Permission Cache)
    ├─ Thread-Safe Cache (ConcurrentHashMap)
    │  └─ Faction Permissions (factionId → memberId → permissions)
    └─ Async LuckPerms Sync (ExecutorService)
       └─ Context-Based Permissions (faction=<uuid>)
    ↓
Database Persistence ← → In-Memory Maps
    ↓
ECS Systems (Synchronize Components)
    ├─ FactionSystem (Component Sync)
    ├─ FactionMarkerFilterSystem (Marker Filtering)
    └─ Protection Systems (Block Protection)
    ↓
Hytale EntityStore (Player Entities)
```

**Key Design Principles:**
- **Single Source of Truth**: `FactionManager` holds all faction data
- **Permission Management**: `FactionPermissionManager` manages member permissions with LuckPerms integration
- **Component Synchronization**: ECS systems keep components in sync with manager
- **Separation of Concerns**: Each layer has distinct responsibilities
- **Event-Driven**: Systems respond to changes automatically
- **Thread Safety**: All managers use thread-safe data structures (`ConcurrentHashMap`, `ExecutorService`)

---

## ECS Integration

### ECS Overview

Entity Component System pattern:
- **Entities**: Game objects (players)
- **Components**: Data attached to entities (`FactionComponent`)
- **Systems**: Logic processing entities (`FactionSystem`)

### Components

#### `FactionComponent`
- **Purpose**: Lightweight faction reference on player entities
- **Data**: Only stores faction UUID (8 bytes)
- **Why UUID only?**: Components should be lightweight; full data in `FactionManager`

### Systems

#### `FactionSystem`
- **Type**: `EntityTickingSystem<EntityStore>`
- **Query**: All entities with `PlayerRef` component
- **Behavior**: Every frame, syncs `FactionComponent` with `FactionManager` state
- **Logic**: Add/update/remove component to match manager
- **Component Modifications**: Uses `CommandBuffer` to defer component changes (required by Hytale ECS - cannot modify components directly during tick)
- **Logging**: Uses `ConfigUtil.verboseDebug()` to avoid log spam (only logs when `verboseDebug: true`)

#### `FactionMarkerFilterSystem`
- **Type**: `EntityTickingSystem<EntityStore>`
- **Query**: All entities with `PlayerRef` component
- **Behavior**: Updates marker filters every 200 ticks (10 seconds)
- **Optimization**: Reduces CPU usage by 95% vs. per-frame updates
- **Logging**: Uses `ConfigUtil.verboseDebug()` to avoid log spam (only logs when `verboseDebug: true`)

---

## Component Registration

### Overview

In Hytale's ECS system, **custom components must be explicitly registered** during plugin initialization. Unlike some ECS frameworks, Hytale does not automatically discover or register components - they must be registered manually in the plugin's `setup()` method.

### Why Registration is Required

Hytale's ECS uses `ComponentType` objects as efficient identifiers for components. These types are:
- **Performance-optimized**: Use integer indices internally for fast lookups
- **Type-safe**: Provide compile-time and runtime type checking
- **Registry-managed**: Must be registered with the `ComponentRegistry` before use

**Critical**: Attempting to use a component before registration will result in runtime errors:
```
java.lang.RuntimeException: Failed to get ComponentType for FactionComponent
Caused by: java.lang.NoSuchMethodException: ComponentType.getComponentType(java.lang.Class)
```

### Registration Pattern

The standard pattern for registering components follows these steps:

1. **Register in Plugin Setup**: Register the component in `Main.setup()`
2. **Store ComponentType**: Save the returned `ComponentType` in a plugin field
3. **Provide Accessor Method**: Add a method to retrieve the `ComponentType`
4. **Implement getComponentType()**: Component class retrieves type from plugin

### Registration Methods

Hytale provides two methods for component registration:

#### Method 1: Simple Registration (No Serialization)

For components that don't need to be saved/loaded from disk:

```java
// In Main.setup()
this.factionComponentType = getEntityStoreRegistry().registerComponent(
    FactionComponent.class, 
    FactionComponent::new  // Supplier for creating new instances
);
```

**Use when**:
- Component is runtime-only (not persisted)
- Component data is lightweight and can be reconstructed
- Component doesn't need to survive server restarts

#### Method 2: Registration with CODEC (Serialization)

For components that need to be saved/loaded:

```java
// In Main.setup()
this.playerMemoriesComponentType = getEntityStoreRegistry().registerComponent(
    PlayerMemories.class,
    "PlayerMemories",           // String ID for serialization
    PlayerMemories.CODEC         // BuilderCodec for serialization
);
```

**Use when**:
- Component data must persist across server restarts
- Component contains complex data structures
- Component needs to be saved in world data

### Implementation Example

Here's how `FactionComponent` is registered in FactionCore:

#### Step 1: Register in Main.setup()

```java
// Main.java
public class Main extends JavaPlugin {
    private ComponentType<EntityStore, FactionComponent> factionComponentType;
    
    @Override
    protected void setup() {
        super.setup();
        
        // Register FactionComponent (no CODEC needed - runtime only)
        this.factionComponentType = getEntityStoreRegistry().registerComponent(
            FactionComponent.class, 
            FactionComponent::new
        );
    }
    
    public ComponentType<EntityStore, FactionComponent> getFactionComponentType() {
        return factionComponentType;
    }
    
    public static Main get() {
        return pluginInstance;  // Set in constructor
    }
}
```

#### Step 2: Implement getComponentType() in Component

```java
// FactionComponent.java
public class FactionComponent implements Component<EntityStore> {
    // ... component fields and methods ...
    
    public static ComponentType<EntityStore, FactionComponent> getComponentType() {
        Main plugin = Main.get();
        if (plugin == null) {
            throw new IllegalStateException(
                "Main plugin instance is not available. Component may not be registered yet."
            );
        }
        ComponentType<EntityStore, FactionComponent> componentType = 
            plugin.getFactionComponentType();
        if (componentType == null) {
            throw new IllegalStateException(
                "FactionComponent has not been registered. Ensure it is registered in Main.setup()."
            );
        }
        return componentType;
    }
}
```

### Registration Flow

```
Plugin Startup
    ↓
Main.setup() called
    ↓
getEntityStoreRegistry().registerComponent()
    ├─ Creates ComponentType instance
    ├─ Registers with ComponentRegistry
    └─ Returns ComponentType
    ↓
Store ComponentType in Main.factionComponentType
    ↓
Component.getComponentType() can now be called
    └─ Retrieves from Main.get().getFactionComponentType()
```

### Common Mistakes and Solutions

#### ❌ Mistake 1: Using Reflection

**Wrong Approach**:
```java
// This will fail - ComponentType.getComponentType() doesn't exist
Method method = ComponentType.class.getMethod("getComponentType", Class.class);
componentType = (ComponentType<EntityStore, FactionComponent>) 
    method.invoke(null, FactionComponent.class);
```

**Why it fails**: `ComponentType` doesn't have a static `getComponentType(Class)` method. Components must be registered first.

**✅ Correct Approach**: Register component in `Main.setup()` and retrieve from plugin instance.

#### ❌ Mistake 2: Forgetting to Register

**Symptom**: `IllegalStateException` or `NullPointerException` when calling `getComponentType()`

**Solution**: Ensure component is registered in `Main.setup()` before any systems try to use it.

#### ❌ Mistake 3: Calling getComponentType() Before Registration

**Symptom**: Component type is null when systems try to use it

**Solution**: Register components before registering systems that use them.

### Registration Order

**Important**: Components must be registered **before** systems that use them:

```java
@Override
protected void setup() {
    super.setup();
    
    // 1. Register components FIRST
    this.factionComponentType = getEntityStoreRegistry().registerComponent(
        FactionComponent.class, 
        FactionComponent::new
    );
    
    // 2. THEN register systems that use the component
    this.factionSystem = new FactionSystem();
    getEntityStoreRegistry().registerSystem((ISystem) factionSystem);
}
```

### Built-in vs. Custom Components

**Built-in Components** (e.g., `PlayerRef`, `Player`, `TransformComponent`):
- Already registered by Hytale
- Can be accessed directly: `PlayerRef.getComponentType()`
- No registration needed

**Custom Components** (e.g., `FactionComponent`):
- Must be registered by your plugin
- Must retrieve from plugin instance: `FactionComponent.getComponentType()`
- Registration required in `setup()`

### Best Practices

1. **Register Early**: Register all components at the start of `setup()`
2. **Store Types**: Keep `ComponentType` references in plugin fields
3. **Validate Access**: Check for null in `getComponentType()` with helpful error messages
4. **Document Dependencies**: Comment which systems depend on which components
5. **Use Static Access**: Provide static `getComponentType()` method for convenience

### Related Patterns

This registration pattern is used throughout Hytale's built-in plugins:
- `PlayerMemories` in MemoriesPlugin
- `Teleport` in TeleportPlugin
- `Deployable` in DeployablesPlugin

All follow the same pattern: register in plugin setup, store in plugin field, retrieve via static method.

---

## Core Components

### FactionManager (Singleton)

**Location**: `com.factioncore.faction.FactionManager`

**Purpose**: Central authority for faction data

**Data Structure**:
```java
Map<UUID, FactionInfo> factions;      // Faction ID → Data
Map<UUID, UUID> playerToFaction;     // Player ID → Faction ID
```

**Key Methods**:
- `createFaction(UUID owner, String name)`
- `getFactionFromPlayer(UUID playerUuid)`
- `addMemberToFaction(UUID factionId, UUID playerUuid)`
- `removePlayerFromFaction(UUID playerUuid)`: Automatically revokes all faction permissions via `FactionPermissionManager`
- `disbandFaction(UUID factionId)`: Automatically revokes all member permissions via `FactionPermissionManager`
- `saveFactions()` / `loadFactions()`

**Persistence**: SQLite database at `universe/FactionCore/factioncore.db` (factions table)

**Permission Integration**: 
- Automatically calls `FactionPermissionManager.revokeAllFactionPermissions()` when players leave factions
- Automatically calls `FactionPermissionManager.revokeAllFactionPermissions()` for all members when factions are disbanded

### FactionInfo

**Location**: `com.factioncore.faction.FactionInfo`

**Fields**: 
- `id` (UUID): Unique faction identifier
- `owner` (UUID): Faction owner UUID
- `name` (String): Faction name
- `description` (String): Faction description
- `memberSet` (Set<UUID>): Set of member UUIDs
- `generalSet` (Set<UUID>): Set of general UUIDs (elevated members)
- `memberPermissions` (Map<UUID, Set<String>>): Member UUID → Set of granted permissions
- `color` (int): Faction color (RGB integer)
- `createdDate` (LocalDateTime): Creation timestamp
- `modifiedDate` (LocalDateTime): Last modification timestamp

**Member Permissions Management**:
- `getMemberPermissions(UUID memberUuid)`: Get permissions for a member
- `setMemberPermissions(UUID memberUuid, Set<String> permissions)`: Set permissions for a member
- `grantMemberPermission(UUID memberUuid, String permission)`: Grant a permission to a member
- `revokeMemberPermission(UUID memberUuid, String permission)`: Revoke a permission from a member
- `hasMemberPermission(UUID memberUuid, String permission)`: Check if member has a permission

**Methods**: 
- `isOwner(UUID uuid)`: Check if UUID is the owner
- `isMember(UUID uuid)`: Check if UUID is a member
- `isOwnerOrMember(UUID uuid)`: Check if UUID is owner or member
- `isGeneral(UUID uuid)`: Check if UUID is a general
- `getPlayerRole(UUID uuid)`: Get role ("owner", "general", "member", or null)
- `addMember(UUID uuid)`: Add a member
- `removeMember(UUID uuid)`: Remove a member
- `addGeneral(UUID uuid)`: Add a general
- `removeGeneral(UUID uuid)`: Remove a general

### FactionMarkerFilterService

**Purpose**: Reusable marker filtering logic

**Filter Logic**:
- Hide self marker
- Hide all if viewer has no faction
- Show only if other player is in same faction

---

## Invite System

### Overview

FactionCore includes a player invitation system that allows faction members with the appropriate permission to invite other players to join their faction. Invites are stored persistently and automatically cleaned up when players join or create factions.

### Architecture

```
Player Command (/f invite <playerName>)
    ↓
InviteFactionCommand.executeAsync()
    ├─ Validate player in faction
    ├─ Check permission (owner or has faction.invite_members)
    ├─ Find target player by username
    ├─ Validate target not in faction
    └─ FactionManager.invitePlayerToFaction()
        ├─ Create PendingInvite
        ├─ Store in PendingInvitesBlockingFile
        └─ Mark dirty for async save
    ↓
When player joins/creates faction:
    FactionManager.clearPendingInvitesForPlayer()
        └─ Remove all invites for that player
```

### Core Data Structures

#### PendingInvite

**Location**: `com.factioncore.invite.PendingInvite`

**Purpose**: Represents a pending faction invite

**Fields**:
- `playerUuid` (UUID): The UUID of the invited player
- `factionId` (UUID): The UUID of the faction that sent the invite
- `inviterName` (String): The name of the player who sent the invite
- `inviteDate` (String): The date when the invite was sent

**Serialization**: Stored in SQLite database (pending_invites table)

### FactionManager Extensions

**Invite Management Methods**:

- `invitePlayerToFaction(UUID playerUuid, UUID factionId, String inviterName)`: Send an invite to a player
- `clearPendingInvitesForPlayer(UUID playerUuid)`: Remove all pending invites for a player (called when player joins/creates faction)
- `getPendingInvitesForPlayer(UUID playerUuid)`: Get all pending invites for a player
- `markInvitesDirty()`: Mark invite data as dirty for async save

**Data Storage**:
- `PendingInvitesBlockingFile`: Stores pending invites by player UUID
- `invitesDirty` (boolean): Tracks if invite data needs saving
- Async saving every 5 seconds when data is dirty (shared with chunk data saving thread)
- Final save on plugin shutdown

### File Storage

#### PendingInvitesBlockingFile

**Location**: `com.factioncore.files.PendingInvitesBlockingFile`

**Purpose**: Handles persistence of pending invites using `BlockingDiskFile` pattern

**File Location**: `universe/FactionCore/pending_invites.json`

**Data Structure**: `Map<UUID, List<PendingInvite>>`
- Map key: Player UUID
- Value: List of pending invites for that player

**JSON Format**:
```json
{
  "PendingInvites": [
    {
      "PlayerUUID": "player-uuid",
      "Invites": [
        {
          "PlayerUUID": "player-uuid",
          "FactionUUID": "faction-uuid",
          "InviterName": "InviterName",
          "InviteDate": "2024-01-01T00:00:00"
        }
      ]
    }
  ]
}
```

**Methods**:
- `syncLoad()`: Loads invite data from disk (blocking)
- `syncSave()`: Saves invite data to disk (blocking)
- `getPendingInvites()`: Returns all pending invites map
- `getPendingInvitesForPlayer(UUID playerUuid)`: Returns invites for a specific player
- `addPendingInvite(PendingInvite invite)`: Adds an invite
- `removePendingInvitesForPlayer(UUID playerUuid)`: Removes all invites for a player
- `removePendingInvite(UUID playerUuid, UUID factionId)`: Removes a specific invite

### Permission Integration

**Permission Check**: `PermissionUtil.canInviteMembersInFaction(UUID playerUuid, FactionInfo faction)`

**Permission Logic**:
- Owner always has permission (bypasses check)
- Members need `faction.invite_members` permission (granted by owner via UI)

**Command Enforcement**: `InviteFactionCommand` checks permission before allowing invite

### Invite Cleanup

**Automatic Cleanup**: Pending invites are automatically removed when:
1. Player joins a faction: `FactionManager.addPlayerToFaction()` calls `clearPendingInvitesForPlayer()`
2. Player creates a faction: `FactionManager.createFaction()` calls `clearPendingInvitesForPlayer()`

**Why Cleanup?**: Once a player has a faction, they cannot receive invites, so all pending invites become invalid.

### Async Saving

**Pattern**: Uses the same async saving thread as chunk data (shared `ExecutorService`)

**Saving Behavior**:
- Saves every 5 seconds when `invitesDirty` is true
- Final save on plugin shutdown
- Thread-safe via in-memory ConcurrentHashMap with single writer thread for database writes

---

## Chunk Claiming System

### Overview

FactionCore includes a comprehensive chunk claiming system that allows factions to claim and protect chunks. The system integrates seamlessly with the existing faction system, using factions as the claiming entity (replacing SimpleClaims' party system).

### Architecture

```
Player Command (/f claim)
    ↓
FactionManager.claimChunkByRawCoords()
    ├─ Validate world claimable
    ├─ Validate claim limit
    ├─ Create ChunkInfo
    ├─ Store in ClaimedChunkBlockingFile
    └─ Mark dirty for async save
    ↓
Protection Systems (ECS Event Systems)
    └─ Check FactionManager.isAllowedToInteract()
        └─ Cancel event if not allowed
```

### Core Data Structures

#### ChunkInfo

**Location**: `com.factioncore.claim.ChunkInfo`

**Purpose**: Represents a claimed chunk

**Fields**:
- `factionOwner` (UUID): The faction that owns this chunk
- `chunkX`, `chunkZ` (int): Chunk coordinates
- `createdTracked` (ModifiedTracking): Audit trail of who claimed and when

**Serialization**: Uses Hytale's `BuilderCodec` pattern for CODEC-based serialization

**Key Methods**:
- `formatCoordinates(int chunkX, int chunkZ)`: Formats coordinates as "chunkX:chunkZ" string key

#### ModifiedTracking

**Location**: `com.factioncore.claim.tracking.ModifiedTracking`

**Purpose**: Tracks who modified something and when (audit trail)

**Fields**:
- `userUuid` (UUID): Who made the change
- `userName` (String): Username at time of change
- `date` (String): When the change was made

**Serialization**: Uses Hytale's `BuilderCodec` pattern

### FactionManager Extensions

**Chunk Management Methods**:

- `getChunk(String dimension, int chunkX, int chunkZ)`: Get chunk info by chunk coordinates
- `getChunkRawCoords(String dimension, int blockX, int blockZ)`: Get chunk info by block coordinates (converts to chunk coords)
- `claimChunkBy(String dimension, int chunkX, int chunkZ, FactionInfo faction, Player owner, PlayerRef playerRef)`: Claim a chunk
- `claimChunkByRawCoords(String dimension, int blockX, int blockZ, FactionInfo faction, Player owner, PlayerRef playerRef)`: Claim by block coordinates
- `unclaim(String dimension, int chunkX, int chunkZ)`: Unclaim a chunk
- `unclaimRawCoords(String dimension, int blockX, int blockZ)`: Unclaim by block coordinates
- `hasEnoughClaimsLeft(FactionInfo faction)`: Check if faction can claim more chunks
- `getAmountOfClaims(FactionInfo faction)`: Get current claim count
- `canClaimInDimension(World world)`: Check if claiming is allowed in a dimension
- `isAllowedToInteract(UUID playerUUID, String dimension, int chunkX, int chunkZ, Predicate<FactionInfo> interactMethod)`: Check if player can interact

**Data Storage**:
- `ClaimedChunkBlockingFile`: Stores chunk claims by dimension
- `factionClaimCounts` (Map<UUID, Integer>): Tracks claim counts per faction

**Async Saving**:
- Uses `CompletableFuture` with `ExecutorService` for async file I/O
- Saves every 5 seconds when data is dirty
- Final save on plugin shutdown

### File Storage

#### ClaimedChunkBlockingFile

**Location**: `com.factioncore.files.ClaimedChunkBlockingFile`

**Purpose**: Handles persistence of claimed chunks using `BlockingDiskFile` pattern

**File Location**: `universe/FactionCore/claimed_chunks.json`

**Data Structure**: `Map<String, Map<String, ChunkInfo>>`
- Outer map key: Dimension name
- Inner map key: Formatted chunk coordinates ("chunkX:chunkZ")
- Value: `ChunkInfo` object

**JSON Format**:
```json
{
  "Dimensions": [
    {
      "Dimension": "world",
      "ChunkInfo": [
        {
          "UUID": "faction-uuid",
          "ChunkX": 0,
          "ChunkZ": 0,
          "CreatedTracker": {
            "UserUUID": "player-uuid",
            "UserName": "PlayerName",
            "Date": "2024-01-01T00:00:00"
          }
        }
      ]
    }
  ]
}
```

**Methods**:
- `syncLoad()`: Loads chunk data from disk (blocking)
- `syncSave()`: Saves chunk data to disk (blocking)
- `getChunks()`: Returns the chunks map

### SQLite Database Pattern

**Purpose**: Thread-safe database persistence with in-memory caching for fast lookups

**Key Features**:
- Single writer thread pattern (prevents SQLite lock contention)
- All writes queued and processed sequentially
- In-memory maps for all runtime queries (O(1) lookups)
- Async loading on startup (non-blocking)
- Retry with exponential backoff for error recovery
- Database is ONLY for persistence - never queried during gameplay

**Architecture**:
```
Server Startup:
  DatabaseManager loads all data from SQLite (async)
  → Data populated into in-memory maps (ConcurrentHashMap)
  → Plugin ready - all queries use in-memory maps

During Gameplay:
  All queries use in-memory maps (fast, O(1) lookups)
  → NO database queries during gameplay

On Data Changes:
  Update in-memory maps immediately
  → Queue write to DatabaseManager (async, non-blocking)
  → Write processed by single writer thread (periodic saves)

Periodic Saves:
  Every 5 seconds, writer thread processes queued writes
  → Batch operations for efficiency
  → Retry with backoff on errors
```

**Best Practices**:
- **CRITICAL**: DatabaseManager is ONLY for persistence - never call it for reads during gameplay
- All runtime queries use in-memory maps (factions, chunks, invites)
- Queue all writes immediately (non-blocking)
- Single writer thread processes all writes sequentially
- Load all data on startup (async, non-blocking)
- Use retry with exponential backoff for database errors

### Claim Limits

**Configuration**: Set via `ClaimConfig.defaultFactionClaimsAmount` (default: 25)

**Tracking**: Stored in `FactionManager.factionClaimCounts` map

**Validation**: Checked before claiming via `hasEnoughClaimsLeft()`

**Access**: `FactionInfo.getMaxClaimAmount()` reads from config

### World Blacklist

**Configuration**: Set via `ClaimConfig.worldNameBlacklistForClaiming` (String array)

**Validation**: Checked via `FactionManager.canClaimInDimension(World world)`

**Default Restrictions**:
- Worlds with `isDeleteOnRemove()` enabled (temporary worlds)
- Worlds containing "Gaia_Temple" in name
- Worlds in blacklist array

### Chunk Visualizer UI

**Location**: `com.factioncore.ui.ChunkVisualizerPage`

**Purpose**: Interactive UI for visualizing and claiming/unclaiming chunks in a grid layout around the player's position

**Architecture**:
```
Player clicks "CLAIM" button in FactionShowPage
    ↓
FactionShowPage.handleDataEvent() → "claim" action
    ├─ Get player's current chunk coordinates
    ├─ Get world dimension
    └─ Open ChunkVisualizerPage
        ↓
ChunkVisualizerPage.build()
    ├─ Load UI template (Pages/ChunkVisualizer.ui)
    ├─ Get grid radius from ClaimConfig
    ├─ Build grid of chunk squares (configurable size)
    ├─ For each chunk:
    │   ├─ Check if claimed (get ChunkInfo from FactionManager)
    │   ├─ If claimed: Show faction color, tooltip with owner info
    │   ├─ If unclaimed: Show wilderness, tooltip with claim hint
    │   └─ Bind click events (left-click to claim, right-click to unclaim)
    └─ Update claim count display
        ↓
Player clicks chunk square
    ↓
ChunkVisualizerPage.handleDataEvent()
    ├─ Parse ChunkVisualizerData (action, chunkX, chunkZ)
    ├─ If "Claim": Validate and claim chunk
    └─ If "Unclaim": Validate and unclaim chunk
        ↓
Refresh UI to show updated state
```

**Key Features**:
- **Configurable Grid Size**: Grid radius configurable via `ClaimConfig.chunkVisualizerGridRadius` (default: 8, creates 17x17 grid)
- **Dynamic Grid Building**: Grid is built dynamically based on player's current chunk position
- **Visual Feedback**: 
  - Center chunk marked with "+" symbol
  - Claimed chunks show faction color overlay (semi-transparent)
  - Unclaimed chunks show wilderness (green tint)
  - Tooltips show owner info for claimed chunks, claim hints for unclaimed
- **Click-to-Claim**: Left-click unclaimed chunks to claim, right-click owned chunks to unclaim
- **Real-time Updates**: UI refreshes after claim/unclaim operations

**UI Components**:
- `ChunkVisualizer.ui`: Main page layout with scrollable grid container
- `ChunkEntry.ui`: Reusable chunk square button component (32x32 pixels)

**Event Data Structure**:
```java
public static class ChunkVisualizerData {
    private String action;      // "Claim" or "Unclaim"
    private Integer chunkX;     // Chunk X coordinate
    private Integer chunkZ;     // Chunk Z coordinate
    
    // Uses BuilderCodec for type-safe serialization
    public static final BuilderCodec<ChunkVisualizerData> CODEC = ...;
}
```

**Best Practices**:
1. **Clear Before Append**: Always call `uiCommandBuilder.clear("#ChunkCards")` before rebuilding grid to prevent UI element accumulation
2. **Index-Based Selectors**: Use array-style selectors `#ChunkCards[z][x]` for grid elements
3. **Structured Event Data**: Use typed `EventData` with separate fields instead of string parsing
4. **Refresh After Actions**: Call `refreshPage()` after claim/unclaim to update UI state
5. **Configurable Grid**: Read grid size from config to allow server customization

**Integration with FactionShowPage**:
- "CLAIM" button in `FactionShowPage` opens `ChunkVisualizerPage`
- Button only visible/enabled if player is in a faction
- Passes player's current chunk coordinates and dimension to visualizer

**Configuration**:
- `ClaimConfig.chunkVisualizerGridRadius` (int, default: 8): Grid radius in chunks (creates (radius*2+1) x (radius*2+1) grid)

---

## Protection Systems

### Overview

FactionCore implements protection systems using Hytale's ECS event system. Protection is handled via `EntityEventSystem` classes that listen to ECS events and cancel them if the player is not allowed to interact.

### Architecture

```
ECS Event (BreakBlockEvent, PlaceBlockEvent, UseBlockEvent.Pre)
    ↓
EntityEventSystem.handle()
    ├─ Get player UUID from PlayerRef
    ├─ Get world name
    ├─ Get block position
    └─ Check ProtectionService.isAllowedToInteract()
        ├─ Check if chunk is claimed
        ├─ Check if player's faction owns chunk
        └─ Check protection settings
            ↓
        If not allowed → event.setCancelled(true)
```

### Protection Systems

#### BreakBlockProtectionSystem

**Location**: `com.factioncore.systems.protection.BreakBlockProtectionSystem`

**Type**: `EntityEventSystem<EntityStore, BreakBlockEvent>`

**Purpose**: Prevents players from breaking blocks in claimed chunks

**Event**: `BreakBlockEvent` (extends `CancellableEcsEvent`)

**Protection Check**: Uses `ProtectionService.isBlockBreakEnabled()` predicate

**Registration**: Registered in `Main.setup()` as ECS system

#### PlaceBlockProtectionSystem

**Location**: `com.factioncore.systems.protection.PlaceBlockProtectionSystem`

**Type**: `EntityEventSystem<EntityStore, PlaceBlockEvent>`

**Purpose**: Prevents players from placing blocks in claimed chunks

**Event**: `PlaceBlockEvent` (extends `CancellableEcsEvent`)

**Protection Check**: Uses `ProtectionService.isBlockPlaceEnabled()` predicate

**Registration**: Registered in `Main.setup()` as ECS system

#### UseBlockProtectionSystem

**Location**: `com.factioncore.systems.protection.UseBlockProtectionSystem`

**Type**: `EntityEventSystem<EntityStore, UseBlockEvent.Pre>`

**Purpose**: Prevents players from interacting with blocks in claimed chunks

**Event**: `UseBlockEvent.Pre` (implements `ICancellableEcsEvent`)

**Protection Check**: Uses `ProtectionService.isBlockInteractEnabled()` predicate

**Registration**: Registered in `Main.setup()` as ECS system

**Note**: Uses `UseBlockEvent.Pre` (the cancellable version) instead of `UseBlockEvent.Post`

### ProtectionService

**Location**: `com.factioncore.systems.protection.ProtectionService`

**Purpose**: Centralized protection logic

**Key Method**: `isAllowedToInteract(UUID playerUuid, String dimension, int blockX, int blockZ, Predicate<FactionInfo> interactMethod)`

**Protection Logic**:
1. Check creative mode bypass (if enabled in config)
2. Get chunk info for block position
3. If chunk not claimed → allow
4. Get faction that owns chunk
5. If protection setting allows interaction → allow (predicate test)
6. Check if player is in same faction as chunk owner → allow
7. Otherwise → deny

**Protection Predicates**:
- `isBlockBreakEnabled(FactionInfo)`: Checks if block break is enabled for faction
- `isBlockPlaceEnabled(FactionInfo)`: Checks if block place is enabled for faction
- `isBlockInteractEnabled(FactionInfo)`: Checks if block interact is enabled for faction
- `isPVPEnabled(FactionInfo)`: Checks if PVP is enabled for faction

**Configuration Source**: Reads from `ClaimConfig` via `Main.getClaimConfig()`

### ECS Event System Pattern

**Important**: ECS events (like `BreakBlockEvent`, `PlaceBlockEvent`, `UseBlockEvent`) are handled via `EntityEventSystem`, NOT via `getEventRegistry().register()`. This is the proper way to handle ECS events in Hytale.

**Pattern**:
```java
public class MyProtectionSystem extends EntityEventSystem<EntityStore, MyEvent> {
    
    public MyProtectionSystem() {
        super(MyEvent.class);
    }
    
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();  // Or filter by component type
    }
    
    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> archetypeChunk, 
                      Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, 
                      MyEvent event) {
        // Get entity ref
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        
        // Get player info
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        
        // Get world
        EntityStore entityStore = (EntityStore) store.getExternalData();
        World world = entityStore.getWorld();
        
        // Check protection
        if (!isAllowed(...)) {
            event.setCancelled(true);
        }
    }
}
```

**Registration**:
```java
// In Main.setup()
getEntityStoreRegistry().registerSystem((ISystem) new MyProtectionSystem());
```

**Key Points**:
- Extend `EntityEventSystem<EntityStore, EventType>`
- Implement `getQuery()` to filter entities
- Implement `handle()` to process events
- Use `event.setCancelled(true)` to cancel cancellable events
- Register as ECS system, not via event registry

### Protection Settings

**Configuration**: Set via `ClaimConfig` (CODEC-based config)

**Settings**:
- `defaultFactionBlockBreakEnabled` (boolean, default: false)
- `defaultFactionBlockPlaceEnabled` (boolean, default: false)
- `defaultFactionBlockInteractEnabled` (boolean, default: false)
- `defaultFactionPVPEnabled` (boolean, default: false)
- `creativeModeBypassProtection` (boolean, default: false)

**Note**: Currently, protection settings are global per faction type. Per-faction overrides (like SimpleClaims had) are not implemented but can be added by extending `FactionInfo` with override fields.

---

## Configuration System

### Overview

FactionCore uses two CODEC-based configuration systems (Hytale's recommended pattern):
1. **GeneralConfig**: For plugin settings (debug, verboseDebug, maxFactionNameLength)
2. **ClaimConfig**: For chunk claiming settings

Both use Hytale's `BuilderCodec` pattern for type-safe, validated serialization.

### GeneralConfig (CODEC-based)

**Location**: `com.factioncore.config.GeneralConfig`

**Purpose**: Plugin-wide settings (debug logging, faction name length limits)

**File Location**: `universe/FactionCore/config.json` (custom path, not plugin data directory)

**Registration**: Manually created in `Main` constructor with custom path:
```java
Path generalConfigPath = Constants.UNIVERSE_PATH.resolve("FactionCore");
Files.createDirectories(generalConfigPath); // Ensure directory exists
this.generalConfig = new Config<>(generalConfigPath, "config", GeneralConfig.CODEC);
```

**Why Custom Path?**: 
- Keeps config file in `universe/FactionCore/` alongside other data files
- Makes backups easier (all plugin data in one location)
- Follows same pattern as other data files (faction data, chunk claims, etc.)

**Loading Pattern**:
```java
// In setup() - MUST load before any ConfigUtil.debug() calls
this.generalConfig.load().join();
this.generalConfig.save().join(); // Ensures file exists with defaults if needed
```

**Critical**: Config must be loaded **before** any code tries to access it. `Config.get()` throws `IllegalStateException` if config isn't loaded.

**Configuration Fields**:
- `Debug` (boolean, default: false): General debug logging
- `VerboseDebug` (boolean, default: false): Verbose debug logging (tick functions, frequent calls)
- `MaxFactionNameLength` (integer, default: 20): Maximum allowed length for faction names (1-100)

**JSON Key Requirements**:
- **CRITICAL**: Hytale's `KeyedCodec` requires JSON keys to start with **uppercase letters**
- Keys must match CODEC definition exactly: `"Debug"`, `"VerboseDebug"`, `"MaxFactionNameLength"`
- Lowercase keys (`"debug"`, `"verboseDebug"`) will be ignored and defaults used

**JSON Format**:
```json
{
  "Debug": true,
  "VerboseDebug": false,
  "MaxFactionNameLength": 20
}
```

**Access**:
```java
Main main = Main.get();
if (main != null && main.getGeneralConfig() != null) {
    GeneralConfig config = main.getGeneralConfig().get();
    boolean debug = config.isDebug();
}
```

**Error Handling**: `ConfigUtil.getGeneralConfig()` handles unloaded configs gracefully:
```java
try {
    return config.get();
} catch (IllegalStateException e) {
    // Config not loaded yet - try to load it
    config.load().join();
    return config.get();
}
```

### CODEC Configuration Best Practices

**1. JSON Key Naming**:
- **CRITICAL**: All JSON keys must start with uppercase letters
- Hytale's `KeyedCodec` validates this at runtime and throws `IllegalArgumentException` if violated
- Example: `"Debug"` ✅, `"debug"` ❌

**2. Loading Order**:
- Load config in `setup()` **before** any code that accesses it
- Use `load().join()` to ensure synchronous loading
- Call `save().join()` after loading to create file with defaults if missing

**3. Custom Paths**:
- Use `new Config<>(path, "name", CODEC)` for custom paths (e.g., `universe/FactionCore/`)
- Use `withConfig(CODEC)` for plugin data directory (e.g., `run/mods/plugin-name/`)
- Always ensure directory exists before creating Config: `Files.createDirectories(path)`

**4. Error Handling**:
- `Config.get()` throws `IllegalStateException` if config isn't loaded
- Always wrap `config.get()` calls in try-catch or ensure config is loaded first
- Implement fallback loading in utility methods that access config

**5. Pattern Comparison**:

**SimpleClaims Pattern** (plugin data directory):
```java
// In constructor
CONFIG = withConfig("SimpleClaims", SimpleClaimsConfig.CODEC);

// In setup()
CONFIG.save(); // Creates file if missing, loads if exists
```

**FactionCore Pattern** (custom path):
```java
// In constructor
Path generalConfigPath = Constants.UNIVERSE_PATH.resolve("FactionCore");
Files.createDirectories(generalConfigPath);
this.generalConfig = new Config<>(generalConfigPath, "config", GeneralConfig.CODEC);

// In setup()
this.generalConfig.load().join();
this.generalConfig.save().join();
```

**6. Access Pattern**:
```java
// Safe access with error handling
Config<MyConfig> config = main.getMyConfig();
if (config != null) {
    try {
        MyConfig values = config.get();
        // Use values
    } catch (IllegalStateException e) {
        // Config not loaded - try to load it
        config.load().join();
        MyConfig values = config.get();
    }
}
```

### ClaimConfig (CODEC-based)

**Location**: `com.factioncore.config.ClaimConfig`

**Purpose**: Configuration for chunk claiming system

**File Location**: `universe/FactionCore/claim_config.json` (managed by Hytale's `withConfig()`)

**Registration**: Registered in `Main` constructor via `withConfig(ClaimConfig.CODEC)`

**Pattern**: Uses Hytale's recommended `BuilderCodec` pattern for serialization

**Configuration Fields**:
- `defaultFactionClaimsAmount` (int, default: 25): Maximum chunks a faction can claim
- `defaultFactionBlockPlaceEnabled` (boolean, default: false): Allow block placement in claims
- `defaultFactionBlockBreakEnabled` (boolean, default: false): Allow block breaking in claims
- `defaultFactionBlockInteractEnabled` (boolean, default: false): Allow block interaction in claims
- `defaultFactionPVPEnabled` (boolean, default: false): Allow PVP in claims
- `worldNameBlacklistForClaiming` (String[], default: []): Worlds where claiming is disabled
- `creativeModeBypassProtection` (boolean, default: false): Allow creative mode players to bypass protection

**Access**:
```java
Main main = Main.get();
if (main != null && main.getClaimConfig() != null) {
    ClaimConfig config = main.getClaimConfig().get();
    int maxClaims = config.getDefaultFactionClaimsAmount();
}
```

**Why Separate Config?**:
- Follows Hytale's recommended pattern for plugin configurations
- Uses CODEC serialization (type-safe, validated)
- Separate from GeneralConfig for better organization
- Managed by Hytale's config system (automatic file creation/loading)

### ConfigUtil

**Location**: `com.factioncore.util.ConfigUtil`

**Purpose**: Utility class providing access to GeneralConfig values

**Methods**:
- `isDebug()`: Check if debug mode is enabled
- `debug(String)`: Log debug message if debug enabled
- `isVerboseDebug()`: Check if verbose debug is enabled
- `verboseDebug(String)`: Log verbose debug message if enabled

**Implementation**: Accesses `GeneralConfig` via `Main.getGeneralConfig().get()`

**Error Handling**: Gracefully handles unloaded configs by attempting to load them automatically

---

## Command System

### Overview

FactionCore uses Hytale's command system with async command support for world access.

### Command Architecture

```
AbstractAsyncCommand
    ↓
FactionCommand (main command)
    ├─ CreateFactionCommand (subcommand)
    ├─ ShowFactionCommand (subcommand)
    ├─ InviteFactionCommand (subcommand)
    ├─ ClaimChunkCommand (subcommand)
    ├─ UnclaimChunkCommand (subcommand)
    └─ ClaimInfoCommand (subcommand)
```

### Command Registration

**In Main.setup()**:
```java
getCommandManager().registerCommand(new FactionCommand());
```

**Subcommand Registration**:
```java
public class FactionCommand extends AbstractAsyncCommand {
    public FactionCommand() {
        super("f", "Faction management commands");
        addSubCommand(new CreateFactionCommand());
        addSubCommand(new ShowFactionCommand());
        addSubCommand(new InviteFactionCommand());
        addSubCommand(new ClaimChunkCommand());
        addSubCommand(new UnclaimChunkCommand());
        addSubCommand(new ClaimInfoCommand());
    }
}
```

### Invite Commands

#### InviteFactionCommand

**Command**: `/f invite <playerName>`

**Purpose**: Invites a player to the sender's faction

**Permission**: Requires `faction.invite_members` permission (owner always has this permission)

**Flow**:
1. Validate sender is in a faction
2. Check permission: owner or has `faction.invite_members` permission
3. Find target player by username (case-insensitive)
4. Check if target player is already in a faction
5. Check if target player already has a pending invite from this faction
6. Create and store pending invite
7. Notify target player if online

**Error Messages**:
- "You must be in a faction to invite players" (if not in faction)
- "You don't have permission to invite members to this faction" (if no permission)
- "Player 'X' not found or not online" (if player not found)
- "You cannot invite yourself to your faction" (if trying to invite self)
- "Player 'X' is already in a faction" (if target already in faction)
- "Failed to send invite. The player may already have a pending invite from your faction." (if duplicate invite)

**Success Behavior**:
- Invite stored in `pending_invites.json`
- Inviter receives success message
- Target player receives notification if online

**Pending Invite Cleanup**:
- All pending invites for a player are automatically cleared when:
  - Player joins a faction (via `addPlayerToFaction()`)
  - Player creates a faction (via `createFaction()`)

### Claim Commands

#### ClaimChunkCommand

**Command**: `/f claim`

**Purpose**: Claims the chunk where the player is standing

**Flow**:
1. Validate player is in a faction (create one if needed)
2. Check if claiming is allowed in dimension
3. Check if chunk is already claimed
4. Check if faction has claim limit available
5. Claim the chunk via `FactionManager.claimChunkByRawCoords()`
6. Send success message with claim count

**Error Messages**:
- "You must be in a faction to claim chunks" (if not in faction)
- "You cannot claim chunks in this dimension" (if world blacklisted)
- "This chunk is already claimed by your faction" (if already owned)
- "This chunk is already claimed by another faction" (if claimed by other)
- "You have reached your claim limit (X/Y)" (if limit reached)

#### UnclaimChunkCommand

**Command**: `/f unclaim`

**Purpose**: Unclaims the chunk where the player is standing

**Flow**:
1. Validate player is in a faction
2. Check if chunk is claimed
3. Check if player's faction owns the chunk
4. Unclaim via `FactionManager.unclaimRawCoords()`
5. Send success message with updated claim count

**Error Messages**:
- "You must be in a faction to unclaim chunks" (if not in faction)
- "This chunk is not claimed" (if chunk not claimed)
- "This chunk is not claimed by your faction" (if owned by other faction)

#### ClaimInfoCommand

**Command**: `/f claiminfo`

**Purpose**: Shows information about the current chunk

**Displays**:
- Chunk claim status (claimed/not claimed)
- Faction owner (if claimed)
- Chunk coordinates
- Player's faction claim count (current/max)

**Format**:
```
=== Chunk Info ===
Status: Claimed by FactionName
Chunk: 0, 0
This is your faction's claim
Your faction: 5/25 claims
```

### Async Commands

**Why Async?**: Commands that need to:
- Access world/entity data
- Perform operations requiring `World` executor

**Pattern**:
```java
public class ShowFactionCommand extends AbstractAsyncCommand {
    
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        // Validate sender
        if (!(sender instanceof Player)) {
            context.sendMessage(Message.raw("...").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        
        // Execute async with world executor
        return CompletableFuture.runAsync(() -> {
            // Get PlayerRef
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            
            // Perform command action
            // (e.g., open a page, modify data, etc.)
        }, (Executor) world);
    }
}
```

### Command Best Practices

1. **Validate Sender**: Check if sender is `Player` for player-only commands
2. **Check Reference Validity**: Verify `ref.isValid()` before using
3. **Use World Executor**: Pass `world` as executor for async operations
4. **Error Handling**: Return appropriate error messages, don't throw exceptions
5. **Debug Logging**: Use `ConfigUtil.debug()` to track command execution
6. **Permission Checks**: Verify permissions before executing operations
7. **Help Messages**: Provide clear help when no subcommand is provided

### Example: ShowFactionCommand

**Purpose**: Shows faction information or create form

**Flow**:
1. Validate sender is Player
2. Check reference validity
3. Get PlayerRef from store
4. Perform command action
5. Handle result

**Key Points**:
- Uses `AbstractAsyncCommand` for world access
- Validates all prerequisites before opening UI
- Uses world executor for async operations

---

## Logging System

### Overview

FactionCore uses Hytale's `HytaleLogger` with a configurable debug mode for detailed logging.

### Logger Initialization

Each utility class initializes its logger lazily:

```java
private static HytaleLogger getLogger() {
    if (LOGGER == null) {
        LOGGER = HytaleLogger.forEnclosingClass();
        // Fallback to sub-logger if needed
    }
    return LOGGER;
}
```

### Log Levels

- **INFO**: Normal operations, config loading/saving
- **WARNING**: Non-critical issues (missing files, parse warnings)
- **SEVERE**: Critical errors (file I/O failures, data corruption)

### Debug Mode

**Configuration**: Controlled via `config.json` with two levels:
- `debug: true/false` - General debug logging (commands, permission checks, etc.)
- `verboseDebug: true/false` - Verbose debug logging (tick functions, frequent method calls)

**Usage**: 
- `ConfigUtil.debug(String message)` - Logs if `debug` is enabled
- `ConfigUtil.verboseDebug(String message)` - Logs only if both `debug` and `verboseDebug` are enabled

**Purpose**:
- **General Debug** (`debug: true`): Detailed operation tracking (faction creation, member changes, permission checks, JSON integrity checks)
- **Verbose Debug** (`verboseDebug: true`): Tick function logging and frequently-called methods (can be very verbose)

**Example**:
```java
// General debug - shows in logs when debug: true
ConfigUtil.debug("createFaction called: name=" + name + ", ownerUuid=" + ownerUuid);

// Verbose debug - only shows when both debug: true AND verboseDebug: true
ConfigUtil.verboseDebug("FactionSystem.tick(): processing player " + playerUuid);
```

**Configuration Example**:
```json
{
  "Debug": true,
  "VerboseDebug": false,
  "MaxFactionNameLength": 20
}
```

**Configuration Options**:
- `Debug: true/false` - General debug logging (commands, permission checks, etc.)
- `VerboseDebug: true/false` - Verbose debug logging (tick functions, frequent method calls)
- `MaxFactionNameLength: integer` - Maximum allowed length for faction names (1-100, default: 20)

**Critical**: JSON keys must start with uppercase letters (Hytale's `KeyedCodec` requirement).

This configuration enables general debug logs but disables verbose tick function logs to reduce log spam.

### Logging Locations

- **ConfigUtil**: Config file operations, debug mode changes
- **JsonIntegrityUtil**: JSON validation results (errors/warnings)
- **HytalePermissionManager**: Permission file loading/parsing, permission checks
- **FactionManager**: All faction operations (create, add/remove members, save/load)
- **PermissionUtil**: Permission checks (LuckPerms vs. Hytale fallback)
- **FactionSystem**: Component synchronization (uses `verboseDebug`)
- **FactionMarkerFilterSystem**: Marker filter updates (uses `verboseDebug`)
- **FactionMarkerFilterService**: Filter application logic (uses `verboseDebug`)
- **FactionManager**: Chunk claiming operations, claim limits, protection checks (uses `debug`)
- **Protection Systems**: Block protection checks (uses `verboseDebug` for event handling)
- **Claim Commands**: Chunk claiming/unclaiming operations (uses `debug`)

### Best Practices

1. Use `ConfigUtil.debug()` for general debug logging (commands, permission checks, etc.)
2. Use `ConfigUtil.verboseDebug()` for tick functions and frequently-called methods
3. Use `getLogger().at(Level.SEVERE)` for critical errors
4. Use `getLogger().at(Level.WARNING)` for recoverable issues
5. Include context in log messages (UUIDs, faction names, file paths)
6. Keep `verboseDebug: false` in production to avoid log spam from tick functions

---

## Permissions System

### Overview

FactionCore **REQUIRES LuckPerms** to be installed. All permissions are managed exclusively through LuckPerms. The plugin will fail to start if LuckPerms is not available.

**Important**: This plugin no longer supports Hytale's `permissions.json` fallback. LuckPerms must be installed and running for the plugin to function.

### PermissionUtil

**Location**: `com.factioncore.util.PermissionUtil`

**Purpose**: Unified permission checking interface using LuckPerms API exclusively

**Startup Validation**: The plugin validates that LuckPerms is available during `Main.setup()`. If LuckPerms is not detected, the plugin will fail to start with a clear error message.

**Permission Check Flow**:
```
hasPermission(playerUuid, permission)
    ↓
Check LuckPerms API (REQUIRED)
    ├─ Try getUser() (fast path for loaded users)
    ├─ If null → loadUser() to load from storage
    ├─ Check permission via cached data
    └─ Return result (true/false)
```

**Error Handling**: If LuckPerms API is not available (should not happen after startup check), permission checks return `false` (secure default) and log an error.

**Key Methods**:
- `hasPermission(UUID playerUuid, String permission)`: Generic permission check (plugin permissions)
- `canCreateFaction(UUID playerUuid)`: Faction creation permission
- `canShowFaction(UUID playerUuid)`: View faction information permission
- `canClaimChunk(UUID playerUuid)`: Claim chunks permission
- `canUnclaimChunk(UUID playerUuid)`: Unclaim chunks permission
- `canViewClaimInfo(UUID playerUuid)`: View claim information permission
- `canRenameFaction(UUID playerUuid)`: Rename faction permission (plugin permission only)
- `canChangeColor(UUID playerUuid)`: Change faction color permission (plugin permission only)
- `canDisbandFaction(UUID playerUuid)`: Disband faction permission
- `canAccessSettings(UUID playerUuid)`: Access faction settings permission
- `canViewMembers(UUID playerUuid)`: View faction members permission

**Admin Permission Methods** (for actions on ANY faction):
- `canRenameFactionAdmin(UUID playerUuid)`: Check `factioncore.admin.rename` or `factioncore.admin.*`
- `canChangeFactionColorAdmin(UUID playerUuid)`: Check `factioncore.admin.changeColor` or `factioncore.admin.*`
- `canDisbandFactionAdmin(UUID playerUuid)`: Check `factioncore.admin.disband` or `factioncore.admin.*`
- `canAddMemberAdmin(UUID playerUuid)`: Check `factioncore.admin.addMember` or `factioncore.admin.*`
- `canRemoveMemberAdmin(UUID playerUuid)`: Check `factioncore.admin.removeMember` or `factioncore.admin.*`
- `canKickMemberAdmin(UUID playerUuid)`: Check `factioncore.admin.kickMember` or `factioncore.admin.*`
- `canManageMemberPermissionsAdmin(UUID playerUuid)`: Check `factioncore.admin.manageMemberPermissions` or `factioncore.admin.*`
- `canClaimChunkAdmin(UUID playerUuid)`: Check `factioncore.admin.claimChunk` or `factioncore.admin.*`
- `canUnclaimChunkAdmin(UUID playerUuid)`: Check `factioncore.admin.unclaimChunk` or `factioncore.admin.*`
- `canBypassProtectionAdmin(UUID playerUuid)`: Check `factioncore.admin.bypassProtection` or `factioncore.admin.*`

**Faction Member Permission Methods** (check owner, admin permissions, AND owner-granted permissions):
- `canRenameFactionInFaction(UUID playerUuid, FactionInfo faction)`: Check if player is owner, has admin permission, or has `faction.rename`
- `canChangeColorInFaction(UUID playerUuid, FactionInfo faction)`: Check if player is owner, has admin permission, or has `faction.change_color`
- `canInviteMembersInFaction(UUID playerUuid, FactionInfo faction)`: Check if player is owner, has admin permission, or has `faction.invite_members`
- `canManageAlliesInFaction(UUID playerUuid, FactionInfo faction)`: Check if player is owner, has admin permission, or has `faction.manage_allies`
- `canManageEnemiesInFaction(UUID playerUuid, FactionInfo faction)`: Check if player is owner, has admin permission, or has `faction.manage_enemies`
- `canKickMembersInFaction(UUID playerUuid, FactionInfo faction)`: Check if player is owner, has admin permission, or has `faction.kick_members`

**Plugin Permission Constants**:
- `PERM_CREATE_FACTION = "factioncore.player.create_faction"` - Create a new faction
- `PERM_SHOW_FACTION = "factioncore.player.show"` - View faction information UI
- `PERM_CLAIM_CHUNK = "factioncore.player.claim"` - Claim chunks
- `PERM_UNCLAIM_CHUNK = "factioncore.player.unclaim"` - Unclaim chunks
- `PERM_CLAIM_INFO = "factioncore.player.claiminfo"` - View chunk claim information
- `PERM_RENAME_FACTION = "factioncore.player.rename"` - Rename faction (plugin permission, not used for faction inner permissions)
- `PERM_CHANGE_COLOR = "factioncore.player.color"` - Change faction color (plugin permission, not used for faction inner permissions)
- `PERM_DISBAND_FACTION = "factioncore.player.disband"` - Disband faction (owner only)
- `PERM_SETTINGS = "factioncore.player.settings"` - Access faction settings
- `PERM_VIEW_MEMBERS = "factioncore.player.members"` - View faction members list

**Admin Permission Constants** (for actions on ANY faction, grouped under `factioncore.admin.*` for wildcard support):
- `PERM_ADMIN_RENAME = "factioncore.admin.rename"` - Rename any faction
- `PERM_ADMIN_CHANGE_COLOR = "factioncore.admin.changeColor"` - Change color of any faction
- `PERM_ADMIN_DISBAND = "factioncore.admin.disband"` - Disband any faction
- `PERM_ADMIN_ADD_MEMBER = "factioncore.admin.addMember"` - Add members to any faction
- `PERM_ADMIN_REMOVE_MEMBER = "factioncore.admin.removeMember"` - Remove members from any faction
- `PERM_ADMIN_KICK_MEMBER = "factioncore.admin.kickMember"` - Kick members from any faction
- `PERM_ADMIN_MANAGE_MEMBER_PERMISSIONS = "factioncore.admin.manageMemberPermissions"` - Manage member permissions for any faction
- `PERM_ADMIN_CLAIM_CHUNK = "factioncore.admin.claimChunk"` - Claim chunks for any faction
- `PERM_ADMIN_UNCLAIM_CHUNK = "factioncore.admin.unclaimChunk"` - Unclaim chunks for any faction
- `PERM_ADMIN_BYPASS_PROTECTION = "factioncore.admin.bypassProtection"` - Bypass chunk protection (break/place/interact in any claimed chunk)

**Faction Member Permission Constants** (granted by owner):
- `FACTION_PERM_RENAME = "faction.rename"` - Rename faction
- `FACTION_PERM_CHANGE_COLOR = "faction.change_color"` - Change faction color
- `FACTION_PERM_INVITE_MEMBERS = "faction.invite_members"` - Invite members
- `FACTION_PERM_KICK_MEMBERS = "faction.kick_members"` - Kick members
- `FACTION_PERM_MANAGE_ALLIES = "faction.manage_allies"` - Manage allies
- `FACTION_PERM_MANAGE_ENEMIES = "faction.manage_enemies"` - Manage enemies

### LuckPerms Integration

**Requirement**: LuckPerms **MUST** be installed and running. The plugin validates this during startup and will fail to load if LuckPerms is not available.

**Startup Validation**: In `Main.setup()`, the plugin checks for LuckPerms availability:
- Uses reflection to check for `net.luckperms.api.LuckPermsProvider` class
- Tries multiple classloaders: PluginBridgeClassLoader, context, system, and default
- If LuckPerms is not found, the plugin throws `IllegalStateException` and fails to start
- Clear error message: "FactionCore requires LuckPerms to be installed. Please install LuckPerms and restart the server."

**Detection**: Uses reflection to check for `net.luckperms.api.LuckPermsProvider` class
- **Primary Method**: Uses Hytale's `PluginBridgeClassLoader` via `PluginManager.get().getBridgeClassLoader()` (most reliable for plugin-to-plugin access)
- **Fallback Methods**: Tries context, system, and default classloaders if bridge classloader fails
- Caches the working classloader to avoid repeated checks

**API Access**: Uses reflection to avoid compile-time dependency (LuckPerms API is `compileOnly` in build.gradle)

**User Loading**:
- First attempts `getUser()` for fast access to already-loaded users
- If user not in memory, uses `loadUser()` to load from storage
- Waits for user data to load before checking permissions

**Error Handling**:
- If LuckPerms API not loaded (should not happen after startup check) → return `false` and log error
- If user not found in LuckPerms after loading → return `false`
- On any error → return `false` (secure default) and log error

### Security Model

**Default Behavior**: Deny by default (secure)
- If player not found in LuckPerms → no permissions
- If permission check fails → deny
- If API error → deny

**Permission Hierarchy**:
1. LuckPerms API (REQUIRED) - only permission system
2. Default (no permission) - secure default if check fails

### FactionPermissionManager

**Location**: `com.factioncore.permission.FactionPermissionManager`

**Purpose**: Thread-safe singleton manager for faction member permissions using LuckPerms context-based permissions with local caching.

**Architecture**:
```
Owner grants permission via UI (World Thread)
    ↓
FactionPermissionManager.grantPermission()
    ├─ Update thread-safe cache immediately (ConcurrentHashMap)
    └─ Queue async sync to LuckPerms (ExecutorService)
    ↓
Permission Check (World/Universe Thread)
    ├─ Check cache first (O(1) lookup, lock-free)
    └─ Return result immediately
    ↓
Periodic Sync (Every 30s)
    └─ Poll LuckPerms for external changes
```

**Key Features**:
- **Thread-Safe**: Uses `ConcurrentHashMap` for lock-free reads and safe concurrent writes
- **Non-Blocking**: All public methods return immediately; LuckPerms sync happens async
- **Cache-First**: O(1) cache lookups for fast permission checks
- **Async Sync**: Single-threaded `ExecutorService` for all LuckPerms operations (prevents race conditions)
- **Periodic Sync**: Background thread polls LuckPerms every 30 seconds to detect external changes

**Initialization**:
- Singleton pattern with lazy initialization
- Initialized in `Main.setup()` after LuckPerms validation, before `FactionManager`
- Starts periodic sync thread on initialization (runs every 30 seconds)
- Shutdown executor on plugin disable (called in `Main.shutdown()`)

**Initialization Order in Main.setup()** (CRITICAL):
1. Load GeneralConfig (before any ConfigUtil.debug() calls)
2. Validate LuckPerms availability (REQUIRED - plugin fails if not available)
3. Initialize ConfigUtil
4. **Initialize FactionPermissionManager** (after LuckPerms validation, before FactionManager)
5. Initialize FactionManager (loads factions from database)
6. Register ECS components (FactionComponent)
7. Register ECS systems (FactionSystem, FactionMarkerFilterSystem, ProtectionSystems)
8. Register commands
9. Register event handlers

**Why This Order Matters**:
- `FactionPermissionManager` must be ready before `FactionManager` loads factions (in case cleanup is needed during load)
- LuckPerms must be validated before `FactionPermissionManager` initialization (required dependency)
- Components must be registered before systems that use them (ECS requirement)

**Methods**:
- `grantPermission(UUID memberUuid, String permission, UUID factionId)`: Grant permission, update cache, async sync to LuckPerms
- `revokePermission(UUID memberUuid, String permission, UUID factionId)`: Revoke permission, update cache, async sync to LuckPerms
- `hasPermission(UUID memberUuid, String permission, UUID factionId)`: Check cache (fast path, O(1) lookup)
- `getMemberPermissions(UUID memberUuid, UUID factionId)`: Get all permissions for a member
- `setMemberPermissions(UUID memberUuid, Set<String> permissions, UUID factionId)`: Set all permissions for a member
- `revokeAllFactionPermissions(UUID memberUuid, UUID factionId)`: Revoke all permissions for a member in a faction
- `shutdown()`: Shutdown all executors (called on plugin disable)

**Context-Based Permissions**:
- Uses LuckPerms context: `faction=<faction-uuid>`
- Permission nodes: `faction.rename`, `faction.change_color`, etc.
- Each permission is scoped to a specific faction via context

**Thread Safety**:
- Cache: `ConcurrentHashMap` (lock-free reads, safe concurrent writes)
- LuckPerms operations: Single-threaded `ExecutorService` (prevents race conditions)
- Permission checks: Read-only cache access (no locking needed)

### Faction Member Permissions System

**Overview**: FactionCore implements a flexible member-level permission system where faction owners can grant specific permissions to members. This system operates independently of plugin permissions and gives owners full control over who can perform actions within their faction.

**Architecture**:
```
Owner grants permission via UI
    ↓
FactionPermissionManager.setMemberPermissions()
    ├─ Update thread-safe cache immediately
    └─ Queue async sync to LuckPerms with context
    ↓
FactionInfo.setMemberPermissions() (for database serialization)
    ↓
PermissionUtil.canRenameFactionInFaction() checks:
    ├─ Is owner? → Always allowed
    ├─ Has admin permission? → Check LuckPerms
    └─ Has faction.rename permission? → Check FactionPermissionManager cache
```

**Permission Types**:
- **Plugin Permissions**: Global permissions managed via LuckPerms/Hytale permissions.json (e.g., `factioncore.player.create_faction` or `factioncore.player.*`)
- **Faction Member Permissions**: Granular permissions granted by faction owner (e.g., `faction.rename`, `faction.change_color`)

**Key Design Principles**:
1. **Owners Always Have All Permissions**: Owners bypass all permission checks and can perform any action
2. **Owner-Granted Permissions Only**: Faction inner permissions (rename, color, invite, allies, enemies) are controlled solely by owner-granted permissions, not plugin permissions
3. **Flexible Delegation**: Owners can grant specific permissions to members without giving them full control
4. **Backward Compatible**: Old JSON files without `memberPermissions` field are handled gracefully (null-safe)

**Permission Storage**:
- **Runtime**: Stored in `FactionPermissionManager` cache (thread-safe `ConcurrentHashMap`)
- **Persistence**: Synced to LuckPerms with context `faction=<faction-uuid>`
- **Database**: `FactionInfo.memberPermissions` field kept for backward compatibility and database serialization
- **Cache Strategy**: Cache-first with async LuckPerms sync (non-blocking)
- **Periodic Sync**: Background thread polls LuckPerms every 30 seconds for external changes

**Permission Check Flow for Faction Actions**:
```
canRenameFactionInFaction(playerUuid, faction)
    ↓
Is player the owner?
    ├─ Yes → Return true (owners always allowed)
    └─ No → Check admin permission
        ├─ Has factioncore.admin.rename or factioncore.admin.* → Return true
        └─ No admin permission → Check FactionPermissionManager.hasPermission()
            ├─ Check cache (O(1) lookup, lock-free)
            ├─ Has permission → Return true
            └─ No permission → Return false
```

**Permission Priority for Faction Actions**:
1. **Owner Status**: Owners always have all permissions on their own faction
2. **Admin Permissions**: Admins with specific admin permissions can perform actions on ANY faction
3. **Faction Member Permissions**: Members with owner-granted permissions can perform actions on their faction

**Note**: Admin permissions (`factioncore.admin.*`) allow performing actions on ANY faction, not just the player's own faction.

### Admin Permission System Architecture

**Overview**: FactionCore implements a granular admin permission system that allows administrators to perform actions on ANY faction, not just their own. This replaces the previous admin override system with a more flexible, permission-based approach.

**Design Principles**:
1. **Faction Owners**: Always have full control over their own faction (no permission check needed)
2. **Admin Permissions**: Allow performing actions on ANY faction if the admin has the corresponding permission
3. **Wildcard Support**: `factioncore.admin.*` grants all admin permissions for convenience
4. **Granular Control**: Each action has its own permission node for fine-grained control

**Permission Check Pattern**:
All action handlers follow this pattern:
```java
boolean isOwner = faction.isOwner(playerUuid);
boolean hasAdminPermission = PermissionUtil.can<Action>Admin(playerUuid);
boolean hasMemberPermission = PermissionUtil.can<Action>InFaction(playerUuid, faction);

if (isOwner || hasAdminPermission || hasMemberPermission) {
    // Allow action
} else {
    // Deny action
}
```

**Protection Bypass**: The `ProtectionService.isAllowedToInteract()` method checks for `factioncore.admin.bypassProtection` or `factioncore.admin.*` permission before checking chunk ownership. This allows admins to break/place/interact in any claimed chunk.

**Database Changes**: The previous `admin_overrides` table has been removed. Admin permissions are now managed entirely through LuckPerms, providing better integration with server permission management.

### Permission List

**Command Permissions**:
- `factioncore.player.create_faction` - Required for `/f create` command
- `factioncore.player.show` - Required for `/f show` command (available to everyone by default)
- `factioncore.player.claim` - Required for `/f claim` command
- `factioncore.player.unclaim` - Required for `/f unclaim` command
- `factioncore.player.claiminfo` - Required for `/f claiminfo` command

**UI Action Permissions**:
- `factioncore.player.create_faction` - Required to create faction via UI
- `factioncore.player.settings` - Required to access faction settings page (owner only)
- `factioncore.player.members` - Required to view faction members page
- `factioncore.player.rename` - Required to rename faction (owner only)
- `factioncore.player.color` - Required to change faction color (owner only)
- `factioncore.player.disband` - Required to disband faction (owner only)

**Wildcard Support**:
- `factioncore.*` - Grants all FactionCore permissions
- `factioncore.admin.*` - Grants all admin permissions (allows actions on ANY faction)
- `factioncore.player.*` - Grants all player permissions (create_faction, show, claim, unclaim, claiminfo, rename, color, disband, settings, members)

### Usage Example

```java
// Check if player can create faction
if (PermissionUtil.canCreateFaction(playerUuid)) {
    // Allow faction creation
} else {
    // Deny with message
}

// Check if player can claim chunks
if (PermissionUtil.canClaimChunk(playerUuid)) {
    // Allow chunk claiming
}

// Check admin permission (for actions on ANY faction)
if (PermissionUtil.canRenameFactionAdmin(playerUuid)) {
    // Admin can rename any faction
}

// Check if player can rename their own faction (owner, admin, or member permission)
if (PermissionUtil.canRenameFactionInFaction(playerUuid, faction)) {
    // Player can rename this faction
}
```

### Permission Enforcement

All commands and UI actions now enforce permissions:

**Commands**:
- `/f create` - Checks `factioncore.player.create_faction`
- `/f show` - Checks `factioncore.player.show` (available to everyone by default)
- `/f claim` - Checks `factioncore.player.claim`
- `/f unclaim` - Checks `factioncore.player.unclaim`
- `/f claiminfo` - Checks `factioncore.player.claiminfo`

**UI Actions**:
- Create faction (via UI) - Checks `factioncore.player.create_faction`
- Open settings - All members can access (controls disabled based on permissions)
- View members - Checks `factioncore.player.members`
- Rename faction - Checks: owner OR `factioncore.admin.rename`/`factioncore.admin.*` OR `faction.rename` (owner-granted)
- Change color - Checks: owner OR `factioncore.admin.changeColor`/`factioncore.admin.*` OR `faction.change_color` (owner-granted)
- Disband faction - Checks: owner OR `factioncore.admin.disband`/`factioncore.admin.*`
- Manage member permissions - Checks: owner OR `factioncore.admin.manageMemberPermissions`/`factioncore.admin.*`

**Admin Permissions** (for actions on ANY faction):
- `factioncore.admin.rename` - Rename any faction
- `factioncore.admin.changeColor` - Change color of any faction
- `factioncore.admin.disband` - Disband any faction
- `factioncore.admin.addMember` - Add members to any faction
- `factioncore.admin.removeMember` - Remove members from any faction
- `factioncore.admin.kickMember` - Kick members from any faction
- `factioncore.admin.manageMemberPermissions` - Manage member permissions for any faction
- `factioncore.admin.claimChunk` - Claim chunks for any faction
- `factioncore.admin.unclaimChunk` - Unclaim chunks for any faction
- `factioncore.admin.bypassProtection` - Bypass chunk protection (break/place/interact in any claimed chunk)
- `factioncore.admin.*` - Wildcard granting all admin permissions

**Faction Member Permissions** (granted by owner via UI):
- `faction.rename` - Allows member to rename the faction
- `faction.change_color` - Allows member to change faction color
- `faction.invite_members` - Allows member to invite other players
- `faction.kick_members` - Allows member to kick other members
- `faction.manage_allies` - Allows member to manage faction allies
- `faction.manage_enemies` - Allows member to manage faction enemies

**Permission Priority**:
1. **Owners**: Always have all permissions on their own faction
2. **Admins**: With `factioncore.admin.*` permissions can perform actions on ANY faction
3. **Members**: With owner-granted permissions can perform actions on their faction

**Note**: 
- **Owners always have all permissions** on their faction, regardless of plugin permissions
- **Admin permissions** allow performing actions on ANY faction, not just the player's own faction
- **Faction member permissions** are controlled by owner-granted permissions for actions within the faction
- **Settings page** is accessible to all members, but controls are disabled based on their granted permissions

---

## Data Flow

### Player Creates Faction

```
/f create MyFaction
    ↓
FactionCommand → PermissionUtil.canCreateFaction()
    ↓
FactionManager.createFaction()
    ├─ Create FactionInfo
    ├─ Update maps
    ├─ Save to JSON
    └─ Update marker filters
    ↓
Next frame: FactionSystem.tick()
    └─ Add FactionComponent to player entity
```

### Player Joins World

```
PlayerReadyEvent
    ↓
FactionMarkerFilterService.applyFilterToPlayer()
    └─ Immediately apply marker filter
    ↓
Next frame: FactionSystem.tick()
    └─ Sync FactionComponent with FactionManager
```

### Periodic Marker Update

```
Every 200 ticks (10 seconds):
    ↓
FactionMarkerFilterSystem.tick()
    └─ Update all player marker filters
```

### Player Claims Chunk

```
/f claim
    ↓
ClaimChunkCommand.executeAsync()
    ├─ Validate player in faction
    ├─ Check dimension claimable
    ├─ Check chunk not already claimed
    ├─ Check claim limit available
    └─ FactionManager.claimChunkByRawCoords()
        ├─ Create ChunkInfo
        ├─ Store in ClaimedChunkBlockingFile
        ├─ Update factionClaimCounts
        └─ Mark dirty for async save
        ↓
    Next 5 seconds: Async save thread
        └─ Save chunk data to disk
```

### Player Breaks Block in Claimed Chunk

```
BreakBlockEvent fired
    ↓
BreakBlockProtectionSystem.handle()
    ├─ Get player UUID from PlayerRef
    ├─ Get world name
    ├─ Get block position
    └─ ProtectionService.isAllowedToInteract()
        ├─ Check admin bypass permission (factioncore.admin.bypassProtection or factioncore.admin.*)
        ├─ Get chunk info
        ├─ Check if chunk claimed
        ├─ Check if player's faction owns chunk
        └─ Check protection settings
            ↓
        If not allowed → event.setCancelled(true)
            ↓
        Block break prevented
```

### Owner Grants Permission to Member

```
Owner grants permission via UI (World Thread)
    ↓
FactionPermissionManager.setMemberPermissions()
    ├─ Update thread-safe cache immediately (ConcurrentHashMap)
    └─ Queue async sync to LuckPerms (ExecutorService, non-blocking)
    ↓
FactionInfo.setMemberPermissions() (for database serialization)
    └─ FactionManager.saveFactions() (queued, async)
    ↓
Next time member tries action:
    PermissionUtil.canRenameFactionInFaction()
        ├─ Check if owner → always allowed
        ├─ Check admin permission → check LuckPerms
        └─ Check FactionPermissionManager.hasPermission() → check cache (O(1) lookup)
```

### Member Tries to Rename Faction

```
Member attempts to rename faction
    ↓
Permission check: canRename = isOwner || canRenameFactionAdmin() || canRenameFactionInFaction()
    ├─ Is owner? → return true
    ├─ Has factioncore.admin.rename or factioncore.admin.*? → return true (can rename ANY faction)
    └─ Has faction.rename? → Check FactionPermissionManager.hasPermission()
        ├─ Check cache (O(1) lookup, lock-free)
        ├─ Has permission → return true
        └─ No permission → return false
    ↓
If allowed → Update faction name
    └─ Save to database (queued, async)
```

---

## Performance Considerations

### Component Design
- `FactionComponent` stores only UUID (8 bytes)
- Full data in singleton manager (no duplication)

### System Optimization
- **FactionSystem**: Runs every frame (necessary for sync)
  - Uses `verboseDebug` logging to reduce log spam from tick functions
- **FactionMarkerFilterSystem**: Updates every 10 seconds (95% CPU reduction)
  - Uses `verboseDebug` logging to reduce log spam from tick functions
- **Permission Checks**: 
  - **Cache-First**: `FactionPermissionManager` uses `ConcurrentHashMap` for O(1) lock-free reads
  - Caches LuckPerms availability to avoid repeated classloader checks
  - Loads users on-demand using `loadUser()` when not in memory
  - All permission checks are non-blocking (cache-only for fast path)

### Data Access
- O(1) HashMap lookups in `FactionManager`
- O(1) cache lookups in `FactionPermissionManager` (lock-free reads)
- ECS optimizes component queries internally
- Chunk lookups: O(1) via dimension → coordinate map

### Persistence
- **Faction Data**: Save on every change (immediate persistence)
- **Chunk Data**: Async saving every 5 seconds when dirty (reduces I/O)
- **Permission Data**: Async sync to LuckPerms via single-threaded `ExecutorService` (non-blocking)
- **Final Save**: All systems save on shutdown (safety net)

### Protection System Performance
- **Event Systems**: Only process when events fire (not every frame)
- **Query Filtering**: Systems use `Query.any()` to process all entities (events filter automatically)
- **Early Returns**: Protection checks return early if chunk not claimed

### Permission System Performance
- **Cache Strategy**: All permission checks use in-memory cache (O(1) lookups, lock-free)
- **Async Sync**: LuckPerms operations happen asynchronously (non-blocking)
- **Single Writer**: Single-threaded `ExecutorService` prevents race conditions
- **Periodic Sync**: Background thread polls LuckPerms every 30 seconds (detects external changes)

---

## Extension Points

### Adding Features

1. **New Component**: 
   - Implement `Component<EntityStore>`
   - **Register in `Main.setup()`** using `getEntityStoreRegistry().registerComponent()`
   - Store `ComponentType` in `Main` field
   - Add `getComponentType()` static method that retrieves from `Main`
2. **New System**: 
   - For ticking: Extend `EntityTickingSystem<EntityStore>`, register in `Main.setup()` **after** component registration
   - For events: Extend `EntityEventSystem<EntityStore, EventType>`, implement `getQuery()` and `handle()`, register in `Main.setup()`
3. **New Command**: Extend `AbstractAsyncCommand`, register as subcommand in `FactionCommand` or create new main command
4. **New Permission**: 
   - For plugin permissions: Add constant to `PermissionUtil`, check in commands
   - For faction member permissions: 
     - Add constant to `PermissionUtil` (e.g., `FACTION_PERM_NEW_FEATURE`)
     - Add method to `PermissionUtil` (e.g., `canNewFeatureInFaction()`)
     - Permissions automatically managed by `FactionPermissionManager` (no changes needed)
     - Update UI if needed (e.g., `FactionMemberPermissionsPage`)
5. **New Protection**: Create `EntityEventSystem` for the event type, check `ProtectionService.isAllowedToInteract()`, register in `Main.setup()`
6. **New Manager**: 
   - Follow singleton pattern
   - Use thread-safe data structures (`ConcurrentHashMap`, `ExecutorService`)
   - Initialize in `Main.setup()` in correct order
   - Implement `shutdown()` method for cleanup

### Example: Faction Chat

```java
// Component
public class FactionChatComponent implements Component<EntityStore> {}

// System
public class FactionChatSystem extends EntityTickingSystem<EntityStore> {
    // Filter chat by faction
}

// Register
getEntityStoreRegistry().registerSystem(new FactionChatSystem());
```

---

## ECS Best Practices

1. **Component = Data Only**: No logic in components
2. **System = Logic Only**: Systems process entities, don't store state
3. **Manager = Authority**: `FactionManager` is source of truth
4. **Query-Based**: Systems use queries for efficient filtering
5. **Separation of Concerns**: Clear boundaries between layers
6. **Register Components First**: Always register components before systems that use them
7. **Store ComponentTypes**: Keep `ComponentType` references in plugin instance, not static fields
8. **Validate Access**: Check for null and provide helpful error messages in `getComponentType()`
9. **Use CommandBuffer for Component Modifications**: Never call `store.addComponent()` or `store.removeComponent()` directly during a tick. Always use `commandBuffer.addComponent()`, `commandBuffer.removeComponent()`, or `commandBuffer.replaceComponent()` to defer modifications until after the tick completes. This prevents `IllegalStateException: Store is currently processing!` errors.
10. **ECS Events Use EntityEventSystem**: ECS events (like `BreakBlockEvent`, `PlaceBlockEvent`) must be handled via `EntityEventSystem`, NOT via `getEventRegistry().register()`. Register event systems in `Main.setup()` as ECS systems.
11. **Async File I/O**: Use `CompletableFuture` with `ExecutorService` for file operations. Don't use raw `Thread` objects. Mark data as dirty and save periodically.
12. **BlockingDiskFile Pattern**: Use `BlockingDiskFile` for all persistent data files. Provides thread-safe read/write operations with locks.

## Permission System Best Practices

1. **Owners Always Have All Permissions**: Owners bypass all permission checks for their faction. Always check `faction.isOwner(playerUuid)` first and return `true` immediately.

2. **Faction Inner Permissions Are Owner-Controlled**: Faction inner permissions (rename, color, invite, allies, enemies) are controlled solely by owner-granted permissions, not plugin permissions. Don't check plugin permissions for these actions.

3. **Use Dual Permission Methods**: For faction actions, use methods like `canRenameFactionInFaction()` that check owner-granted permissions, not just `canRenameFaction()` which checks plugin permissions.

4. **Use FactionPermissionManager for Runtime Checks**: Always use `FactionPermissionManager.hasPermission()` for runtime permission checks. The cache provides O(1) lookups and is thread-safe.

5. **Permission Constants**: Use constants from `PermissionUtil` (e.g., `FACTION_PERM_RENAME`) instead of hardcoded strings to avoid typos.

6. **Permission Validation in Handlers**: Always validate permissions in handlers, even if controls are disabled (defense in depth).

7. **Owner-Only Actions**: Destructive actions (like disband) should remain owner-only and hidden from non-owners, not just disabled.

8. **Member Permissions Storage**: 
   - **Runtime**: Stored in `FactionPermissionManager` cache (thread-safe `ConcurrentHashMap`)
   - **Persistence**: Synced to LuckPerms with context `faction=<faction-uuid>`
   - **Database**: `FactionInfo.memberPermissions` field kept for backward compatibility and database serialization

9. **Thread Safety**: All permission operations are thread-safe:
   - Cache uses `ConcurrentHashMap` (lock-free reads)
   - LuckPerms sync uses single-threaded `ExecutorService` (prevents race conditions)
   - Permission checks are read-only (no locking needed)

10. **Non-Blocking Operations**: All `FactionPermissionManager` public methods return immediately. LuckPerms sync happens asynchronously in background.

11. **Cleanup on Removal**: Always call `FactionPermissionManager.revokeAllFactionPermissions()` when:
    - Player leaves faction (`FactionManager.removePlayerFromFaction()`)
    - Faction is disbanded (`FactionManager.disbandFaction()`)

12. **Initialization Order**: Initialize `FactionPermissionManager` in `Main.setup()` after LuckPerms validation, before `FactionManager`. This ensures permission manager is ready if cleanup is needed during faction loading.

13. **Shutdown Handling**: Always call `FactionPermissionManager.shutdown()` in `Main.shutdown()` to properly close all executors and complete pending LuckPerms syncs.

---

## Troubleshooting

### Component Registration Errors

**Error**: `Failed to get ComponentType for FactionComponent`
- **Cause**: Component not registered or registered after systems try to use it
- **Solution**: Ensure `FactionComponent` is registered in `Main.setup()` before `FactionSystem` registration
- **Check**: Verify `getEntityStoreRegistry().registerComponent()` is called for `FactionComponent`

**Error**: `Main plugin instance is not available`
- **Cause**: `Main.get()` returns null (plugin not initialized)
- **Solution**: Ensure `pluginInstance = this` is set in `Main` constructor
- **Check**: Verify `Main` constructor sets static instance

**Error**: `FactionComponent has not been registered`
- **Cause**: `factionComponentType` field is null in `Main`
- **Solution**: Ensure registration happens in `setup()` and field is assigned
- **Check**: Verify registration code executes and doesn't throw exceptions

### Components Not Syncing
- Check `FactionSystem` registration in `Main.setup()`
- Verify `FactionComponent` is registered before `FactionSystem`
- Verify `FactionManager` initialization
- Check server logs for errors

**Error**: `IllegalStateException: Store is currently processing! Ensure you aren't calling a store method from a system.`
- **Cause**: System is calling `store.addComponent()`, `store.removeComponent()`, or `store.replaceComponent()` directly during a tick
- **Solution**: Use `CommandBuffer` methods instead: `commandBuffer.addComponent()`, `commandBuffer.removeComponent()`, or `commandBuffer.replaceComponent()`
- **Why**: Hytale's ECS defers component modifications until after all systems finish ticking to maintain consistency
- **Example Fix**:
  ```java
  // ❌ Wrong - causes IllegalStateException
  store.addComponent(ref, componentType, component);
  
  // ✅ Correct - uses CommandBuffer
  commandBuffer.addComponent(ref, componentType, component);
  ```

### Marker Filters Not Working
- Verify `FactionMarkerFilterSystem` registration
- Check if `applyFilterToPlayer()` is called
- Verify player has faction in `FactionManager`

### Data Not Persisting
- Check `FactionManager.saveFactions()` called on shutdown
- Verify `universe/FactionCore/` directory exists
- Check file permissions
- Verify data is stored in `universe/` directory (not `mods/`)


### Configuration Issues

**Config Not Loading**:
- **Error**: `IllegalStateException: Config is not loaded`
- **Cause**: Config accessed before `load().join()` is called
- **Solution**: Ensure config is loaded in `setup()` before any `ConfigUtil.debug()` calls
- **Check**: Verify `generalConfig.load().join()` is called early in `setup()`

**Config Values Not Applied**:
- **Symptom**: Config shows default values even though JSON file has different values
- **Cause**: JSON keys don't match CODEC definition (case mismatch)
- **Solution**: Ensure JSON keys start with uppercase: `"Debug"`, `"VerboseDebug"`, `"PermissionsMode"`
- **Check**: Verify JSON file uses uppercase keys matching CODEC definition exactly

**Config File Not Found**:
- **Symptom**: Config loads but file doesn't exist at expected location
- **Cause**: Config using plugin data directory instead of custom path
- **Solution**: Ensure `new Config<>(path, "name", CODEC)` uses `Constants.UNIVERSE_PATH.resolve("FactionCore")`
- **Check**: Verify config path in logs matches `universe/FactionCore/config.json`

**Config Loading from Wrong Location**:
- **Symptom**: Config loads from `run/mods/com.factioncore_FactionCore/config.json` instead of `universe/FactionCore/config.json`
- **Cause**: Using `withConfig()` which uses plugin data directory
- **Solution**: Use `new Config<>(customPath, "config", CODEC)` for custom paths
- **Check**: Log actual config path using reflection to verify location

### Chunk Claiming Issues

**Chunk Not Claiming**:
- Check if player is in a faction (`/f show` to verify)
- Verify dimension is claimable (not blacklisted, not temporary world)
- Check claim limit: `FactionInfo.getMaxClaimAmount()` vs current claims
- Enable debug mode to see detailed claim process logs
- Verify `ClaimConfig` is loaded (check `Main.getClaimConfig()`)

**Protection Not Working**:
- Verify protection systems are registered in `Main.setup()`
- Check `ClaimConfig` protection settings (block break/place/interact enabled)
- Verify chunk is actually claimed (use `/f claiminfo`)
- Enable debug mode to see protection check logs
- Verify `ProtectionService.isAllowedToInteract()` is being called

**Chunk Data Not Saving**:
- Check async save thread is running (logs should show "Saving chunk data...")
- Verify `FactionManager.markDirty()` is called after chunk operations
- Check file permissions on `universe/FactionCore/claimed_chunks.json`
- Verify `FactionManager.shutdown()` is called on plugin shutdown (final save)
- Check server logs for file I/O errors

**Claim Limit Issues**:
- Verify `ClaimConfig.defaultFactionClaimsAmount` is set correctly
- Check `FactionManager.factionClaimCounts` map is being updated
- Verify claim counts are initialized from loaded chunks on startup
- Use `/f claiminfo` to see current claim count

**World Blacklist Not Working**:
- Verify `ClaimConfig.worldNameBlacklistForClaiming` array is set
- Check world name matches exactly (case-sensitive)
- Verify `FactionManager.canClaimInDimension()` is checking blacklist
- Enable debug mode to see dimension validation logs

### Member Permissions Issues

**Member Permissions Not Saving**:
- Verify `FactionInfo.memberPermissions` is not `final` (must be mutable for JSON deserialization)
- Check that `FactionManager.saveFactions()` is called after permission changes
- Verify JSON file has `memberPermissions` field (check `faction_core.json`)
- Check that `FactionPermissionManager` is initialized (check startup logs)
- Enable debug mode to see permission save operations and LuckPerms sync

**Member Can't Perform Action Despite Permission**:
- Check if member is the owner (owners always have all permissions)
- Verify permission was granted via `FactionPermissionManager` (check cache)
- Check permission constant matches exactly (e.g., `"faction.rename"` not `"faction.rename_faction"`)
- Use `FactionPermissionManager.hasPermission(memberUuid, permission, factionId)` to verify permission exists
- Check LuckPerms sync logs (permissions sync async, may take a moment)
- Enable debug mode to see permission check logs and cache lookups
- Verify `FactionPermissionManager` periodic sync is running (check logs every 30s)

**LuckPerms Sync Issues**:
- Check that LuckPerms is installed and running
- Verify `FactionPermissionManager` is initialized before `FactionManager`
- Check logs for LuckPerms sync errors
- Verify context is set correctly: `faction=<faction-uuid>`
- Check that executors are not shutdown prematurely

**Old JSON Files Missing Permissions**:
- The system handles null `memberPermissions` gracefully (backward compatible)
- Permissions will be initialized as empty map when first accessed
- No migration needed - permissions start empty for existing factions

**Permission Button Not Showing**:
- Verify viewer is the faction owner (`faction.isOwner(playerUuid)`)
- Check that member is not the owner themselves (button hidden for owner's own entry)

### Permission Issues

**LuckPerms Not Detected**:
- **First**: Check `config.json` - ensure `useLuckperms: true` if you want to use LuckPerms
- Check server logs for "LuckPerms detected" or "LuckPerms not found" messages
- If `useLuckperms: false`, the plugin will skip LuckPerms detection entirely (this is expected behavior)
- Verify LuckPerms JAR is in `run/mods/` directory
- Check if LuckPerms loads after FactionCore (classloader timing issue)
- The plugin tries multiple classloaders to find LuckPerms (PluginBridgeClassLoader, context, system, default)
- Look for log messages indicating which classloader was used: "LuckPerms detected via PluginBridgeClassLoader"
- If not found, automatically falls back to Hytale permissions.json

**Hytale permissions.json Not Working**:
- Enable debug mode: `ConfigUtil.setDebug(true)` to see path resolution
- Verify file is at `run/permissions.json` (same level as `universe/`)
- Check JSON format - supports both nested `{"groups": [...]}` and direct `[...]` formats
- Verify UUID matches exactly (case-sensitive)
- Check group name spelling (case-sensitive)
- Check permission node spelling (`factioncore.player.create_faction`)
- Look for debug logs showing: "parsed user ... with groups: [...]"

**Permission Check Returns False**:
- Check debug logs for detailed permission check flow
- Verify user UUID is in `users` section of permissions.json
- Verify group name matches exactly (case-sensitive)
- Verify permission string matches exactly (case-sensitive)
- Check if user is in the correct group
- Verify group has the permission in `groups` section

---

## File Storage Best Practices

### Directory Structure

FactionCore follows Hytale best practices for file storage:

**Data Storage**: `universe/FactionCore/`
- General Configuration: `universe/FactionCore/config.json` (CODEC-based, manually loaded, plugin settings) - **ONLY JSON FILE**
- Claim Configuration: `universe/FactionCore/claim_config.json` (CODEC-based, managed by Hytale's `withConfig()`)
- **SQLite Database**: `universe/FactionCore/factioncore.db` (all persistent data)
  - Factions table (faction data, members, generals, permissions)
  - Claims table (chunk claims)
  - Pending invites table
- All plugin data is stored in the `universe/` directory alongside world/server data

**Storage Patterns**:
- **SQLite Database**: All persistent data (factions, claims, invites)
  - Single writer thread pattern (prevents lock contention)
  - Queued writes (async, non-blocking)
  - Periodic saves every 5 seconds when dirty
  - In-memory maps for all runtime queries (O(1) lookups)
- **CODEC-based (withConfig)**: Managed by Hytale's config system (claim config)
- **CODEC-based (custom path)**: Manually loaded in `setup()`, saved after loading (general config)

**Why `universe/` and not `mods/`?**
- `mods/` directory is for plugin JAR files and asset packs (code/resources)
- `universe/` directory is for world/server data (persistent game state)
- Storing data in `universe/` keeps it with world data, making backups easier
- Follows the same pattern as hyfaction (reference implementation)

**Implementation**:
```java
// All data files use Constants.UNIVERSE_PATH
Path basePath = Constants.UNIVERSE_PATH.resolve("Factioncore");
Path configPath = basePath.resolve("config.json");
Path dbPath = basePath.resolve("factioncore.db");

// GeneralConfig uses custom path (manually created)
Path generalConfigPath = Constants.UNIVERSE_PATH.resolve("FactionCore");
Files.createDirectories(generalConfigPath);
this.generalConfig = new Config<>(generalConfigPath, "config", GeneralConfig.CODEC);

// DatabaseManager handles all persistent data (factions, claims, invites)
// Database location: universe/FactionCore/factioncore.db
DatabaseManager databaseManager = new DatabaseManager(logger);

// ClaimConfig is managed by Hytale's withConfig() system
// File location: universe/FactionCore/claim_config.json (automatic)
this.claimConfig = withConfig(ClaimConfig.CODEC);
```

**Permissions File**: `run/permissions.json`
- Hytale's default permissions file is at the root of the run directory
- Resolved via `Constants.UNIVERSE_PATH.getParent().resolve("permissions.json")`
- This is separate from plugin data storage

### Configuration File Structure

The `config.json` file is located at `universe/FactionCore/config.json` with the following structure:

```json
{
  "Debug": true,
  "VerboseDebug": false,
  "MaxFactionNameLength": 20
}
```

**Critical Notes**:
- **Keys must start with uppercase**: Hytale's `KeyedCodec` requires JSON keys to start with uppercase letters
- **Case-sensitive**: Keys must match CODEC definition exactly (`"Debug"`, not `"debug"`)
- **Invalid keys ignored**: Lowercase keys will be ignored and default values used

**Default Values**:
- `Debug: false` - General debug logging disabled by default
- `VerboseDebug: false` - Verbose debug logging disabled by default
- `MaxFactionNameLength: 20` - Maximum faction name length (1-100)

**Loading**: The config file is loaded in `Main.setup()` before any code accesses it:
```java
this.generalConfig.load().join();
this.generalConfig.save().join(); // Creates file with defaults if missing
```

---

## World Map Integration

### Overview

FactionCore integrates with Hytale's world map system to visualize claimed chunks on the world map. Claimed chunks are rendered with faction colors and borders, making it easy to see faction territories at a glance.

### Architecture

```
World Map Request
    ↓
FactionWorldMapProvider.getGenerator()
    └─ Returns FactionChunkWorldMap.INSTANCE
        ↓
FactionChunkWorldMap.generate()
    ├─ For each chunk to generate:
    │   └─ FactionImageBuilder.build()
    │       ├─ Fetch chunk data from world
    │       ├─ Sample block heights, fluids, environments
    │       ├─ Get faction info for chunk
    │       ├─ Apply faction color overlay
    │       ├─ Draw borders for adjacent chunks
    │       └─ Optionally draw faction name
    │       ↓
    └─ Combine all chunk images into WorldMap
        ↓
Display on world map UI
```

### Core Components

#### FactionWorldMapProvider

**Location**: `com.factioncore.map.FactionWorldMapProvider`

**Purpose**: Implements `IWorldMapProvider` to register custom world map generation

**Registration**:
- Registered in `Main.setup()` via `IWorldMapProvider.CODEC.register()`
- Applied to existing worlds in `Main.start()` via `Universe.getUniverseReady()` (waits for async world loading)
- Applied to new worlds via `AddWorldEvent` handler

**Key Methods**:
- `getGenerator(World world)`: Returns `FactionChunkWorldMap.INSTANCE` for all worlds
- Logs when called for debugging purposes

#### FactionChunkWorldMap

**Location**: `com.factioncore.map.FactionChunkWorldMap`

**Purpose**: Implements `IWorldMap` to generate map images for claimed chunks

**Key Features**:
- Delegates to `FactionImageBuilder` for individual chunk rendering
- Combines multiple chunk images into a `WorldMap`
- Uses `CompletableFuture` for async image generation
- No custom points of interest (returns empty map)
- Comprehensive logging for debugging map generation

**Methods**:
- `generate(World world, int imageWidth, int imageHeight, LongSet chunksToGenerate)`: Generates map images for specified chunks
  - Logs when called (world name, chunk count, image dimensions)
  - Logs completion status (successful chunks count)
- `generatePointsOfInterest(World world)`: Returns empty map (no custom POIs)
- `getWorldMapSettings()`: Returns settings from `ChunkWorldMap.INSTANCE`

#### FactionImageBuilder

**Location**: `com.factioncore.map.FactionImageBuilder`

**Purpose**: Handles low-level rendering of individual chunk images with faction overlays

**Rendering Process**:
1. **Chunk Data Fetching**: 
   - Fetches `WorldChunk` and `ChunkColumn` data asynchronously
   - Samples neighbor chunks for height calculations
   - Loads fluid sections for water rendering

2. **Block Sampling**:
   - Samples block heights, tints, and types across chunk
   - Calculates shading from height differences
   - Applies biome tints and fluid colors

3. **Faction Overlay**:
   - Gets dimension name (tries `worldChunk.getWorld().getName()` first, falls back to `world.getName()`)
   - Gets `FactionInfo` for chunk from `FactionManager`
   - Validates faction color is not zero (invisible)
   - Checks adjacent chunks for border detection (with null safety)
   - Applies faction color overlay (60% opacity for interior, 85% for borders)
   - Wrapped in try-catch to prevent silent failures
   - Optionally draws faction name text on chunk

4. **Image Generation**:
   - Combines all samples into final `MapImage`
   - Applies shading and lighting
   - Returns completed image for world map

**Key Methods**:
- `build(long index, int imageWidth, int imageHeight, World world)`: Main entry point for async image building
- `fetchChunk()`: Fetches chunk data from world
- `sampleNeighborsSync()`: Samples neighbor chunks for height calculations
- `generateImageAsync()`: Generates final image with faction overlays
  - Handles dimension name resolution (tries multiple methods)
  - Comprehensive debug logging (dimension, chunk lookup, faction retrieval, color values)
  - Error handling with try-catch blocks
- `getForceBlockColor()`: Applies faction color overlay to block colors
  - Validates RGB component ranges
  - Clamps blended color values to 0-255
  - Skips overlay if faction color is 0 (invisible)
- `drawText()`: Draws faction name on chunk (if enabled in config)

**Configuration Integration**:
- Reads `ClaimConfig.renderClaimNamesOnWorldMap` (if implemented) to control name rendering
- Uses faction colors from `FactionInfo.getColor()`

### Registration Flow

**In Main.setup()**:
```java
// Register world map provider codec
IWorldMapProvider.CODEC.register(
    FactionWorldMapProvider.ID, 
    FactionWorldMapProvider.class, 
    FactionWorldMapProvider.CODEC
);
```

**In Main.start()**:
```java
// CRITICAL: Worlds load asynchronously, must wait for them to be ready
Universe universe = Universe.get();
universe.getUniverseReady().thenRun(() -> {
    // Now worlds are guaranteed to be loaded
    for (World world : universe.getWorlds().values()) {
        if (world.getWorldMapManager() != null) {
            world.getWorldMapManager().setGenerator(
                FactionWorldMapProvider.INSTANCE.getGenerator(world)
            );
        }
    }
});

// Fallback: Also try immediately if worlds are already available
if (universe.getWorlds().size() > 0) {
    registerWorldMapProviders(universe);
}
```

**Important**: `Main.start()` is called before worlds finish loading. Always use `Universe.getUniverseReady()` to wait for async world loading before accessing `universe.getWorlds()`.

**Via AddWorldEvent**:
```java
// Apply to newly added worlds
getEventRegistry().registerGlobal(AddWorldEvent.class, event -> {
    World world = event.getWorld();
    world.getWorldMapManager().setGenerator(
        FactionWorldMapProvider.INSTANCE.getGenerator(world)
    );
});
```

### World Map Update Queue

**Purpose**: Queue map updates when chunks are claimed/unclaimed to refresh world map

**Implementation**:
- `FactionManager.queueMapUpdate(World world, int chunkX, int chunkZ)`: Queues chunk for map regeneration
- Uses world's map manager to invalidate/regenerate specific chunks
- Called automatically after claim/unclaim operations

### Best Practices

1. **Provider Registration Timing**: 
   - Register provider codec in `setup()`
   - **CRITICAL**: In `start()`, use `Universe.getUniverseReady()` to wait for async world loading before accessing worlds
   - Apply to worlds in the `getUniverseReady()` callback and `AddWorldEvent` handler
   - Always check if `WorldMapManager` is null before calling `setGenerator()`

2. **Async Image Building**: 
   - Use `CompletableFuture` for all chunk data fetching and image generation
   - Never block the world thread during image generation

3. **Dimension Name Handling**:
   - Try `worldChunk.getWorld().getName()` first (matches SimpleClaims approach)
   - Fall back to `world.getName()` if worldChunk method fails
   - Log which method was used for debugging

4. **Faction Color Overlay**: 
   - Apply semi-transparent overlay (60% interior, 85% borders) to preserve terrain detail
   - Validate color is not zero (invisible) before applying
   - Clamp RGB values to 0-255 range
   - Blend colors properly to maintain terrain visibility

5. **Border Detection**: 
   - Check adjacent chunks to determine border pixels for enhanced visibility
   - **Null Safety**: Always check if nearby chunks are null before accessing `getFactionOwner()`
   - Check if `getFactionOwner()` returns null (deleted faction) before comparison
   - Wrap border detection in try-catch to prevent failures

6. **Error Handling**:
   - Wrap faction overlay code in try-catch blocks to prevent silent failures
   - Log exceptions with stack traces for debugging
   - Continue map generation even if overlay fails (don't break the map)

7. **Debug Logging**:
   - Log dimension names used at claim time vs map generation time
   - Log chunk lookup results (found/not found)
   - Log faction retrieval results (found/not found)
   - Log color values (hex and RGB components)
   - Log when map generation is requested and completed
   - Use both `ConfigUtil.debug()` (conditional) and `LOGGER.at(Level.INFO)` (always) for critical events

8. **Performance**: 
   - Image building is async and doesn't block world thread
   - Use efficient null checks to avoid unnecessary processing

9. **Map Updates**: 
   - Queue map updates after claim/unclaim operations to keep map current

### Visual Features

- **Faction Colors**: Claimed chunks show faction color overlay (60% opacity interior, 85% borders)
- **Borders**: Chunk borders are highlighted when adjacent to different factions or wilderness
- **Terrain Preservation**: Overlay is semi-transparent to show underlying terrain
- **Optional Names**: Faction names can be drawn on chunks (if enabled in config)
- **Color Validation**: Invisible colors (0) are skipped to prevent black overlays

### Troubleshooting

**Issue**: No colored squares appear on map
- **Check**: Verify worlds are loaded using `Universe.getUniverseReady()`
- **Check**: Verify `WorldMapManager` is not null before setting generator
- **Check**: Enable debug logging to see dimension names, chunk lookups, and faction retrieval
- **Check**: Verify faction colors are not zero (invisible)
- **Check**: Check server logs for exceptions during map generation

**Issue**: Dimension name mismatch
- **Solution**: Code tries both `worldChunk.getWorld().getName()` and `world.getName()`
- **Debug**: Check logs to see which dimension name is being used

**Issue**: Null pointer exceptions in border detection
- **Solution**: Code now safely checks for null chunks and null faction owners
- **Debug**: Check logs for "Exception during border detection" messages

---

## UI System

### Overview

FactionCore implements an interactive UI system using Hytale's `InteractiveCustomUIPage` pattern. The UI allows players to manage factions, view chunk claims, and interact with the claiming system through visual interfaces.

### UI Components

#### ChunkVisualizerPage

**Location**: `com.factioncore.ui.ChunkVisualizerPage`

**Type**: `InteractiveCustomUIPage<ChunkVisualizerData>`

**Purpose**: Displays a grid of chunks around the player's position for visual claiming/unclaiming

**Key Features**:
- Configurable grid size (via `ClaimConfig.chunkVisualizerGridRadius`)
- Dynamic grid building based on player position
- Click-to-claim/unclaim functionality
- Real-time UI updates after operations
- Visual feedback (colors, tooltips, center marker)

**Event Handling**:
- Uses structured `ChunkVisualizerData` with typed fields (action, chunkX, chunkZ)
- Left-click on unclaimed chunks → claim action
- Right-click on owned chunks → unclaim action
- Validates permissions and claim limits before operations

**UI Files**:
- `Pages/ChunkVisualizer.ui`: Main page layout with scrollable grid
- `Pages/ChunkEntry.ui`: Reusable chunk square button (32x32 pixels)

#### FactionShowPage

**Location**: `com.factioncore.ui.FactionShowPage`

**Integration**: Opens `ChunkVisualizerPage` when "CLAIM" button is clicked

**Flow**:
1. Player clicks "CLAIM" button in faction UI
2. `FactionShowPage.handleDataEvent()` receives "claim" action
3. Gets player's current chunk coordinates and dimension
4. Opens `ChunkVisualizerPage` with player's position as center

### UI Event Handling Patterns

#### Structured Event Data

**Best Practice**: Use typed `EventData` with separate fields instead of string parsing

**Example**:
```java
// ✅ Good: Structured data
public static class ChunkVisualizerData {
    private String action;      // "Claim" or "Unclaim"
    private Integer chunkX;
    private Integer chunkZ;
    
    public static final BuilderCodec<ChunkVisualizerData> CODEC = 
        BuilderCodec.builder(ChunkVisualizerData.class, ChunkVisualizerData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), ...)
            .addField(new KeyedCodec<>("ChunkX", Codec.INTEGER), ...)
            .addField(new KeyedCodec<>("ChunkZ", Codec.INTEGER), ...)
            .build();
}

// ❌ Bad: String parsing
EventData.of("Action", "Claim:chunkX:chunkZ")
```

#### Event Binding

**Pattern**: Bind events in `build()` method using `UIEventBuilder`

**Example**:
```java
// Left-click to claim
uiEventBuilder.addEventBinding(
    CustomUIEventBindingType.Activating,
    selector,
    EventData.of(ChunkVisualizerData.KEY_ACTION, "Claim")
        .append(ChunkVisualizerData.KEY_CHUNK_X, String.valueOf(worldChunkX))
        .append(ChunkVisualizerData.KEY_CHUNK_Z, String.valueOf(worldChunkZ))
);

// Right-click to unclaim
uiEventBuilder.addEventBinding(
    CustomUIEventBindingType.RightClicking,
    selector,
    EventData.of(ChunkVisualizerData.KEY_ACTION, "Unclaim")
        .append(ChunkVisualizerData.KEY_CHUNK_X, String.valueOf(worldChunkX))
        .append(ChunkVisualizerData.KEY_CHUNK_Z, String.valueOf(worldChunkZ))
);
```

**Note**: `EventData.append()` requires `String` values - convert integers using `String.valueOf()`

#### Dynamic UI Building

**Pattern**: Clear container before appending new elements

**Example**:
```java
// CRITICAL: Clear before appending to prevent accumulation
uiCommandBuilder.clear("#ChunkCards");

// Build grid dynamically
for (int z = 0; z < gridSize; z++) {
    // Append row
    uiCommandBuilder.appendInline("#ChunkCards", "Group { ... }");
    
    for (int x = 0; x < gridSize; x++) {
        // Append chunk entry
        uiCommandBuilder.append("#ChunkCards[" + z + "]", "Pages/ChunkEntry.ui");
        
        // Use index-based selectors
        String selector = "#ChunkCards[" + z + "][" + x + "]";
        uiCommandBuilder.set(selector + ".Text", "...");
    }
}
```

#### UI Refresh Pattern

**Pattern**: Refresh page after state-changing operations

**Example**:
```java
@Override
public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ChunkVisualizerData data) {
    // Perform operation (claim/unclaim)
    if ("Claim".equals(data.action)) {
        // ... claim logic ...
    }
    
    // Refresh UI to show updated state
    refreshPage(ref, store);
}

private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
    if (!ref.isValid()) return;
    
    UICommandBuilder commandBuilder = new UICommandBuilder();
    UIEventBuilder eventBuilder = new UIEventBuilder();
    build(ref, commandBuilder, eventBuilder, store);
    sendUpdate(commandBuilder, eventBuilder, true);
}
```

### MessageHelper Utility

**Location**: `com.factioncore.util.MessageHelper`

**Purpose**: Utility class for building multi-line tooltip messages

**Usage**:
```java
Message tooltip = MessageHelper.multiLine()
    .append(Message.raw("Owner: ").bold(true).color("#93844c"))
    .append(Message.raw(factionName))
    .nl()
    .append(Message.raw("Description: ").bold(true).color("#93844c"))
    .append(Message.raw(description))
    .build();

uiCommandBuilder.set(selector + ".TooltipTextSpans", tooltip);
```

**Key Methods**:
- `multiLine()`: Creates new `ML` builder instance
- `append(Message)`: Adds message to builder
- `nl()`: Adds newline separator
- `build()`: Returns final `Message` object

**Best Practices**:
1. Chain methods for readable code
2. Use `nl()` for line breaks in tooltips
3. Apply formatting (bold, color) to individual message parts
4. Call `build()` at the end to get final `Message`

### UI Best Practices

1. **Clear Before Append**: Always clear containers before rebuilding dynamic content
2. **Index-Based Selectors**: Use array-style selectors for grid elements (`#Container[row][col]`)
3. **Structured Event Data**: Use typed `EventData` with separate fields, not string parsing
4. **Type Conversions**: Convert integers to strings for `EventData.append()` using `String.valueOf()`
5. **Refresh After Actions**: Call `refreshPage()` after state-changing operations
6. **Configurable UI**: Read UI parameters from config (e.g., grid size)
7. **Validation**: Validate permissions and state before performing operations
8. **Error Messages**: Provide clear error messages for failed operations
9. **Visual Feedback**: Use colors, tooltips, and markers to provide user feedback
10. **Null Safety**: Use `@Nonnull` annotations and check for null before operations

---

## Conclusion

FactionCore demonstrates a well-architected Hytale plugin that:
- Follows ECS best practices
- Implements comprehensive chunk claiming and protection system
- Uses ECS event systems for block protection
- Implements comprehensive logging with debug mode
- Provides flexible, configurable permission system with LuckPerms integration
- Features flexible member-level permission system (owner-granted permissions with LuckPerms context)
- Features interactive UI system with proper event handling
- Maintains clean separation of concerns across layers
- Optimizes for performance (component design, system tick rates, async file I/O, permission caching)
- Ensures data consistency (single source of truth pattern)
- Follows Hytale file storage conventions (universe/ directory)
- Implements proper command system with async support
- Uses CODEC-based configuration (Hytale recommended pattern)
- Implements thread-safe permission management with async LuckPerms sync

The plugin integrates seamlessly with Hytale's ECS system while providing:
- Robust logging with configurable debug levels
- Configurable permission management (LuckPerms with context-based permissions)
- Flexible member-level permissions (owner-controlled, independent of plugin permissions)
- Thread-safe permission caching with O(1) lookups
- Chunk claiming system with protection (break, place, interact)
- Interactive UI system following Hytale UI best practices
  - Chunk visualizer with configurable grid size
  - Click-to-claim/unclaim functionality
  - Real-time UI updates
- World map integration showing claimed chunks with faction colors
- Proper data storage practices (universe/ directory, BlockingDiskFile pattern)
- Clean architecture with clear extension points

**Key Implementation Highlights**:
- **ECS Integration**: Lightweight components, efficient systems, proper registration, event systems for protection
- **Chunk Claiming**: Full chunk claiming system with protection, claim limits, world blacklists
- **Chunk Visualizer UI**: Interactive grid-based UI for visualizing and claiming chunks with configurable size
- **World Map Integration**: Custom world map provider showing claimed chunks with faction colors and borders
- **Protection Systems**: ECS event-based protection (BreakBlock, PlaceBlock, UseBlock) using EntityEventSystem
- **Member Permissions System**: Flexible owner-granted permission system with LuckPerms integration
  - Owner-controlled permissions (rename, color, invite, allies, enemies)
  - Independent of plugin permissions for faction inner actions
  - Thread-safe caching with `ConcurrentHashMap` for O(1) lookups
  - Async LuckPerms sync via single-threaded `ExecutorService`
  - Context-based permissions (`faction=<faction-uuid>`) for scoped access
  - Periodic sync (every 30s) to detect external changes
  - Automatic cleanup on member/faction removal
- **FactionPermissionManager**: Thread-safe singleton for permission management
  - Cache-first strategy for fast permission checks
  - Non-blocking public API (all methods return immediately)
  - Single-threaded executor prevents race conditions
  - Proper shutdown handling for all executors
- **Configuration**: Dual CODEC-based config system (GeneralConfig for plugin settings, ClaimConfig for claim settings)
  - Custom path support for GeneralConfig (universe/FactionCore/config.json)
  - Proper loading order (load before access)
  - Uppercase JSON keys required (Hytale KeyedCodec requirement)
- **File Storage**: SQLite database with in-memory caching, single writer thread pattern, async saving
- **Command System**: Async commands with world executor, proper validation, claim commands
- **Data Management**: Singleton manager pattern, JSON persistence, component synchronization, async file I/O
- **Developer Experience**: Comprehensive debug logging, clear error messages, extensible architecture
