# Hytale Modding Best Practices

## ECS System

Always register components before systems that use them. Never register systems before their required components.

Always use `CommandBuffer` for component modifications during ticks. Never call `store.addComponent()` or `store.removeComponent()` directly during a tick - use `commandBuffer.addComponent()`, `commandBuffer.removeComponent()`, or `commandBuffer.replaceComponent()` instead.

Always use queries to filter entities in systems. Never iterate over all entities manually.

Always choose the right system type based on when code needs to run: `EntityTickingSystem` for per-frame logic, `RefSystem` for lifecycle hooks, `EntityEventSystem` for ECS events.

Always use delta time (`dt`) in ticking systems for frame-rate independence. Never assume fixed frame rates.

Always use the component as trigger pattern - add components to request actions, let systems fulfill them.

Always use `store.getComponent(ref, ComponentType)` to read components. Never access component data directly.

Always handle ECS events via `EntityEventSystem`, not via `getEventRegistry().register()`. Register event systems in `Main.setup()` as ECS systems.

Always store `ComponentType` references in plugin instance fields, never in static fields.

## Threading

Always use `world.execute()` to access ECS from non-World threads. Never access EntityStore directly from non-World threads.

Never block waiting for another World thread. Use `world.execute()`, not `executeAndWait()`.

Always use thread-safe types (`Atomic*`, `ConcurrentHashMap`, `volatile`) for shared plugin data accessed from multiple threads.

Always remember: Universe data is thread-safe, World data is thread-bound. Access World data only from its World thread.

Always use `CompletableFuture` with `ExecutorService` for async file operations. Never use raw `Thread` objects.

Always use `BlockingDiskFile` for persistent data files. Provides thread-safe read/write operations with locks.

## UI Design

Always call `clear()` before `append()` when building dynamic lists. Never append without clearing first.

Always use index-based selectors (`#ContainerName[index]`) to reference dynamically appended items. Never use numbered IDs in component files.

Always create separate reusable component files for list items. Never create numbered static slots (`#item0`, `#item1`).

Always use `LayoutMode: TopScrolling` with `ScrollbarStyle` for scrollable lists. Never use static layouts for variable-length lists.

Always include unique identifiers (UUIDs) in event data, not just indices. Never rely solely on indices for item identification.

## General Practices

Always make the manager class the single source of truth for plugin data. Never duplicate data structures.

Always check for null and provide helpful error messages in component access methods.

Always mark data as dirty when modified and save periodically. Never save synchronously on every change.

Always validate entity refs with `ref.isValid()` when refs might become invalid. Never assume refs remain valid indefinitely.

Always use `Query.and()`, `Query.or()`, and `Query.not()` for efficient entity filtering. Never manually check every entity.

Always separate concerns: components are pure data, systems are pure logic, managers handle persistence.

Always use `EntityStatMap` for health and statistics instead of custom health components.

Always remember that `PlayerRef` is the bridge between network connections and ECS entities - players aren't entities until they join a world.
