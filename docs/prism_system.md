# Prism System

The **prism** feature gives each player a temporary, private flat world (16 chunks) with a prefab pasted at the origin. Players enter via `/f prism` and leave from the same command when inside the prism world. The world is created on demand and deleted when they leave.

---

## Overview

- **Command:** `/f prism` (no arguments).
- **From a normal world:** Opens a UI with a single button **"Test prism"**. Clicking it creates a flat 16-chunk world, pastes the prefab at (0, 0, 0), then teleports the player to (0, 1, 0). Their current world and position are saved for return.
- **From the prism world:** The same command opens a UI with **"Leave prism"**. Clicking it teleports the player back to the saved location and, after a short delay, removes the prism world (and deletes its folder).
- **Per-player:** Each player has their own prism world. World name is `prism-<playerUUID>` (e.g. `prism-550e8400-e29b-41d4-a716-446655440000`).

---

## User Flow

1. Player runs `/f prism` in any world.
2. If they have permission (`factioncore.player.prism`), a custom UI opens.
3. **Not in prism:** Button shows "Test prism". Click → world is created, prefab pasted, player teleported in. UI closes.
4. **In prism:** Button shows "Leave prism". Click → player is teleported back to where they were, prism world is removed. UI closes.
5. If the player runs `/f prism` again from a normal world, they get a new prism (any previous one for that player was already removed or is replaced).

---

## Requirements

### Permission

- **Node:** `factioncore.player.prism`
- **Usage:** Required to open the prism UI and to enter a prism (leave is allowed once inside).
- **Grant example (LuckPerms):** `lp user <player> permission set factioncore.player.prism true`

### Prefab asset

The prism world pastes a **server prefab** at (0, 0, 0). The prefab key is:

- **Key:** `prefab_prism.prefab.json`
- **Location:** Server Prefabs folder. Typically this is the `prefabs/` directory relative to the server (or the universe). The file must be named `prefab_prism.prefab.json` and placed so that `PrefabStore.get().getServerPrefab("prefab_prism.prefab.json")` can load it.

If the prefab is missing, the player sees an error when clicking "Test prism" and is not teleported.

---

## Architecture (for developers)

### Flow

```
/f prism (PrismFactionCommand)
    → openCustomPage(PrismPage)
        → build(): append "Pages/PrismPage.ui", set button label + bind Action
        → [user clicks button]
    → handleDataEvent(PrismPageEventData)
        → testPrism: PrismService.enterPrism(ref, store, playerRef, onError)
        → leavePrism: PrismService.leavePrism(ref, store, playerRef, onError)
```

### Key classes

| Class | Role |
|-------|------|
| **PrismFactionCommand** | Registers `/f prism`, checks player/ref/permission, opens `PrismPage` on the world executor. |
| **PrismPage** | `InteractiveCustomUIPage<PrismPageEventData>`. Builds UI from current world (prism vs normal), handles "testPrism" and "leavePrism" actions, calls `PrismService`. |
| **PrismPageEventData** | Event payload: single field `Action` ("testPrism" / "leavePrism") with CODEC for client→server. |
| **PrismService** | Singleton. Holds return locations `Map<UUID, PrismReturnLocation>`. `enterPrism`: save return → create world → wait chunk → paste prefab → teleport in. `leavePrism`: teleport back → schedule `removeWorld` after delay. |
| **PrismReturnLocation** | Immutable: `worldName` + `Transform` for the return position. |

### Files

| Path | Purpose |
|------|---------|
| `commands/PrismFactionCommand.java` | Command handler. |
| `ui/PrismPage.java` | Custom UI page (one button, context-aware). |
| `ui/events/PrismPageEventData.java` | Event data CODEC. |
| `prism/PrismService.java` | World create/paste/teleport/remove and return-location storage. |
| `resources/Common/UI/Custom/Pages/PrismPage.ui` | UI layout (title + `#ActionButton`). |
| Lang keys in `lang/en.json`, `lang/fr.json` | `ui.prism.*`, `chat.help.prism`, `chat.prism.*`. |

### World configuration

Prism worlds are created with:

- **Generator:** Flat (default `FlatWorldGenProvider`: one layer of grass at Y 0–1).
- **Chunk region:** 16 chunks (4×4). Block region (0, 0)–(96, 96) for both `PregenerateRegion` and `KeepLoadedRegion`.
- **Spawn:** Global spawn at (0, 1, 0).
- **Delete on remove:** `true`, so the world folder is deleted when the world is removed.

Built in `PrismService.buildPrismWorldConfig()`.

### Threading and execution

- **enterPrism** is invoked from the UI handler (player’s current world context). It saves the return location, then runs world creation and chunk wait on `CompletableFuture` chains. The actual **teleport** is scheduled on the **current world** executor so the `ref` and `store` are valid when adding the `Teleport` component.
- **leavePrism** runs in the prism world’s store context. It adds the `Teleport` component (player is moved out), then runs `removeWorld(prismWorldName)` in a separate thread after `REMOVE_WORLD_DELAY_SECONDS` (2 seconds) so the player has left before the world is unloaded.

---

## Customization

### Prefab key

Change the prefab used at origin:

- In **PrismService**, update `PREFAB_KEY` (e.g. to another server prefab path).
- Ensure that prefab file exists where the server loads prefabs from.

### Permission

- Permission node is `PermissionUtil.PERM_PRISM` (`factioncore.player.prism`). To add a separate “prism admin” node, add a constant and a `canUsePrism`-style check, and use it in `PrismFactionCommand` and/or `PrismPage`.

### World size or generator

- Edit `PrismService.buildPrismWorldConfig()`:
  - **Larger/smaller region:** Change the `Box2D` (e.g. 0–96 = 4×4 chunks; 0–48 = 2×2; 0–192 = 8×8).
  - **Different generator:** Replace the Flat provider with another `IWorldGenProvider` from the codec (e.g. Void) or a custom one.

### Leave delay

- `REMOVE_WORLD_DELAY_SECONDS` in **PrismService** controls how long after adding the return teleport the prism world is removed. Increase if players are still in the world when it is removed.

### UI text and messages

- All user-visible strings are in lang files under `ui.prism.*` and `chat.prism.*`. Adjust `en.json` / `fr.json` (and any other locales) to change button labels and error messages.

---

## Troubleshooting

| Issue | Cause | What to do |
|-------|--------|------------|
| "Could not find document Pages/PrismPage..." | Client could not resolve the UI document. | Ensure the append path is `Pages/PrismPage.ui` (with `.ui`) and that `PrismPage.ui` is in the asset pack under `Common/UI/Custom/Pages/`. Rebuild and reload assets. |
| Error when clicking "Test prism" | Prefab not found or world creation failed. | Add `prefab_prism.prefab.json` to the server Prefabs folder. Check server logs for prefab load or world init errors. |
| No permission | User lacks `factioncore.player.prism`. | Grant the permission (e.g. via LuckPerms). |
| Return world not found on leave | The world the player came from was unloaded or removed. | Return location is only the world name; if that world no longer exists, leave will show an error. Consider handling “return world missing” (e.g. send to default world) in `PrismService.leavePrism`. |
| Prism world not deleted | `removeWorld` failed or delay not elapsed. | Check logs for removeWorld exceptions. Ensure `DeleteOnRemove` is true on the prism `WorldConfig` so the folder is deleted in `validateDeleteOnRemove`. |

---

## Summary

- **Users:** `/f prism` → UI with one button (enter or leave). Enter: temp flat 16-chunk world + prefab at origin + teleport in. Leave: teleport back, then world removed.
- **Admins:** Grant `factioncore.player.prism`; provide `prefab_prism.prefab.json` in server Prefabs.
- **Developers:** Command → PrismPage → PrismService; return locations in memory; world created via `Universe.makeWorld` with Flat config and `DeleteOnRemove`; customize via `PrismService` constants and `buildPrismWorldConfig()`.
