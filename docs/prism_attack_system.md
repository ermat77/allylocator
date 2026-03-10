# Prism Attack System

This document describes the **prism attack** feature: how other factions can attack a prism, how defenders join, the temp attack world, prefab-based spawn blocks, configurable timer, and the event-driven flow with DB-backed sessions and participants.

Related docs: [prism_system.md](prism_system.md) (test prism /f prism), [prism_block_system.md](prism_block_system.md) (block UI, harvest, cancel).

---

## Overview

- **Attack:** A player from a **non-ally** faction can interact with another faction’s prism block and click **Attack**. This starts an attack session: prism status becomes `attacked`, a **temp attack world** is created, a random **prefab** from config is pasted, and the attacker is teleported into that world at an attacker spawn (e.g. red crystal block).
- **Defend:** Members of the prism’s faction (or its allies) see a **Defend** button on the faction prisms list when a prism is under attack. Clicking Defend teleports them into the attack world at a defender spawn (e.g. blue crystal block).
- **Join window and combat:** A configurable **join timer** (`attackerTimeSeconds`, default 60 seconds) is the time during which players can join the attack or defense. When this timer reaches 0, the attack status becomes **in_progress** (combat starts) and no new players can join. The battle can end in three ways: (1) **Prism NPC killed** (attack prefab must include an NPC with role id `Prism_Attacked_NPC_Role`) → attackers win; (2) **All attackers killed** → defenders win; (3) **Time to auto-destroy** (`timeBeforeAutoDestruction`, default 600 seconds) reached → defenders win. When the battle ends, participants are teleported back and the attack world is removed.
- **Persistence:** Attack sessions and participants are stored in the database (`prism_attacks`, `prism_attack_participants`) so the system survives restarts and supports pre-teleport checks and re-join flows.
- **Event-driven:** The UI does **not** run join/teleport logic. It **dispatches** a `PlayerJoinPrismAttackEvent`. A single **PrismAttackEventHandler** runs all pre-teleport checks, assigns spawn, inserts the participant, then adds the **Teleport** component on the player’s current world (ECS-safe pattern).
- **Entity damage (players and NPCs):** In the prism attack world, damage to players and NPCs is **only active when attack status is in_progress** (combat phase). When status is **preparing** (join window), damage to players and NPCs is **cancelled**. This is enforced by **PrismAttackEntityDamageProtectionSystem**, which runs in the damage filter phase and cancels the `Damage` event when the attack is not yet in progress.

---

## User Flow

### Starting an attack (attacker)

1. Player interacts with another faction’s prism block (F key). They must **not** be in the prism’s faction and **not** in an ally of that faction (see [BlockUseOpenUISystem](#blockuseopenuisystem)).
2. **OtherPrismPage** opens with **Attack** and **Members**.
3. Player clicks **Attack**. **PrismAttackService.startAttack(prismId, playerRef, ref, store)** is called:
   - If an attack for this prism already exists: dispatches `PlayerJoinPrismAttackEvent` (join as attacker).
   - Otherwise: sets prism status to `attacked`, creates the attack world, pastes a random prefab from config, inserts attack row and adds to cache, then dispatches the same event for the first attacker.
4. **PrismAttackEventHandler** receives the event: runs pre-teleport checks, **saves the player’s current world and position** (for teleport back when the attack ends), assigns an attacker spawn slot, inserts into `prism_attack_participants` (including return position), then schedules `currentWorld.execute(() -> store.addComponent(ref, Teleport.getComponentType(), teleport))`. The player is teleported into the attack world.
5. If this was the first attacker, the attack status is set to **ACTIVE** and two timers are scheduled: **combat start** at `ends_at` (join window end) and **destruction** at `destruction_at`. When the join timer reaches 0, status becomes **in_progress** (combat starts), an **attacker death map** is built (attacker UUID → hasDied), and the countdown HUD is cleared. The battle can then end by: prism NPC killed (attackers win), all attackers killed (defenders win), or destruction timer (defenders win). While status is ACTIVE, participants in the attack world see a countdown HUD (e.g. "XX seconds before the attack starts") via the MultipleHUD mod.

### Joining defense (defender)

1. Player opens the faction UI and goes to **Prisms** (**FactionPrismsPage**). For prisms with status `attacked`, a **Defend** button is shown.
2. Player clicks **Defend**. The page dispatches **PlayerJoinPrismAttackEvent** with `role: "defender"` and closes.
3. **PrismAttackEventHandler** runs: checks attack is ACTIVE, player is in defender faction or ally, attack world exists, and there is a free defender slot. It assigns a spawn, inserts the participant, then adds the Teleport component. The player is teleported into the attack world at a defender spawn.

### Battle end conditions

When the attack status is **in_progress** (combat), the battle ends when one of the following happens:

| Condition | Winner | Resolution |
|-----------|--------|------------|
| **Prism NPC killed** | Attackers | The NPC in the attack prefab with role id **Prism_Attacked_NPC_Role** is killed. Handled by **PrismAttackDeathResolutionSystem** when that entity gains DeathComponent. |
| **All attackers killed** | Defenders | Every attacker (from `prism_attack_participants`) has died in the attack world. Tracked via an in-memory map (attacker UUID → hasDied) populated when combat starts; **PrismAttackDeathResolutionSystem** marks attackers dead when they gain DeathComponent and calls defenders win when all are dead. |
| **Time to auto-destroy** | Defenders | The **destruction** timer (`timeBeforeAutoDestruction`) fires. Scheduled when the attack becomes ACTIVE. |

The attack prefab **must** include an NPC with role id **Prism_Attacked_NPC_Role** (see **PrismAttackConstants**) for “prism NPC killed” to trigger attackers win.

### Resolution (attackers win)

When the **prism NPC** is killed:

- Attack status is set to **RESOLVED_ATTACKERS_WIN**.
- **Participants with a saved return position are teleported back** to their previous world and coordinates (from `prism_attack_participants` return columns).
- Points = prism’s time alive (minutes) × chunk multiplier; awarded to the **attacker faction** via **FactionPointsManager.awardPoints(attackerFactionId, "prismAttack", points)**.
- Prism is removed from **PrismManager** and the block is set to air in the main world.
- The attack world is removed (**Universe.removeWorld**) after a short delay so teleports are applied first.
- Attack is removed from the in-memory cache (and attacker death map cleared).

### Resolution (defenders win)

When **all attackers are killed** or the **destruction timer** expires:

- Attack status is set to **RESOLVED_DEFENDERS_WIN**.
- **Participants with a saved return position are teleported back** (same as attackers win).
- The prism is **not** removed; no points are awarded to attackers.
- The attack world is removed and the attack is removed from the in-memory cache.

---

## Database

### Prism status (existing `prisms` table)

- New column: **`status`** `TEXT DEFAULT 'harvesting'`. Values: `harvesting`, `attacked`.
- Migration in **DatabaseManager.migratePrismColumns** (same duplicate-column catch pattern as other migrations).
- **PrismRecord** has `getStatus()`. **PrismManager.updateStatus(prismId, status)** updates in-memory and DB.

### New tables

**`prism_attacks`** (one row per attack):

| Column | Type | Description |
|--------|------|-------------|
| attack_id | TEXT PK | UUID of the attack |
| prism_id | TEXT | UUID of the prism |
| attack_world_name | TEXT | e.g. `prism-attack-<prismId>` |
| defender_faction_id | TEXT | Prism owner faction |
| attacker_faction_id | TEXT | First attacker’s faction |
| status | TEXT | PENDING_START, ACTIVE, in_progress, RESOLVED_ATTACKERS_WIN, RESOLVED_DEFENDERS_WIN, CANCELLED |
| started_at | INTEGER | Epoch ms |
| ends_at | INTEGER | Epoch ms (combat start = when status switches to in_progress) |
| destruction_at | INTEGER | Epoch ms (when attack world is removed and resolution runs) |
| prefab_id | TEXT | Server prefab key used |
| attackers_block_id | TEXT | Kept for backward compatibility; no longer set for new attacks |
| defenders_block_id | TEXT | Kept for backward compatibility; no longer set for new attacks |
| defender_positions_json | TEXT | Semicolon-separated "x,y,z;..." |
| attacker_positions_json | TEXT | Semicolon-separated "x,y,z;..." |

**`prism_attack_participants`** (who is in an attack and where they spawn):

| Column | Type | Description |
|--------|------|-------------|
| attack_id | TEXT | FK to prism_attacks |
| player_uuid | TEXT | Participant |
| role | TEXT | `attacker` or `defender` |
| joined_at | INTEGER | Epoch ms |
| spawn_x, spawn_y, spawn_z | INTEGER | Spawn in attack world |
| return_world_name | TEXT | World name the player was in before joining (for teleport back) |
| return_x, return_y, return_z | REAL | Position before joining (for teleport back) |

Unique on `(attack_id, player_uuid)`.

Indexes: `idx_prism_attacks_prism_id`, `idx_prism_attacks_status`, `idx_prism_attack_participants_attack_id`.

---

## Event dispatch and handler

### Flow

1. **UI** (OtherPrismPage for Attack, FactionPrismsPage for Defend) only builds **PlayerJoinPrismAttackEvent** (playerRef, ref, store, prismId, role) and calls **PrismAttackEventDispatcher.getInstance().dispatch(event)**. It does **not** run join logic or add the Teleport component.
2. **PrismAttackEventHandlerImpl** is registered with the dispatcher (in Main setup). Its **onPlayerJoinPrismAttack(event)**:
   - Runs all **pre-teleport checks** (attack exists and is joinable — status ACTIVE or PENDING_START; in_progress does not allow new joins; faction/role, not already in attack, attack world exists, free slot).
   - Reads the player’s **current world name and position** (for teleport back when the attack ends).
   - Calls **PrismAttackService.assignParticipantSlot(attackId, playerUuid, role, returnWorldName, returnX, returnY, returnZ)** to assign spawn and insert into `prism_attack_participants` (including return position).
   - If attack was PENDING_START, calls **PrismAttackService.updateAttackStatusToActive(attackId)** (and timers are scheduled there).
   - Gets **currentWorld = store.getExternalData().getWorld()** and schedules **currentWorld.execute(() -> store.addComponent(ref, Teleport.getComponentType(), teleport))** with a Teleport to the attack world at the assigned spawn.

### ECS/threading rule

- Only the **event handler** adds the Teleport component.
- Teleport is added on the **current** world’s store (the one from the event). The engine moves the player; we never touch the attack world’s store from the UI or another world’s thread.
- All validation and DB writes happen **before** scheduling `currentWorld.execute(...)`.

---

## Config: prism_attacks.json

- **Location:** Plugin data directory (e.g. `mods/FactionCore/prism_attacks.json`).
- **Main** uses **PrismAttacksConfigHolder** (Gson load/save) so the file has a clean JSON structure.

### How the config is loaded

- **Holder:** **PrismAttacksConfigHolder** (in `com.factioncore.config`) owns the config path and in-memory **PrismAttacksConfig**. Main keeps a single instance and exposes it via **getPrismAttacksConfig()**; callers use **getPrismAttacksConfig().get()** to read the config.
- **When:** During plugin setup (in **Main.setup()**), after ClaimConfig and PrismMultiplierConfig, the holder’s **load()** and **save()** are called (each returns a **CompletableFuture**; Main calls **.join()** to wait). So the file is read (and optionally written) once at server start.
- **Serialization:** The file is plain JSON. **Gson** is used to deserialize into **PrismAttacksConfig** (and nested **PrefabEntry**, **PositionEntry**). No Hytale codec is used; the JSON keys match the Java field names (`attackerTimeSeconds`, `prefabs`, `prefabId`, `attackersPositions`, `defendersPositions`).
- **If the file does not exist:** The holder creates it. It sets in-memory config to default values (**attackerTimeSeconds** 60, one **PrefabEntry** with the default prefab and spawn position lists), ensures the plugin data directory exists (**Files.createDirectories** on the parent path), then writes the default config as JSON to **prism_attacks.json**. No manual creation is required.
- **If load or save fails:** The holder sets in-memory config to a default instance and throws. Main catches the exception, logs **"Failed to load/save PrismAttacksConfig: …"** (with cause), and continues; the plugin uses the in-memory defaults until the next successful load.
- **Saving:** **save()** writes the current in-memory config back to **prism_attacks.json** (e.g. after load to create or overwrite the file). Both load and save run on a single-threaded IO executor to avoid concurrent file access.

### Structure (prism_attacks.json)

- **attackerTimeSeconds** (int, default 60): **Join window** in seconds: time during which players can join the attack or defense before combat starts. When this timer reaches 0, the attack status becomes **in_progress** (no new joins).
- **timeBeforeAutoDestruction** (int, default 600): Seconds after attack start before the attack world is **auto-removed** and resolution runs (**defenders win**: participants teleported back, prism **not** removed). This is independent of the join window.
- **prefabs** (array of objects): Each entry has:
  - **prefabId** (string): Server prefab key (e.g. `prefab_prism.prefab.json`). One prefab is chosen at random per attack. Resolved via **PrefabStore.get().getServerPrefab(prefabKey)** when pasting the arena. The prefab **must** include an NPC with role id **Prism_Attacked_NPC_Role** so that killing that NPC ends the battle with attackers win.
  - **attackersPositions** (array of `{ "x", "y", "z" }`): Optional. Coordinates for attacker spawns. One position is randomly chosen per joining attacker. If missing or empty, a hardcoded fallback list is used.
  - **defendersPositions** (array of `{ "x", "y", "z" }`): Optional. Coordinates for defender spawns. One position is randomly chosen per joining defender. If missing or empty, a hardcoded fallback list is used.

Example (config-based positions):

```json
{
  "attackerTimeSeconds": 60,
  "timeBeforeAutoDestruction": 600,
  "prefabs": [
    {
      "prefabId": "spawn_alan_prism.prefab.json",
      "attackersPositions": [{ "x": 0, "y": 1, "z": 0 }, { "x": 1, "y": 1, "z": 0 }, { "x": 2, "y": 1, "z": 0 }],
      "defendersPositions": [{ "x": 0, "y": 2, "z": 0 }, { "x": 1, "y": 2, "z": 0 }, { "x": 2, "y": 2, "z": 0 }]
    }
  ]
}
```

Spawn positions are defined only by **attackersPositions** and **defendersPositions** in the config. If either is missing or empty for the chosen prefab, a hardcoded fallback list (e.g. `0,1,0;1,1,0` for attackers, `0,2,0;1,2,0` for defenders) is used. When an attacker or defender joins, they are assigned a **random** free position from the role’s list; that position is persisted in `prism_attack_participants`, so the next joiner gets another random free slot.

---

## Key classes and files

| Class / File | Role |
|--------------|------|
| **PrismAttacksConfig** | POJO for prism attack settings: attackerTimeSeconds (join window), timeBeforeAutoDestruction (world removal), list of PrefabEntry (prefabId, attackersPositions, defendersPositions). Spawn positions come only from these position lists. Serialized/deserialized by Gson. |
| **PrismAttacksConfigHolder** | Loads and saves prism_attacks.json with Gson. get() returns current config; load()/save() run on IO executor. Creates file with defaults if missing. |
| **PrismAttackService** | Cache of active attacks (by prism id and attack id). startAttack (create world, paste prefab, insert attack, dispatch event). assignParticipantSlot (with return position), updateAttackStatusToActive. getAttackRecordByWorldName(worldName) returns attack by world name (any status) for damage gating. Two timers: combat start (status → in_progress, build attacker death map, clear HUD) and destruction (resolveAttackDefendersWin: teleport back, remove world). resolveAttackAttackersWin (prism NPC killed): teleport back, award FP, remove prism, remove world. resolveAttackDefendersWin (all attackers dead or timer): teleport back, remove world only. markAttackerDeadAndCheckAllDead(attackId, playerUuid) for death resolution. Startup recovery (load PENDING_START/ACTIVE/in_progress, CANCELLED if world missing, reschedule combat start and/or destruction). |
| **PrismAttackEntityDamageProtectionSystem** | DamageEventSystem in filter phase. Cancels entity damage (players/NPCs) in prism attack world when status is not in_progress; allows damage when status is in_progress. |
| **PrismAttackDeathResolutionSystem** | RefChangeSystem for DeathComponent. When an entity gains DeathComponent in a prism attack world (IN_PROGRESS): if player and attacker, mark dead and resolve defenders win when all attackers dead; if NPC with role id Prism_Attacked_NPC_Role, resolve attackers win. |
| **PrismAttackConstants** | PRISM_ATTACKED_NPC_ROLE_ID: role id that the prism NPC in the attack prefab must have. |
| **PlayerJoinPrismAttackEvent** | Payload: playerRef, ref, store, prismId, role ("attacker" / "defender"). |
| **PrismAttackEventDispatcher** | Singleton; addHandler(PrismAttackEventHandler), dispatch(PlayerJoinPrismAttackEvent). |
| **PrismAttackEventHandlerImpl** | Implements PrismAttackEventHandler. Pre-teleport checks, assignParticipantSlot, then currentWorld.execute(add Teleport). Sends red message to player on failure. |
| **PrismAttackRecord** / **PrismAttackParticipantRecord** | Immutable records for DB rows (used by DatabaseManager and PrismAttackService). |
| **BlockUseOpenUISystem** | Ally check: if interactor is in an **ally** of the prism’s faction, open PrismBlockPage (no Attack). Only non-owner and non-ally get OtherPrismPage with Attack. |
| **OtherPrismPage** | On Attack click: PrismAttackService.startAttack(prismId, playerRef, ref, store); then close page. |
| **FactionPrismsPage** | Build: multiplier label and Defend button (visible when prism status is `attacked`). handleDataEvent "defend": dispatch PlayerJoinPrismAttackEvent with role defender, close page. |
| **PrismRow.ui** | Added #MultiplierLabel and #DefendButton (Visible: false by default). |
| **DatabaseManager** | prism status migration and load/insert/updatePrismStatus; prism_attacks / prism_attack_participants tables and all related methods. |
| **PrismManager** | updateStatus(prismId, status); put(..., status). |
| **PrismService** | setBlockToAirStatic(world, x, y, z) used by PrismAttackService when attackers win. |

---

## Startup recovery

On plugin load, **PrismAttackService.initializeAsync()**:

1. Loads from **prism_attacks** where status IN (`PENDING_START`, `ACTIVE`, `in_progress`).
2. For each row: if **Universe.getWorld(attack_world_name)** is null, set status to **CANCELLED**, set prism status back to **harvesting**, and skip.
3. If the world exists: put the attack in the in-memory cache. If now ≥ **destruction_at**, call **resolveAttackDefendersWin** immediately. Else if now ≥ **ends_at**, set cache status to **in_progress** and schedule only **destruction** at `destruction_at`. Else schedule both **combat start** at `ends_at` and **destruction** at `destruction_at`.

---

## HUD (MultipleHUD mod)

When the **MultipleHUD** mod is present, all players in an **ACTIVE** prism attack (in the attack world) see a countdown HUD: **"XX seconds before the attack starts"** (key **ui.prism_attack.timer_seconds**). The HUD is updated every second by **PrismAttackHudTicker**. When the join timer reaches 0 (status becomes **in_progress**), **PrismAttackHudHelper.clearHudForAttack** removes the HUD for that attack’s participants. The plugin runs without the mod; the HUD is simply not shown if MultipleHUD is not loaded.

## I18n keys

- **ui.faction_prisms.multiplier**: Multiplier label in the prism list row (e.g. "Multiplier: {0}").
- **ui.prism_attack.timer_seconds**: HUD countdown text (e.g. "{0} seconds before the attack starts"). Parameter: remaining seconds.
- **ui.prism_attack.***: Error messages used by PrismAttackEventHandlerImpl when a pre-teleport check fails (e.g. no_prism, attack_not_found, must_be_in_faction, defender_must_be_faction_or_ally, world_not_found, no_slot_or_already_joined, etc.). Defined in `lang/en.json` and `lang/fr.json`.

---

## Summary

- **Attacker:** Use other faction’s prism block → Attack → startAttack creates (or reuses) attack world and dispatches event → handler assigns slot and teleports. Battle ends when: prism NPC killed (attackers win), all attackers killed (defenders win), or destruction timer (defenders win).
- **Defender:** Faction Prisms list → Defend on an attacked prism → dispatch event → handler assigns defender slot and teleports.
- **Persistence:** prism status column; prism_attacks and prism_attack_participants tables; in-memory cache and startup recovery.
- **Config:** prism_attacks.json (timer, prefab list, attacker/defender spawn positions).
- **Safety:** Single place for teleport (event handler), validate then schedule, DB as source of truth, ref/store from the player’s current world only. Return position is saved when joining and used to teleport players back when the attack ends.
