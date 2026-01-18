# Hytale Plugin UI Creation Guide

## Overview

This guide explains how to create custom user interfaces (UIs) in Hytale plugins. The system uses a declarative approach where UI files describe layouts and Java code handles logic.

## Core Concepts

### Mental Model

- **UI Files**: Declarative descriptions of what should appear on screen (layout, position, size, colors, text)
- **Java Code**: Handles logic, data, actions, and behavior
- **Communication**: Two-way communication between Java and UI files via element IDs

### Communication Directions

1. **Java → UI**: Load UI files, update text, show/hide elements
2. **UI → Java**: Handle events (button clicks, form submissions, selection changes)

### Connection Mechanism

- UI elements are identified by **IDs** (e.g., `#title`, `#nameInput`)
- Java code connects to UI elements by their ID
- The UI file knows nothing about Java code structure

## File Structure

### Required Directory Structure

```
src/
  main/
    java/
      com/
        yourpackage/
          YourPage.java
          YourCommand.java
    resources/
      common/
        UI/
          custom/
            Pages/
              YourPage.ui
      manifest.json
```

**Important**: The UI files must be located at exactly `resources/common/UI/custom/Pages/` (case-sensitive).

### Gradle Configuration

UI files must be included in the JAR. Add to `build.gradle`:

```gradle
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.resources) {
        include '**/*'
    }
}
```

### Manifest Configuration

In `manifest.json`, add:

```json
{
  "includesAssets": true
}
```

This tells Hytale that the mod has assets that need to be bundled.

## Level 1: Basic Static UI

### UI File Structure

UI files use a custom domain-specific language (not JSON or YAML). Basic structure:

```ui
group {
  anchor: 500 400
  background-color: #000000(0.8)
  layout-mode: top-to-bottom
  padding: 20
  
  label #title {
    text: "Hello World"
    anchor: 0 50
    style {
      font-size: 24
      text-color: #FFFFFF
      alignment: center
    }
  }
}
```

### Common UI Properties

- **anchor**: Defines width and height (e.g., `500 400`)
- **background-color**: Hex code with opacity in parentheses (e.g., `#000000(0.8)`)
- **layout-mode**: Direction for ordering elements (`top-to-bottom`, `left-to-right`)
- **padding**: Inner spacing inside the group
- **text**: Text content for labels
- **style**: Contains font-size, text-color, alignment, etc.

### Java Implementation

#### BasicCustomUIPage Class

```java
public class HelloWorldPage extends BasicCustomUIPage {
    
    public HelloWorldPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CAN_DISMISS);
    }
    
    @Override
    protected void build(UICommandBuilder command) {
        command.append("Pages/HelloWorldPage");
    }
}
```

**Key Points:**
- Extends `BasicCustomUIPage`
- Constructor takes `PlayerRef` and `CustomPageLifetime`
- `build()` method loads the UI file using relative path from `resources/common/UI/custom/`
- Path in `command.append()` is relative: `"Pages/YourPage"` (not full path)

#### CustomPageLifetime Options

- `CAN_DISMISS`: Player can close the dialogue
- `CANNOT_CLOSE`: Player cannot close it
- `CAN_DISMISS_OR_CLOSE_THROUGH_INTERACTION`: Can close through interaction

#### Command to Open UI

```java
public class HelloWorldCommand extends AbstractPlayerCommand {
    
    public HelloWorldCommand() {
        super("helloworld", "Opens hello world UI", false);
    }
    
    @Override
    public void execute(CommandContext context, StoreRef store, PlayerRef player, World world) {
        Player playerInstance = store.getComponent(store, player.getComponentType());
        HelloWorldPage page = new HelloWorldPage(player);
        playerInstance.getPageManager().openCustomPage(store, page);
    }
}
```

## Level 2: Interactive UI with Events

### UI File with Inputs

```ui
import common.UI

group {
  anchor: 500 400
  layout-mode: top-to-bottom
  padding: 20
  
  label #title {
    text: "Enter Your Name"
  }
  
  $C.addTextField #nameInput {
    anchor: 0 40
    placeholder: "Enter your name"
  }
  
  textButton #greetButton {
    anchor: 0 40
    text: "Greet Me"
  }
}
```

**Key Points:**
- Import `common.UI` for custom components like `textField`
- Custom components use prefix: `$C.addTextField`
- Built-in components (like `textButton`) don't need prefix
- IDs are set with `#` prefix: `#nameInput`, `#greetButton`

### Event Data Class

When a user interacts with the UI, data needs to flow from UI to Java. This requires:

1. **Event Data Container**: A class to hold the data
2. **Codec**: Instructions for Hytale on how to serialize/deserialize the data

#### Event Data Container

```java
public static class GreetEventData {
    public String playerName;
}
```

#### Codec Definition

```java
public static final BuilderCodec<GreetEventData> CODEC = BuilderCodec.builder(
    GreetEventData.class,
    GreetEventData::new
)
.toBuilder()
.append(
    Codec.STRING.fieldOf("playerName")
        .forGetter(data -> data.playerName)
        .apply((data, value) -> {
            data.playerName = value;
            return data;
        })
)
.build();
```

**Codec Breakdown:**
- `BuilderCodec.builder()`: Creates codec for the class
- `GreetEventData::new`: How to instantiate the class
- `.append()`: Field mapping
- `Codec.STRING.fieldOf("playerName")`: Look for key "playerName" as a string
- `forGetter`: How to read the value from the object
- `apply`: How to set the value in the object (setter)

### The `@` Prefix

**Critical Concept**: The `@` prefix binds UI values to event data.

- `playerName`: Regular value set manually
- `@playerName`: Binds to UI element - Hytale reads it from an input

The `@` tells Hytale: "Get this value from the UI element I specify later."

### InteractiveCustomUIPage Implementation

```java
public class HelloWorldPage extends InteractiveCustomUIPage<GreetEventData> {
    
    public HelloWorldPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CAN_DISMISS, GreetEventData.CODEC);
    }
    
    @Override
    protected void build(UICommandBuilder command) {
        command.append("Pages/HelloWorldPage");
        
        command.addEventBinding(
            "#greetButton",
            EventData.newEventData()
                .append("@playerName", "#nameInput.value")
        );
    }
    
    @Override
    protected void handleDataEvent(StoreRef store, GreetEventData data) {
        Player player = store.getComponent(store, playerRef.getComponentType());
        
        String name = (data.playerName != null && !data.playerName.isEmpty()) 
            ? data.playerName 
            : "Stranger";
        
        player.sendMessage("Hello " + name);
        player.getPageManager().setPage(Page.NONE); // Close the page
    }
}
```

**Key Points:**
- Extends `InteractiveCustomUIPage<EventDataType>`
- Constructor passes the codec as third parameter
- `addEventBinding()`: Binds UI element to event
  - First parameter: Element ID that triggers the event (`#greetButton`)
  - Second parameter: `EventData` with `@` prefixed fields bound to UI values
- `handleDataEvent()`: Called when event is triggered
  - Receives `StoreRef` and deserialized event data
  - Access UI values through the event data object

**Event Binding Syntax:**
```java
command.addEventBinding(
    "#buttonId",                    // Element that triggers event
    EventData.newEventData()
        .append("@fieldName", "#elementId.property")  // Bind UI value to field
);
```

## Level 3: Dynamic Values (Java → UI)

### Setting Values from Java

Use the `set()` method to update UI elements dynamically:

```java
command.set("#stat1Value", "text", String.valueOf(playersOnline));
```

**Syntax:**
- First parameter: Element ID
- Second parameter: Property to change (usually `"text"`)
- Third parameter: Value (must be a string)

**Important**: Numbers must be converted to strings using `String.valueOf()`.

### Example: Dynamic Info Panel

#### Java Implementation

```java
public class InfoPanelPage extends InteractiveCustomUIPage<CloseEventData> {
    
    private final int playersOnline;
    private final int questCount;
    private final String uptime;
    
    public InfoPanelPage(PlayerRef playerRef, int playersOnline, int questCount, String uptime) {
        super(playerRef, CustomPageLifetime.CAN_DISMISS, CloseEventData.CODEC);
        this.playersOnline = playersOnline;
        this.questCount = questCount;
        this.uptime = uptime;
    }
    
    @Override
    protected void build(UICommandBuilder command) {
        command.append("Pages/InfoPanel");
        
        // Set dynamic values
        command.set("#stat1Value", "text", String.valueOf(playersOnline));
        command.set("#stat2Value", "text", String.valueOf(questCount));
        command.set("#stat3Value", "text", uptime);
        
        // Bind close button
        command.addEventBinding("#closeButton", EventData.newEventData());
    }
    
    @Override
    protected void handleDataEvent(StoreRef store, CloseEventData data) {
        Player player = store.getComponent(store, playerRef.getComponentType());
        player.getPageManager().setPage(Page.NONE);
    }
}
```

#### Empty Event Data (Close Event)

For events without input data:

```java
public static class CloseEventData {
    // Empty class
}

public static final BuilderCodec<CloseEventData> CODEC = BuilderCodec.builder(
    CloseEventData.class,
    CloseEventData::new
).toBuilder().build();
```

### Advanced Layout Techniques

#### Flex Weight

Use `flex-weight` to distribute space:

```ui
group {
  anchor: 500 400
  layout-mode: left-to-right
  
  group #leftPanel {
    width: 150
    background-color: #333333(0.8)
  }
  
  group #rightPanel {
    flex-weight: 1  // Takes remaining space (350px in 500px container)
  }
}
```

#### Even Distribution

```ui
group #statsGrid {
  layout-mode: left-to-right
  anchor: 0 100
  
  group {
    flex-weight: 1  // Each takes 1/3 of available width
    label #stat1Value { ... }
  }
  
  group {
    flex-weight: 1
    label #stat2Value { ... }
  }
  
  group {
    flex-weight: 1
    label #stat3Value { ... }
  }
}
```

### Custom Button Styles

Define reusable styles as variables:

```ui
$closeButtonStyle = {
  background-color: #FF0000(0.8)
  text-color: #FFFFFF
  font-size: 16
}

textButton #closeButton {
  style: $closeButtonStyle
  text: "Close"
}
```

Styles can be reused across multiple elements and potentially imported from other files to create a UI component library.

## Summary of Key Classes and Methods

### Classes

- **BasicCustomUIPage**: Simple UI without event handling
- **InteractiveCustomUIPage<T>**: UI with event handling (generic type is event data class)
- **UICommandBuilder**: Provides methods to build UI (`append()`, `set()`, `addEventBinding()`)
- **BuilderCodec<T>**: Defines serialization/deserialization for event data

### Methods

- **`command.append(path)`**: Load UI file (relative path from `resources/common/UI/custom/`)
- **`command.set(elementId, property, value)`**: Update UI element property
- **`command.addEventBinding(elementId, eventData)`**: Bind UI element to event
- **`handleDataEvent(store, data)`**: Handle incoming events (override in InteractiveCustomUIPage)
- **`player.getPageManager().openCustomPage(store, page)`**: Open a UI page
- **`player.getPageManager().setPage(Page.NONE)`**: Close the current page

### Important Patterns

1. **Event Data Flow**: User interaction → UI element → Event binding → Codec deserialization → `handleDataEvent()`
2. **Dynamic Updates**: Java code → `command.set()` → UI element property
3. **ID Connection**: Java finds UI elements by ID (e.g., `#title`, `#nameInput`)
4. **@ Prefix**: Binds UI values to event data fields
5. **Codec Required**: All event data classes need a codec for serialization

## Best Practices

1. **File Organization**: Keep UI files in `resources/common/UI/custom/Pages/`
2. **Naming**: Use consistent naming (e.g., `YourPage.ui` and `YourPage.java`)
3. **IDs**: Use descriptive IDs with `#` prefix
4. **Type Conversion**: Always convert numbers to strings when setting UI values
5. **Codec Definition**: Make codecs `public static final` for easy access
6. **Event Data**: Keep event data classes simple with public fields (or test getters/setters)
7. **Lifetime**: Use appropriate `CustomPageLifetime` for your use case
8. **Error Handling**: Check for null/empty values in event handlers

## Notes and Limitations

- UI files use a custom domain-specific language (not standard JSON/YAML)
- Documentation for all UI properties may not be fully available yet
- Some UI components require imports (e.g., `import common.UI` for text fields)
- Custom components use `$C.` prefix, built-in components don't
- Flex-weight behavior is similar to CSS flexbox
- Styles can be defined as variables and reused
- Custom components may be extractable for reuse (not fully tested)

## Entity Component System Note

The code uses Hytale's Entity Component System (ECS), which is why you see:
- `PlayerRef` vs `Player` (ref is a reference, Player is the component)
- `StoreRef` and `store.getComponent()` pattern
- This will be explained in more detail in future videos/articles
