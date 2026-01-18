# RealFaction Plugin - Technical Documentation

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [ECS Integration](#ecs-integration)
4. [Component Registration](#component-registration)
5. [Core Components](#core-components)
6. [UI System](#ui-system)
7. [Command System](#command-system)
8. [Logging System](#logging-system)
9. [Permissions System](#permissions-system)
10. [Data Flow](#data-flow)
11. [Performance Considerations](#performance-considerations)
12. [Extension Points](#extension-points)
13. [File Storage Best Practices](#file-storage-best-practices)

---

## Overview

RealFaction is a Hytale server plugin implementing a faction system with map marker filtering. Built on Hytale's Entity Component System (ECS) for efficient, scalable faction management.

**Key Features:**
- Faction creation, membership management
- Persistent JSON storage
- Map marker filtering (faction-only visibility)
- Interactive UI system for faction management
- ECS-based architecture
- Event-driven updates

---

## Architecture

### System Architecture

```
┌─────────────────────────────────────────────┐
│         RealFaction Plugin                  │
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
2. **UI Layer**: Interactive user interfaces (`FactionShowPage`)
3. **Manager Layer**: Business logic & persistence (`FactionManager`)
4. **ECS Layer**: Component sync & filtering (`FactionSystem`, `FactionMarkerFilterSystem`)
5. **Service Layer**: Reusable logic (`FactionMarkerFilterService`)
6. **Utility Layer**: Logging, permissions, config (`ConfigUtil`, `PermissionUtil`)

### Data Flow Architecture

```
User Command/Event
    ↓
FactionManager (Singleton - Source of Truth)
    ↓
JSON Persistence ← → In-Memory Maps
    ↓
ECS Systems (Synchronize Components)
    ↓
Hytale EntityStore (Player Entities)
```

**Key Design Principles:**
- **Single Source of Truth**: `FactionManager` holds all faction data
- **Component Synchronization**: ECS systems keep components in sync with manager
- **Separation of Concerns**: Each layer has distinct responsibilities
- **Event-Driven**: Systems respond to changes automatically

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

Here's how `FactionComponent` is registered in RealFaction:

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

**Location**: `com.realfaction.faction.FactionManager`

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
- `removePlayerFromFaction(UUID playerUuid)`
- `saveFactions()` / `loadFactions()`

**Persistence**: JSON file at `universe/Realfaction/real_faction.json`

### FactionInfo

**Fields**: `id`, `owner`, `name`, `members[]`, `createdAt`

**Methods**: `isOwnerOrMember()`, `addMember()`, `removeMember()`

### FactionMarkerFilterService

**Purpose**: Reusable marker filtering logic

**Filter Logic**:
- Hide self marker
- Hide all if viewer has no faction
- Show only if other player is in same faction

---

## UI System

### Overview

RealFaction uses **HyUI** (a Hytale UI library) to build user interfaces programmatically using a builder API. This approach allows creating UIs entirely in Java code without requiring separate `.ui` files, providing better type safety and easier dynamic UI generation.

### Architecture

```
Command → Static Method → PageBuilder → UI Elements → Event Handlers
   ↓            ↓             ↓              ↓              ↓
/f show  FactionSettingsPage.open()  PageBuilder  ButtonBuilder  UIContext
```

**Key Components**:
1. **PageBuilder**: Main builder for creating pages
2. **Element Builders**: Specialized builders for UI elements (ButtonBuilder, TextFieldBuilder, etc.)
3. **UIContext**: Provides access to UI element values in event handlers
4. **Event Listeners**: Lambda-based event handling with direct value access

### HyUI Integration

**Dependency Configuration**:
```gradle
dependencies {
    // HyUI - implementation so it gets bundled into the final JAR
    implementation(files('libs/HyUI-0.3.0-all.jar'))
}

// Fat JAR task to bundle HyUI
tasks.register('fatJar', Jar) {
    archiveClassifier.set('')
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    from sourceSets.main.output
    from sourceSets.main.resources
    
    // Include HyUI classes
    from(zipTree('libs/HyUI-0.3.0-all.jar')) {
        exclude 'META-INF/**'
    }
    
    manifest {
        from jar.manifest
    }
}

tasks.build.dependsOn fatJar
```

**Key Benefits**:
- **No UI Files Required**: Build UI entirely in Java code
- **Type Safety**: Compile-time checking for UI structure
- **Dynamic Generation**: Easily create UIs based on runtime data
- **Better IDE Support**: Full autocomplete and refactoring support
- **Simplified Build**: No need to manage separate `.ui` files

### Building UI with HyUI

**Core Concept**: Use builder classes to construct UI elements programmatically, then add them to a `PageBuilder` which handles opening the page.

#### Basic Page Structure

```java
public class MyPage {
    public static void open(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        // Create page builder
        PageBuilder pageBuilder = PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismiss);
        
        // Create page overlay (matches $C.@PageOverlay)
        PageOverlayBuilder pageOverlay = PageOverlayBuilder.pageOverlay();
        
        // Create decorated container (matches $C.@DecoratedContainer)
        ContainerBuilder container = ContainerBuilder.container()
            .withTitleText("My Page Title")
            .withAnchor(new HyUIAnchor().setWidth(600).setHeight(500));
        
        // Create content group
        GroupBuilder contentGroup = GroupBuilder.group()
            .withId("Content")
            .withLayoutMode("Top");
        
        // Add elements to content group
        contentGroup.addChild(
            LabelBuilder.label()
                .withId("myLabel")
                .withText("Hello World")
        );
        
        // Add content to container
        container.addContentChild(contentGroup);
        
        // Add container to page overlay
        pageOverlay.addChild(container);
        
        // Add page overlay to page
        pageBuilder.addElement(pageOverlay);
        
        // Add back button
        pageBuilder.addElement(ButtonBuilder.backButton());
        
        // Open the page
        pageBuilder.open(playerRef, store);
    }
}
```

#### Available Builders

**Page Builders**:
- `PageBuilder`: Main page builder, use `PageBuilder.pageForPlayer(playerRef)`
- `PageOverlayBuilder`: Creates page overlay wrapper
- `ContainerBuilder`: Creates decorated container with title

**Element Builders**:
- `ButtonBuilder`: Buttons (`textButton()`, `cancelTextButton()`, `backButton()`)
- `TextFieldBuilder`: Text inputs (`textInput()`)
- `LabelBuilder`: Text labels (`label()`)
- `GroupBuilder`: Container groups (`group()`)
- `CheckBoxBuilder`: Checkboxes (`checkBox()`)
- `SliderBuilder`: Sliders (`slider()`)

**Common Methods** (available on all element builders):
- `.withId(String id)`: Set element ID
- `.withAnchor(HyUIAnchor anchor)`: Set positioning/sizing
- `.withVisible(boolean visible)`: Control visibility
- `.withFlexWeight(int weight)`: Set flex weight for layout
- `.addChild(UIElementBuilder<?> child)`: Add child elements (for groups)

### Event Handling

#### Button Events

Buttons use `addEventListener` with lambda handlers:

```java
ButtonBuilder button = ButtonBuilder.textButton()
    .withId("myButton")
    .withText("Click Me")
    .addEventListener(CustomUIEventBindingType.Activating, (Void v, UIContext ctx) -> {
        // Handle button click
        Player player = store.getComponent(ref, Player.getComponentType());
        player.sendMessage(Message.raw("Button clicked!"));
    });
```

#### Text Field Value Capture

**Important**: Text field values must be captured using `ValueChanged` events, as they are not automatically available in button click handlers.

```java
// Store value in a holder array
final String[] valueHolder = new String[1];

// Create text field
TextFieldBuilder textInput = TextFieldBuilder.textInput()
    .withId("MyInput")
    .withAnchor(new HyUIAnchor().setHeight(35));

// Listen to value changes
textInput.addEventListener(CustomUIEventBindingType.ValueChanged, (String value) -> {
    valueHolder[0] = value;  // Store the value
    ConfigUtil.debug("Input value changed to: " + value);
});

// Use stored value in button handler
ButtonBuilder submitButton = ButtonBuilder.textButton()
    .withId("submitButton")
    .withText("Submit")
    .addEventListener(CustomUIEventBindingType.Activating, (Void v, UIContext ctx) -> {
        String value = valueHolder[0];
        if (value == null) {
            // Fallback: try to get from UIContext (may not work)
            value = ctx.getValue("MyInput", String.class)
                .or(() -> ctx.getValue("#MyInput", String.class))
                .orElse(null);
        }
        // Use the value
        handleSubmit(value, ctx);
    });
```

#### UIContext Value Retrieval

`UIContext` provides access to UI element values, but text field values may not always be available. Try multiple key formats:

```java
// Try different key formats
String value = ctx.getValue("ElementId", String.class)
    .or(() -> ctx.getValue("#ElementId", String.class))
    .or(() -> ctx.getValue("@ElementId", String.class))
    .orElse(null);
```

**Note**: The `ValueChanged` event listener approach is more reliable for text fields.

### Anchoring and Layout

#### HyUIAnchor

Use `HyUIAnchor` to position and size elements:

```java
// Set width and height
new HyUIAnchor().setWidth(600).setHeight(500)

// Set height only
new HyUIAnchor().setHeight(35)

// Set positioning
new HyUIAnchor().setLeft(10).setTop(20)

// Set full (fills available space)
new HyUIAnchor().setFull(10)  // 10px padding on all sides
```

#### Layout Modes

Groups support layout modes for arranging children:

```java
GroupBuilder group = GroupBuilder.group()
    .withLayoutMode("Top")        // Vertical layout (top to bottom)
    .withLayoutMode("Right")      // Horizontal layout (left to right)
    .withLayoutMode("Left")       // Horizontal layout (right to left)
    .withLayoutMode("Bottom");    // Vertical layout (bottom to top)
```

#### Flex Weight

Use flex weight to distribute space:

```java
LabelBuilder spacer = LabelBuilder.label()
    .withFlexWeight(1);  // Takes remaining space
```

### Complete Example: FactionSettingsPage

Here's a complete example showing how to build a UI with text inputs and buttons:

```java
public class FactionSettingsPage {
    
    public static void open(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        // Get faction data
        UUID playerUuid = playerRef.getUuid();
        FactionManager factionManager = FactionManager.getInstance();
        FactionInfo faction = factionManager.getFactionFromPlayer(playerUuid);
        
        if (faction == null || !faction.isOwner(playerUuid)) {
            return;  // Not owner, close page
        }
        
        // Create page builder
        PageBuilder pageBuilder = PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismiss);
        
        // Create page structure
        PageOverlayBuilder pageOverlay = PageOverlayBuilder.pageOverlay();
        ContainerBuilder container = ContainerBuilder.container()
            .withTitleText("Faction Settings")
            .withAnchor(new HyUIAnchor().setWidth(600).setHeight(500));
        
        GroupBuilder contentGroup = GroupBuilder.group()
            .withId("Content")
            .withLayoutMode("Top");
        
        // Create text field with value capture
        final String[] renameValueHolder = new String[1];
        TextFieldBuilder renameInput = TextFieldBuilder.textInput()
            .withId("RenameInput")
            .withAnchor(new HyUIAnchor().setHeight(35))
            .withFlexWeight(1);
        
        // Capture value changes
        renameInput.addEventListener(CustomUIEventBindingType.ValueChanged, (String value) -> {
            renameValueHolder[0] = value;
        });
        
        contentGroup.addChild(renameInput);
        
        // Create button with event handler
        ButtonBuilder renameButton = ButtonBuilder.textButton()
            .withId("renameButton")
            .withText("Rename")
            .addEventListener(CustomUIEventBindingType.Activating, (Void v, UIContext ctx) -> {
                String newName = renameValueHolder[0];
                if (newName == null) {
                    // Fallback to UIContext
                    newName = ctx.getValue("RenameInput", String.class)
                        .or(() -> ctx.getValue("#RenameInput", String.class))
                        .orElse(null);
                }
                handleRename(ref, store, playerRef, newName, ctx);
            });
        
        contentGroup.addChild(renameButton);
        
        // Assemble page
        container.addContentChild(contentGroup);
        pageOverlay.addChild(container);
        pageBuilder.addElement(pageOverlay);
        pageBuilder.addElement(ButtonBuilder.backButton());
        
        // Open page
        pageBuilder.open(playerRef, store);
    }
    
    private static void handleRename(Ref<EntityStore> ref, Store<EntityStore> store,
                                   PlayerRef playerRef, String newName, UIContext ctx) {
        // Handle rename logic
        if (newName == null || newName.trim().isEmpty()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            player.sendMessage(Message.raw("Name cannot be empty").color(Color.RED));
            return;
        }
        // ... process rename
    }
}
```

**Key Patterns**:
1. **Static Methods**: Use static `open()` methods instead of extending `InteractiveCustomUIPage`
2. **Value Holders**: Use final arrays to capture text field values
3. **ValueChanged Events**: Listen to text field changes to capture values
4. **Fallback Retrieval**: Try UIContext if value holder is null
5. **Page Assembly**: Build from bottom up (elements → groups → containers → page)

### Event Types

**Available Event Types**:
- `CustomUIEventBindingType.Activating`: Button clicks, element activation
- `CustomUIEventBindingType.ValueChanged`: Text field changes, dropdown selections

**Event Handler Signatures**:

```java
// Button activation (no value parameter)
.addEventListener(CustomUIEventBindingType.Activating, 
    (Void v, UIContext ctx) -> { /* handler */ })

// Text field value change (value parameter)
.addEventListener(CustomUIEventBindingType.ValueChanged, 
    (String value) -> { /* handler */ })

// Text field value change with context
.addEventListener(CustomUIEventBindingType.ValueChanged, 
    (String value, UIContext ctx) -> { /* handler */ })
```

### UI Best Practices

1. **Use Value Holders**: Always capture text field values using `ValueChanged` events and store in holder arrays
2. **Provide Fallbacks**: Try UIContext retrieval as fallback if value holder is null
3. **Use Static Methods**: Prefer static `open()` methods over extending `InteractiveCustomUIPage`
4. **Validate Input**: Always check for null/empty values before processing
5. **Use Debug Logging**: Add extensive `ConfigUtil.debug()` calls to track UI flow and value capture
6. **Handle Errors Gracefully**: Show user-friendly messages, don't crash on invalid input
7. **Close Pages Properly**: Use `player.getPageManager().setPage(ref, store, Page.None)` to close
8. **Build Bottom-Up**: Assemble UI from elements → groups → containers → page
9. **Set Element IDs**: Always set IDs on elements you need to reference later
10. **Use Appropriate Builders**: Use specialized builders (ContainerBuilder, PageOverlayBuilder) for structure

### Debugging UI Issues

**Common Issues and Solutions**:

1. **Text Field Values Not Captured**:
   - ✅ Use `ValueChanged` event listener to capture values
   - ✅ Store in final array holder
   - ✅ Check holder value in button handler
   - ✅ Add debug logging to track value changes

2. **Events Not Firing**:
   - ✅ Verify event listener is attached before adding element to page
   - ✅ Check event type matches element type (Activating for buttons)
   - ✅ Ensure element has an ID set

3. **Elements Not Visible**:
   - ✅ Check anchor settings (width/height)
   - ✅ Verify element is added to correct parent
   - ✅ Check layout mode on parent group

4. **Page Not Opening**:
   - ✅ Verify `pageBuilder.open(playerRef, store)` is called
   - ✅ Check for exceptions in page building code
   - ✅ Ensure player has valid reference

### Migration from .ui Files

**Old Approach** (`.ui` files + `InteractiveCustomUIPage`):
- Declarative UI files
- Event data classes with codecs
- `build()` and `handleDataEvent()` methods

**New Approach** (HyUI builders):
- Programmatic UI building
- Direct lambda event handlers
- Static `open()` methods
- No codecs needed

**Benefits of Migration**:
- Type safety at compile time
- Better IDE support
- Easier dynamic UI generation
- No separate UI files to maintain
- Direct value access in handlers

---

## Command System

### Overview

RealFaction uses Hytale's command system with async command support for UI operations and world access.

### Command Architecture

```
AbstractAsyncCommand
    ↓
FactionCommand (main command)
    ├─ CreateFactionCommand (subcommand)
    └─ ShowFactionCommand (subcommand)
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
    }
}
```

### Async Commands

**Why Async?**: Commands that need to:
- Access world/entity data
- Open UI pages
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
            
            // Open UI
            FactionShowPage page = new FactionShowPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
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

**Purpose**: Opens UI to show faction or create form

**Flow**:
1. Validate sender is Player
2. Check reference validity
3. Get PlayerRef from store
4. Create FactionShowPage
5. Open via PageManager

**Key Points**:
- Uses `AbstractAsyncCommand` for world access
- Validates all prerequisites before opening UI
- Uses world executor for async operations

---

## Logging System

### Overview

RealFaction uses Hytale's `HytaleLogger` with a configurable debug mode for detailed logging.

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
  "debug": true,
  "verboseDebug": false,
  "useLuckperms": false
}
```

**Configuration Options**:
- `debug: true/false` - General debug logging (commands, permission checks, etc.)
- `verboseDebug: true/false` - Verbose debug logging (tick functions, frequent method calls)
- `useLuckperms: true/false` - Enable/disable LuckPerms usage (default: `false`). When `false`, always uses Hytale permissions.json

This configuration enables general debug logs but disables verbose tick function logs to reduce log spam, and disables LuckPerms usage.

### Logging Locations

- **ConfigUtil**: Config file operations, debug mode changes
- **JsonIntegrityUtil**: JSON validation results (errors/warnings)
- **HytalePermissionManager**: Permission file loading/parsing, permission checks
- **FactionManager**: All faction operations (create, add/remove members, save/load)
- **PermissionUtil**: Permission checks (LuckPerms vs. Hytale fallback)
- **FactionSystem**: Component synchronization (uses `verboseDebug`)
- **FactionMarkerFilterSystem**: Marker filter updates (uses `verboseDebug`)
- **FactionMarkerFilterService**: Filter application logic (uses `verboseDebug`)

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

RealFaction uses a configurable dual-permission system with automatic fallback:
1. **Primary**: LuckPerms API (if available and enabled in config)
2. **Fallback**: Hytale's `permissions.json` file

**Configuration Control**: The `useLuckperms` option in `config.json` controls whether LuckPerms should be used. When set to `false` (default), the plugin will always use Hytale's permissions.json, even if LuckPerms is detected on the server.

### PermissionUtil

**Location**: `com.realfaction.util.PermissionUtil`

**Purpose**: Unified permission checking interface

**Permission Check Flow**:
```
hasPermission(playerUuid, permission)
    ↓
Is useLuckperms enabled in config?
    ├─ No → Check Hytale permissions.json (skip LuckPerms entirely)
    └─ Yes → Is LuckPerms available?
        ├─ Yes → Check LuckPerms API
        │   ├─ Try getUser() (fast path for loaded users)
        │   ├─ If null → loadUser() to load from storage
        │   ├─ Check permission via cached data
        │   ├─ Success → Return result
        │   └─ Failure/Error → Fallback to Hytale
        └─ No → Check Hytale permissions.json
```

**Key Methods**:
- `hasPermission(UUID playerUuid, String permission)`: Generic permission check
- `canCreateFaction(UUID playerUuid)`: Faction creation permission

**Permission Constants**:
- `PERM_CREATE_FACTION = "realfaction.create_faction"`

### LuckPerms Integration

**Configuration**: Controlled by `useLuckperms` in `config.json`
- When `useLuckperms: false` (default), LuckPerms detection is skipped entirely
- When `useLuckperms: true`, the plugin attempts to detect and use LuckPerms

**Detection**: Uses reflection to check for `net.luckperms.api.LuckPermsProvider` class
- **Primary Method**: Uses Hytale's `PluginBridgeClassLoader` via `PluginManager.get().getBridgeClassLoader()` (most reliable for plugin-to-plugin access)
- **Fallback Methods**: Tries context, system, and default classloaders if bridge classloader fails
- Caches both availability status and the working classloader to avoid repeated checks
- Logs which classloader successfully detected LuckPerms for debugging

**API Access**: Uses reflection to avoid compile-time dependency

**User Loading**:
- First attempts `getUser()` for fast access to already-loaded users
- If user not in memory, uses `loadUser()` to load from storage
- Waits for user data to load before checking permissions

**Fallback Behavior**:
- If LuckPerms API not loaded → fallback to Hytale
- If user not found in LuckPerms after loading → fallback to Hytale
- On any error → fallback to Hytale
- All fallbacks are logged for debugging

### HytalePermissionManager

**Location**: `com.realfaction.util.HytalePermissionManager`

**Purpose**: Manages Hytale's default `permissions.json` file

**File Location**: `run/permissions.json` (same directory as `universe/`)

The path is resolved by:
1. Getting `Constants.UNIVERSE_PATH` (points to `run/universe`)
2. Converting to absolute path
3. Getting parent directory (`run/`)
4. Resolving `permissions.json` in that directory

**Data Structure**:

The parser supports both formats:

**Nested Object Format** (Hytale's default):
```json
{
  "users": {
    "uuid": {
      "groups": ["group1", "group2"]
    }
  },
  "groups": {
    "groupName": ["permission1", "permission2", "*"]
  }
}
```

**Direct Array Format** (also supported):
```json
{
  "users": {
    "uuid": ["group1", "group2"]
  },
  "groups": {
    "groupName": ["permission1", "permission2", "*"]
  }
}
```

**Features**:
- Automatic reload on file modification
- Supports both nested object and direct array JSON formats
- Wildcard support (`*` = all permissions, `realfaction.*` = all realfaction permissions)
- OP group detection (`isOp()` method)
- Extensive debug logging for troubleshooting permission issues

**Permission Resolution**:
1. Get player's groups from `users` map
2. For each group, check `groups` map for permissions
3. Support for `*` (all permissions) and `prefix.*` (wildcard)

### Security Model

**Default Behavior**: Deny by default (secure)
- If player not in permissions.json → no permissions
- If permission check fails → deny
- If file read error → deny

**Permission Hierarchy**:
1. Config check: If `useLuckperms: false`, skip to Hytale permissions.json
2. LuckPerms (if available and enabled) - highest priority
3. Hytale permissions.json - fallback
4. Default (no permission) - secure default

### Usage Example

```java
// Check if player can create faction
if (PermissionUtil.canCreateFaction(playerUuid)) {
    // Allow faction creation
} else {
    // Deny with message
}

// Generic permission check
if (PermissionUtil.hasPermission(playerUuid, "realfaction.admin")) {
    // Admin operation
}
```

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
  - Caches LuckPerms availability to avoid repeated classloader checks
  - Loads users on-demand using `loadUser()` when not in memory
  - Falls back gracefully to Hytale permissions.json if LuckPerms unavailable

### Data Access
- O(1) HashMap lookups in `FactionManager`
- ECS optimizes component queries internally

### Persistence
- Save on every change (immediate persistence)
- Save on shutdown (safety net)
- No periodic saves (reduces I/O)

---

## Extension Points

### Adding Features

1. **New Component**: 
   - Implement `Component<EntityStore>`
   - **Register in `Main.setup()`** using `getEntityStoreRegistry().registerComponent()`
   - Store `ComponentType` in `Main` field
   - Add `getComponentType()` static method that retrieves from `Main`
2. **New System**: Extend `EntityTickingSystem<EntityStore>`, register in `Main.setup()` **after** component registration
3. **New Command**: Extend `AbstractAsyncCommand`, register as subcommand in `FactionCommand` or create new main command
4. **New UI Page**: Extend `InteractiveCustomUIPage`, create `.ui` file, open via command
5. **New Permission**: Add constant to `PermissionUtil`, check in commands/UI handlers

### Example: Adding a New UI Command

**Step 1: Create Command**
```java
public class FactionListCommand extends AbstractAsyncCommand {
    public FactionListCommand() {
        super("list", "List all factions");
        setPermissionGroup(GameMode.Adventure);
    }
    
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        // Validate sender, get PlayerRef, open UI
        FactionListPage page = new FactionListPage(playerRef);
        player.getPageManager().openCustomPage(ref, store, page);
        return CompletableFuture.completedFuture(null);
    }
}
```

**Step 2: Register Subcommand**
```java
// In FactionCommand constructor
addSubCommand(new FactionListCommand());
```

**Step 3: Create UI Page**
```java
public class FactionListPage extends InteractiveCustomUIPage<FactionListEventData> {
    // Implement build() and handleDataEvent()
}
```

**Step 4: Create UI File**
```
src/main/resources/Common/UI/Custom/Pages/FactionListPage.ui
```

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

### Example: Adding a New UI Feature

**Adding a Faction Settings UI**:

1. **Create UI File**: `src/main/resources/Common/UI/Custom/Pages/FactionSettingsPage.ui`
2. **Create Java Page**: `FactionSettingsPage extends InteractiveCustomUIPage<FactionSettingsEventData>`
3. **Add Command**: `FactionSettingsCommand extends AbstractAsyncCommand`
4. **Register**: Add subcommand in `FactionCommand`
5. **Handle Events**: Implement `handleDataEvent()` for settings changes

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
- Verify `universe/Realfaction/` directory exists
- Check file permissions
- Verify data is stored in `universe/` directory (not `mods/`)

### UI Issues

**Error: "Could not find document XXXXX for Custom UI Append command"**:
- **Cause**: UI file not found or incorrect path
- **Solutions**:
  1. Verify UI file is at `src/main/resources/Common/UI/Custom/Pages/YourPage.ui` (capital letters)
  2. Check `commands.append()` includes `.ui` extension: `"Pages/YourPage.ui"`
  3. Ensure `build.gradle` includes resources in JAR (see [UI System](#ui-system) section)
  4. Verify `manifest.json` has `"IncludesAssetPack": true`
  5. Rebuild JAR and restart server

**Error: "CustomUI command data must be an object if no property was selected"**:
- **Cause**: Setting value on element without property path
- **Solution**: Use property paths: `commands.set("#elementId.Text", value)` not `commands.set("#elementId", value)`
- **Common Property Paths**:
  - Labels: `#elementId.Text`
  - Groups: `#elementId.Visible`
  - TextFields: `#elementId.value` (for reading only)

**Error: "Key must start with an upper case character! Key: 'action'"**:
- **Cause**: EventData field names must start with uppercase (KeyedCodec requirement)
- **Solution**: Use uppercase field names: `public String Action;` not `public String action;`
- **Also**: Use uppercase in `EventData.of("Action", "value")` and `KeyedCodec("Action", ...)`

**Error: "Unknown property Placeholder on node of type TextField"**:
- **Cause**: TextField component doesn't support `Placeholder` property
- **Solution**: Remove `Placeholder` property, use a Label above the TextField instead

**UI Not Appearing**:
- Check server logs for UI loading errors
- Verify UI file syntax (capitalized properties, correct structure)
- Ensure `$C = "../Common.ui";` is at the top of UI file
- Verify element IDs match between UI file and Java code
- Check that `commands.append()` path is correct and includes `.ui` extension

**Events Not Firing**:
- Verify event binding uses correct element ID (with `#` prefix)
- Check `CustomUIEventBindingType` matches element type (Activating for buttons)
- Ensure EventData codec is properly defined with uppercase field names
- Verify `handleDataEvent()` method is implemented correctly

### Permission Issues

**LuckPerms Not Detected**:
- **First**: Check `config.json` - ensure `useLuckperms: true` if you want to use LuckPerms
- Check server logs for "LuckPerms detected" or "LuckPerms not found" messages
- If `useLuckperms: false`, the plugin will skip LuckPerms detection entirely (this is expected behavior)
- Verify LuckPerms JAR is in `run/mods/` directory
- Check if LuckPerms loads after RealFaction (classloader timing issue)
- The plugin tries multiple classloaders to find LuckPerms (PluginBridgeClassLoader, context, system, default)
- Look for log messages indicating which classloader was used: "LuckPerms detected via PluginBridgeClassLoader"
- If not found, automatically falls back to Hytale permissions.json

**Hytale permissions.json Not Working**:
- Enable debug mode: `ConfigUtil.setDebug(true)` to see path resolution
- Verify file is at `run/permissions.json` (same level as `universe/`)
- Check JSON format - supports both nested `{"groups": [...]}` and direct `[...]` formats
- Verify UUID matches exactly (case-sensitive)
- Check group name spelling (case-sensitive)
- Check permission node spelling (`realfaction.create_faction`)
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

RealFaction follows Hytale best practices for file storage:

**Data Storage**: `universe/Realfaction/`
- Configuration: `universe/Realfaction/config.json`
- Faction data: `universe/Realfaction/real_faction.json`
- All plugin data is stored in the `universe/` directory alongside world/server data

**Why `universe/` and not `mods/`?**
- `mods/` directory is for plugin JAR files and asset packs (code/resources)
- `universe/` directory is for world/server data (persistent game state)
- Storing data in `universe/` keeps it with world data, making backups easier
- Follows the same pattern as hyfaction (reference implementation)

**Implementation**:
```java
// All data files use Constants.UNIVERSE_PATH
Path basePath = Constants.UNIVERSE_PATH.resolve("Realfaction");
Path configPath = basePath.resolve("config.json");
Path factionDataPath = basePath.resolve("real_faction.json");
```

**Permissions File**: `run/permissions.json`
- Hytale's default permissions file is at the root of the run directory
- Resolved via `Constants.UNIVERSE_PATH.getParent().resolve("permissions.json")`
- This is separate from plugin data storage

### Configuration File Structure

The `config.json` file is located at `universe/Realfaction/config.json` with the following structure:

```json
{
  "debug": false,
  "verboseDebug": false,
  "useLuckperms": false
}
```

**Default Values**:
- `debug: false` - General debug logging disabled by default
- `verboseDebug: false` - Verbose debug logging disabled by default
- `useLuckperms: false` - LuckPerms usage disabled by default (uses Hytale permissions.json)

The config file is automatically created with these defaults when the plugin first loads.

---

## Conclusion

RealFaction demonstrates a well-architected Hytale plugin that:
- Follows ECS best practices
- Implements comprehensive logging with debug mode
- Provides flexible, configurable permission system with fallback
- Features interactive UI system with proper event handling
- Maintains clean separation of concerns across layers
- Optimizes for performance (component design, system tick rates)
- Ensures data consistency (single source of truth pattern)
- Follows Hytale file storage conventions (universe/ directory)
- Implements proper command system with async support

The plugin integrates seamlessly with Hytale's ECS system while providing:
- Robust logging with configurable debug levels
- Configurable permission management (LuckPerms with Hytale fallback)
- Interactive UI system following Hytale UI best practices
- Proper data storage practices (universe/ directory)
- Clean architecture with clear extension points

**Key Implementation Highlights**:
- **ECS Integration**: Lightweight components, efficient systems, proper registration
- **UI System**: Declarative UI files, event-driven interactions, proper codec patterns
- **Command System**: Async commands with world executor, proper validation
- **Data Management**: Singleton manager pattern, JSON persistence, component synchronization
- **Developer Experience**: Comprehensive debug logging, clear error messages, extensible architecture
