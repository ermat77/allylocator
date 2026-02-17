# Hytale UI Design Patterns & Reusable Code Best Practices

## Table of Contents

1. [Dynamic List Generation](#dynamic-list-generation)
2. [Reusable UI Components](#reusable-ui-components)
3. [Component Composition](#component-composition)
4. [Layout Patterns](#layout-patterns)
5. [Event Binding Patterns](#event-binding-patterns)
6. [Best Practices](#best-practices)
7. [Common Patterns Reference](#common-patterns-reference)

---

## Dynamic List Generation

### The Problem

When you need to display a variable number of items (members, plugins, invites, etc.), you have two approaches:

1. **Static Pre-created Slots** (❌ Not Recommended)
   - Create fixed number of UI elements (e.g., `#memberRow0` through `#memberRow19`)
   - Show/hide and update them dynamically
   - **Limitations**: Fixed maximum count, wasted resources, harder to maintain

2. **Dynamic Append Pattern** (✅ Recommended)
   - Create a reusable component file
   - Dynamically append instances to a container
   - Use index-based selectors to update properties
   - **Benefits**: Unlimited items, efficient, scalable, matches Hytale's internal patterns

### The Solution: Clear + Append Pattern

This pattern is used by Hytale's own `PluginListPage` and is the recommended approach for dynamic lists.

#### Step 1: Create a Reusable Component File

Create a separate `.ui` file for the list item:

**`Pages/MemberRow.ui`**
```ui
$C = "../Common.ui";

Group {
  LayoutMode: Right;
  Anchor: (Height: 30);
  
  Label #MemberLabel {
    Text: "";
    FlexWeight: 1;
  }
  
  $C.@TextButton #PermissionsButton {
    Text: "Permissions";
    Visible: false;
  }
}
```

**Key Points:**
- Component is self-contained and reusable
- IDs are generic (`#MemberLabel`, not `#member0`)
- Properties that change should have default values
- Use `Visible: false` for optional elements

#### Step 2: Create a Scrollable Container

In your main page UI file:

**`Pages/FactionShowPage.ui`**
```ui
Group #MemberList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $C.@DefaultScrollbarStyle;
  FlexWeight: 1;
}
```

**Key Points:**
- Use `LayoutMode: TopScrolling` for scrollable lists
- Always include `ScrollbarStyle: $C.@DefaultScrollbarStyle`
- Use `FlexWeight: 1` to fill available space

#### Step 3: Clear and Append in Java

In your Java page class:

```java
// Clear the container first
commandBuilder.clear("#MemberList");

// Append items dynamically
for (int i = 0; i < members.size(); i++) {
    MemberInfo member = members.get(i);
    
    // Append the component to the container
    commandBuilder.append("#MemberList", "Pages/MemberRow.ui");
    
    // Use index-based selector to set properties
    String selector = "#MemberList[" + i + "]";
    String labelSelector = selector + " #MemberLabel";
    String buttonSelector = selector + " #PermissionsButton";
    
    // Set properties on the appended item
    commandBuilder.set(labelSelector + ".Text", memberText);
    commandBuilder.set(buttonSelector + ".Visible", shouldShowButton);
    
    // Bind events using the index-based selector
    eventBuilder.addEventBinding(
        CustomUIEventBindingType.Activating,
        buttonSelector,
        new EventData()
            .append("Action", "permissions")
            .append("MemberUuid", member.uuid.toString())
    );
}
```

**Key Points:**
- **Always call `clear()` first** to remove old items
- Use `append(containerSelector, componentPath)` to add items
- Use **index-based selectors**: `#ContainerName[index]` to reference items
- Use **nested selectors**: `#ContainerName[index] #ChildElement` for child elements
- Index is 0-based and matches append order

#### When You Need to Filter Items (e.g. Skip Nulls)

If some source items must be skipped (e.g. null or invalid), **build a display list first** and iterate over it. That way the loop index `i` always matches the append order and the selector `#ContainerName[i]` stays correct. Do **not** use a separate `rowIndex` with `continue` in the loop—that can desync the index from the appended position.

```java
// Build display list: valid items only
ArrayList<Item> displayList = new ArrayList<>();
for (Item item : sourceList) {
    if (item != null && item.getId() != null) {
        displayList.add(item);
    }
}
commandBuilder.clear("#MyList");
for (int i = 0; i < displayList.size(); i++) {
    Item item = displayList.get(i);
    commandBuilder.append("#MyList", "Pages/MyRow.ui");
    String selector = "#MyList[" + i + "]";
    commandBuilder.set(selector + " #Label.Text", item.getName());
    // ...
}
```

### Complete Example: Member List

**Java Implementation:**
```java
private void buildFactionInfoState(UICommandBuilder commandBuilder, 
                                   UIEventBuilder eventBuilder,
                                   FactionInfo faction, UUID playerUuid) {
    // ... other setup code ...
    
    // Build member list
    List<MemberInfo> members = buildMemberList(faction, universe);
    
    // Clear and rebuild dynamically
    commandBuilder.clear("#MemberList");
    
    if (members.isEmpty()) {
        commandBuilder.set("#noMembersLabel.Visible", true);
    } else {
        commandBuilder.set("#noMembersLabel.Visible", false);
        boolean isOwner = faction.isOwner(playerUuid);
        
        for (int i = 0; i < members.size(); i++) {
            MemberInfo member = members.get(i);
            String memberText = getRolePrefix(member.role) + member.name;
            
            // Append component
            commandBuilder.append("#MemberList", "Pages/MemberRow.ui");
            
            // Set properties using index selector
            String selector = "#MemberList[" + i + "]";
            commandBuilder.set(selector + " #MemberLabel.Text", memberText);
            
            // Conditionally show button and bind event
            if (isOwner && !member.uuid.equals(playerUuid)) {
                commandBuilder.set(selector + " #PermissionsButton.Visible", true);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector + " #PermissionsButton",
                    new EventData()
                        .append("Action", "permissions")
                        .append("MemberUuid", member.uuid.toString())
                );
            }
        }
    }
}
```

---

## Reusable UI Components

### Component Design Principles

1. **Self-Contained**: Component should work independently
2. **Configurable**: Use properties that can be set from Java
3. **Generic IDs**: Use descriptive but generic IDs (not numbered)
4. **Default Values**: Provide sensible defaults for all properties
5. **Optional Elements**: Hide optional elements by default (`Visible: false`)

### Component Structure Template

```ui
$C = "../Common.ui";

Group {
  // Container properties
  LayoutMode: Right;  // or Top, Left, etc.
  Anchor: (Height: 30);
  
  // Required elements
  Label #LabelName {
    Text: "";
    FlexWeight: 1;
  }
  
  // Optional elements (hidden by default)
  $C.@TextButton #ActionButton {
    Text: "Action";
    Visible: false;
  }
}
```

### Component Usage Pattern

**1. Create the component file** (`Pages/MyComponent.ui`)

**2. Use in main page:**
```ui
Group #MyList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $C.@DefaultScrollbarStyle;
  FlexWeight: 1;
}
```

**3. Append from Java:**
```java
commandBuilder.append("#MyList", "Pages/MyComponent.ui");
```

### Component Variants

For components with variations, create separate files:

- `Pages/MemberRow.ui` - Standard member row
- `Pages/MemberRowOwner.ui` - Owner-specific variant
- `Pages/MemberRowCompact.ui` - Compact variant

Or use conditional visibility in a single component:

```ui
Group {
  Label #MemberName { ... }
  
  // Owner-only section
  Group #OwnerActions {
    Visible: false;  // Set from Java
    $C.@TextButton #PromoteButton { ... }
    $C.@TextButton #DemoteButton { ... }
  }
}
```

---

## Component Composition

### Building Complex UIs from Simple Components

Instead of creating monolithic UI files, compose pages from smaller components:

#### Example: Settings Page with Sections

**Main Page** (`Pages/SettingsPage.ui`):
```ui
$C = "../Common.ui";

Group {
  LayoutMode: Top;
  Padding: (Full: 20);
  
  Label #Title { Text: "Settings"; }
  
  // General settings section
  Group #GeneralSection {
    LayoutMode: Top;
    // Append settings items here
  }
  
  // Advanced settings section
  Group #AdvancedSection {
    LayoutMode: Top;
    // Append settings items here
  }
}
```

**Setting Item Component** (`Pages/SettingItem.ui`):
```ui
$C = "../Common.ui";

Group {
  LayoutMode: Right;
  Anchor: (Height: 40);
  
  Label #SettingLabel {
    Text: "";
    FlexWeight: 1;
  }
  
  $C.@TextField #SettingValue {
    Anchor: (Width: 200, Height: 30);
  }
}
```

**Java Composition:**
```java
// Build general settings
commandBuilder.clear("#GeneralSection");
for (Setting setting : generalSettings) {
    commandBuilder.append("#GeneralSection", "Pages/SettingItem.ui");
    String selector = "#GeneralSection[" + index + "]";
    commandBuilder.set(selector + " #SettingLabel.Text", setting.getName());
    commandBuilder.set(selector + " #SettingValue.Value", setting.getValue());
}

// Build advanced settings
commandBuilder.clear("#AdvancedSection");
// ... similar pattern ...
```

### Nested Components

Components can contain other components:

**Card Component** (`Pages/Card.ui`):
```ui
Group {
  LayoutMode: Top;
  Background: #141c26(0.98);
  Padding: (Full: 10);
  
  Label #CardTitle { ... }
  
  Group #CardContent {
    LayoutMode: Top;
    // Append child components here
  }
}
```

**Usage:**
```java
// Append card
commandBuilder.append("#MainContainer", "Pages/Card.ui");
String cardSelector = "#MainContainer[" + i + "]";
commandBuilder.set(cardSelector + " #CardTitle.Text", "My Card");

// Append items to card content
commandBuilder.append(cardSelector + " #CardContent", "Pages/CardItem.ui");
```

---

## Layout Patterns

### Scrollable Lists

**Always use this pattern for dynamic lists:**

```ui
Group #MyList {
  LayoutMode: TopScrolling;  // Required for scrolling
  ScrollbarStyle: $C.@DefaultScrollbarStyle;  // Required for scrollbar
  FlexWeight: 1;  // Fill available space
}
```

### Horizontal Lists

For horizontal scrolling lists:

```ui
Group #HorizontalList {
  LayoutMode: LeftScrolling;  // Horizontal scrolling
  ScrollbarStyle: $C.@DefaultScrollbarStyle;
  Anchor: (Height: 50);  // Fixed height
}
```

### Grid Layouts

Using flex-weight for even distribution:

```ui
Group #GridContainer {
  LayoutMode: Left;  // Horizontal layout
  Anchor: (Height: 200);
  
  Group #GridItem0 {
    FlexWeight: 1;  // Equal width
    LayoutMode: Top;
    // Content
  }
  
  Group #GridItem1 {
    FlexWeight: 1;
    LayoutMode: Top;
    // Content
  }
  
  Group #GridItem2 {
    FlexWeight: 1;
    LayoutMode: Top;
    // Content
  }
}
```

### Responsive Layouts

Use `FlexWeight` for responsive behavior:

```ui
Group {
  LayoutMode: Left;
  
  Group #Sidebar {
    Anchor: (Width: 200);  // Fixed width
  }
  
  Group #MainContent {
    FlexWeight: 1;  // Takes remaining space
    LayoutMode: Top;
  }
}
```

---

## Event Binding Patterns

### Index-Based Event Binding

When binding events to dynamically appended items:

```java
for (int i = 0; i < items.size(); i++) {
    // Append item
    commandBuilder.append("#List", "Pages/Item.ui");
    
    // Bind event using index selector
    String selector = "#List[" + i + "]";
    eventBuilder.addEventBinding(
        CustomUIEventBindingType.Activating,
        selector + " #ActionButton",
        new EventData()
            .append("Action", "itemAction")
            .append("ItemIndex", String.valueOf(i))
            .append("ItemId", items.get(i).getId())
    );
}
```

### Event Data with Item Identification

Always include enough data to identify the item:

```java
// Good: Includes unique identifier
new EventData()
    .append("Action", "permissions")
    .append("MemberUuid", member.uuid.toString())

// Avoid: Only using index (can change)
new EventData()
    .append("Action", "permissions")
    .append("Index", String.valueOf(i))  // ❌ Fragile
```

### Conditional Event Binding

Bind events conditionally based on state:

```java
if (isOwner && !member.uuid.equals(playerUuid)) {
    // Only bind if conditions are met
    eventBuilder.addEventBinding(
        CustomUIEventBindingType.Activating,
        selector + " #PermissionsButton",
        eventData
    );
}
```

---

## Best Practices

### 1. File Organization

```
resources/
  Common/
    UI/
      Custom/
        Pages/
          MainPage.ui          # Main pages
          ComponentRow.ui      # Reusable components
          ComponentCard.ui
          ComponentButton.ui
```

**Naming Convention:**
- Main pages: `PageNamePage.ui` (e.g., `FactionShowPage.ui`)
- Components: `ComponentName.ui` (e.g., `MemberRow.ui`, `InviteCard.ui`)
- Keep names descriptive and consistent

### 2. Component Design

✅ **DO:**
- Create separate files for reusable components
- Use generic, descriptive IDs
- Provide default values for all properties
- Hide optional elements by default
- Keep components focused and single-purpose

❌ **DON'T:**
- Create numbered IDs in components (`#item0`, `#item1`)
- Hardcode values that should be dynamic
- Create components that are too specific
- Mix layout and content in components

### 3. Java Code Organization

✅ **DO:**
- Always call `clear()` before appending to containers
- Use index-based selectors consistently
- Extract component building into separate methods
- Use helper methods for selector construction

```java
// Good: Helper method for selectors
private String getItemSelector(String container, int index) {
    return "#" + container + "[" + index + "]";
}

// Usage
String selector = getItemSelector("MemberList", i);
commandBuilder.set(selector + " #MemberLabel.Text", text);
```

### 4. Performance Considerations

- **Clear before append**: Always clear containers to avoid memory leaks
- **Batch operations**: Group related `set()` calls together
- **Limit items**: Consider pagination for very large lists (100+ items)
- **Reuse components**: Don't create new component files for slight variations

### 5. Error Handling

```java
// Always check for empty lists
if (members.isEmpty()) {
    commandBuilder.set("#noMembersLabel.Visible", true);
    return;
}

// Validate indices
if (i >= 0 && i < members.size()) {
    // Safe to access
}

// Handle null values
String memberText = member != null ? member.getName() : "Unknown";
```

### 6. Debugging Tips

**Add debug logging:**
```java
ConfigUtil.debug("Appending member " + i + ": " + member.getName());
commandBuilder.append("#MemberList", "Pages/MemberRow.ui");
ConfigUtil.debug("Selector: #MemberList[" + i + "]");
```

**Verify selectors:**
```java
// Test if selector works by setting a property
commandBuilder.set("#MemberList.LayoutMode", "Top");  // Should not fail
```

**Check container state:**
```java
// Clear before building
commandBuilder.clear("#MemberList");
ConfigUtil.debug("Cleared #MemberList, appending " + items.size() + " items");
```

---

## Common Patterns Reference

### Pattern 1: Simple List

**Use Case**: Display a list of items with optional actions

**Component** (`Pages/ListItem.ui`):
```ui
Group {
  LayoutMode: Right;
  Anchor: (Height: 30);
  
  Label #ItemLabel { Text: ""; FlexWeight: 1; }
  $C.@TextButton #ActionButton { Text: "Action"; Visible: false; }
}
```

**Container**:
```ui
Group #ItemList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $C.@DefaultScrollbarStyle;
  FlexWeight: 1;
}
```

**Java**:
```java
commandBuilder.clear("#ItemList");
for (int i = 0; i < items.size(); i++) {
    commandBuilder.append("#ItemList", "Pages/ListItem.ui");
    String s = "#ItemList[" + i + "]";
    commandBuilder.set(s + " #ItemLabel.Text", items.get(i).getName());
}
```

### Pattern 2: Card List

**Use Case**: Display items in card format

**Component** (`Pages/Card.ui`):
```ui
Group {
  LayoutMode: Top;
  Background: #141c26(0.98);
  Padding: (Full: 10);
  Anchor: (Height: 80);
  
  Label #CardTitle { Text: ""; }
  Label #CardDescription { Text: ""; }
  $C.@TextButton #CardButton { Text: "View"; }
}
```

**Container**:
```ui
Group #CardList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $C.@DefaultScrollbarStyle;
  FlexWeight: 1;
}
```

### Pattern 3: Form with Dynamic Fields

**Use Case**: Build forms dynamically

**Component** (`Pages/FormField.ui`):
```ui
Group {
  LayoutMode: Top;
  Anchor: (Height: 50);
  
  Label #FieldLabel { Text: ""; Anchor: (Height: 20); }
  $C.@TextField #FieldInput { Anchor: (Height: 30); }
}
```

**Container**:
```ui
Group #FormFields {
  LayoutMode: Top;
  FlexWeight: 1;
}
```

**Java**:
```java
commandBuilder.clear("#FormFields");
for (int i = 0; i < fields.size(); i++) {
    commandBuilder.append("#FormFields", "Pages/FormField.ui");
    String s = "#FormFields[" + i + "]";
    commandBuilder.set(s + " #FieldLabel.Text", fields.get(i).getLabel());
    
    // Bind field value to event data
    eventBuilder.addEventBinding(
        CustomUIEventBindingType.ValueChanged,
        s + " #FieldInput",
        new EventData()
            .append("@Field" + i, s + " #FieldInput.Value")
    );
}
```

### Pattern 4: Tabbed Interface

**Use Case**: Multiple sections with tabs

**Tabs Container**:
```ui
Group #Tabs {
  LayoutMode: Left;
  Anchor: (Height: 40);
  
  $C.@TextButton #Tab0 { Text: "General"; }
  $C.@TextButton #Tab1 { Text: "Advanced"; }
  $C.@TextButton #Tab2 { Text: "Permissions"; }
}
```

**Content Container**:
```ui
Group #TabContent {
  LayoutMode: Top;
  FlexWeight: 1;
}
```

**Java**:
```java
// Show/hide tab content
commandBuilder.set("#TabContent.Visible", true);
commandBuilder.clear("#TabContent");

// Append content based on selected tab
if (selectedTab == 0) {
    commandBuilder.append("#TabContent", "Pages/GeneralSettings.ui");
} else if (selectedTab == 1) {
    commandBuilder.append("#TabContent", "Pages/AdvancedSettings.ui");
}
```

---

## Summary

### Key Takeaways

1. **Use Dynamic Append Pattern** for variable-length lists
   - Create reusable component files
   - Use `clear()` + `append()` pattern
   - Use index-based selectors

2. **Design Reusable Components**
   - Self-contained and configurable
   - Generic IDs, default values
   - Single purpose

3. **Compose Complex UIs**
   - Build from simple components
   - Use composition over duplication
   - Extract common patterns

4. **Follow Best Practices**
   - Always clear before append
   - Use descriptive selectors
   - Include unique identifiers in event data
   - Add error handling and logging

5. **Match Hytale Patterns**
   - Study `PluginListPage` for reference
   - Use `TopScrolling` for lists
   - Include `ScrollbarStyle` for scrollable containers

### Quick Reference

| Task | Pattern |
|------|---------|
| Dynamic list | `clear()` + `append()` + index selectors |
| Filtered list (skip nulls) | Build display list first, then loop with index `i` (no `continue`) |
| Reusable component | Separate `.ui` file with generic IDs |
| Scrollable list | `LayoutMode: TopScrolling` + `ScrollbarStyle` |
| Event binding | Index selector + unique identifier in EventData |
| Conditional elements | `Visible: false` default, set from Java |

---

## Examples in Codebase

### Real Examples

1. **FactionShowPage** - Member list with permissions buttons
   - Component: `Pages/MemberRow.ui`
   - Container: `#MemberList` with `TopScrolling`
   - Pattern: Clear + append with index-based selectors

2. **PluginListPage** (Hytale internal) - Plugin list
   - Component: `Pages/PluginListButton.ui`
   - Container: `#PluginList` with `TopScrolling`
   - Pattern: Same clear + append pattern

3. **Pending Invites** - Dynamic invite list
   - Uses `appendInline()` for simple cases
   - Can be refactored to use component pattern

4. **FactionPrismsPage** - Prism list (points + coordinates per row, scrollable)
   - **Component**: `Pages/FactionPrism/PrismRow.ui` — row with `#PointsLabel` and `#CoordsLabel` (labels only; no buttons or checkboxes in the row).
   - **Container**: `#PrismList` with `LayoutMode: TopScrolling` and `ScrollbarStyle: $C.@DefaultScrollbarStyle` so all rows are shown and the list scrolls when there are many prisms.
   - **Pattern**: Build a **display list** of valid prisms (e.g. filter nulls), then `clear("#PrismList")`, then for each index `i`: `append("#PrismList", "Pages/FactionPrism/PrismRow.ui")`, then set properties using selector `#PrismList[" + i + "]` (e.g. `#PrismList[i] #PointsLabel.Text`, `#PrismList[i] #CoordsLabel.Text`). No separate row index or `continue` in the loop—use the same loop index `i` for both append order and selectors.
   - **Java**: `FactionPrismsPage.java` — see `build()` for the display-list construction and the append + set loop. All prisms are displayed; scrolling is provided by the container.

### Migration Guide

**From Static to Dynamic:**

**Before (Static):**
```ui
Group #memberRow0 { ... }
Group #memberRow1 { ... }
// ... 18 more ...
```

**After (Dynamic):**
```ui
Group #MemberList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $C.@DefaultScrollbarStyle;
  FlexWeight: 1;
}
```

**Java Before:**
```java
for (int i = 0; i < 20; i++) {
    commandBuilder.set("#memberRow" + i + ".Visible", i < members.size());
}
```

**Java After:**
```java
commandBuilder.clear("#MemberList");
for (int i = 0; i < members.size(); i++) {
    commandBuilder.append("#MemberList", "Pages/MemberRow.ui");
    String s = "#MemberList[" + i + "]";
    // Set properties...
}
```

---

*This documentation is based on patterns discovered in Hytale's internal code and real-world implementation in the FactionCore plugin.*
