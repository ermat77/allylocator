# Ally and Member Permission System

This document describes how FactionCore decides whether a player can perform an action (e.g. open chest, place block, break block) in a **claimed chunk**. Permissions are split into two systems: **member permissions** (same-faction) and **ally permissions** (allied faction).

---

## Overview

- **Claim owner** = the faction that owns the chunk.
- **Actor** = the player performing the action (or null for global events like explosions).

The same code path is used for all interaction types. The decision flow is:

1. **Admin bypass** — If the actor has `factioncore.admin.bypassProtection` (or `factioncore.admin.*`), the action is allowed.
2. **Unclaimed chunk** — Allowed.
3. **No actor (global event)** — Denied in claimed chunks (e.g. explosions).
4. **Actor not in any faction** — Denied in claimed chunks.
5. **Same faction** — If the actor’s faction is the claim owner: **member permissions** apply (see below).
6. **Different faction** — If the actor’s faction is an **ally** of the claim owner: **ally permissions** apply (see below). Otherwise denied.

---

## Member Permissions (Same-Faction)

When the actor is in the **same faction** that owns the chunk:

- **Faction owner** (the player who created the faction) is **always allowed** for all interaction types. No permission check.
- **Other members** are allowed only if they have the corresponding **member permission** for that faction.

### Permission strings

Member permissions use the prefix `factioncore.ownfaction.` plus a **permission key**:

| Permission key   | Full permission string              | Meaning in claims        |
|------------------|--------------------------------------|--------------------------|
| `open_chest`     | `factioncore.ownfaction.open_chest`   | Open chests              |
| `open_door`      | `factioncore.ownfaction.open_door`    | Open doors               |
| `open_bench`     | `factioncore.ownfaction.open_bench`   | Open workbenches         |
| `use_chair`      | `factioncore.ownfaction.use_chair`    | Use chairs               |
| `use_portal`     | `factioncore.ownfaction.use_portal`   | Use portals/teleporters  |
| `place_block`    | `factioncore.ownfaction.place_block`  | Place blocks             |
| `break_block`    | `factioncore.ownfaction.break_block`  | Break/damage blocks      |

Command-only member permissions (granted by owner, checked for /f commands):

| Permission key   | Full permission string              | Meaning                    |
|------------------|--------------------------------------|----------------------------|
| `set_home`       | `ownfaction.set_home`                | Set faction home (/f sethome) |
| `home`           | `ownfaction.home`                    | Teleport to faction home (/f home) |

Permissions are **per faction and per member**: the same player can have different permissions in different factions. The check uses the **faction that owns the chunk** as context (faction UUID).

### Storage and API

- **Storage**: In-memory map in `FactionManager`, keyed by `factionId_memberUuid`. Persisted to the database and loaded at startup.
- **API**: `FactionPermissionManager.getInstance().hasPermission(memberUuid, permission, factionId)` delegates to `FactionManager.hasMemberPermission(factionId, memberUuid, permission)`.
- **Configuration**: In-game via the faction UI → **Member permissions** (per-member toggles for each permission). The faction owner can grant/revoke these.

---

## Ally Permissions (Allied Faction)

When the actor is in a **different faction** that is **allied** with the claim owner, the action is allowed only if the **claim owner’s ally settings** grant that permission for the ally faction.

- **Direction**: Permissions are **unidirectional**. “Faction A’s ally settings for Faction B” = what **A** allows **B** to do in **A’s** claims. B’s settings for A do not affect what A’s members can do in B’s claims.
- **Who configures**: The **chunk owner** (claim owner) opens **Diplomacy → [ally name] → Ally settings** and toggles what that ally is allowed to do in their claims.

### AllySettings flags

Each ally relationship (from the claim owner’s perspective) has an `AllySettings` object with:

| Setting                               | Effect in claim owner’s chunks        |
|---------------------------------------|--------------------------------------|
| Share player markers                  | Map/marker visibility (separate from interactions). |
| Allow interactions in claimed chunks  | Master switch; individual toggles below still apply per type. |
| Allow open chest                     | Open chests                          |
| Allow open door                      | Open doors                           |
| Allow open bench                     | Open workbenches                     |
| Allow use chair                      | Use chairs                           |
| Allow use portal                     | Use portals/teleporters              |
| Allow place block                    | Place blocks                         |
| Allow break block                    | Break/damage blocks                  |

If there is **no** ally settings entry for (claim owner, ally faction), the default is effectively “no permissions” (all off). The claim owner must open Ally settings for that ally and enable the desired toggles.

### Mapping to protection checks

Protection code uses a **permission key** (e.g. `open_chest`, `break_block`). The key is mapped to the corresponding `AllySettings` getter in `ProtectionService.isAllyAllowedForPermissionKey(settings, interactPermissionKey)`:

- `open_chest` → `settings.isAllowOpenChest()`
- `open_door` → `settings.isAllowOpenDoor()`
- `open_bench` → `settings.isAllowOpenBench()`
- `use_chair` → `settings.isAllowUseChair()`
- `use_portal` → `settings.isAllowUsePortal()`
- `place_block` → `settings.isAllowPlaceBlock()`
- `break_block` → `settings.isAllowBreakBlock()`

### Storage and API

- **Storage**: In-memory map in `FactionManager`, keyed by `factionId_allyFactionId` (claim owner ID first, ally faction ID second). Persisted to the database and loaded at startup.
- **API**: `FactionManager.getAllySettings(chunkOwnerFactionId, allyFactionId)` returns the chunk owner’s settings for that ally. If no entry exists, a new default (all false) is returned.
- **Configuration**: In-game via **Diplomacy → [ally] → Ally settings**. Only the chunk owner’s settings for that ally are used when the ally acts in the owner’s claims.

---

## Summary Table

| Actor relation to claim owner | Who is checked                    | Where to configure                          |
|------------------------------|------------------------------------|---------------------------------------------|
| Same faction, owner          | No check (always allowed)         | —                                           |
| Same faction, member         | Member permission for that faction | Faction UI → Member permissions             |
| Allied faction               | Ally settings (chunk owner → ally) | Diplomacy → [ally] → Ally settings          |
| Other / no faction           | Denied                             | —                                           |

---

## Code References

- **Protection entry point**: `ProtectionService.isAllowedToInteract(playerUuid, dimension, blockX, blockZ, interactPermissionKey)` — use the **String** overload with a constant from `PermissionUtil` (e.g. `INTERACT_KEY_BREAK_BLOCK`). This is the canonical path that applies both member and ally logic.
- **Member permissions**: `FactionPermissionManager.hasPermission(memberUuid, "factioncore.ownfaction.<key>", factionId)`; data in `FactionManager` (DB-backed).
- **Ally permissions**: `FactionManager.getAllySettings(chunkOwnerFactionId, allyFactionId)`; mapping from permission key to `AllySettings` in `ProtectionService.isAllyAllowedForPermissionKey`.
- **Constants**: Permission keys and full permission strings are defined in `PermissionUtil` (e.g. `INTERACT_KEY_*`, `FACTION_PERM_*`).
