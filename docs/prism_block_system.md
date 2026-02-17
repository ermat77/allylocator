# Prism Block System

This document describes how the **Prism Block** works when placed in the world and used by players (F key). It covers the two UI pages (own faction vs other faction), the effect multiplier config, harvest (with multiplier), cancel prism (return block to inventory), and the Members button on the other-faction page.

For the separate “test prism” feature (temporary flat world via `/f prism`), see [prism_system.md](prism_system.md).

---

## Overview

- **Block:** A placeable block (custom model) that opens a custom UI when the player uses it (F key). Block/item ID: `com.factioncore:Prism_Block` (or short form `Prism_Block`).
- **Two UIs:** Which page opens depends on who owns the prism and who is interacting:
  - **Own faction:** Player’s faction is the same as the prism’s placer faction → **OwnPrismPage** (time alive, effect multiplier, Harvest, Cancel Prism).
  - **Other faction:** Player’s faction is different from the prism’s owner → **OtherPrismPage** (Attack placeholder, Members).
  - **Fallback:** No prism record or no faction → **PrismBlockPage** (minimal: time alive only, no actions).
- **Effect multiplier:** A value based on the prism’s chunk distance from a “spawn” chunk (e.g. 0,0). Configured in `prism_multiplier_config.json`. Used for harvest points and shown on OwnPrismPage.
- **Harvest:** On OwnPrismPage, Harvest awards faction points = **time alive (minutes) × effect multiplier**, then removes the prism block and its record.
- **Cancel Prism:** On OwnPrismPage, Cancel Prism removes the prism (block + record) and gives **one Prism Block item** back to the player (short item ID `Prism_Block`).
- **Members:** On OtherPrismPage, Members opens **FactionMembersPage** for the prism’s owner faction, with a back button that returns to OtherPrismPage.
- **Attack:** On OtherPrismPage, Attack is a placeholder (no logic yet); it only closes the page.

---

## User Flow

### Placing a Prism Block

1. Player must be in a faction.
2. Player places a Prism Block from their inventory (one per chunk; duplicate placement in the same chunk is blocked with a message).
3. The block position and faction/placer are stored in **PrismManager** and the database.

### Using a Prism Block (F key)

1. **BlockUseOpenUISystem** handles the Use Block event for the Prism Block.
2. It looks up the prism at the block position (**PrismManager.getByPosition**), the placer’s faction, and the interactor’s faction.
3. **Routing:**
   - Interactor’s faction **equals** placer’s faction → open **OwnPrismPage** (with time alive, multiplier, Harvest, Cancel Prism).
   - Interactor’s faction **differs** from placer’s → open **OtherPrismPage** (Attack, Members).
   - No prism or no faction → open **PrismBlockPage** (fallback, time alive only).

### OwnPrismPage

- **Time alive:** Minutes since the prism was placed (`PrismRecord.getScoreMinutes`).
- **Effect multiplier:** From **PrismMultiplierService** using the prism’s chunk and the multiplier config (see below).
- **Harvest:** Awards `timeAliveMinutes × multiplier` FP to the prism’s faction, removes the block and DB record, closes the page. Message: “Earned X FP”.
- **Cancel Prism:** Verifies same faction, removes prism from PrismManager and sets block to air, gives one `Prism_Block` item to the player (via combined hotbar-first or storage), closes the page. Success/error message.

### OtherPrismPage

- **Attack:** Placeholder; closes the page. Logic to be added later.
- **Members:** Opens **FactionMembersPage** for the prism owner’s faction, with a back target that reopens OtherPrismPage.

---

## Effect Multiplier Config

The multiplier is determined by the **chunk distance** of the prism from a configured “spawn” chunk (e.g. 0,0). Distance is **Chebyshev**: `max(|chunkX - spawnChunkX|, |chunkZ - spawnChunkZ|)`.

### Config file

- **Path:** Plugin data dir: `prism_multiplier_config.json` (e.g. `mods/FactionCore/prism_multiplier_config.json`).
- **Created on first load** from defaults if missing; also a default is in `resources/prism_multiplier_config.json`.

### Format

```json
{
  "SpawnChunkX": 0,
  "SpawnChunkZ": 0,
  "MaxChunkDistances": "5,10,15,20,25",
  "Multipliers": "50,10,5,2,1"
}
```

- **SpawnChunkX, SpawnChunkZ:** Chunk coordinates of the “spawn” (usually 0, 0).
- **MaxChunkDistances:** Comma-separated list of maximum chunk distances (inclusive bands).
- **Multipliers:** Comma-separated list of multipliers; index `i` pairs with `MaxChunkDistances[i]`.

**Band rule:** For a prism at chunk distance `d`, the multiplier is the one for the **smallest** `MaxChunkDistances[i]` such that `d <= MaxChunkDistances[i]`. If none match, the default multiplier is 1.

Example:

- Distance 0–5 → multiplier 50  
- Distance 6–10 → multiplier 10  
- Distance 11–15 → multiplier 5  
- Distance 16–20 → multiplier 2  
- Distance 21–25 → multiplier 1  
- Distance > 25 → 1 (default)

### Code

- **PrismMultiplierConfig** (CODEC): Loads spawn + the two comma-separated strings; parses them and exposes `getMultiplierForDistance(int distance)`.
- **PrismMultiplierService**: Singleton; `getMultiplier(int chunkX, int chunkZ)` computes Chebyshev distance from config spawn and returns `config.getMultiplierForDistance(distance)`.
- **Main:** Registers `Config<PrismMultiplierConfig>` as `prismMultiplierConfig`, load/save in setup.

---

## Harvest and Points

- **PrismService.harvestPrism(prismId, playerUuid, ref, store):**
  1. Resolves prism and checks player is in the prism’s faction.
  2. `timeAliveMinutes = prism.getScoreMinutes(now)`.
  3. `multiplier = PrismMultiplierService.getInstance().getMultiplier(prism.getChunkX(), prism.getChunkZ())`.
  4. `pointsToAdd = timeAliveMinutes * multiplier`.
  5. Awards `pointsToAdd` FP to the prism’s faction via **FactionPointsManager** (reason `"harvestPrism"`).
  6. Removes prism from PrismManager and sets the block to air.

So the points shown and awarded on Harvest are **time alive (minutes) × effect multiplier**.

---

## Cancel Prism and Giving the Block Back

- **PrismService.cancelPrism(prismId, playerUuid, ref, store):**
  1. Resolves prism and checks player is in the prism’s faction.
  2. Removes prism from PrismManager.
  3. Schedules setting the block to air on the world executor.
  4. Calls **givePrismBlockToPlayer(ref, store)**.

- **givePrismBlockToPlayer:** Gets the Player component, then adds one item via:
  - Prefer: `player.getInventory().getCombinedHotbarFirst().addItemStack(new ItemStack("Prism_Block", 1))`.
  - Fallback: `player.getInventory().getStorage().addItemStack(...)`.

The item ID used is the **short** form **`Prism_Block`** so the client can resolve it with the mod namespace and match the asset at `Server/Item/Items/Prism_Block.json`. Using the full block-type ID can lead to “Invalid Item” on the client due to ID normalization.

---

## Architecture (Key Classes and Files)

### Routing and UI

| Class / File | Role |
|--------------|------|
| **BlockUseOpenUISystem** | On Use Block for Prism Block: resolves prism, placer faction, interactor faction; opens OwnPrismPage, OtherPrismPage, or PrismBlockPage. Computes effect multiplier for own-faction case. |
| **OwnPrismPage** | UI: time alive, effect multiplier, Harvest, Cancel Prism. Handles harvest (PrismService.harvestPrism) and cancel (PrismService.cancelPrism). |
| **OtherPrismPage** | UI: Attack (no-op, close), Members (open FactionMembersPage with back to OtherPrismPage). |
| **PrismBlockPage** | Fallback when no faction or no placer; shows time alive, no Harvest/Attack. |

### Multiplier and Config

| Class / File | Role |
|--------------|------|
| **PrismMultiplierConfig** | CODEC: SpawnChunkX/Z, MaxChunkDistances (string), Multipliers (string). Parses to lists; `getMultiplierForDistance(distance)` returns multiplier for smallest matching band. |
| **PrismMultiplierService** | `getMultiplier(chunkX, chunkZ)`: Chebyshev distance from config spawn → `getMultiplierForDistance(distance)`. |
| **Main** | Holds `Config<PrismMultiplierConfig>`, load/save in setup. |
| **prism_multiplier_config.json** | Spawn chunk + comma-separated bands (see format above). |

### Prism Data and Actions

| Class / File | Role |
|--------------|------|
| **PrismManager** | In-memory + DB: prism by position and by ID. Used by BlockUseOpenUISystem, harvest, cancel. |
| **PrismRecord** | worldName, x,y,z, id, factionId, playerUuid, createdAtMs; `getChunkX()`, `getChunkZ()`, `getScoreMinutes(now)`. |
| **PrismService** | harvestPrism (points = time × multiplier, remove block); cancelPrism (remove block, give Prism_Block item). |

### UI and Events

| File / Class | Role |
|--------------|------|
| **OwnPrismPage.ui** | Layout: title, time alive, effect multiplier, Harvest button, Cancel Prism button. |
| **OtherPrismPage.ui** | Layout: title, Attack button, Members button. |
| **OwnPrismPageEventData** | Action: "harvest" \| "cancelPrism". |
| **OtherPrismPageEventData** | Action: "attack" \| "members". |

### Lang keys

- **Own:** `ui.own_prism.title`, `ui.own_prism.time_alive`, `ui.own_prism.effect_multiplier`, `ui.own_prism.harvest`, `ui.own_prism.cancel_prism`, `chat.own_prism.cancelled`, `chat.own_prism.cancel_failed`.
- **Other:** `ui.other_prism.title`, `ui.other_prism.attack`, `ui.other_prism.members`.
- **Shared:** `chat.faction_points.earned`, `chat.prism_block.one_per_chunk`.

---

## Debug Logging

With **Debug** enabled in config (`config.json`: `"Debug": true`):

- **BlockUseOpenUISystem:** When opening OwnPrismPage, logs timeAlive, chunk, effectMultiplier, and full prism multiplier config (spawn + bands).
- **PrismMultiplierService.getMultiplier:** Logs chunkX/Z, spawn, distance, multiplier, and config bands.
- **PrismService.cancelPrism / givePrismBlockToPlayer:** Logs ref/store, player, inventory, combined/storage, item ID used, and add result (remainder). On exception, logs message and throwable.

Use these logs to verify config loading, multiplier calculation, and item give (e.g. if the block does not appear or shows as invalid).

---

## Summary

- **Prism Block** in the world opens **OwnPrismPage** (same faction) or **OtherPrismPage** (other faction) or **PrismBlockPage** (fallback).
- **Effect multiplier** comes from `prism_multiplier_config.json` (spawn chunk + distance bands); **Harvest** awards **time alive × multiplier** FP; multiplier is shown on OwnPrismPage.
- **Cancel Prism** removes the block and gives one **Prism_Block** item back (short ID for correct client resolution).
- **OtherPrismPage:** **Members** opens FactionMembersPage with back; **Attack** is a placeholder.
