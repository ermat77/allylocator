# How the Native Hytale `/spawn` Command Works (from HytaleServer.jar)

This document summarizes the decompiled behavior of the built-in `/spawn` command from `HytaleServer.jar` (CFR 0.152), so you can align your plugin’s `SpawnCommand` or reuse the same concepts.

---

## 1. Entry point: `SpawnCommand` (builtin teleport)

**Package:** `com.hypixel.hytale.builtin.teleport.commands.teleport.SpawnCommand`  
**Base class:** `AbstractPlayerCommand` (not `AbstractAsyncCommand`).

- **Command name:** `"spawn"`
- **Description key:** `"server.commands.spawn.desc"`
- **Permission:** `HytalePermissions.fromCommand("spawn.self")` (i.e. `spawn.self`).
- **Sub-commands / variants:**
  - **Usage variant:** `SpawnOtherCommand` → `/spawn <player> [spawnIndex]` (permission: `spawn.other`)
  - **Sub-commands:** `SpawnSetCommand`, `SpawnSetDefaultCommand` (for setting world spawn).

---

## 2. Execution flow (self-teleport)

When a **player** runs `/spawn` (or `/spawn [spawnIndex]`):

1. **Resolve spawn transform**  
   `resolveSpawn(context, world, playerRef, spawnIndexArg)`:
   - Gets **world config’s spawn provider:** `world.getWorldConfig().getSpawnProvider()` → `ISpawnProvider`.
   - If **optional `spawnIndex`** is provided:
     - Calls `spawnProvider.getSpawnPoints()` (array of `Transform`).
     - Validates index: `0 <= spawnIndex < spawnPoints.length`; otherwise sends `server.commands.spawn.indexNotFound` / `server.commands.errors.spawnIndexOutOfRange` and throws `GeneralCommandException`.
     - Returns `spawnPoints[spawnIndex]`.
   - If **no index**: returns `spawnProvider.getSpawnPoint(world, playerRef.getUuid())` (per-player or global spawn).
2. **Teleport history**  
   Gets or creates `TeleportHistory` on the entity and appends current world, position, and head rotation with description `"World " + world.getName() + "'s spawn"`.
3. **Apply teleport**  
   - `Teleport teleportComponent = Teleport.createForPlayer(world, spawnTransform);`
   - `store.addComponent(ref, Teleport.getComponentType(), teleportComponent);`
4. **Feedback**  
   Sends translation `server.commands.spawn.teleported` with params `x`, `y`, `z`.

So the **native `/spawn` is synchronous**: it resolves spawn from the world’s `ISpawnProvider`, records history, adds the `Teleport` component, and sends a message. There is **no delay/countdown** and **no damage-cancel** in the built-in implementation.

---

## 3. “Spawn other” variant: `SpawnOtherCommand`

- **Permission:** `spawn.other`
- **Args:** required `player` (`PlayerRef`), optional `spawnIndex`.
- **Flow:**
  - Resolve target’s `Ref` and `Store`; if ref is null or invalid, send `server.commands.errors.playerNotInWorld` and return.
  - Run the rest **on the world executor:** `world.execute(() -> { ... })`.
  - Inside: resolve spawn via `SpawnCommand.resolveSpawn(context, world, targetPlayerRef, spawnIndexArg)`, then same as self-teleport: get `TransformComponent` and `HeadRotation`, snapshot position/rotation, append to `TeleportHistory`, add `Teleport` component.
  - Message: `server.commands.spawn.teleportedOther` with `username`, `x`, `y`, `z`.

---

## 4. Where spawn position comes from: `ISpawnProvider`

**Interface:** `com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider`

- **Per-player (and world):**  
  `Transform getSpawnPoint(World world, UUID uuid);`
- **Legacy / list:**  
  `Transform[] getSpawnPoints();` (deprecated but still used for optional `spawnIndex`).
- **Distance check:**  
  `boolean isWithinSpawnDistance(Vector3d position, double distance);`

**World config:**  
`World.getWorldConfig().getSpawnProvider()` returns the `ISpawnProvider` for that world (e.g. configured in world config or set via `/spawn set` / set-default commands).

**Implementations (from decompiled code):**

- **GlobalSpawnProvider**  
  Single `Transform spawnPoint`; `getSpawnPoint(world, uuid)` returns a clone of it; `getSpawnPoints()` returns a one-element array. So one fixed spawn for everyone.
- **FitToHeightMapSpawnProvider**  
  Wraps another `ISpawnProvider`. If the resolved spawn’s Y is below 0, it loads the chunk and sets Y to `worldChunk.getHeight(x, z) + 1`. So it keeps X/Z (and rotation) but snaps Y to the heightmap.
- **IndividualSpawnProvider**  
  (Not fully inspected here.) Likely provides per-UUID or per-player spawn points.

So the **native `/spawn` does not hardcode (0, 100, 0)**. It always uses the world’s configured spawn provider; default configs often use a global or heightmap-adjusted point.

---

## 5. How teleport is applied: `Teleport` component

**Class:** `com.hypixel.hytale.server.core.modules.entity.teleport.Teleport`

- **Factory used by spawn:**  
  `Teleport.createForPlayer(World world, Transform transform)`  
  - Copies position from `transform`, builds body rotation from yaw only, keeps head rotation from `transform`, and by default resets velocity.
- **Usage:**  
  Add the component to the entity: `store.addComponent(ref, Teleport.getComponentType(), teleportComponent)`. The server’s teleport systems then move the player (and optionally change world).

So your plugin’s use of `Teleport.createForPlayer(targetWorld, transform)` and `store.addComponent(ref, Teleport.getComponentType(), teleport)` matches how the native `/spawn` applies the teleport.

---

## 6. Differences from your plugin’s `SpawnCommand`

| Aspect | Native `/spawn` (HytaleServer) | Your `SpawnCommand` |
|--------|--------------------------------|----------------------|
| **Base class** | `AbstractPlayerCommand` | `AbstractAsyncCommand` |
| **Spawn position** | From `world.getWorldConfig().getSpawnProvider()` (per world/config) | Fixed `(0, 100, 0)` in current world |
| **Optional index** | `/spawn [spawnIndex]` supported | Not supported |
| **“Spawn other”** | Built-in variant with `spawn.other` | Not implemented |
| **Delay / cancel** | None; immediate teleport | Uses DelayTp (and * bypass) |
| **Teleport history** | Appends to `TeleportHistory` before teleport | Not used |
| **Permissions** | `spawn.self`, `spawn.other` | Your permission model (e.g. * bypass) |

So the **native `/spawn`** is synchronous, uses the **world’s spawn provider** (no hardcoded coordinates), supports an optional **spawn index** and **spawn other**, and records **teleport history**; your implementation adds **delayed teleport** and a fixed spawn position.

---

## 7. Making your `/spawn` behave like the native one (optional)

If you want your `/spawn` to mirror the native behavior more closely (while keeping delay/cancel if desired):

1. **Resolve spawn from world config**  
   Instead of a fixed `(0, 100, 0)`:
   - Get `World world` from the player’s store (as you already do).
   - Call `world.getWorldConfig().getSpawnProvider()`.
   - Use `spawnProvider.getSpawnPoint(world, playerRef.getUuid())` for the `Transform` (and optionally support `getSpawnPoints()` + index if you add an argument).
2. **Teleport history (optional)**  
   If you want `/back` and similar to include “last spawn”: get or create `TeleportHistory` on the entity and append the current world, position, and head rotation (e.g. description `"World " + world.getName() + "'s spawn"`) before adding the `Teleport` component.
3. **Keep your delay/cancel**  
   You can keep using `DelayTpHelper.requestDelayedTeleport` and only apply the above spawn resolution and history when you actually perform the teleport (either immediately for * or after the delay).

Decompiled sources used for this summary are under `build/decompiled/` (e.g. `com/hypixel/hytale/builtin/teleport/commands/teleport/SpawnCommand.java` and the spawn/teleport classes referenced above).
