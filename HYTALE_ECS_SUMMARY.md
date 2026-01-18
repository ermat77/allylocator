# Hytale ECS System - Detailed Summary

## Overview

Hytale implements its own ECS (Entity Component System) framework built from scratch. This document provides a comprehensive overview of how Hytale's ECS architecture works, including concrete types, patterns, and implementation details.

## Architecture Hierarchy

### The Big Picture

The server architecture follows this hierarchy:

```
Server
  └── Universe (singleton, one per server)
      ├── PlayerRef (contains all connected players)
      ├── PlayerStorage (saves player data to disk)
      └── Worlds (multiple instances)
          └── EntityStore (where all ECS magic happens)
```

### Universe

- **Type**: Singleton
- **Quantity**: Exactly one per server
- **Purpose**: The root of everything
- **Access**: Call `Universe.get()` to access it
- **Contains**:
  - Worlds
  - Players
  - Player storage

**Example Usage:**
```java
Universe universe = Universe.get();
World world = universe.getWorld("worldName");
```

### World

- **Type**: Game container
- **Purpose**: One game instance
- **Contains**:
  - Entity Store (all entities in this world)
  - Chunk Store (all blocks and terrain)
  - List of players currently in this world
  - World-specific settings (time, weather, etc.)

**Example Usage:**
```java
EntityStore store = world.getEntityStore();
List<PlayerRef> players = world.getPlayers();
```

### Entity Store

- **Location**: Inside each world
- **Purpose**: The heart of Hytale's ECS
- **Function**: All entities live here

## Player Ref: The Bridge

### What is Player Ref?

`PlayerRef` is a special class that serves as the bridge between the network connection and the ECS entity.

### Key Characteristics

- **When Created**: When a player connects to the server
- **Contains**:
  - UUID
  - Username
  - Network handler (packet handler)
- **Not an Entity**: Initially, players are NOT in the ECS yet
- **Becomes Entity**: When they join a world, the PlayerRef gets linked to an actual ECS entity in the EntityStore
- **Link Behavior**: 
  - Link is created when player joins a world
  - Link breaks when player leaves the world
  - PlayerRef persists even when not in a world

### Structure

```
PlayerRef
  ├── UUID
  ├── String (username)
  ├── PacketHandler (network connection)
  ├── Entity (when in a world)
  └── EntityStoreHolder (safe data when not in a world)
```

## Core ECS Types

### Ref (Reference)

- **What it is**: A pointer to an entity
- **Analogy**: Think of it as a house address - tells you where to find one specific entity in the store
- **Entity Representation**: In Hytale, an entity is just an ID, and that ID is wrapped in a `Ref`
- **Validity**: 
  - Can become invalid if the entity is removed
  - Check validity with `ref.isValid()`
  - In most cases (especially when coming from events), you don't need to check validity

### Store (Entity Container)

- **What it is**: Where all entities in a world live
- **Analogy**: Think of the store as the village, and refs as house addresses within it
- **Capabilities**:
  - Get a component from an entity
  - Add a component to an entity
  - Remove a component from an entity
  - Check if an entity has a component

### Component Types

- **What it is**: A unique identifier for each component class
- **Access**: Get it with a static method on the component class
- **Why not use class directly?**: Performance
- **Internal Implementation**: Component type has an integer index internally
- **Performance Benefit**: Way faster than looking up by class name
- **Why it matters**: Hytale processes thousands of entities per frame, so this optimization is critical

## Reading Components

### The Core ECS Read Pattern

The fundamental pattern for reading components:

1. **Get the ref** (pointer to an entity)
2. **Get the store** (entity container)
3. **Get the component** from the store using the ref and component type
4. **Read data** from the component

**Example:**
```java
Ref entityRef = ...;  // Step 1: Get ref
EntityStore store = ...;  // Step 2: Get store
TransformComponent transform = store.getComponent(entityRef, TransformComponent.TYPE);  // Step 3: Get component
float x = transform.getPositionX();  // Step 4: Read data
float y = transform.getPositionY();
float z = transform.getPositionZ();
```

**Key Points:**
- You don't work with the entity itself
- You work with pointers (ref) and the entity store
- This is the core ECS read pattern used constantly

## Writing Components

### Three Operations

#### 1. Adding a Component
When an entity didn't have the component before:
```java
store.addComponent(entityRef, componentType, componentData);
```

#### 2. Modifying a Component
When updating an existing component:
```java
store.setComponent(entityRef, componentType, componentData);
```

#### 3. Removing a Component
```java
store.removeComponent(entityRef, componentType);
```

### Component as Trigger Pattern

A common ECS pattern where adding a component triggers behavior.

**Example: Teleport Pattern**

Instead of modifying position directly, you add a teleport component:

```java
// Define teleport component with coordinates
TeleportComponent teleport = new TeleportComponent(x, y, z);

// Add component to entity
store.addComponent(entityRef, TeleportComponent.TYPE, teleport);

// A teleport system runs every tick:
// 1. Finds entities with teleport components
// 2. Moves them
// 3. Removes the component
```

**Key Concept**: The component is like a request that the system fulfills. Adding a component can trigger behavior.

## System Types

Systems determine **when** your code runs. Hytale has different system types for different timing needs.

### System Type Selection Guide

Choose the system type based on when your code needs to run:

| System Type | When to Use |
|------------|-------------|
| **Entity Ticking System** | Need to run every frame |
| **Ref System** | Need to run when an entity joins or leaves |
| **Ref Change System** | Need to run when a component changes |
| **Damage Event System** | Need to run when damage happens |

### Entity Ticking System

Runs every frame for entities matching a query.

**Structure:**
```java
public class PoisonDamageSystem extends EntityTickingSystem {
    
    @Override
    protected Query getQuery() {
        // Filter: only process entities with both Health and Poison components
        return Query.and(
            ComponentType.of(HealthComponent.class),
            ComponentType.of(PoisonComponent.class)
        );
    }
    
    @Override
    protected void tick(Ref entity, float dt) {
        // Process entity every frame
        // dt = delta time (time since last frame in seconds)
    }
}
```

**Key Methods:**
- `getQuery()`: Defines which entities the system processes
- `tick(Ref entity, float dt)`: Called every frame for each matching entity

**Delta Time (dt):**
- **What it is**: Time since the last frame in seconds
- **At 60fps**: dt ≈ 0.016 seconds
- **Why it matters**: Makes game frame-rate independent
- **Example**: If you want to deal 10 damage per second, you need to respect the frame rate. You don't want more damage if the game runs at higher FPS.

### Queries

**What is a Query?**
- A filter that tells the system which entities to process
- Without queries, you'd have to manually check every entity
- With queries, Hytale does this efficiently for you

**Query Combinations:**

1. **AND**: Entities with both components
   ```java
   Query.and(HealthComponent.TYPE, PoisonComponent.TYPE)
   ```

2. **OR**: Entities with either component
   ```java
   Query.or(PlayerRef.TYPE, NPCComponent.TYPE)
   ```

3. **NOT**: Entities that do NOT have a specific component
   ```java
   Query.not(InvulnerableComponent.TYPE)
   ```

4. **Combined**: Complex filters
   ```java
   // Entities with health but not invulnerable
   Query.and(
       HealthComponent.TYPE,
       Query.not(InvulnerableComponent.TYPE)
   )
   ```

### Ref System (Lifecycle Hooks)

Runs when entities appear or disappear.

**Use Cases:**
- Welcome messages
- Initialization
- Cleanup
- Tracking joins and leaves

**Example: Welcome System**
```java
public class WelcomeSystem extends RefSystem {
    
    @Override
    protected Query getQuery() {
        // Only want player refs
        return Query.of(PlayerRef.TYPE);
    }
    
    @Override
    protected void onEntityAdded(Ref entity) {
        // Player joined - send welcome message
        // No need to check if it's a player - query already filtered
    }
    
    @Override
    protected void onEntityRemoved(Ref entity) {
        // Player left - send goodbye message
    }
}
```

### Damage Event System

Used for combat, intercepting and modifying damage.

**Example: Armor System**
```java
public class ArmorSystem extends DamageEventSystem {
    
    @Override
    protected void onDamage(DamageEvent event) {
        Ref entity = event.getTarget();
        ArmorComponent armor = store.getComponent(entity, ArmorComponent.TYPE);
        
        if (armor != null) {
            // Entity has armor - reduce damage
            float damage = event.getAmount();
            float reducedDamage = damage - armor.getValue();
            event.setAmount(reducedDamage);
        }
        
        // Propagate event further
    }
}
```

**Use Cases:**
- Armor damage modifiers
- Invulnerability
- Damage logging

## Performance: Archetypes

### What are Archetypes?

**Definition**: An archetype is a "fingerprint" of which component types an entity has.

**Key Concept**: Entities with the same components are grouped together.

### How It Works

1. **Grouping**: Entities with the same component set share the same archetype
   - Example: All NPCs, all players, all projectiles

2. **Query Optimization**: When a query needs specific components, Hytale can skip entire archetypes that don't match
   - Example: If query needs `PlayerRef`, Hytale skips archetypes B and C entirely
   - Doesn't have to check every single entity

3. **Memory Layout**: Entities with the same archetype are stored together in memory
   - Stored in "archetype chunks"
   - Very cache-friendly
   - Very fast

### Why It Matters

**Structure of Arrays (SoA) Pattern:**
- When a system iterates over health components, they're all next to each other in memory
- CPU can prefetch them efficiently
- This is why Hytale is so performant in modding

**Visualization:**
```
Archetype A: [Entity1, Entity2, Entity3, ...] (all have same components)
Archetype B: [Entity4, Entity5, Entity6, ...] (different component set)
Archetype C: [Entity7, Entity8, Entity9, ...] (another component set)
```

## Important Systems and Components

### EntityStatMap

**Purpose**: Health and statistics system

**Key Features:**
- Hytale doesn't use a simple health component
- Has a flexible stat system
- Works for: health, mana, stamina, hunger, and more
- All stats work the same way:
  - Current value
  - Max value
  - Regeneration
  - Modifiers

**Extensibility:**
- You can add your own stats to any entity that has EntityStatMap
- This is why Hytale chose this architecture - easy to extend stats

**Usage:**
```java
EntityStatMap stats = store.getComponent(entityRef, EntityStatMap.TYPE);
Stat health = stats.getStat(StatType.HEALTH);
float currentHealth = health.getCurrent();
float maxHealth = health.getMax();
```

### Transform Component

**Purpose**: Position and rotation

**Contains:**
- Position (X, Y, Z)
- Rotation (body direction)

**Usage:**
```java
TransformComponent transform = store.getComponent(entityRef, TransformComponent.TYPE);
Vector3 position = transform.getPosition();
Rotation rotation = transform.getRotation(); // Body direction
```

**Important Distinction:**
- **Transform rotation**: Body direction (where legs and body face)
- **Head rotation component**: Camera direction (where the camera looks)
- **For teleporting**: You need to set the head rotation component separately

## Interaction System

### Important: Not Part of ECS

The interaction system is **separate from ECS**. It's a separate subsystem.

### Why Separate?

Interactions handle the **timing of actions**:
- Swing animations
- Charge up
- Cooldowns

They eventually trigger ECS changes, but the animation timing isn't stored as components.

### How It Works

**Analogy**: Interaction means "a player clicked something" → animation plays at the right moment → ECS gets modified

**Example: Attack Flow**

1. **Player clicks attack button** (left mouse click)
2. **Interaction starts**: Swing animation begins
3. **Interaction ticks each frame**: Animation progresses
4. **At hit point**: ECS gets modified
   - Damage event is added
   - System processes damage
   - Health components get updated

### Interaction Types

Hytale has many interaction types:
- Place block interaction
- Damage entity interaction
- Projectile interaction
- Use entity interaction
- And many more...

### Configuration

- **Not hardcoded**: Interactions are configured in JSON assets
- **Examples**: A sword's attack, a boss's draw, a shovel's dig
- All defined as **interaction chains**

### Interaction Class Example

```java
public class DamageEntityInteraction extends Interaction {
    // Properties
    private float damage;
    private float angle;
    private List<DamageEffect> effects;
    
    // Interaction doesn't directly modify ECS components
    // It describes what should happen
    // Actual ECS changes happen when interaction executes at the right moment
}
```

## Systems vs Events vs Interactions

### Three Different Concepts

| Type | What It Is | Example |
|------|-----------|---------|
| **System** | ECS processor that runs every tick | Auto-heal system that heals over time |
| **Event** | Notification broadcast | Player chat event when someone types |
| **Interaction** | Timed action sequence | Sword swing with animation |

### Attack Flow Example

1. **Interaction starts**: Sword swing begins (animation)
2. **At hit point**: Event is fired
3. **System processes**: Damage system handles the event
4. **Component updated**: Health component gets updated

## Summary of Key Concepts

### Hierarchy
- **Universe**: Singleton, one per server (root of everything)
- **World**: Game container with entity store
- **Entity Store**: Container for all entities (heart of ECS)

### Core Types
- **Ref**: Pointer to one entity (like a house address)
- **Store**: Container for all entities (the village)
- **Component Type**: Fast identifier for component classes (integer index for performance)

### Patterns
- **Read Pattern**: `store.getComponent(ref, componentType)`
- **Write Pattern**: `store.addComponent()`, `store.setComponent()`, `store.removeComponent()`
- **Component as Trigger**: Adding a component triggers system behavior

### Performance
- **Queries**: Filter which entities a system processes
- **Archetypes**: Group entities by component set for fast iteration (Structure of Arrays)

### System Types
- **Ticking System**: Runs every frame
- **Ref System**: Lifecycle hooks (join/leave)
- **Damage Event System**: Combat and damage handling
- **Ref Change System**: Component change detection

### Important Notes
- **Interactions**: Separate from ECS, handle timed actions and animations
- **EntityStatMap**: Flexible stat system (health, mana, stamina, etc.)
- **Transform Component**: Position and body rotation (use HeadRotationComponent for camera direction)

## Best Practices

1. **Choose the right system type** based on when your code needs to run
2. **Use queries efficiently** to filter entities
3. **Leverage archetypes** - they're automatically optimized
4. **Use delta time** in ticking systems for frame-rate independence
5. **Component as trigger pattern** - add components to request actions
6. **Don't modify ECS directly from interactions** - let systems handle it
7. **Remember**: PlayerRef is the bridge between network and ECS entities
